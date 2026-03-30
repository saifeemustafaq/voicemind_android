package com.voicemind.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Routes(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    data object Recordings : Routes("recordings", "Recordings", Icons.Filled.Mic, Icons.Outlined.Mic)
    data object Checklist : Routes("checklist", "Checklist", Icons.Filled.Checklist, Icons.Outlined.Checklist)
    data object Summaries : Routes("summaries", "Summaries", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    data object Folders : Routes("folders", "Folders", Icons.Filled.Folder, Icons.Outlined.Folder)
    data object Settings : Routes("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val drawerItems by lazy { listOf(Recordings, Checklist, Summaries, Folders) }

        fun orderedItems(order: List<String>): List<Routes> {
            val mapped = order.mapNotNull { route -> drawerItems.find { it.route == route } }
            val missing = drawerItems.filter { item -> mapped.none { it.route == item.route } }
            return mapped + missing
        }
    }
}

const val FOLDER_DETAIL_ROUTE = "folder_detail/{folderId}"

fun folderDetailRoute(folderId: String) = "folder_detail/$folderId"

const val TASK_DETAIL_ROUTE = "task_detail/{itemId}"

fun taskDetailRoute(itemId: String) = "task_detail/$itemId"

const val RECORDING_DETAIL_ROUTE = "recording_detail/{recordingId}"

fun recordingDetailRoute(recordingId: String) = "recording_detail/$recordingId"

const val SHARED_ITEMS_ROUTE = "shared_items"

const val SHARED_RECORDING_DETAIL_ROUTE = "shared_recording/{ownerUid}/{recordingId}"

fun sharedRecordingDetailRoute(ownerUid: String, recordingId: String) =
    "shared_recording/$ownerUid/$recordingId"

const val SHARED_SUMMARY_DETAIL_ROUTE = "shared_summary/{ownerUid}/{summaryId}"

fun sharedSummaryDetailRoute(ownerUid: String, summaryId: String) =
    "shared_summary/$ownerUid/$summaryId"
