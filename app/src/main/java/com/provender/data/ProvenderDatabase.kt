package com.provender.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.provender.data.dao.InventoryChangeDao
import com.provender.data.dao.InventoryItemDao
import com.provender.data.dao.StorageLocationDao
import com.provender.data.entity.InventoryChange
import com.provender.data.entity.InventoryItem
import com.provender.data.entity.InventoryItemFts
import com.provender.data.entity.StorageLocation

@Database(
    entities = [
        InventoryItem::class,
        InventoryItemFts::class,
        StorageLocation::class,
        InventoryChange::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ProvenderDatabase : RoomDatabase() {
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun inventoryChangeDao(): InventoryChangeDao

    companion object {
        const val NAME = "provender.db"
    }
}
