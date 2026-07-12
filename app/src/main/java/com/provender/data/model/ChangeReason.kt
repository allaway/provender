package com.provender.data.model

/** Why an [com.provender.data.entity.InventoryChange] row was written. */
enum class ChangeReason {
    /** Item added by hand. */
    MANUAL_ADD,

    /** Item edited by hand (includes staple toggles and other non-quantity edits, delta 0). */
    MANUAL_EDIT,

    /** Item deleted by hand. */
    MANUAL_DELETE,

    // Snapshot-driven reasons arrive with diffing (Phase 4):
    SNAPSHOT_NEW,
    SNAPSHOT_CONSUMED,
    SNAPSHOT_QUANTITY,
}
