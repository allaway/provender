package com.provender.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.provender.data.entity.InventoryItem
import com.provender.data.entity.StorageLocation
import com.provender.data.model.Category
import com.provender.data.model.QuantityUnits
import com.provender.data.repository.ItemDraft

/** Add/edit bottom sheet. Pure form state; all persistence goes through the ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorSheet(
    editorState: EditorState,
    locations: List<StorageLocation>,
    onSave: (ItemDraft) -> Unit,
    onDelete: (InventoryItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = editorState.existing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var quantityText by remember(existing) {
        mutableStateOf(existing?.quantity?.let { formatQuantity(it) }.orEmpty())
    }
    var unit by remember(existing) { mutableStateOf(existing?.unit ?: "count") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: Category.OTHER) }
    var locationId by remember(existing, locations) {
        mutableStateOf(existing?.locationId ?: locations.firstOrNull()?.id)
    }
    var isStaple by remember(existing) { mutableStateOf(existing?.isStaple ?: false) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existing == null) "Add item" else "Edit item",
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

            DropdownField(
                label = "Location",
                value = locations.firstOrNull { it.id == locationId }?.name ?: "—",
                options = locations,
                optionLabel = { it.name },
                onSelect = { locationId = it.id },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Staple", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Assumed available for recipe matching",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = isStaple, onCheckedChange = { isStaple = it })
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val targetLocation = locationId ?: return@Button
                    onSave(
                        ItemDraft(
                            name = name,
                            category = category,
                            quantity = quantityText.trim().toDoubleOrNull(),
                            unit = unit,
                            locationId = targetLocation,
                            isStaple = isStaple,
                            notes = notes,
                        ),
                    )
                },
                enabled = name.isNotBlank() && locationId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            if (existing != null) {
                TextButton(
                    onClick = { onDelete(existing) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete item", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $value", maxLines = 1)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
