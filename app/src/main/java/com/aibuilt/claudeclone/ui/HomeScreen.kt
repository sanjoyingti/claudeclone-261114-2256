package com.aibuilt.claudeclone.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aibuilt.claudeclone.AppContainer
import com.aibuilt.claudeclone.ui.artifacts.ArtifactsScreen
import com.aibuilt.claudeclone.ui.artifacts.ArtifactsViewModel
import com.aibuilt.claudeclone.ui.chat.ChatScreen
import com.aibuilt.claudeclone.ui.chat.ChatViewModel
import com.aibuilt.claudeclone.ui.projects.ProjectsScreen
import com.aibuilt.claudeclone.ui.projects.ProjectsViewModel

private const val TAB_CHAT = "tab_chat"
private const val TAB_PROJECTS = "tab_projects"
private const val TAB_ARTIFACTS = "tab_artifacts"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(TAB_CHAT, "Chats", Icons.Outlined.ChatBubbleOutline),
    Tab(TAB_PROJECTS, "Projects", Icons.Outlined.FolderOpen),
    Tab(TAB_ARTIFACTS, "Artifacts", Icons.Outlined.Code)
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit
) {
    val tabNavController = rememberNavController()
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                TABS.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                tabNavController.navigate(tab.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = TAB_CHAT,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(TAB_CHAT) {
                val vm: ChatViewModel = viewModel(factory = container.chatViewModelFactory)
                ChatScreen(viewModel = vm, onOpenSettings = onOpenSettings)
            }
            composable(TAB_PROJECTS) {
                val vm: ProjectsViewModel = viewModel(factory = container.projectsViewModelFactory)
                ProjectsScreen(viewModel = vm)
            }
            composable(TAB_ARTIFACTS) {
                val vm: ArtifactsViewModel = viewModel(factory = container.artifactsViewModelFactory)
                ArtifactsScreen(viewModel = vm)
            }
        }
    }
}
