package com.provender.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.provender.data.entity.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryItemDao {

    @Query("SELECT * FROM inventory_items ORDER BY nameNormalized")
    fun observeAll(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE locationId = :locationId ORDER BY nameNormalized")
    fun observeByLocation(locationId: Long): Flow<List<InventoryItem>>

    /**
     * FTS search over name/normalized name/notes. [ftsQuery] must already be sanitized into
     * MATCH syntax (see FtsQuery) — raw user input is not valid MATCH input.
     */
    @Query(
        """
        SELECT inventory_items.* FROM inventory_items
        JOIN inventory_items_fts ON inventory_items.id = inventory_items_fts.rowid
        WHERE inventory_items_fts MATCH :ftsQuery
        ORDER BY inventory_items.nameNormalized
        """,
    )
    fun search(ftsQuery: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getById(id: Long): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE locationId = :locationId")
    suspend fun listByLocation(locationId: Long): List<InventoryItem>

    @Insert
    suspend fun insert(item: InventoryItem): Long

    @Update
    suspend fun update(item: InventoryItem)

    @Delete
    suspend fun delete(item: InventoryItem)
}
