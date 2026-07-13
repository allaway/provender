package com.provender.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.provender.ui.debug.DebugLlmScreen
import com.provender.ui.inventory.InventoryScreen
import com.provender.ui.navigation.TopLevelDestination
import com.provender.ui.recipes.RecipesScreen
import com.provender.ui.roulette.RouletteScreen
import com.provender.ui.settings.SettingsScreen

/** Hidden route — reachable only via the secret gesture in Settings, never the nav bar. */
private const val DEBUG_LLM_ROUTE = "debug/llm"

@Composable
fun ProvenderRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.INVENTORY.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.INVENTORY.route) { InventoryScreen() }
            composable(TopLevelDestination.RECIPES.route) { RecipesScreen() }
            composable(TopLevelDestination.ROULETTE.route) { RouletteScreen() }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenDebugLlm = { navController.navigate(DEBUG_LLM_ROUTE) },
                )
            }
            composable(DEBUG_LLM_ROUTE) {
                DebugLlmScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
