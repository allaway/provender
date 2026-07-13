package com.provender.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.provender.ai.ExtractedItem
import com.provender.ai.LlmEngine
import com.provender.ai.LlmJson
import com.provender.ai.PhotoInput
import com.provender.data.ProvenderDatabase
import com.provender.data.entity.InventoryChange
import com.provender.data.entity.InventoryItem
import com.provender.data.entity.Snapshot
import com.provender.data.entity.SnapshotStatus
import com.provender.data.model.ChangeReason
import com.provender.di.ApplicationScope
import com.provender.di.IoDispatcher
import com.provender.matching.NameNormalizer
import com.provender.mlkit.OcrClient
import com.provender.mlkit.PhotoDownscaler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

@Singleton
class DefaultSnapshotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ProvenderDatabase,
    private val llmEngine: LlmEngine,
    private val ocrClient: OcrClient,
    private val downscaler: PhotoDownscaler,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SnapshotRepository {

    private val snapshotDao = database.snapshotDao()
    private val itemDao = database.inventoryItemDao()
    private val changeDao = database.inventoryChangeDao()

    override fun observeSnapshot(id: Long): Flow<Snapshot?> = snapshotDao.observeById(id)

    override fun newSessionDir(): File =
        File(File(context.filesDir, SNAPSHOTS_DIR), "session-${System.currentTimeMillis()}")
            .apply { mkdirs() }

    override suspend fun importPhoto(sessionDir: File, contentUri: String): String =
        withContext(ioDispatcher) {
            val target = File(sessionDir, "import-${System.currentTimeMillis()}.jpg")
            val input = context.contentResolver.openInputStream(Uri.parse(contentUri))
                ?: throw IOException("Could not open the selected photo")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            target.absolutePath
        }

    override suspend fun createAndAnalyze(locationId: Long, photoPaths: List<String>): Long {
        val id = snapshotDao.insert(
            Snapshot(
                locationId = locationId,
                createdAt = System.currentTimeMillis(),
                photoUris = photoPaths.map { "file://$it" },
                status = SnapshotStatus.ANALYZING,
            ),
        )
        // Analysis is a background job owned by the app scope, not the screen: it keeps
        // running if the user navigates away (SPEC §2 — never a blocking spinner).
        applicationScope.launch { analyze(id) }
        return id
    }

    override suspend fun retryAnalysis(snapshotId: Long) {
        val snapshot = snapshotDao.getById(snapshotId) ?: return
        snapshotDao.update(
            snapshot.copy(status = SnapshotStatus.ANALYZING, errorMessage = null),
        )
        applicationScope.launch { analyze(snapshotId) }
    }

    private suspend fun analyze(snapshotId: Long) {
        val snapshot = snapshotDao.getById(snapshotId) ?: return
        val paths = snapshot.photoUris.map { it.removePrefix("file://") }
        val result = withContext(ioDispatcher) {
            runCatching {
                val smallPaths = paths.map { downscaler.downscale(it) }
                val ocrText = paths
                    .joinToString("\n") { ocrClient.recognizeText(it) }
                    .trim()
                    .takeIf { it.isNotBlank() }
                llmEngine
                    .extractInventory(smallPaths.map { PhotoInput("file://$it") }, ocrText)
                    .getOrThrow()
            }
        }
        val current = snapshotDao.getById(snapshotId) ?: return
        result
            .onSuccess { items ->
                snapshotDao.update(
                    current.copy(
                        status = SnapshotStatus.READY,
                        extractionJson = LlmJson.json.encodeToString(
                            ListSerializer(ExtractedItem.serializer()),
                            items,
                        ),
                        errorMessage = null,
                    ),
                )
            }
            .onFailure { error ->
                snapshotDao.update(
                    current.copy(
                        status = SnapshotStatus.FAILED,
                        errorMessage = error.message ?: "Analysis failed",
                    ),
                )
            }
    }

    override suspend fun commitSeed(snapshotId: Long, drafts: List<ItemDraft>) {
        database.withTransaction {
            val snapshot = snapshotDao.getById(snapshotId)
                ?: error("No snapshot with id $snapshotId")
            val now = System.currentTimeMillis()
            drafts.forEach { draft -> insertFromSnapshot(snapshot, draft, now) }
            snapshotDao.update(snapshot.copy(status = SnapshotStatus.COMMITTED))
        }
    }

    override suspend fun itemsAtLocation(locationId: Long): List<InventoryItem> =
        itemDao.listByLocation(locationId)

    override suspend fun commitDiff(snapshotId: Long, commit: DiffCommit) {
        database.withTransaction {
            val snapshot = snapshotDao.getById(snapshotId)
                ?: error("No snapshot with id $snapshotId")
            val now = System.currentTimeMillis()

            commit.adds.forEach { draft -> insertFromSnapshot(snapshot, draft, now) }

            commit.quantityChanges.forEach { change ->
                val old = itemDao.getById(change.itemId) ?: return@forEach
                itemDao.update(
                    old.copy(
                        quantity = change.newQuantity,
                        unit = change.newUnit ?: old.unit,
                        lastSeenAt = now,
                        lastConfirmedAt = now,
                    ),
                )
                changeDao.insert(
                    InventoryChange(
                        itemId = old.id,
                        itemName = old.name,
                        snapshotId = snapshotId,
                        delta = (change.newQuantity ?: 0.0) - (old.quantity ?: 0.0),
                        reason = ChangeReason.SNAPSHOT_QUANTITY,
                        createdAt = now,
                    ),
                )
            }

            commit.consumedItemIds.forEach { itemId ->
                val old = itemDao.getById(itemId) ?: return@forEach
                itemDao.delete(old)
                changeDao.insert(
                    InventoryChange(
                        itemId = old.id,
                        itemName = old.name,
                        snapshotId = snapshotId,
                        delta = -(old.quantity ?: 0.0),
                        reason = ChangeReason.SNAPSHOT_CONSUMED,
                        createdAt = now,
                    ),
                )
            }

            // SPEC §3.2: "still there but hidden" (and unchanged matches) update
            // last_confirmed_at without a change entry.
            commit.confirmedItemIds.forEach { itemId ->
                val old = itemDao.getById(itemId) ?: return@forEach
                itemDao.update(old.copy(lastSeenAt = now, lastConfirmedAt = now))
            }

            commit.moves.forEach { move ->
                val old = itemDao.getById(move.itemId) ?: return@forEach
                itemDao.update(
                    old.copy(locationId = move.toLocationId, lastSeenAt = now, lastConfirmedAt = now),
                )
                changeDao.insert(
                    InventoryChange(
                        itemId = old.id,
                        itemName = old.name,
                        snapshotId = snapshotId,
                        delta = 0.0,
                        reason = ChangeReason.SNAPSHOT_MOVED,
                        createdAt = now,
                    ),
                )
            }

            snapshotDao.update(snapshot.copy(status = SnapshotStatus.COMMITTED))
        }
    }

    /** Must run inside a transaction. */
    private suspend fun insertFromSnapshot(snapshot: Snapshot, draft: ItemDraft, now: Long) {
        val name = draft.name.trim()
        val itemId = itemDao.insert(
            InventoryItem(
                name = name,
                nameNormalized = NameNormalizer.normalize(name),
                category = draft.category,
                quantity = draft.quantity,
                unit = draft.unit?.trim()?.takeIf { it.isNotEmpty() },
                locationId = snapshot.locationId,
                isStaple = draft.isStaple,
                lastSeenAt = now,
                lastConfirmedAt = now,
                notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        changeDao.insert(
            InventoryChange(
                itemId = itemId,
                itemName = name,
                snapshotId = snapshot.id,
                delta = draft.quantity ?: 1.0,
                reason = ChangeReason.SNAPSHOT_NEW,
                createdAt = now,
            ),
        )
    }

    private companion object {
        const val SNAPSHOTS_DIR = "snapshots"
    }
}
