package com.provender.testing

import com.provender.data.entity.InventoryItem
import com.provender.data.entity.StorageLocation
import com.provender.data.repository.InventoryRepository
import com.provender.data.repository.ItemDraft
import com.provender.data.repository.LocationSection
import com.provender.matching.NameNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory InventoryRepository for ViewModel tests; records mutation calls. */
class FakeInventoryRepository : InventoryRepository {

    val locationsFlow = MutableStateFlow(
        listOf(
            StorageLocation(id = 1, name = "Pantry", sortOrder = 0),
            StorageLocation(id = 2, name = "Fridge", sortOrder = 1),
        ),
    )
    val itemsFlow = MutableStateFlow<List<InventoryItem>>(emptyList())

    /** Human-readable log of mutations, oldest first, e.g. "add:Rice", "staple:3:true". */
    val mutations = mutableListOf<String>()

    private var nextId = 1L

    override fun observeSections(): Flow<List<LocationSection>> =
        combine(locationsFlow, itemsFlow) { locations, items ->
            val grouped = items.groupBy { it.locationId }
            locations.map { LocationSection(it, grouped[it.id].orEmpty()) }
        }

    override fun observeLocations(): Flow<List<StorageLocation>> = locationsFlow

    override fun search(rawQuery: String): Flow<List<InventoryItem>> {
        val needle = NameNormalizer.normalize(rawQuery)
        return itemsFlow.map { items ->
            if (needle.isEmpty()) emptyList()
            else items.filter { it.nameNormalized.contains(needle) }
        }
    }

    override suspend fun addItem(draft: ItemDraft): Long {
        val id = nextId++
        itemsFlow.value = itemsFlow.value + draft.toItem(id)
        mutations += "add:${draft.name.trim()}"
        return id
    }

    override suspend fun updateItem(itemId: Long, draft: ItemDraft) {
        itemsFlow.value = itemsFlow.value.map { if (it.id == itemId) draft.toItem(itemId) else it }
        mutations += "update:$itemId:${draft.name.trim()}"
    }

    override suspend fun deleteItem(itemId: Long) {
        itemsFlow.value = itemsFlow.value.filterNot { it.id == itemId }
        mutations += "delete:$itemId"
    }

    override suspend fun setStaple(itemId: Long, isStaple: Boolean) {
        itemsFlow.value = itemsFlow.value.map {
            if (it.id == itemId) it.copy(isStaple = isStaple) else it
        }
        mutations += "staple:$itemId:$isStaple"
    }

    override suspend fun seedDefaultLocationsIfEmpty() {
        mutations += "seed"
    }

    private fun ItemDraft.toItem(id: Long) = InventoryItem(
        id = id,
        name = name.trim(),
        nameNormalized = NameNormalizer.normalize(name),
        category = category,
        quantity = quantity,
        unit = unit,
        locationId = locationId,
        isStaple = isStaple,
        lastSeenAt = 0L,
        notes = notes,
    )
}
