package com.provender.ui.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.provender.ai.ExtractedItem
import com.provender.ai.LlmJson
import com.provender.data.entity.Snapshot
import com.provender.data.entity.SnapshotStatus
import com.provender.data.model.Category
import com.provender.data.repository.InventoryRepository
import com.provender.data.repository.ItemDraft
import com.provender.data.repository.SnapshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One editable row on the Confirm screen. [localId] is screen-local, not a DB id. */
data class ConfirmRow(
    val localId: Long,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val category: Category = Category.OTHER,
    /** 0..1 from the extraction; 1.0 for rows the user added by hand. */
    val confidence: Float = 1f,
    val notes: String? = null,
)

/** Editor sheet target; null [row] means "add a missed item". */
data class ConfirmEditor(val row: ConfirmRow?)

data class ConfirmUiState(
    val snapshot: Snapshot? = null,
    val locationName: String = "",
    val rows: List<ConfirmRow> = emptyList(),
    val editor: ConfirmEditor? = null,
    val isCommitting: Boolean = false,
    /** Set after a successful commit; the screen navigates back to Inventory. */
    val committed: Boolean = false,
)

@HiltViewModel
class ConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val snapshotRepository: SnapshotRepository,
    inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val snapshotId: Long = checkNotNull(savedStateHandle["snapshotId"])

    private val rows = MutableStateFlow<List<ConfirmRow>?>(null)
    private val editor = MutableStateFlow<ConfirmEditor?>(null)
    private val isCommitting = MutableStateFlow(false)
    private val committed = MutableStateFlow(false)
    private var nextLocalId = 1L

    init {
        // Seed the editable rows exactly once, when extraction results first arrive.
        viewModelScope.launch {
            snapshotRepository.observeSnapshot(snapshotId).collect { snapshot ->
                if (snapshot?.status == SnapshotStatus.READY && rows.value == null) {
                    val extracted = snapshot.extractionJson
                        ?.let { LlmJson.parseArray<ExtractedItem>(it) }
                        .orEmpty()
                    rows.value = extracted.map { it.toConfirmRow(nextLocalId++) }
                }
            }
        }
    }

    val uiState: StateFlow<ConfirmUiState> = combine(
        snapshotRepository.observeSnapshot(snapshotId),
        inventoryRepository.observeLocations(),
        rows,
        editor,
        combine(isCommitting, committed) { committing, done -> committing to done },
    ) { snapshot, locations, rowList, editorState, (committing, done) ->
        ConfirmUiState(
            snapshot = snapshot,
            locationName = locations.firstOrNull { it.id == snapshot?.locationId }?.name ?: "",
            rows = rowList.orEmpty(),
            editor = editorState,
            isCommitting = committing,
            committed = done,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConfirmUiState(),
    )

    fun onRowClick(row: ConfirmRow) {
        editor.value = ConfirmEditor(row)
    }

    fun onAddRowClick() {
        editor.value = ConfirmEditor(row = null)
    }

    fun onEditorDismiss() {
        editor.value = null
    }

    fun onEditorSave(edited: ConfirmRow) {
        rows.value = rows.value.orEmpty().let { current ->
            if (current.any { it.localId == edited.localId }) {
                current.map { if (it.localId == edited.localId) edited else it }
            } else {
                current + edited
            }
        }
        editor.value = null
    }

    /** Fresh localId for a row added from the editor sheet. */
    fun newLocalId(): Long = nextLocalId++

    fun onDeleteRow(localId: Long) {
        rows.value = rows.value.orEmpty().filterNot { it.localId == localId }
        editor.value = null
    }

    fun onRetryAnalysis() {
        viewModelScope.launch { snapshotRepository.retryAnalysis(snapshotId) }
    }

    fun onCommit() {
        val snapshot = uiState.value.snapshot ?: return
        val toCommit = rows.value.orEmpty().filter { it.name.isNotBlank() }
        if (isCommitting.value) return
        isCommitting.value = true
        viewModelScope.launch {
            snapshotRepository.commitSeed(
                snapshotId,
                toCommit.map { row ->
                    ItemDraft(
                        name = row.name,
                        category = row.category,
                        quantity = row.quantity,
                        unit = row.unit,
                        locationId = snapshot.locationId,
                        notes = row.notes,
                    )
                },
            )
            isCommitting.value = false
            committed.value = true
        }
    }
}

/** Maps an extraction result to an editable row; categories match by enum name or label. */
fun ExtractedItem.toConfirmRow(localId: Long): ConfirmRow = ConfirmRow(
    localId = localId,
    name = name,
    quantity = quantity,
    unit = unit,
    category = category?.let { raw ->
        Category.entries.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
        }
    } ?: Category.OTHER,
    confidence = confidence,
    notes = notes,
)
