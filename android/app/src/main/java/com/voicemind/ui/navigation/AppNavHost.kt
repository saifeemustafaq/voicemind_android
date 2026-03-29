package com.voicemind.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.ui.checklist.ChecklistScreen
import com.voicemind.ui.checklist.TaskDetailScreen
import com.voicemind.ui.folders.FolderDetailScreen
import com.voicemind.ui.folders.FoldersScreen
import com.voicemind.ui.recording.RecordingDetailScreen
import com.voicemind.ui.recording.RecordingsScreen
import com.voicemind.ui.settings.SettingsScreen
import com.voicemind.ui.sharing.SharedItemsScreen
import com.voicemind.ui.sharing.SharedRecordingDetailScreen
import com.voicemind.ui.summaries.SummariesScreen
import kotlinx.coroutines.launch

private const val TABS_ROUTE = "tabs"

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
    val defaultLandingPageOrNull by navPreferenceRepository.defaultLandingPage.collectAsStateWithLifecycle(initialValue = null)
    val navOrderOrNull by navPreferenceRepository.navOrder.collectAsStateWithLifecycle(initialValue = null)
    val useSidebar = useSidebarOrNull ?: return
    val startDestination = defaultLandingPageOrNull ?: return
    val orderedNavItems = navOrderOrNull?.let { Routes.orderedItems(it) } ?: Routes.drawerItems

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
            if (navController.currentBackStackEntry?.destination?.route != destination.route) {
                navController.popBackStack(destination.route, inclusive = false)
            }
        }
    }

    val onOpenDrawer: (() -> Unit)? = if (useSidebar) {
        { scope.launch { drawerState.open() } }
    } else {
        null
    }

    val onSettings: () -> Unit = { navigateTo(Routes.Settings) }

    if (useSidebar) {
        // ── Sidebar / Drawer mode — unchanged from original ─────────────

        val sidebarNavigate: (Routes) -> Unit = { destination ->
            scope.launch { drawerState.close() }
            navigateTo(destination)
        }

        LaunchedEffect(openRecordingsOnStart) {
            if (openRecordingsOnStart) {
                navigateTo(Routes.Recordings)
                onRecordingsOpened()
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SidebarDrawer(
                    currentRoute = currentRoute ?: startDestination,
                    onNavigate = sidebarNavigate,
                    items = orderedNavItems,
                )
            },
        ) {
            Scaffold { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable(Routes.Recordings.route) {
                        RecordingsScreen(onOpenDrawer = onOpenDrawer, navController = navController, onSettings = onSettings)
                    }
                    composable(Routes.Checklist.route) {
                        ChecklistScreen(
                            onOpenDrawer = onOpenDrawer,
                            onTaskClick = { itemId -> navController.navigate(taskDetailRoute(itemId)) },
                            onSettings = onSettings,
                        )
                    }
                    composable(Routes.Summaries.route) {
                        SummariesScreen(onOpenDrawer = onOpenDrawer, onSettings = onSettings)
                    }
                    composable(Routes.Folders.route) {
                        FoldersScreen(
                            onFolderClick = { folderId -> navController.navigate(folderDetailRoute(folderId)) },
                            onSharedItemsClick = { navController.navigate(SHARED_ITEMS_ROUTE) },
                            onOpenDrawer = onOpenDrawer,
                            onSettings = onSettings,
                        )
                    }
                    detailRoutes(navController, onSignOut, onOpenDrawer)
                }
            }
        }
    } else {
        // ── Bottom bar mode — HorizontalPager for tab swiping ───────────

        val startIndex = orderedNavItems
            .indexOfFirst { it.route == startDestination }
            .coerceAtLeast(0)

        val pagerState = rememberPagerState(initialPage = startIndex) { orderedNavItems.size }

        val pagerCurrentRoute by remember {
            derivedStateOf { orderedNavItems[pagerState.currentPage].route }
        }

        LaunchedEffect(openRecordingsOnStart) {
            if (openRecordingsOnStart) {
                val recIndex = orderedNavItems.indexOfFirst { it is Routes.Recordings }
                if (recIndex >= 0) pagerState.scrollToPage(recIndex)
                onRecordingsOpened()
            }
        }

        Scaffold(
            bottomBar = {
                BottomNavBar(
                    currentRoute = if (currentRoute == TABS_ROUTE || currentRoute == null)
                        pagerCurrentRoute else currentRoute,
                    onNavigate = { destination ->
                        val index = orderedNavItems.indexOf(destination)
                        if (index >= 0) {
                            if (currentRoute != TABS_ROUTE) {
                                navController.popBackStack(TABS_ROUTE, inclusive = false)
                            }
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                    items = orderedNavItems,
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TABS_ROUTE,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(TABS_ROUTE) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = orderedNavItems.size - 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (orderedNavItems[page]) {
                            Routes.Recordings -> RecordingsScreen(
                                navController = navController,
                                onSettings = onSettings,
                            )
                            Routes.Checklist -> ChecklistScreen(
                                onTaskClick = { itemId -> navController.navigate(taskDetailRoute(itemId)) },
                                onSettings = onSettings,
                            )
                            Routes.Summaries -> SummariesScreen(
                                onSettings = onSettings,
                            )
                            Routes.Folders -> FoldersScreen(
                                onFolderClick = { folderId -> navController.navigate(folderDetailRoute(folderId)) },
                                onSharedItemsClick = { navController.navigate(SHARED_ITEMS_ROUTE) },
                                onSettings = onSettings,
                            )
                            else -> {}
                        }
                    }
                }
                detailRoutes(navController, onSignOut, onOpenDrawer = null)
            }
        }
    }
}

private fun NavGraphBuilder.detailRoutes(
    navController: NavController,
    onSignOut: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
) {
    composable(TASK_DETAIL_ROUTE) {
        TaskDetailScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.Settings.route) {
        SettingsScreen(onSignOut = onSignOut, onOpenDrawer = onOpenDrawer)
    }
    composable(FOLDER_DETAIL_ROUTE) { backStackEntry ->
        val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
        FolderDetailScreen(folderId = folderId, onBack = { navController.popBackStack() })
    }
    composable(RECORDING_DETAIL_ROUTE) { backStackEntry ->
        val recordingId = backStackEntry.arguments?.getString("recordingId") ?: return@composable
        RecordingDetailScreen(
            recordingId = recordingId,
            navController = navController,
            onBack = { navController.popBackStack() },
        )
    }
    composable(SHARED_ITEMS_ROUTE) {
        SharedItemsScreen(
            onBack = { navController.popBackStack() },
            onRecordingClick = { ownerUid, recordingId ->
                navController.navigate(sharedRecordingDetailRoute(ownerUid, recordingId))
            },
            onTaskClick = { taskId -> navController.navigate(taskDetailRoute(taskId)) },
        )
    }
    composable(SHARED_RECORDING_DETAIL_ROUTE) { backStackEntry ->
        val ownerUid = backStackEntry.arguments?.getString("ownerUid") ?: return@composable
        val recordingId = backStackEntry.arguments?.getString("recordingId") ?: return@composable
        SharedRecordingDetailScreen(
            ownerUid = ownerUid,
            recordingId = recordingId,
            onBack = { navController.popBackStack() },
        )
    }
}
