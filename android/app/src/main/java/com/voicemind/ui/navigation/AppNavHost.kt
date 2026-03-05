package com.voicemind.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.ui.checklist.ChecklistScreen
import com.voicemind.ui.checklist.TaskDetailScreen
import com.voicemind.ui.folders.FolderDetailScreen
import com.voicemind.ui.folders.FoldersScreen
import com.voicemind.ui.home.HomeScreen
import com.voicemind.ui.recording.RecordingsScreen
import com.voicemind.ui.settings.SettingsScreen
import com.voicemind.ui.summaries.SummariesScreen
import com.voicemind.ui.theme.IosBackground
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    onSignOut: () -> Unit,
    navPreferenceRepository: NavPreferenceRepository,
    openRecordingsOnStart: Boolean = false,
    onRecordingsOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val useSidebarOrNull by navPreferenceRepository.useSidebar.collectAsStateWithLifecycle(initialValue = null)
    val useSidebar = useSidebarOrNull ?: return

    val showBottomBar = !useSidebar

    val navigateTo: (Routes) -> Unit = { destination ->
        val popped = navController.popBackStack(destination.route, inclusive = false)
        if (!popped) {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            // If restoreState brought back sub-routes (e.g. folder_detail on top
            // of folders), pop back to the tab root so the user always lands on
            // the main screen for the tab they tapped.
            if (navController.currentBackStackEntry?.destination?.route != destination.route) {
                navController.popBackStack(destination.route, inclusive = false)
            }
        }
    }

    // Navigate to Recordings when the app is opened from a notification tap.
    LaunchedEffect(openRecordingsOnStart) {
        if (openRecordingsOnStart) {
            navigateTo(Routes.Recordings)
            onRecordingsOpened()
        }
    }

    val onOpenDrawer: (() -> Unit)? = if (useSidebar) {
        { scope.launch { drawerState.open() } }
    } else {
        null
    }

    val onSettings: () -> Unit = { navigateTo(Routes.Settings) }

    val sidebarNavigate: (Routes) -> Unit = { destination ->
        scope.launch { drawerState.close() }
        navigateTo(destination)
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = modifier
        ) {
            composable(Routes.Home.route) {
                HomeScreen(
                    onFolderClick = { folderId ->
                        navController.navigate(folderDetailRoute(folderId))
                    },
                    onRecordingClick = {
                        navigateTo(Routes.Recordings)
                    },
                    onViewAllFolders = {
                        navigateTo(Routes.Folders)
                    },
                    onOpenDrawer = onOpenDrawer,
                    onSettings = onSettings,
                )
            }
            composable(Routes.Recordings.route) {
                RecordingsScreen(onOpenDrawer = onOpenDrawer, navController = navController, onSettings = onSettings)
            }
            composable(Routes.Checklist.route) {
                ChecklistScreen(
                    onOpenDrawer = onOpenDrawer,
                    onTaskClick = { itemId ->
                        navController.navigate(taskDetailRoute(itemId))
                    },
                    onSettings = onSettings,
                )
            }
            composable(TASK_DETAIL_ROUTE) {
                TaskDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Summaries.route) {
                SummariesScreen(onOpenDrawer = onOpenDrawer, onSettings = onSettings)
            }
            composable(Routes.Folders.route) {
                FoldersScreen(
                    onFolderClick = { folderId ->
                        navController.navigate(folderDetailRoute(folderId))
                    },
                    onOpenDrawer = onOpenDrawer,
                    onSettings = onSettings,
                )
            }
            composable(Routes.Settings.route) {
                SettingsScreen(
                    onSignOut = onSignOut,
                    onOpenDrawer = onOpenDrawer,
                )
            }
            composable(FOLDER_DETAIL_ROUTE) { backStackEntry ->
                val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
                FolderDetailScreen(
                    folderId = folderId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (useSidebar) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SidebarDrawer(
                    currentRoute = currentRoute ?: Routes.Home.route,
                    onNavigate = sidebarNavigate,
                )
            },
        ) {
            Scaffold(containerColor = IosBackground) { innerPadding ->
                content(Modifier.padding(innerPadding))
            }
        }
    } else {
        Scaffold(
            containerColor = IosBackground,
            bottomBar = {
                if (showBottomBar) {
                    BottomNavBar(
                        currentRoute = currentRoute ?: Routes.Home.route,
                        onNavigate = navigateTo,
                    )
                }
            }
        ) { innerPadding ->
            content(Modifier.padding(innerPadding))
        }
    }
}
