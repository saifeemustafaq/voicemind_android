package com.voicemind.ui.recording

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicemind.ui.components.voiceMindTextFieldColors
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosDestructive
import com.voicemind.ui.theme.IosLabel
import com.voicemind.ui.theme.IosOpaqueSeparator
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosWhite
import com.voicemind.ui.theme.VmDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingBottomSheet(
    viewModel: RecordingViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (!state.showSheet) return

    val sheetShape = RoundedCornerShape(topStart = VmDimens.RadiusLarge, topEnd = VmDimens.RadiusLarge)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = sheetShape,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(VmDimens.SpaceXs))
                    .background(IosOpaqueSeparator)
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = IosAccent
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Saving...", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(32.dp))
                return@Column
            }

            val pulseAlpha = if (state.isRecording) {
                val transition = rememberInfiniteTransition(label = "pulse")
                val alpha by transition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                alpha
            } else {
                1f
            }

            Text(
                text = if (state.isPaused) "Paused" else "Recording",
                style = MaterialTheme.typography.titleSmall,
                color = IosDestructive,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(pulseAlpha)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = formatTime(state.elapsedSeconds),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 40.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = voiceMindTextFieldColors()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Discard
                Surface(
                    onClick = { viewModel.discardRecording() },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = IosDestructive.copy(alpha = 0.1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Lucide.Trash2,
                            contentDescription = "Discard",
                            tint = IosDestructive,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Pause / Resume
                Surface(
                    onClick = {
                        if (state.isPaused) viewModel.resumeRecording()
                        else viewModel.pauseRecording()
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = IosAccent.copy(alpha = 0.1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isPaused) Lucide.Play else Lucide.Pause,
                            contentDescription = if (state.isPaused) "Resume" else "Pause",
                            tint = IosAccent,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                // Stop and save
                Button(
                    onClick = { viewModel.stopAndSave() },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IosDestructive,
                        contentColor = IosWhite
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Icon(
                        Lucide.Square,
                        contentDescription = "Stop and save",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

private fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
