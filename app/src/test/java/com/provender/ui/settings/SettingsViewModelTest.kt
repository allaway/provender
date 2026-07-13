package com.provender.ui.settings

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.provender.ai.CapabilityChecker
import com.provender.ai.DeviceSpecs
import com.provender.ai.LlmBackend
import com.provender.ai.ModelState
import com.provender.ai.ModelVariant
import com.provender.testing.FakeAppSettingsRepository
import com.provender.testing.FakeModelRepository
import com.provender.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gb = 1024L * 1024 * 1024

    private lateinit var modelRepository: FakeModelRepository
    private lateinit var settings: FakeAppSettingsRepository

    @Before
    fun setUp() {
        modelRepository = FakeModelRepository()
        settings = FakeAppSettingsRepository()
    }

    private fun viewModel(totalRam: Long = 8 * gb) = SettingsViewModel(
        modelRepository = modelRepository,
        settings = settings,
        capabilityChecker = CapabilityChecker(
            DeviceSpecs(totalRamBytes = totalRam, supported64BitAbis = listOf("arm64-v8a")),
        ),
    )

    @Test
    fun `reflects defaults and capability once loaded`() = runTest {
        viewModel().uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(ModelVariant.E2B, loaded.selectedVariant)
            assertEquals(LlmBackend.CPU, loaded.backend)
            assertEquals(ModelState.NotDownloaded, loaded.modelState)
            assertTrue(loaded.capability.supported)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `low-ram device surfaces a capability warning`() = runTest {
        viewModel(totalRam = 3 * gb).uiState.test {
            val loaded = awaitUntil { !it.isLoading }
            assertTrue(!loaded.capability.supported)
            assertTrue(!loaded.capability.reason.isNullOrBlank())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `download click enqueues and state follows the repository`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { !it.isLoading }

            vm.onDownloadClick()

            val downloading = awaitUntil { it.modelState is ModelState.Downloading }
            assertEquals(ModelState.Downloading(null), downloading.modelState)
            assertEquals(listOf("download:E2B"), modelRepository.calls)

            modelRepository.setState(ModelVariant.E2B, ModelState.Downloaded(999L))
            awaitUntil { it.modelState == ModelState.Downloaded(999L) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching variant switches the observed model state`() = runTest {
        modelRepository.setState(ModelVariant.E2B, ModelState.Downloaded(1L))
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { it.modelState == ModelState.Downloaded(1L) }

            vm.onVariantSelected(ModelVariant.E4B)

            val switched = awaitUntil { it.selectedVariant == ModelVariant.E4B }
            assertEquals(ModelState.NotDownloaded, switched.modelState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `import failure lands in actionError`() = runTest {
        modelRepository.importFailure = IOException("Could not open the selected file")
        val vm = viewModel()

        vm.uiState.test {
            awaitUntil { !it.isLoading }

            vm.onImportModel("content://docs/model.litertlm")

            val failed = awaitUntil { it.actionError != null }
            assertEquals("Could not open the selected file", failed.actionError)
            assertEquals(listOf("import:E2B:content://docs/model.litertlm"), modelRepository.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `backend selection persists to settings`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { !it.isLoading }

            vm.onBackendSelected(LlmBackend.GPU)

            awaitUntil { it.backend == LlmBackend.GPU }
            assertEquals(LlmBackend.GPU, settings.backend.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Skips intermediate emissions until [predicate] holds. */
private suspend fun <T> TurbineTestContext<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
