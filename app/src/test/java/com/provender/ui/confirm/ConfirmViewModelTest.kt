package com.provender.ui.confirm

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.provender.ai.ExtractedItem
import com.provender.data.entity.Snapshot
import com.provender.data.entity.SnapshotStatus
import com.provender.data.model.Category
import com.provender.testing.FakeInventoryRepository
import com.provender.testing.FakeSnapshotRepository
import com.provender.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConfirmViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var snapshots: FakeSnapshotRepository
    private lateinit var inventory: FakeInventoryRepository

    @Before
    fun setUp() {
        snapshots = FakeSnapshotRepository()
        inventory = FakeInventoryRepository()
    }

    private fun snapshot(status: SnapshotStatus, extractionJson: String? = null) = Snapshot(
        id = 1L,
        locationId = 1L,
        createdAt = 0L,
        photoUris = emptyList(),
        status = status,
        extractionJson = extractionJson,
    )

    private fun viewModel() = ConfirmViewModel(
        savedStateHandle = SavedStateHandle(mapOf("snapshotId" to 1L)),
        snapshotRepository = snapshots,
        inventoryRepository = inventory,
    )

    @Test
    fun `rows populate once analysis turns READY`() = runTest {
        snapshots.setSnapshot(snapshot(SnapshotStatus.ANALYZING))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { it.snapshot?.status == SnapshotStatus.ANALYZING }

            snapshots.setSnapshot(
                snapshot(
                    SnapshotStatus.READY,
                    """[{"name":"black beans","quantity":3,"unit":"can","category":"canned","confidence":0.9}]""",
                ),
            )

            val ready = awaitUntil { it.rows.isNotEmpty() }
            val row = ready.rows.single()
            assertEquals("black beans", row.name)
            assertEquals(Category.CANNED, row.category)
            assertEquals(0.9f, row.confidence, 1e-6f)
            assertEquals("Pantry", ready.locationName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rows can be edited, added and deleted before commit`() = runTest {
        snapshots.setSnapshot(
            snapshot(SnapshotStatus.READY, """[{"name":"rice","confidence":0.6}]"""),
        )
        val vm = viewModel()

        vm.uiState.test {
            val ready = awaitUntil { it.rows.isNotEmpty() }
            val original = ready.rows.single()

            vm.onEditorSave(original.copy(name = "brown rice", quantity = 2.0, unit = "kg"))
            val edited = awaitUntil { it.rows.single().name == "brown rice" }
            assertEquals(2.0, edited.rows.single().quantity!!, 0.0)

            vm.onEditorSave(
                ConfirmRow(localId = vm.newLocalId(), name = "salt", confidence = 1f),
            )
            awaitUntil { it.rows.size == 2 }

            vm.onDeleteRow(original.localId)
            val afterDelete = awaitUntil { it.rows.size == 1 }
            assertEquals("salt", afterDelete.rows.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `commit hands drafts to the repository and flags completion`() = runTest {
        snapshots.setSnapshot(
            snapshot(
                SnapshotStatus.READY,
                """[{"name":"rice","confidence":0.6},{"name":"beans","confidence":0.9}]""",
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { it.rows.size == 2 }

            vm.onCommit()

            val done = awaitUntil { it.committed }
            assertTrue(done.committed)
            assertEquals(listOf("commit:1:2items"), snapshots.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry is forwarded for failed snapshots`() = runTest {
        snapshots.setSnapshot(snapshot(SnapshotStatus.FAILED))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { it.snapshot?.status == SnapshotStatus.FAILED }

            vm.onRetryAnalysis()

            awaitUntil { it.snapshot?.status == SnapshotStatus.ANALYZING }
            assertEquals(listOf("retry:1"), snapshots.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class ExtractedItemMappingTest {

    @Test
    fun `maps category by enum name or label case-insensitively`() {
        assertEquals(
            Category.CANNED,
            ExtractedItem(name = "beans", category = "canned").toConfirmRow(1).category,
        )
        assertEquals(
            Category.PRODUCE,
            ExtractedItem(name = "kale", category = "Produce").toConfirmRow(1).category,
        )
        assertEquals(
            Category.OTHER,
            ExtractedItem(name = "mystery", category = "weird-stuff").toConfirmRow(1).category,
        )
        assertEquals(
            Category.OTHER,
            ExtractedItem(name = "no category").toConfirmRow(1).category,
        )
    }
}

private suspend fun <T> TurbineTestContext<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
