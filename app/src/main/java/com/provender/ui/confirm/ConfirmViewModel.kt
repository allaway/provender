package com.provender.ui.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.provender.ai.ExtractedItem
import com.provender.ai.LlmJson
import com.provender.data.entity.Snapshot
import com.provender.data.entity.SnapshotStatus
import com.provender.data.entity.StorageLocation
import com.provender.data.model.Category
import com.provender.data.repository.DiffCommit
import com.provender.data.repository.InventoryRepository
import com.provender.data.repository.ItemDraft
import com.provender.data.repository.MoveDecision
import com.provender.data.repository.QuantityChangeDecision
import com.provender.data.repository.SnapshotRepository
import com.provender.matching.InventoryDiffer
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

/** First snapshot for a location seeds inventory; later ones review a diff (SPEC §3.1). */
enum class ConfirmMode { SEED, DIFF }

/** A proposed quantity update, toggleable by the user. */
data class ChangedRowUi(
    val itemId: Long,
    val name: String,
    val oldQuantity: Double?,
    val oldUnit: String?,
    val newQuantity: Double?,
    val newUnit: String?,
    val apply: Boolean = true,
)

enum class MissingDecision { STILL_THERE, CONSUMED, MOVED }

/** An inventory item the photos didn't show; the user picks what happened (SPEC §3.2). */
data class MissingRowUi(
    val itemId: Long,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val decision: MissingDecision = MissingDecision.STILL_THERE,
    val movedToLocationId: Long? = null,
)

data class DiffUiState(
    val changed: List<ChangedRowUi> = emptyList(),
    val missing: List<MissingRowUi> = emptyList(),
    /** Matched-and-unchanged items; silently re-confirmed on commit. */
    val confirmedItemIds: List<Long> = emptyList(),
)

