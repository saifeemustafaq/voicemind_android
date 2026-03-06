package com.voicemind.ui.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicemind.ui.components.VoiceMindTopAppBar
import com.voicemind.ui.recording.RecordingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId: String,
    onBack: () -> Unit,
    viewModel: FoldersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val folderName = state.folders.find { it.id == folderId }?.name ?: "Folder"

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceMindTopAppBar(
            title = folderName,
            icon = Lucide.Folder,
            onBack = onBack,
        )
        RecordingsScreen(folderId = folderId, onBack = onBack)
    }
}
