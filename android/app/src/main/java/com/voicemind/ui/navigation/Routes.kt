package com.voicemind.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Routes(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Routes("home", "Home", Icons.Default.Home)
    data object Recordings : Routes("recordings", "Recordings", Icons.Default.Mic)
    data object Checklist : Routes("checklist", "Checklist", Icons.Default.Checklist)
    data object Folders : Routes("folders", "Folders", Icons.Default.Folder)
    data object Settings : Routes("settings", "Settings", Icons.Default.Settings)

    companion object {
        val drawerItems by lazy { listOf(Home, Recordings, Checklist, Folders, Settings) }
    }
}

const val FOLDER_DETAIL_ROUTE = "folder_detail/{folderId}"

fun folderDetailRoute(folderId: String) = "folder_detail/$folderId"
