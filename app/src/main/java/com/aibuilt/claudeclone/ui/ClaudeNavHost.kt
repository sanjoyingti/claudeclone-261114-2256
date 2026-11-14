package com.aibuilt.claudeclone.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aibuilt.claudeclone.AppContainer
import com.aibuilt.claudeclone.ui.settings.SettingsScreen
import com.aibuilt.claudeclone.ui.settings.SettingsViewModel
import com.aibuilt.claudeclone.ui.splash.SplashScreen

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun ClaudeNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_SPLASH) {
        composable(ROUTE_SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_HOME) {
            HomeScreen(
                container = container,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
        composable(ROUTE_SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory)
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
