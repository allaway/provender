package com.provender.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.provender.data.model.Category

@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = StorageLocation::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("locationId"),
        Index("nameNormalized"),
    ],
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Canonical form (lowercased, trimmed, singularized) used for matching and sorting. */
    val nameNormalized: String,
    val category: Category = Category.OTHER,
    /** Null when the amount is fuzzy ("some", "low", "plenty"). */
    val quantity: Double? = null,
    val unit: String? = null,
    val locationId: Long,
    /** Staples (oil, salt, flour…) are assumed available for matching unless marked out. */
    val isStaple: Boolean = false,
    /** Epoch millis of the last time the item was seen or edited. */
    val lastSeenAt: Long,
    /** Epoch millis of the last explicit confirmation ("still there"); null until confirmed. */
    val lastConfirmedAt: Long? = null,
    val notes: String? = null,
    val barcode: String? = null,
)
