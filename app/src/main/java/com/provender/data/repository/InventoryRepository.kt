package com.provender.data.repository

import com.provender.data.entity.InventoryItem
import com.provender.data.entity.StorageLocation
import com.provender.data.model.Category
import kotlinx.coroutines.flow.Flow

/** One location plus its items, in display order. */
data class LocationSection(
    val location: StorageLocation,
    val items: List<InventoryItem>,
)

/** User-entered fields for creating or editing an item; the repository fills in the rest. */
data class ItemDraft(
    val name: String,
    val category: Category = Category.OTHER,
    val quantity: Double? = null,
    val unit: String? = null,
    val locationId: Long,
    val isStaple: Boolean = false,
    val notes: String? = null,
    /** Set when the item came from a barcode scan. */
    val barcode: String? = null,
)

/**
 * Inventory data access. Implementations must write an
 * [com.provender.data.entity.InventoryChange] row for every mutation, in the same
 * transaction as the mutation itself.
 */
interface InventoryRepository {

    /** All locations with their items, ordered by location sortOrder. Empty locations included. */
    fun observeSections(): Flow<List<LocationSection>>

    fun observeLocations(): Flow<List<StorageLocation>>

    /** FTS search over item names and notes; [rawQuery] is raw user input. */
    fun search(rawQuery: String): Flow<List<InventoryItem>>

    suspend fun addItem(draft: ItemDraft): Long

    suspend fun updateItem(itemId: Long, draft: ItemDraft)

    suspend fun deleteItem(itemId: Long)

    suspend fun setStaple(itemId: Long, isStaple: Boolean)

    /** Seeds Pantry / Fridge / Freezer / Spices on first run; no-op otherwise. */
    suspend fun seedDefaultLocationsIfEmpty()
}
