package com.voicemind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw

private val PlayerButtonDark = Color(0xFF1C1C1E)
private val PlayerSkipButtonDark = Color(0xFF2C2C2E)
private val PlayerTrackInactive = Color(0xFFD1D1D6)

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun InlinePlayerControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val sliderProgress = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f
    val displayedProgress = if (isDragging) dragValue else sliderProgress

    Column(modifier = modifier.fillMaxWidth()) {
        // Transport buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                size = 40.dp,
                backgroundColor = PlayerSkipButtonDark,
                onClick = onSkipBackward,
                contentDescription = "Skip back 15 seconds",
            ) {
                Icon(
                    imageVector = Lucide.RotateCcw,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            Box(modifier = Modifier.size(16.dp))

            TransportButton(
                size = 48.dp,
                backgroundColor = PlayerButtonDark,
                onClick = onPlayPause,
                contentDescription = if (isPlaying) "Pause" else "Play",
            ) {
                Icon(
                    imageVector = if (isPlaying) Lucide.Pause else Lucide.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Box(modifier = Modifier.size(16.dp))

            TransportButton(
                size = 40.dp,
                backgroundColor = PlayerSkipButtonDark,
                onClick = onSkipForward,
                contentDescription = "Skip forward 15 seconds",
            ) {
                Icon(
                    imageVector = Lucide.RotateCw,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Seek slider
        Slider(
            value = displayedProgress,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * durationMs).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = PlayerButtonDark,
                activeTrackColor = PlayerButtonDark,
                inactiveTrackColor = PlayerTrackInactive,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(if (isDragging) (dragValue * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportButton(
    size: Dp,
    backgroundColor: Color,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
