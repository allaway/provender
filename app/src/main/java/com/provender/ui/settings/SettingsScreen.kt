package com.provender.ui.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings shell. Real model management (download/delete, E2B vs E4B, CPU/GPU backend)
 * arrives in Phase 2; locations/staples/synonyms editors in later phases.
 *
 * Deliberately no API-key UI anywhere — all AI runs on-device.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
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
                        "Not downloaded. Photo understanding and recipe generation run " +
                            "entirely on this phone — model management arrives in Phase 2.",
                    )
                },
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
