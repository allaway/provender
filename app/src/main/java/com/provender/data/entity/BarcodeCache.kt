package com.provender.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-first barcode lookups (SPEC §2 Tier 1): seeded from Open Food Facts on cache miss,
 * then kept forever so repeat scans work offline.
 */
@Entity(tableName = "barcode_cache")
data class BarcodeCache(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val fetchedAt: Long,
)
