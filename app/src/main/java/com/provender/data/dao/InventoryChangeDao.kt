package com.provender.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.provender.data.entity.InventoryChange
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryChangeDao {

    @Insert
    suspend fun insert(change: InventoryChange): Long

    @Query("SELECT * FROM inventory_changes ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<InventoryChange>>

    @Query("SELECT * FROM inventory_changes WHERE itemId = :itemId ORDER BY createdAt DESC")
    suspend fun getForItem(itemId: Long): List<InventoryChange>
}
