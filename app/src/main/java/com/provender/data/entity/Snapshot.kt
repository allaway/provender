package com.provender.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SnapshotStatus {
    /** Photos handed off; on-device OCR + extraction running. */
    ANALYZING,

    /** Extraction finished; awaiting review on the Confirm screen. */
    READY,

    /** Extraction failed (model missing, unparseable output twice, …). */
    FAILED,

    /** Reviewed and applied to inventory. */
    COMMITTED,
}

/** One photo session of a single storage location (SPEC §4). */
@Entity(
    tableName = "snapshots",
    foreignKeys = [
        ForeignKey(
            entity = StorageLocation::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("locationId")],
)
data class Snapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: Long,
    val createdAt: Long,
    /** file:// URIs of the session's photos in app-private storage. */
    val photoUris: List<String>,
    val status: SnapshotStatus,
    /** Raw extraction result (JSON array of ExtractedItem) once READY. */
    val extractionJson: String? = null,
    /** Human-readable reason when FAILED. */
    val errorMessage: String? = null,
)
