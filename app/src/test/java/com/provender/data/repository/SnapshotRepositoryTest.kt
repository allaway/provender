package com.provender.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.provender.ai.ExtractedItem
import com.provender.ai.FakeLlmEngine
import com.provender.data.ProvenderDatabase
import com.provender.data.entity.Snapshot
import com.provender.data.entity.SnapshotStatus
import com.provender.data.entity.StorageLocation
import com.provender.data.model.Category
import com.provender.data.model.ChangeReason
import com.provender.mlkit.OcrClient
import com.provender.mlkit.PhotoDownscaler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapshotRepositoryTest {

    private class RecordingOcrClient : OcrClient {
        val calls = mutableListOf<String>()
        var text: String = "NOODLES 500g"
        override suspend fun recognizeText(photoPath: String): String {
            calls += photoPath
            return text
        }
    }

    private lateinit var db: ProvenderDatabase
    private lateinit var engine: FakeLlmEngine
    private lateinit var ocr: RecordingOcrClient
    private var locationId: Long = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ProvenderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = FakeLlmEngine()
        ocr = RecordingOcrClient()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher): DefaultSnapshotRepository =
        DefaultSnapshotRepository(
            context = ApplicationProvider.getApplicationContext(),
            database = db,
            llmEngine = engine,
            ocrClient = ocr,
            downscaler = PhotoDownscaler(),
            applicationScope = CoroutineScope(dispatcher),
            ioDispatcher = dispatcher,
        )

    private suspend fun seedLocation(): Long {
        db.storageLocationDao().insertAll(listOf(StorageLocation(id = 7, name = "Pantry", sortOrder = 0)))
        return 7L
    }

    @Test
    fun `analysis stores extraction results and flips status to READY`() = runTest {
        locationId = seedLocation()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = repository(dispatcher)

        val id = repository.createAndAnalyze(locationId, listOf("/no/such/photo.jpg"))
        testScheduler.advanceUntilIdle()

        val snapshot = db.snapshotDao().getById(id)!!
        assertEquals(SnapshotStatus.READY, snapshot.status)
        assertNotNull(snapshot.extractionJson)
        assertTrue(snapshot.extractionJson!!.contains("black beans"))
        // OCR ran on the photo and its text reached the engine.
        assertEquals(listOf("/no/such/photo.jpg"), ocr.calls)
        assertEquals("NOODLES 500g", engine.extractCalls.single().second)
    }

    @Test
    fun `engine failure flips status to FAILED with the message`() = runTest {
        locationId = seedLocation()
        engine.extractionResult = Result.failure(IllegalStateException("model not downloaded"))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = repository(dispatcher)

        val id = repository.createAndAnalyze(locationId, listOf("/p.jpg"))
        testScheduler.advanceUntilIdle()

        val snapshot = db.snapshotDao().getById(id)!!
        assertEquals(SnapshotStatus.FAILED, snapshot.status)
        assertEquals("model not downloaded", snapshot.errorMessage)
    }

    @Test
    fun `retry after failure re-runs analysis`() = runTest {
        locationId = seedLocation()
        engine.extractionResult = Result.failure(IllegalStateException("boom"))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = repository(dispatcher)
        val id = repository.createAndAnalyze(locationId, listOf("/p.jpg"))
        testScheduler.advanceUntilIdle()

        engine.extractionResult = Result.success(listOf(ExtractedItem(name = "rice")))
        repository.retryAnalysis(id)
        testScheduler.advanceUntilIdle()

        assertEquals(SnapshotStatus.READY, db.snapshotDao().getById(id)!!.status)
    }

    @Test
    fun `commitSeed inserts items with SNAPSHOT_NEW change rows in one shot`() = runTest {
        locationId = seedLocation()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = repository(dispatcher)
        val id = db.snapshotDao().insert(
            Snapshot(
                locationId = locationId,
                createdAt = 0L,
                photoUris = emptyList(),
                status = SnapshotStatus.READY,
                extractionJson = "[]",
            ),
        )

        repository.commitSeed(
            id,
            listOf(
                ItemDraft(name = " Black Beans ", category = Category.CANNED, quantity = 3.0, unit = "can", locationId = locationId),
                ItemDraft(name = "Rice", quantity = null, unit = "some", locationId = locationId),
            ),
        )

        val items = db.inventoryItemDao().observeAll().first()
        assertEquals(listOf("black bean", "rice"), items.map { it.nameNormalized })
        items.forEach { assertEquals(locationId, it.locationId) }

        val changes = db.inventoryChangeDao().observeRecent(10).first()
        assertEquals(2, changes.size)
        changes.forEach {
            assertEquals(ChangeReason.SNAPSHOT_NEW, it.reason)
            assertEquals(id, it.snapshotId)
        }
        assertEquals(SnapshotStatus.COMMITTED, db.snapshotDao().getById(id)!!.status)
    }

    @Test
    fun `commitDiff applies every decision type with the right change rows`() = runTest {
        locationId = seedLocation()
        db.storageLocationDao().insertAll(
            listOf(StorageLocation(id = 8, name = "Fridge", sortOrder = 1)),
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = repository(dispatcher)
        val itemDao = db.inventoryItemDao()

        suspend fun seedItem(name: String, quantity: Double?): Long = itemDao.insert(
            com.provender.data.entity.InventoryItem(
                name = name,
                nameNormalized = name.lowercase(),
                quantity = quantity,
                unit = "count",
                locationId = locationId,
                lastSeenAt = 1L,
            ),
        )

        val changedId = seedItem("beans", 3.0)
        val consumedId = seedItem("milk", 1.0)
        val stillThereId = seedItem("flour", 2.0)
        val movedId = seedItem("butter", 1.0)
        val snapshotId = db.snapshotDao().insert(
            Snapshot(
                locationId = locationId,
                createdAt = 0L,
                photoUris = emptyList(),
                status = SnapshotStatus.READY,
                extractionJson = "[]",
            ),
        )

        repository.commitDiff(
            snapshotId,
            DiffCommit(
                adds = listOf(ItemDraft(name = "salsa", quantity = 1.0, unit = "jar", locationId = locationId)),
                quantityChanges = listOf(QuantityChangeDecision(changedId, newQuantity = 1.0, newUnit = "can")),
                consumedItemIds = listOf(consumedId),
                confirmedItemIds = listOf(stillThereId),
                moves = listOf(MoveDecision(movedId, toLocationId = 8)),
            ),
        )

        // Add
        val all = itemDao.observeAll().first()
        assertTrue(all.any { it.name == "salsa" })
        // Quantity change
        val changed = itemDao.getById(changedId)!!
        assertEquals(1.0, changed.quantity!!, 0.0)
        assertEquals("can", changed.unit)
        // Consumed: gone from inventory
        assertEquals(null, itemDao.getById(consumedId))
        // Still there: timestamps bumped, nothing else
        val stillThere = itemDao.getById(stillThereId)!!
        assertTrue(stillThere.lastConfirmedAt != null && stillThere.lastSeenAt > 1L)
        assertEquals(2.0, stillThere.quantity!!, 0.0)
        // Moved
        assertEquals(8L, itemDao.getById(movedId)!!.locationId)

        val changes = db.inventoryChangeDao().observeRecent(20).first()
        val byReason = changes.groupBy { it.reason }
        assertEquals(1, byReason[ChangeReason.SNAPSHOT_NEW]!!.size)
        assertEquals(-2.0, byReason[ChangeReason.SNAPSHOT_QUANTITY]!!.single().delta, 1e-9)
        assertEquals(-1.0, byReason[ChangeReason.SNAPSHOT_CONSUMED]!!.single().delta, 1e-9)
        assertEquals(0.0, byReason[ChangeReason.SNAPSHOT_MOVED]!!.single().delta, 0.0)
        // Still-there confirmation wrote NO change row.
        assertEquals(4, changes.size)
        changes.forEach { assertEquals(snapshotId, it.snapshotId) }

        assertEquals(SnapshotStatus.COMMITTED, db.snapshotDao().getById(snapshotId)!!.status)
    }
}
