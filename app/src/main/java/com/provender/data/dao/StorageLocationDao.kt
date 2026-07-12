package com.provender.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.provender.data.entity.StorageLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageLocationDao {

    @Query("SELECT * FROM storage_locations ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<StorageLocation>>

    @Query("SELECT COUNT(*) FROM storage_locations")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(locations: List<StorageLocation>)
}
