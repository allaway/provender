package com.provender.ui.capture

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.provender.network.BarcodeProduct
import com.provender.testing.FakeBarcodeLookupRepository
import com.provender.testing.FakeInventoryRepository
import com.provender.testing.FakeSnapshotRepository
import com.provender.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var snapshots: FakeSnapshotRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var barcodes: FakeBarcodeLookupRepository
    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setUp() {
        snapshots = FakeSnapshotRepository()
        inventory = FakeInventoryRepository()
        barcodes = FakeBarcodeLookupRepository()
        viewModel = CaptureViewModel(snapshots, inventory, barcodes)
    }

    @Test
    fun `defaults to the first location once loaded`() = runTest {
        viewModel.uiState.test {
            val loaded = awaitUntil { it.locations.isNotEmpty() }
            assertEquals(1L, loaded.selectedLocationId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `captured photos accumulate and analyze hands them to the repository`() = runTest {
        viewModel.uiState.test {
            awaitUntil { it.locations.isNotEmpty() }

            viewModel.onPhotoCaptured("/a/1.jpg")
            viewModel.onPhotoCaptured("/a/2.jpg")
            viewModel.onLocationSelected(2L)
            viewModel.onAnalyze()

            val navigating = awaitUntil { it.navigateToSnapshotId != null }
            assertEquals(1L, navigating.navigateToSnapshotId)
            assertEquals(listOf("analyze:2:2photos"), snapshots.calls)

            viewModel.onNavigationHandled()
            awaitUntil { it.navigateToSnapshotId == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `analyze without photos is a no-op`() = runTest {
        viewModel.uiState.test {
            awaitUntil { it.locations.isNotEmpty() }

            viewModel.onAnalyze()

            assertTrue(snapshots.calls.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `known barcode shows the product and adds it to inventory`() = runTest {
        barcodes.products["123"] = BarcodeProduct(name = "Black beans", brand = "Acme")

        viewModel.uiState.test {
            awaitUntil { it.locations.isNotEmpty() }

            viewModel.onBarcodeDetected("123")
            val overlay = awaitUntil { it.barcodeOverlay != null }
            assertEquals("Black beans", overlay.barcodeOverlay!!.product!!.name)

            viewModel.onAddBarcodeItem()
            val added = awaitUntil { it.barcodeOverlay == null && it.message != null }
            assertEquals("Added \"Black beans\"", added.message)
            assertEquals(listOf("add:Black beans"), inventory.mutations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown barcode shows the not-found overlay`() = runTest {
        viewModel.uiState.test {
            awaitUntil { it.locations.isNotEmpty() }

            viewModel.onBarcodeDetected("999")

            val overlay = awaitUntil { it.barcodeOverlay != null }
            assertNull(overlay.barcodeOverlay!!.product)
            assertEquals(listOf("999"), barcodes.lookups)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeat scans of the same code are debounced while the overlay shows`() = runTest {
        barcodes.products["123"] = BarcodeProduct(name = "Beans")

        viewModel.uiState.test {
            awaitUntil { it.locations.isNotEmpty() }

            viewModel.onBarcodeDetected("123")
            awaitUntil { it.barcodeOverlay != null }
            viewModel.onBarcodeDetected("123") // overlay open -> ignored
            viewModel.onDismissBarcode()
            viewModel.onBarcodeDetected("123") // same code within the debounce window -> ignored

            assertEquals(listOf("123"), barcodes.lookups)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `gallery picks are imported through the repository`() = runTest {
        viewModel.uiState.test {
            awaitUntil { it.locations.isNotEmpty() }

            viewModel.onGalleryPicked(listOf("content://photos/9"))

            val imported = awaitUntil { it.photoPaths.isNotEmpty() && !it.isImportingPhotos }
            assertEquals(1, imported.photoPaths.size)
            assertEquals(listOf("import:content://photos/9"), snapshots.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private suspend fun <T> TurbineTestContext<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
