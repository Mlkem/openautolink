package com.openautolink.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openautolink.app.ui.diagnostics.DiagnosticsScreen
import com.openautolink.app.ui.projection.ProjectionScreen
import com.openautolink.app.ui.settings.SettingsScreen

object AppDestinations {
    const val PROJECTION = "projection"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.PROJECTION
    ) {
        composable(AppDestinations.PROJECTION) {
            ProjectionScreen(
                onNavigateToSettings = {
                    navController.navigate(AppDestinations.SETTINGS)
                }
            )
        }
        composable(AppDestinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDiagnostics = {
                    navController.navigate(AppDestinations.DIAGNOSTICS)
                }
            )
        }
        composable(AppDestinations.DIAGNOSTICS) {
            DiagnosticsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
