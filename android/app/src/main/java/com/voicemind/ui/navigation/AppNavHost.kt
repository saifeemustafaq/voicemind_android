package com.voicemind.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.ui.checklist.ChecklistScreen
import com.voicemind.ui.folders.FolderDetailScreen
import com.voicemind.ui.folders.FoldersScreen
import com.voicemind.ui.home.HomeScreen
import com.voicemind.ui.recording.RecordingsScreen
import com.voicemind.ui.settings.SettingsScreen
import com.voicemind.ui.theme.VmSoftPeriwinkleMist
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    onSignOut: () -> Unit,
    navPreferenceRepository: NavPreferenceRepository,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val useSidebar by navPreferenceRepository.useSidebar.collectAsState(initial = true)

    val drawerRoutes = remember { Routes.drawerItems.map { it.route }.toSet() }
    val showBottomBar = !useSidebar && (currentRoute == null || currentRoute in drawerRoutes)

    val navigateTo: (Routes) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val onOpenDrawer: (() -> Unit)? = if (useSidebar) {
        { scope.launch { drawerState.open() } }
    } else {
        null
    }

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
                    onOpenDrawer = onOpenDrawer,
                )
            }
            composable(Routes.Recordings.route) {
                RecordingsScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(Routes.Checklist.route) {
                ChecklistScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(Routes.Folders.route) {
                FoldersScreen(
                    onFolderClick = { folderId ->
                        navController.navigate(folderDetailRoute(folderId))
                    },
                    onOpenDrawer = onOpenDrawer,
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
            Scaffold(containerColor = VmSoftPeriwinkleMist) { innerPadding ->
                content(Modifier.padding(innerPadding))
            }
        }
    } else {
        Scaffold(
            containerColor = VmSoftPeriwinkleMist,
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
