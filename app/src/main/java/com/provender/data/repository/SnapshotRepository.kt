package com.provender.data.repository

import com.provender.data.entity.Snapshot
import kotlinx.coroutines.flow.Flow

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
}
