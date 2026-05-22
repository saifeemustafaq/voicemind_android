package com.voicemind.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A horizontal waveform visualization that keeps the playhead fixed at the center.
 * The waveform scrolls beneath it as [progress] changes.
 *
 * @param bars Normalized bar heights in [0, 1]. Empty → placeholder shown.
 * @param progress Playback progress in [0, 1].
 * @param isExtracting Whether amplitude extraction is still in progress.
 * @param onSeek Called with the new progress in [0, 1] as the user drags.
 */
@Composable
fun AudioWaveform(
    bars: List<Float>,
    progress: Float,
    isExtracting: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    barGap: Dp = 1.dp,
    minBarHeightFraction: Float = 0.08f,
) {
    val playedColor = MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.surfaceVariant
    val playheadColor = MaterialTheme.colorScheme.primary

    // Drag accumulation — tracks total horizontal drag in pixels
    var dragStartProgress by remember { mutableFloatStateOf(0f) }
    var totalDragPx by remember { mutableFloatStateOf(0f) }

    val displayBars: List<Float> = if (bars.isEmpty() || isExtracting) {
        // Placeholder: gentle random-ish heights
        remember {
            (0 until 200).map { i ->
                val base = 0.15f
                val wave = 0.1f * kotlin.math.sin(i * 0.3f).toFloat()
                (base + wave + (i % 7) * 0.01f).coerceIn(0.05f, 0.35f)
            }
        }
    } else {
        bars
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(displayBars.size) {
                detectHorizontalDragGestures(
                    onDragStart = { _ ->
                        dragStartProgress = progress
                        totalDragPx = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragPx += dragAmount
                        val barPitch = (barWidth + barGap).toPx()
                        val totalWidth = displayBars.size * barPitch
                        // Dragging left = seeking forward (waveform moves left → playhead moves right over bars)
                        val delta = -totalDragPx / totalWidth
                        val newProgress = (dragStartProgress + delta).coerceIn(0f, 1f)
                        onSeek(newProgress)
                    },
                )
            },
    ) {
        val barWidthPx = barWidth.toPx()
        val barGapPx = barGap.toPx()
        val barPitch = barWidthPx + barGapPx
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2f

        val playheadBarIndex = (progress * displayBars.size).toInt().coerceIn(0, displayBars.size - 1)
        // Offset so the playhead bar is at the center
        val firstBarX = centerX - playheadBarIndex * barPitch

        displayBars.forEachIndexed { index, amplitude ->
            val x = firstBarX + index * barPitch
            // Only draw bars visible on screen
            if (x + barWidthPx < 0f || x > canvasWidth) return@forEachIndexed

            val barHeight = (amplitude.coerceAtLeast(minBarHeightFraction) * canvasHeight)
                .coerceAtMost(canvasHeight)
            val top = (canvasHeight - barHeight) / 2f
            val color: Color = if (index <= playheadBarIndex) playedColor else unplayedColor

            drawRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(barWidthPx, barHeight),
            )
        }

        // Fixed playhead line at center
        drawLine(
            color = playheadColor,
            start = Offset(centerX, 0f),
            end = Offset(centerX, canvasHeight),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
