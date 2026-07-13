package com.provender.ui.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.provender.data.entity.SnapshotStatus
import com.provender.data.model.Category
import com.provender.data.model.QuantityUnits
import com.provender.ui.components.DropdownField

/**
 * Confirm screen (SPEC §5.3, seed mode): review and edit extraction results, add missed
 * items, then commit them to the snapshot's location. Diff mode arrives in Phase 4.
 */
@Composable
fun ConfirmScreen(
    onDone: () -> Unit,
    viewModel: ConfirmViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.committed) {
        if (state.committed) onDone()
    }

    val snapshot = state.snapshot
    when {
        snapshot == null -> LoadingPane("Loading snapshot…")

        snapshot.status == SnapshotStatus.ANALYZING -> LoadingPane(
            "Reading your photos on-device…\n\nThis can take a minute on the local model. " +
                "You can leave this screen — the analysis keeps running.",
        )

        snapshot.status == SnapshotStatus.FAILED -> FailedPane(
            message = snapshot.errorMessage ?: "Analysis failed.",
            onRetry = viewModel::onRetryAnalysis,
        )

        else -> ReviewPane(state, viewModel)
    }

    state.editor?.let { editorState ->
        ConfirmRowEditorSheet(
            editor = editorState,
            newLocalId = viewModel::newLocalId,
            onSave = viewModel::onEditorSave,
            onDelete = viewModel::onDeleteRow,
            onDismiss = viewModel::onEditorDismiss,
        )
    }
}

@Composable
private fun ReviewPane(state: ConfirmUiState, viewModel: ConfirmViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Found ${state.rows.size} item(s) for ${state.locationName}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        if (state.rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing recognized. Add items below, or go back and retake the photos.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.rows, key = { it.localId }) { row ->
                    ConfirmRowItem(
                        row = row,
                        onClick = { viewModel.onRowClick(row) },
                        onDelete = { viewModel.onDeleteRow(row.localId) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = viewModel::onAddRowClick) {
                Text("Add missed item")
            }
            Button(
                onClick = viewModel::onCommit,
                enabled = state.rows.isNotEmpty() && !state.isCommitting,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (state.isCommitting) "Saving…"
                    else "Add ${state.rows.size} to ${state.locationName}",
                )
            }
        }
    }
}

@Composable
private fun ConfirmRowItem(
    row: ConfirmRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = { ConfidenceBadge(row.confidence) },
        headlineContent = { Text(row.name) },
        supportingContent = {
            val amount = listOfNotNull(
                row.quantity?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() },
                row.unit,
            ).joinToString(" ").ifEmpty { null }
            val categoryLabel = row.category.takeIf { it != Category.OTHER }?.label
            Text(listOfNotNull(amount, categoryLabel, row.notes).joinToString(" · ").ifEmpty { "—" })
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove ${row.name}")
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** SPEC §3.1: each row shows a confidence badge. Green ≥ 0.8, amber ≥ 0.5, red below. */
@Composable
private fun ConfidenceBadge(confidence: Float) {
    val color = when {
        confidence >= 0.8f -> MaterialTheme.colorScheme.primary
        confidence >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${(confidence * 100).toInt()}",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun LoadingPane(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            message,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedPane(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry analysis")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmRowEditorSheet(
    editor: ConfirmEditor,
    newLocalId: () -> Long,
    onSave: (ConfirmRow) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = editor.row
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var quantityText by remember(existing) {
        mutableStateOf(
            existing?.quantity?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            }.orEmpty(),
        )
    }
    var unit by remember(existing) { mutableStateOf(existing?.unit ?: "count") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: Category.OTHER) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existing == null) "Add missed item" else "Edit item",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                DropdownField(
                    label = "Unit",
                    value = unit,
                    options = QuantityUnits,
                    optionLabel = { it },
                    onSelect = { unit = it },
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownField(
                label = "Category",
                value = category.label,
                options = Category.entries.toList(),
                optionLabel = { it.label },
                onSelect = { category = it },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        ConfirmRow(
                            localId = existing?.localId ?: newLocalId(),
                            name = name.trim(),
                            quantity = quantityText.trim().toDoubleOrNull(),
                            unit = unit,
                            category = category,
                            confidence = existing?.confidence ?: 1f,
                            notes = notes.trim().takeIf { it.isNotEmpty() },
                        ),
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            if (existing != null) {
                TextButton(
                    onClick = { onDelete(existing.localId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove item", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
