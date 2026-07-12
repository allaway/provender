package com.provender.ui.inventory

import com.provender.data.entity.InventoryItem
import com.provender.data.entity.StorageLocation
import com.provender.data.repository.LocationSection

/** Editor sheet state; [existing] == null means "add new item". */
data class EditorState(
    val existing: InventoryItem? = null,
)

data class InventoryUiState(
    val query: String = "",
    val sections: List<LocationSection> = emptyList(),
    val searchResults: List<InventoryItem> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val editor: EditorState? = null,
    val isLoading: Boolean = true,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}
