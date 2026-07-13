package com.provender.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.provender.ai.CapabilityChecker
import com.provender.ai.LlmBackend
import com.provender.ai.ModelRepository
import com.provender.ai.ModelState
import com.provender.ai.ModelVariant
import com.provender.ai.VlmCapability
import com.provender.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedVariant: ModelVariant = ModelVariant.E2B,
    val backend: LlmBackend = LlmBackend.CPU,
    val modelState: ModelState = ModelState.NotDownloaded,
    val capability: VlmCapability = VlmCapability(supported = true),
    /** Set when an import or other action failed; cleared on the next action. */
    val actionError: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val settings: AppSettingsRepository,
    private val capabilityChecker: CapabilityChecker,
) : ViewModel() {

    private val actionError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val modelState = settings.selectedVariant.flatMapLatest { variant ->
        modelRepository.observeState(variant)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.selectedVariant,
        settings.backend,
        modelState,
        actionError,
    ) { variant, backend, state, error ->
        SettingsUiState(
            selectedVariant = variant,
            backend = backend,
            modelState = state,
            capability = capabilityChecker.check(variant),
            actionError = error,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun onVariantSelected(variant: ModelVariant) {
        actionError.value = null
        settings.setSelectedVariant(variant)
    }

    fun onBackendSelected(backend: LlmBackend) {
        actionError.value = null
        settings.setBackend(backend)
    }

    fun onDownloadClick() {
        actionError.value = null
        modelRepository.startDownload(settings.selectedVariant.value)
    }

    fun onCancelDownloadClick() {
        actionError.value = null
        modelRepository.cancelDownload(settings.selectedVariant.value)
    }

    fun onDeleteModelClick() {
        actionError.value = null
        modelRepository.deleteModel(settings.selectedVariant.value)
    }

    /** [contentUri] is the document picked by the user (SAF content:// URI). */
    fun onImportModel(contentUri: String) {
        actionError.value = null
        val variant = settings.selectedVariant.value
        viewModelScope.launch {
            runCatching { modelRepository.importModel(variant, contentUri) }
                .onFailure { actionError.value = it.message ?: "Import failed" }
        }
    }
}
