package com.voicemind.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosBackground
import com.voicemind.ui.theme.IosWhite

@Composable
fun RecordFab(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartRecording()
    }

    Box(
        modifier = modifier
            .size(120.dp)
            .background(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to IosBackground,
                        0.5f to IosBackground,
                        1.0f to Color.Transparent,
                    ),
                    radius = with(density) { 60.dp.toPx() },
                )
            ),
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
            modifier = Modifier.size(72.dp),
            containerColor = IosAccent,
            contentColor = IosWhite,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Record",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
