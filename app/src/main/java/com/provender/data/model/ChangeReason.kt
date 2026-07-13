package com.provender.data.model

/** Why an [com.provender.data.entity.InventoryChange] row was written. */
enum class ChangeReason {
    /** Item added by hand. */
    MANUAL_ADD,

    /** Item edited by hand (includes staple toggles and other non-quantity edits, delta 0). */
    MANUAL_EDIT,

    /** Item deleted by hand. */
    MANUAL_DELETE,

    /** Item appeared in a snapshot (seed or diff commit). */
    SNAPSHOT_NEW,

    /** Missing from a snapshot and the user marked it consumed. */
    SNAPSHOT_CONSUMED,

    /** Quantity updated from a snapshot diff. */
    SNAPSHOT_QUANTITY,

    /** Missing from a snapshot and the user said it moved to another location (delta 0). */
    SNAPSHOT_MOVED,
}
