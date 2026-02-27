package com.voicemind.ui.recording

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicemind.ui.theme.VmBlushPink
import com.voicemind.ui.theme.VmDeepViolet
import com.voicemind.ui.theme.VmError
import com.voicemind.ui.theme.VmLightLavender
import com.voicemind.ui.theme.VmSoftPeriwinkleMist
import com.voicemind.ui.theme.VmWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingBottomSheet(
    viewModel: RecordingViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (!state.showSheet) return

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        shape = sheetShape,
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sheetShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            VmSoftPeriwinkleMist.copy(alpha = 0.97f),
                            VmLightLavender.copy(alpha = 0.85f),
                        )
                    )
                )
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                shape = sheetShape,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Drag handle bar
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(VmLightLavender.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = VmDeepViolet
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Saving...", style = MaterialTheme.typography.titleMedium)
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
                        color = VmBlushPink,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.alpha(pulseAlpha)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = formatTime(state.elapsedSeconds),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 48.sp,
                        ),
                        color = VmDeepViolet,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VmDeepViolet,
                            unfocusedBorderColor = VmLightLavender.copy(alpha = 0.4f),
                            focusedContainerColor = VmSoftPeriwinkleMist.copy(alpha = 0.3f),
                            unfocusedContainerColor = VmSoftPeriwinkleMist.copy(alpha = 0.3f),
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Controls in a frosted card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = VmLightLavender.copy(alpha = 0.22f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)),
                    ) {
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
                                color = VmError.copy(alpha = 0.1f),
                                border = BorderStroke(0.5.dp, VmError.copy(alpha = 0.2f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Discard",
                                        tint = VmError,
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
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = VmDeepViolet.copy(alpha = 0.1f),
                                border = BorderStroke(0.5.dp, VmDeepViolet.copy(alpha = 0.2f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (state.isPaused) "Resume" else "Pause",
                                        tint = VmDeepViolet,
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
                                    containerColor = VmBlushPink,
                                    contentColor = VmWhite
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                    pressedElevation = 1.dp,
                                ),
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop and save",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
