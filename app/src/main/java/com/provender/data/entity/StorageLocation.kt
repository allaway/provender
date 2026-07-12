package com.provender.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage_locations")
data class StorageLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
)
