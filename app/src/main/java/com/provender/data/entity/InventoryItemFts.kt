package com.provender.data.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 mirror of [InventoryItem] for item search (SPEC.md §4). Room keeps it in sync with
 * the content table automatically; its rowid equals `inventory_items.id`.
 */
@Fts4(contentEntity = InventoryItem::class)
@Entity(tableName = "inventory_items_fts")
data class InventoryItemFts(
    val name: String,
    val nameNormalized: String,
    val notes: String?,
)
