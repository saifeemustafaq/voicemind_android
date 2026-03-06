package com.voicemind.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.House
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sparkles

sealed class Routes(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    data object Home : Routes("home", "Home", Lucide.House, Lucide.House)
    data object Recordings : Routes("recordings", "Recordings", Lucide.Mic, Lucide.Mic)
    data object Checklist : Routes("checklist", "Checklist", Lucide.ListChecks, Lucide.ListChecks)
    data object Summaries : Routes("summaries", "Summaries", Lucide.Sparkles, Lucide.Sparkles)
    data object Folders : Routes("folders", "Folders", Lucide.Folder, Lucide.Folder)
    data object Settings : Routes("settings", "Settings", Lucide.Settings, Lucide.Settings)

    companion object {
        val drawerItems by lazy { listOf(Home, Recordings, Checklist, Summaries, Folders) }
    }
}

const val FOLDER_DETAIL_ROUTE = "folder_detail/{folderId}"

fun folderDetailRoute(folderId: String) = "folder_detail/$folderId"

const val TASK_DETAIL_ROUTE = "task_detail/{itemId}"

fun taskDetailRoute(itemId: String) = "task_detail/$itemId"
