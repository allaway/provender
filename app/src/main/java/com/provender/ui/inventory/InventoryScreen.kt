package com.provender.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.provender.data.entity.InventoryItem
import com.provender.data.model.Category

@Composable
fun InventoryScreen(viewModel: InventoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddClick) {
                Icon(Icons.Outlined.Add, contentDescription = "Add item")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search inventory") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.isSearching -> SearchResultsList(
                    results = state.searchResults,
                    onItemClick = viewModel::onItemClick,
                    onToggleStaple = viewModel::onToggleStaple,
                )

                else -> GroupedInventoryList(
                    state = state,
                    onItemClick = viewModel::onItemClick,
                    onToggleStaple = viewModel::onToggleStaple,
                )
            }
        }
    }

    state.editor?.let { editorState ->
        ItemEditorSheet(
            editorState = editorState,
            locations = state.locations,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onDismiss = viewModel::onEditorDismiss,
        )
    }
}

@Composable
private fun GroupedInventoryList(
    state: InventoryUiState,
    onItemClick: (InventoryItem) -> Unit,
    onToggleStaple: (InventoryItem) -> Unit,
) {
    val isEmpty = state.sections.all { it.items.isEmpty() }
    if (isEmpty) {
        EmptyState(
            "Your inventory is empty.\nTap + to add your first item — photo capture arrives in Phase 3.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        state.sections.forEach { section ->
            if (section.items.isEmpty()) return@forEach
            item(key = "location-${section.location.id}") {
                Text(
                    text = section.location.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(section.items, key = { "item-${it.id}" }) { item ->
                InventoryItemRow(
                    item = item,
                    onClick = { onItemClick(item) },
                    onToggleStaple = { onToggleStaple(item) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<InventoryItem>,
    onItemClick: (InventoryItem) -> Unit,
    onToggleStaple: (InventoryItem) -> Unit,
) {
    if (results.isEmpty()) {
        EmptyState("No items match your search.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(results, key = { "item-${it.id}" }) { item ->
            InventoryItemRow(
                item = item,
                onClick = { onItemClick(item) },
                onToggleStaple = { onToggleStaple(item) },
            )
        }
    }
}

@Composable
private fun InventoryItemRow(
    item: InventoryItem,
    onClick: () -> Unit,
    onToggleStaple: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { Text(item.summaryLine()) },
        trailingContent = {
            IconButton(onClick = onToggleStaple) {
                Icon(
                    imageVector = if (item.isStaple) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (item.isStaple) "Unmark staple" else "Mark as staple",
                    tint = if (item.isStaple) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun InventoryItem.summaryLine(): String {
    val amount = when {
        quantity != null && unit != null -> "${quantity.formatQuantity()} $unit"
        quantity != null -> quantity.formatQuantity()
        unit != null -> unit
        else -> null
    }
    val categoryLabel = category.takeIf { it != Category.OTHER }?.label
    return listOfNotNull(amount, categoryLabel, notes).joinToString(" · ").ifEmpty { "—" }
}

private fun Double.formatQuantity(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
