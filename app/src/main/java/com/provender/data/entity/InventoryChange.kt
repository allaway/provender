package com.provender.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.provender.data.model.ChangeReason

/**
 * Append-only change log (SPEC.md §3.2). Deliberately no foreign key on [itemId]: history
 * must survive item deletion. [itemName] is denormalized for the same reason.
 */
@Entity(
    tableName = "inventory_changes",
    indices = [Index("itemId"), Index("createdAt")],
)
data class InventoryChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val itemName: String,
    /** Snapshot that produced this change; null for manual edits (snapshots arrive Phase 3). */
    val snapshotId: Long? = null,
    /** Quantity delta; 0.0 for non-quantity edits (rename, staple toggle, …). */
    val delta: Double,
    val reason: ChangeReason,
    val createdAt: Long,
)
