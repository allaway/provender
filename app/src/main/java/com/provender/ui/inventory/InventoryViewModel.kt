package com.provender.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.provender.data.entity.InventoryItem
import com.provender.data.repository.InventoryRepository
import com.provender.data.repository.ItemDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val editor = MutableStateFlow<EditorState?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResults = query.flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else repository.search(q)
    }

    val uiState: StateFlow<InventoryUiState> = combine(
        query,
        repository.observeSections(),
        repository.observeLocations(),
        searchResults,
        editor,
    ) { q, sections, locations, results, editorState ->
        InventoryUiState(
            query = q,
            sections = sections,
            searchResults = results,
            locations = locations,
            editor = editorState,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onAddClick() {
        editor.value = EditorState(existing = null)
    }

    fun onItemClick(item: InventoryItem) {
        editor.value = EditorState(existing = item)
    }

    fun onEditorDismiss() {
        editor.value = null
    }

    fun onSave(draft: ItemDraft) {
        val target = editor.value?.existing
        viewModelScope.launch {
            if (target == null) repository.addItem(draft) else repository.updateItem(target.id, draft)
            editor.value = null
        }
    }

    fun onDelete(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteItem(item.id)
            editor.value = null
        }
    }

    fun onToggleStaple(item: InventoryItem) {
        viewModelScope.launch {
            repository.setStaple(item.id, !item.isStaple)
        }
    }
}
