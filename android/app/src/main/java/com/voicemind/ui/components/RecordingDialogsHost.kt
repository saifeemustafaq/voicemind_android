package com.voicemind.ui.components

import androidx.compose.runtime.Composable
import com.voicemind.data.model.Folder
import com.voicemind.data.model.Recording
import com.voicemind.ui.recording.DeleteRecordingDialog
import com.voicemind.ui.recording.MoveToFolderDialog
import com.voicemind.ui.recording.RecordingsViewModel
import com.voicemind.ui.recording.RenameRecordingDialog
import com.voicemind.ui.recording.TranscriptSheet

@Composable
fun RecordingDialogsHost(
    showTranscript: Recording?,
    showRenameDialog: Recording?,
    showMoveDialog: Recording?,
    showDeleteConfirm: Recording?,
    folders: List<Folder>,
    viewModel: RecordingsViewModel,
    onDismissTranscript: () -> Unit,
    onDismissRename: () -> Unit,
    onDismissMove: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    showTranscript?.let { recording ->
        TranscriptSheet(
            recording = recording,
            viewModel = viewModel,
            onDismiss = onDismissTranscript,
        )
    }

    showRenameDialog?.let { recording ->
        RenameRecordingDialog(
            currentTitle = recording.title,
            onConfirm = { newTitle ->
                viewModel.renameRecording(recording.id, newTitle)
                onDismissRename()
            },
            onDismiss = onDismissRename,
        )
    }

    showMoveDialog?.let { recording ->
        MoveToFolderDialog(
            folders = folders,
            onConfirm = { folderId ->
                viewModel.moveToFolder(recording.id, folderId)
                onDismissMove()
            },
            onDismiss = onDismissMove,
        )
    }

    showDeleteConfirm?.let { recording ->
        DeleteRecordingDialog(
            recordingTitle = recording.title,
            onConfirm = {
                viewModel.deleteRecording(recording)
                onDismissDelete()
            },
            onDismiss = onDismissDelete,
        )
    }
}
