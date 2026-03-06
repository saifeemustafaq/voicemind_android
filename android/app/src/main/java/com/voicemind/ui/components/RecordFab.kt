package com.voicemind.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosWhite
import com.voicemind.ui.theme.VmDimens

@Composable
fun RecordFab(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartRecording()
    }

    Box(
        modifier = modifier.size(VmDimens.FabContainerSize),
        contentAlignment = Alignment.Center,
    ) {
        FloatingActionButton(
            onClick = {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) onStartRecording()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.size(VmDimens.FabSize),
            containerColor = IosAccent,
            contentColor = IosWhite,
            shape = CircleShape,
        ) {
            Icon(
                Lucide.Mic,
                contentDescription = "Record",
                modifier = Modifier.size(VmDimens.IconLg),
            )
        }
    }
}
