package com.provender.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    INVENTORY("inventory", "Inventory", Icons.Outlined.Kitchen),
    RECIPES("recipes", "Recipes", Icons.AutoMirrored.Outlined.MenuBook),
    ROULETTE("roulette", "Roulette", Icons.Outlined.Casino),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}
