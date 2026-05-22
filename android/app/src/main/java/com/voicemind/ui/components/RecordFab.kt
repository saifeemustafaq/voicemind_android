package com.voicemind.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val RecordRed = Color(0xFFE53935)

@Composable
fun RecordFab(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartRecording()
    }

    // Outer Box claims the full 120dp zone flush against the nav bar.
    // Geometry (measured from nav bar top = 0):
    //   0 dp  → gradient solid  (nav bar top)
    //  24 dp  → FAB bottom edge (gradient ≈ 0.95 alpha)
    //  88 dp  → FAB top edge    (gradient ≈ 0.25 alpha, fade begins)
    //  96 dp  → fade start      (gradient transparent, "a few % above button top")
    // 120 dp  → overlay top     (transparent, well above button)
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        // Gradient shelf — fills entire 120dp zone, touch-passthrough
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.20f to Color.Transparent,                       // 96dp — fade start
                            0.27f to surfaceContainer.copy(alpha = 0.25f),   // 88dp — FAB top edge
                            0.65f to surfaceContainer.copy(alpha = 0.85f),   // 42dp — rapidly solidifying
                            0.80f to surfaceContainer.copy(alpha = 0.95f),   // 24dp — FAB bottom edge
                            1.00f to surfaceContainer,                        //  0dp — nav bar top
                        )
                    )
                )
                .pointerInput(Unit) { /* touch passthrough */ }
        )

        // FAB — 24dp internal bottom padding creates the solid shelf zone beneath it
        FloatingActionButton(
            onClick = {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) onStartRecording()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier
                .padding(bottom = 24.dp)
                .size(64.dp),
            containerColor = RecordRed,
            contentColor = Color.White,
            shape = CircleShape,
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Record")
        }
    }
}
