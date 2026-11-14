package com.aibuilt.claudeclone.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aibuilt.claudeclone.AppContainer
import com.aibuilt.claudeclone.ui.chat.ChatScreen
import com.aibuilt.claudeclone.ui.chat.ChatViewModel
import com.aibuilt.claudeclone.ui.settings.SettingsScreen
import com.aibuilt.claudeclone.ui.settings.SettingsViewModel
import com.aibuilt.claudeclone.ui.splash.SplashScreen

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun ClaudeNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_SPLASH) {
        composable(ROUTE_SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(ROUTE_CHAT) {
                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_CHAT) {
            val vm: ChatViewModel = viewModel(factory = container.chatViewModelFactory)
            ChatScreen(
                viewModel = vm,
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
