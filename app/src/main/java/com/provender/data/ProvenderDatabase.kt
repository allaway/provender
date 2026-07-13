package com.provender.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.provender.data.dao.BarcodeCacheDao
import com.provender.data.dao.InventoryChangeDao
import com.provender.data.dao.InventoryItemDao
import com.provender.data.dao.SnapshotDao
import com.provender.data.dao.StorageLocationDao
import com.provender.data.entity.BarcodeCache
import com.provender.data.entity.InventoryChange
import com.provender.data.entity.InventoryItem
import com.provender.data.entity.InventoryItemFts
import com.provender.data.entity.Snapshot
import com.provender.data.entity.StorageLocation

// Still version 1: no build has ever shipped or exported a schema, so pre-release entity
// additions don't need migrations. Bump the version once app/schemas/1.json is committed.
@Database(
    entities = [
        InventoryItem::class,
        InventoryItemFts::class,
        StorageLocation::class,
        InventoryChange::class,
        Snapshot::class,
        BarcodeCache::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ProvenderDatabase : RoomDatabase() {
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun inventoryChangeDao(): InventoryChangeDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun barcodeCacheDao(): BarcodeCacheDao

    companion object {
        const val NAME = "provender.db"
    }
}
