package com.voicemind.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Routes(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    data object Home : Routes("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Recordings : Routes("recordings", "Recordings", Icons.Filled.Mic, Icons.Outlined.Mic)
    data object Checklist : Routes("checklist", "Checklist", Icons.Filled.Checklist, Icons.Outlined.Checklist)
    data object Folders : Routes("folders", "Folders", Icons.Filled.Folder, Icons.Outlined.Folder)
    data object Settings : Routes("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val drawerItems by lazy { listOf(Home, Recordings, Checklist, Folders, Settings) }
    }
}

const val FOLDER_DETAIL_ROUTE = "folder_detail/{folderId}"

fun folderDetailRoute(folderId: String) = "folder_detail/$folderId"

const val TASK_DETAIL_ROUTE = "task_detail/{itemId}"

fun taskDetailRoute(itemId: String) = "task_detail/$itemId"
