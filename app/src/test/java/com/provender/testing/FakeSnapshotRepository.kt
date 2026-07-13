package com.provender.testing

import com.provender.data.entity.Snapshot
import com.provender.data.entity.SnapshotStatus
import com.provender.data.repository.ItemDraft
import com.provender.data.repository.SnapshotRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory SnapshotRepository for ViewModel tests; records calls. */
class FakeSnapshotRepository : SnapshotRepository {

    val snapshots = MutableStateFlow<Map<Long, Snapshot>>(emptyMap())

    /** Calls recorded oldest-first, e.g. "analyze:1:2photos", "commit:1:3items". */
    val calls = mutableListOf<String>()

    var nextSnapshotId = 1L

    private val sessionDir: File =
        File(System.getProperty("java.io.tmpdir"), "fake-session-${System.nanoTime()}")
            .apply { mkdirs() }

    fun setSnapshot(snapshot: Snapshot) {
        snapshots.value = snapshots.value + (snapshot.id to snapshot)
    }

    override fun observeSnapshot(id: Long): Flow<Snapshot?> = snapshots.map { it[id] }

    override fun newSessionDir(): File = sessionDir

    override suspend fun importPhoto(sessionDir: File, contentUri: String): String {
        calls += "import:$contentUri"
        return File(sessionDir, "imported-${calls.size}.jpg").absolutePath
    }

    override suspend fun createAndAnalyze(locationId: Long, photoPaths: List<String>): Long {
        val id = nextSnapshotId++
        calls += "analyze:$locationId:${photoPaths.size}photos"
        setSnapshot(
            Snapshot(
                id = id,
                locationId = locationId,
                createdAt = 0L,
                photoUris = photoPaths.map { "file://$it" },
                status = SnapshotStatus.ANALYZING,
            ),
        )
        return id
    }

    override suspend fun retryAnalysis(snapshotId: Long) {
        calls += "retry:$snapshotId"
        snapshots.value[snapshotId]?.let {
            setSnapshot(it.copy(status = SnapshotStatus.ANALYZING, errorMessage = null))
        }
    }

    override suspend fun commitSeed(snapshotId: Long, drafts: List<ItemDraft>) {
        calls += "commit:$snapshotId:${drafts.size}items"
        snapshots.value[snapshotId]?.let {
            setSnapshot(it.copy(status = SnapshotStatus.COMMITTED))
        }
    }
}
