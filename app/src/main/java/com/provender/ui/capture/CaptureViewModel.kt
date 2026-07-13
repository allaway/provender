package com.provender.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.provender.data.entity.StorageLocation
import com.provender.data.model.Category
import com.provender.data.repository.BarcodeLookupRepository
import com.provender.data.repository.InventoryRepository
import com.provender.data.repository.ItemDraft
import com.provender.data.repository.SnapshotRepository
import com.provender.network.BarcodeProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaptureMode { PHOTO, BARCODE }

/** Result card shown after a barcode scan; [product] == null means "not in any database". */
data class BarcodeOverlay(
    val barcode: String,
    val product: BarcodeProduct?,
)

data class CaptureUiState(
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocationId: Long? = null,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val photoPaths: List<String> = emptyList(),
    val barcodeOverlay: BarcodeOverlay? = null,
    val isLookingUpBarcode: Boolean = false,
    val isImportingPhotos: Boolean = false,
    val message: String? = null,
    /** Set once Analyze has created the snapshot; the screen navigates to Confirm. */
    val navigateToSnapshotId: Long? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val snapshotRepository: SnapshotRepository,
    private val inventoryRepository: InventoryRepository,
    private val barcodeLookup: BarcodeLookupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val sessionDir: File by lazy { snapshotRepository.newSessionDir() }
    private var photoCounter = 0
    private var lastBarcode: String? = null
    private var lastBarcodeAtMillis = 0L

    init {
        viewModelScope.launch {
            inventoryRepository.observeLocations().collect { locations ->
                _uiState.update { state ->
                    state.copy(
                        locations = locations,
                        selectedLocationId = state.selectedLocationId
                            ?: locations.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    /** Target file for the next camera capture. */
    fun newPhotoFile(): File = File(sessionDir, "photo-${photoCounter++}.jpg")

    fun onPhotoCaptured(path: String) {
        _uiState.update { it.copy(photoPaths = it.photoPaths + path) }
    }

    fun onRemovePhoto(path: String) {
        File(path).delete()
        _uiState.update { it.copy(photoPaths = it.photoPaths - path) }
    }

    fun onGalleryPicked(contentUris: List<String>) {
        if (contentUris.isEmpty()) return
        _uiState.update { it.copy(isImportingPhotos = true) }
        viewModelScope.launch {
            val imported = contentUris.mapNotNull { uri ->
                runCatching { snapshotRepository.importPhoto(sessionDir, uri) }.getOrNull()
            }
            _uiState.update {
                it.copy(photoPaths = it.photoPaths + imported, isImportingPhotos = false)
            }
        }
    }

    fun onLocationSelected(locationId: Long) {
        _uiState.update { it.copy(selectedLocationId = locationId) }
    }

    fun onModeChanged(mode: CaptureMode) {
        _uiState.update { it.copy(mode = mode, barcodeOverlay = null) }
    }

    fun onBarcodeDetected(rawValue: String) {
        val now = System.currentTimeMillis()
        val state = _uiState.value
        if (state.isLookingUpBarcode || state.barcodeOverlay != null) return
        if (rawValue == lastBarcode && now - lastBarcodeAtMillis < BARCODE_DEBOUNCE_MS) return
        lastBarcode = rawValue
        lastBarcodeAtMillis = now
        _uiState.update { it.copy(isLookingUpBarcode = true) }
        viewModelScope.launch {
            val product = barcodeLookup.lookup(rawValue)
            _uiState.update {
                it.copy(
                    isLookingUpBarcode = false,
                    barcodeOverlay = BarcodeOverlay(barcode = rawValue, product = product),
                )
            }
        }
    }

    fun onDismissBarcode() {
        _uiState.update { it.copy(barcodeOverlay = null) }
    }

    /** Adds the scanned product straight to inventory at the selected location. */
    fun onAddBarcodeItem() {
        val state = _uiState.value
        val overlay = state.barcodeOverlay ?: return
        val product = overlay.product ?: return
        val locationId = state.selectedLocationId ?: return
        viewModelScope.launch {
            inventoryRepository.addItem(
                ItemDraft(
                    name = product.name,
                    category = Category.OTHER,
                    quantity = 1.0,
                    unit = "count",
                    locationId = locationId,
                    notes = product.brand,
                    barcode = overlay.barcode,
                ),
            )
            _uiState.update {
                it.copy(barcodeOverlay = null, message = "Added \"${product.name}\"")
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun onAnalyze() {
        val state = _uiState.value
        val locationId = state.selectedLocationId ?: return
        if (state.photoPaths.isEmpty()) return
        viewModelScope.launch {
            val snapshotId = snapshotRepository.createAndAnalyze(locationId, state.photoPaths)
            _uiState.update { it.copy(navigateToSnapshotId = snapshotId) }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateToSnapshotId = null) }
    }

    private companion object {
        const val BARCODE_DEBOUNCE_MS = 3_000L
    }
}