data class ConfirmUiState(
    val snapshot: Snapshot? = null,
    val locationName: String = "",
    val locations: List<StorageLocation> = emptyList(),
    val mode: ConfirmMode = ConfirmMode.SEED,
    /** Items to be added — all extractions in seed mode, unmatched ones in diff mode. */
    val rows: List<ConfirmRow> = emptyList(),
    val diff: DiffUiState? = null,
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
    private val diff = MutableStateFlow<DiffUiState?>(null)
    private val mode = MutableStateFlow(ConfirmMode.SEED)
    private val editor = MutableStateFlow<ConfirmEditor?>(null)
    private val isCommitting = MutableStateFlow(false)
    private val committed = MutableStateFlow(false)
    private var nextLocalId = 1L

    init {
        // Seed the editable state exactly once, when extraction results first arrive.
        viewModelScope.launch {
            snapshotRepository.observeSnapshot(snapshotId).collect { snapshot ->
                if (snapshot?.status == SnapshotStatus.READY && rows.value == null) {
                    val extracted = snapshot.extractionJson
                        ?.let { LlmJson.parseArray<ExtractedItem>(it) }
                        .orEmpty()
                    val existing = snapshotRepository.itemsAtLocation(snapshot.locationId)
                    if (existing.isEmpty()) {
                        mode.value = ConfirmMode.SEED
                        rows.value = extracted.map { it.toConfirmRow(nextLocalId++) }
                    } else {
                        val result = InventoryDiffer.diff(extracted, existing)
                        mode.value = ConfirmMode.DIFF
                        rows.value = result.newItems.map { it.toConfirmRow(nextLocalId++) }
                        diff.value = DiffUiState(
                            changed = result.changedQuantities.map { change ->
                                ChangedRowUi(
                                    itemId = change.item.id,
                                    name = change.item.name,
                                    oldQuantity = change.item.quantity,
                                    oldUnit = change.item.unit,
                                    newQuantity = change.extracted.quantity,
                                    newUnit = change.extracted.unit,
                                )
                            },
                            missing = result.missingItems.map { item ->
                                MissingRowUi(
                                    itemId = item.id,
                                    name = item.name,
                                    quantity = item.quantity,
                                    unit = item.unit,
                                )
                            },
                            confirmedItemIds = result.confirmedItems.map { it.id },
                        )
                    }
                }
            }
        }
    }

    private data class EditState(
        val rows: List<ConfirmRow>?,
        val diff: DiffUiState?,
        val mode: ConfirmMode,
        val editor: ConfirmEditor?,
    )

    private val editState = combine(rows, diff, mode, editor, ::EditState)

    val uiState: StateFlow<ConfirmUiState> = combine(
        snapshotRepository.observeSnapshot(snapshotId),
        inventoryRepository.observeLocations(),
        editState,
        combine(isCommitting, committed) { committing, done -> committing to done },
    ) { snapshot, locations, edit, (committing, done) ->
        ConfirmUiState(
            snapshot = snapshot,
            locationName = locations.firstOrNull { it.id == snapshot?.locationId }?.name ?: "",
            locations = locations,
            mode = edit.mode,
            rows = edit.rows.orEmpty(),
            diff = edit.diff,
            editor = edit.editor,
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

    fun onToggleChangedApply(itemId: Long) {
        diff.value = diff.value?.let { state ->
            state.copy(
                changed = state.changed.map {
                    if (it.itemId == itemId) it.copy(apply = !it.apply) else it
                },
            )
        }
    }

    fun onMissingDecision(itemId: Long, decision: MissingDecision) {
        diff.value = diff.value?.let { state ->
            state.copy(
                missing = state.missing.map {
                    if (it.itemId == itemId) it.copy(decision = decision) else it
                },
            )
        }
    }

    fun onMissingMoveTarget(itemId: Long, locationId: Long) {
        diff.value = diff.value?.let { state ->
            state.copy(
                missing = state.missing.map {
                    if (it.itemId == itemId) it.copy(movedToLocationId = locationId) else it
                },
            )
        }
    }

    fun onRetryAnalysis() {
        viewModelScope.launch { snapshotRepository.retryAnalysis(snapshotId) }
    }

    fun onCommit() {
        val snapshot = uiState.value.snapshot ?: return
        if (isCommitting.value) return
        isCommitting.value = true
        val drafts = rows.value.orEmpty()
            .filter { it.name.isNotBlank() }
            .map { row ->
                ItemDraft(
                    name = row.name,
                    category = row.category,
                    quantity = row.quantity,
                    unit = row.unit,
                    locationId = snapshot.locationId,
                    notes = row.notes,
                )
            }
        viewModelScope.launch {
            when (mode.value) {
                ConfirmMode.SEED -> snapshotRepository.commitSeed(snapshotId, drafts)
                ConfirmMode.DIFF -> {
                    val diffState = diff.value ?: DiffUiState()
                    snapshotRepository.commitDiff(
                        snapshotId,
                        DiffCommit(
                            adds = drafts,
                            quantityChanges = diffState.changed
                                .filter { it.apply }
                                .map {
                                    QuantityChangeDecision(
                                        itemId = it.itemId,
                                        newQuantity = it.newQuantity,
                                        newUnit = it.newUnit,
                                    )
                                },
                            consumedItemIds = diffState.missing
                                .filter { it.decision == MissingDecision.CONSUMED }
                                .map { it.itemId },
                            confirmedItemIds = buildList {
                                addAll(diffState.confirmedItemIds)
                                // Declined quantity changes were still seen in the photos.
                                addAll(diffState.changed.filterNot { it.apply }.map { it.itemId })
                                // "Still there" and moves without a chosen target just re-confirm.
                                addAll(
                                    diffState.missing
                                        .filter {
                                            it.decision == MissingDecision.STILL_THERE ||
                                                (it.decision == MissingDecision.MOVED &&
                                                    it.movedToLocationId == null)
                                        }
                                        .map { it.itemId },
                                )
                            },
                            moves = diffState.missing
                                .filter {
                                    it.decision == MissingDecision.MOVED &&
                                        it.movedToLocationId != null
                                }
                                .map { MoveDecision(it.itemId, it.movedToLocationId!!) },
                        ),
                    )
                }
            }
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
