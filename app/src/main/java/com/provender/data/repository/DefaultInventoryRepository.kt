package com.provender.data.repository

import androidx.room.withTransaction
import com.provender.data.FtsQuery
import com.provender.data.ProvenderDatabase
import com.provender.data.entity.InventoryChange
import com.provender.data.entity.InventoryItem
import com.provender.data.entity.StorageLocation
import com.provender.data.model.ChangeReason
import com.provender.matching.NameNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@Singleton
class DefaultInventoryRepository @Inject constructor(
    private val database: ProvenderDatabase,
) : InventoryRepository {

    private val itemDao = database.inventoryItemDao()
    private val locationDao = database.storageLocationDao()
    private val changeDao = database.inventoryChangeDao()

    override fun observeSections(): Flow<List<LocationSection>> =
        combine(locationDao.observeAll(), itemDao.observeAll()) { locations, items ->
            val byLocation = items.groupBy { it.locationId }
            locations.map { location ->
                LocationSection(location, byLocation[location.id].orEmpty())
            }
        }

    override fun observeLocations(): Flow<List<StorageLocation>> = locationDao.observeAll()

    override fun search(rawQuery: String): Flow<List<InventoryItem>> {
        val ftsQuery = FtsQuery.fromUserInput(rawQuery) ?: return flowOf(emptyList())
        return itemDao.search(ftsQuery)
    }

    override suspend fun addItem(draft: ItemDraft): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val item = draft.toItem(id = 0, lastSeenAt = now)
        val id = itemDao.insert(item)
        changeDao.insert(
            InventoryChange(
                itemId = id,
                itemName = item.name,
                delta = draft.quantity ?: 1.0,
                reason = ChangeReason.MANUAL_ADD,
                createdAt = now,
            ),
        )
        id
    }

    override suspend fun updateItem(itemId: Long, draft: ItemDraft) {
        database.withTransaction {
            val old = requireNotNull(itemDao.getById(itemId)) { "No item with id $itemId" }
            val now = System.currentTimeMillis()
            val updated = draft.toItem(id = itemId, lastSeenAt = now)
                .copy(lastConfirmedAt = old.lastConfirmedAt, barcode = old.barcode)
            itemDao.update(updated)
            changeDao.insert(
                InventoryChange(
                    itemId = itemId,
                    itemName = updated.name,
                    delta = (draft.quantity ?: 0.0) - (old.quantity ?: 0.0),
                    reason = ChangeReason.MANUAL_EDIT,
                    createdAt = now,
                ),
            )
        }
    }

    override suspend fun deleteItem(itemId: Long) {
        database.withTransaction {
            val item = itemDao.getById(itemId) ?: return@withTransaction
            itemDao.delete(item)
            changeDao.insert(
                InventoryChange(
                    itemId = itemId,
                    itemName = item.name,
                    delta = -(item.quantity ?: 0.0),
                    reason = ChangeReason.MANUAL_DELETE,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun setStaple(itemId: Long, isStaple: Boolean) {
        database.withTransaction {
            val item = itemDao.getById(itemId) ?: return@withTransaction
            if (item.isStaple == isStaple) return@withTransaction
            val now = System.currentTimeMillis()
            itemDao.update(item.copy(isStaple = isStaple, lastSeenAt = now))
            changeDao.insert(
                InventoryChange(
                    itemId = itemId,
                    itemName = item.name,
                    delta = 0.0,
                    reason = ChangeReason.MANUAL_EDIT,
                    createdAt = now,
                ),
            )
        }
    }

    override suspend fun seedDefaultLocationsIfEmpty() {
        database.withTransaction {
            if (locationDao.count() > 0) return@withTransaction
            locationDao.insertAll(
                listOf("Pantry", "Fridge", "Freezer", "Spices").mapIndexed { index, name ->
                    StorageLocation(name = name, sortOrder = index)
                },
            )
        }
    }

    private fun ItemDraft.toItem(id: Long, lastSeenAt: Long) = InventoryItem(
        id = id,
        name = name.trim(),
        nameNormalized = NameNormalizer.normalize(name),
        category = category,
        quantity = quantity,
        unit = unit?.trim()?.takeIf { it.isNotEmpty() },
        locationId = locationId,
        isStaple = isStaple,
        lastSeenAt = lastSeenAt,
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    )
}
