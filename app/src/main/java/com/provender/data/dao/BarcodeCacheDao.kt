package com.provender.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.provender.data.entity.BarcodeCache

@Dao
interface BarcodeCacheDao {

    @Query("SELECT * FROM barcode_cache WHERE barcode = :barcode")
    suspend fun get(barcode: String): BarcodeCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BarcodeCache)
}
