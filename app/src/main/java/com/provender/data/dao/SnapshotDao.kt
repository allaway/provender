package com.provender.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.provender.data.entity.Snapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {

    @Insert
    suspend fun insert(snapshot: Snapshot): Long

    @Update
    suspend fun update(snapshot: Snapshot)

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun getById(id: Long): Snapshot?

    @Query("SELECT * FROM snapshots WHERE id = :id")
    fun observeById(id: Long): Flow<Snapshot?>

    @Query("SELECT COUNT(*) FROM snapshots WHERE locationId = :locationId AND status = 'COMMITTED'")
    suspend fun committedCountForLocation(locationId: Long): Int
}
