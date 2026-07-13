package com.provender.data.repository

import com.provender.data.entity.InventoryItem
import com.provender.data.entity.Snapshot
import kotlinx.coroutines.flow.Flow

/** Quantity update decision from the diff review. */
data class QuantityChangeDecision(
    val itemId: Long,
    val newQuantity: Double?,
    val newUnit: String?,
)

/** "It moved" decision from the diff review. */
data class MoveDecision(
    val itemId: Long,
    val toLocationId: Long,
)

/** Everything the user confirmed on the diff review screen (SPEC §3.2). */
data class DiffCommit(
    val adds: List<ItemDraft> = emptyList(),
    val quantityChanges: List<QuantityChangeDecision> = emptyList(),
    val consumedItemIds: List<Long> = emptyList(),
    /** Seen in photos unchanged, or missing-but-"still there": timestamps only, no change row. */
    val confirmedItemIds: List<Long> = emptyList(),
    val moves: List<MoveDecision> = emptyList(),
)

/**
 * Snapshot photo sessions: capture files, on-device analysis (OCR + VLM extraction), and
 * the seed-mode commit that turns confirmed rows into inventory (SPEC §3.1). Diffing against
 * existing inventory arrives in Phase 4.
 */
interface SnapshotRepository {

    fun observeSnapshot(id: Long): Flow<Snapshot?>

    /** Fresh app-private directory for one capture session's photos. */
    fun newSessionDir(): java.io.File

    /** Copies a gallery-picked content URI into [sessionDir]; returns the new file path. */
    suspend fun importPhoto(sessionDir: java.io.File, contentUri: String): String

    /**
     * Creates the snapshot row (ANALYZING) and kicks off background analysis that survives
     * navigation: downscale photos, OCR them, run the extraction prompt, store the result
     * on the row (READY) or a message (FAILED). Returns the snapshot id immediately.
     */
    suspend fun createAndAnalyze(locationId: Long, photoPaths: List<String>): Long

    /** Re-runs analysis on an existing (usually FAILED) snapshot. */
    suspend fun retryAnalysis(snapshotId: Long)

    /**
     * Seed-mode commit: inserts every confirmed draft as a new inventory item and writes a
     * SNAPSHOT_NEW change row per item (same transaction), then marks the snapshot COMMITTED.
     */
    suspend fun commitSeed(snapshotId: Long, drafts: List<ItemDraft>)

    /** Current items at a location, for diffing a READY snapshot against inventory. */
    suspend fun itemsAtLocation(locationId: Long): List<InventoryItem>

    /**
     * Diff-mode commit (SPEC §3.2), all in one transaction: adds write SNAPSHOT_NEW rows,
     * quantity changes SNAPSHOT_QUANTITY (delta = new - old), consumed items are deleted
     * with SNAPSHOT_CONSUMED (delta = -old), moves update the location with SNAPSHOT_MOVED
     * (delta 0), and confirmed items get lastSeen/lastConfirmed bumped with NO change row.
     */
    suspend fun commitDiff(snapshotId: Long, commit: DiffCommit)
}
