package com.provender.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.provender.ai.LlmBackend
import com.provender.ai.ModelState
import com.provender.ai.ModelVariant

/**
 * Settings. The Model section manages the on-device Gemma 3n weights (download over Wi-Fi,
 * import from a file, delete, CPU/GPU backend). Deliberately no API-key UI anywhere — all
 * AI runs on-device. Tapping the "On-device model" header 7 times opens the hidden debug
 * prompt screen.
 */
@Composable
fun SettingsScreen(
    onOpenDebugLlm: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var secretTaps by remember { mutableIntStateOf(0) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.onImportModel(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        Text(
            text = "Model",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                headlineContent = { Text("On-device model") },
                supportingContent = {
                    Text(
                        "Photo understanding and recipe generation run entirely on this " +
                            "phone. Nothing you scan ever leaves it.",
                    )
                },
                modifier = Modifier.clickable {
                    secretTaps++
                    if (secretTaps >= 7) {
                        secretTaps = 0
                        onOpenDebugLlm()
                    }
                },
            )
            HorizontalDivider()
            ModelSection(
                state = state,
                onVariantSelected = viewModel::onVariantSelected,
                onBackendSelected = viewModel::onBackendSelected,
                onDownload = viewModel::onDownloadClick,
                onCancel = viewModel::onCancelDownloadClick,
                onDelete = viewModel::onDeleteModelClick,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
            )
        }

        Text(
            text = "Inventory",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                headlineContent = { Text("Storage locations") },
                supportingContent = { Text("Editor coming in a later phase") },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Star, contentDescription = null) },
                headlineContent = { Text("Staples list") },
                supportingContent = { Text("Coming in a later phase") },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                headlineContent = { Text("Synonym overrides") },
                supportingContent = { Text("Coming with the matching engine (Phase 5)") },
            )
        }

        Text(
            text = "Provender keeps everything local: your photos never leave the device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun ModelSection(
    state: SettingsUiState,
    onVariantSelected: (ModelVariant) -> Unit,
    onBackendSelected: (LlmBackend) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.capability.supported) {
            Text(
                text = state.capability.reason ?: "This device can't run the on-device model.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val busy = state.modelState is ModelState.Downloading
        ModelVariant.entries.forEach { variant ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = state.selectedVariant == variant,
                    onClick = { onVariantSelected(variant) },
                    enabled = !busy,
                )
                Column {
                    Text(variant.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "~${formatBytes(variant.approxDownloadBytes)} download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (val modelState = state.modelState) {
            is ModelState.NotDownloaded -> {
                Text(
                    "Not downloaded. Downloads run over Wi-Fi only. The Hugging Face page " +
                        "requires accepting Google's Gemma license — if the download fails, " +
                        "fetch the .litertlm file in your browser and import it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDownload, enabled = state.capability.supported) {
                        Text("Download")
                    }
                    OutlinedButton(onClick = onImport, enabled = state.capability.supported) {
                        Text("Import file")
                    }
                }
            }

            is ModelState.Downloading -> {
                val percent = modelState.progressPercent
                if (percent != null) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$percent%", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Waiting for Wi-Fi or starting…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }

            is ModelState.Downloaded -> {
                Text(
                    "Downloaded · ${formatBytes(modelState.sizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDelete) {
                    Text("Delete model", color = MaterialTheme.colorScheme.error)
                }
            }

            is ModelState.Failed -> {
                Text(
                    modelState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDownload) { Text("Retry") }
                    OutlinedButton(onClick = onImport) { Text("Import file") }
                }
            }
        }

        state.actionError?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text("Inference backend", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LlmBackend.entries.forEach { backend ->
                FilterChip(
                    selected = state.backend == backend,
                    onClick = { onBackendSelected(backend) },
                    label = { Text(backend.name) },
                )
            }
        }
        Text(
            "CPU is the safe default; GPU can be faster on some devices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(gb * 1024)
}
