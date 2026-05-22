package com.voicemind.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val speedSteps = listOf(0.5f, 1.0f, 1.5f, 2.0f)

@Composable
internal fun SpeedBubble(
    currentSpeed: Float,
    onTap: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var hoveredIndex by remember { mutableStateOf(speedSteps.indexOf(currentSpeed).coerceAtLeast(1)) }

    val slotWidthDp = 64.dp
    val slotWidthPx = with(density) { slotWidthDp.toPx() }
    val bubbleWidthDp = slotWidthDp * speedSteps.size + 16.dp

    Box(contentAlignment = Alignment.Center) {
        if (dragging) {
            Surface(
                modifier = Modifier
                    .width(bubbleWidthDp)
                    .height(48.dp)
                    .offset(y = (-56).dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    speedSteps.forEachIndexed { index, speed ->
                        val isHovered = index == hoveredIndex
                        val scale by animateFloatAsState(
                            targetValue = if (isHovered) 1.3f else 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                            label = "scale$index",
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(
                                    if (isHovered) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                ),
                        ) {
                            Text(
                                text = if (speed == 1.0f || speed == 2.0f) "${speed.toInt()}x"
                                       else "${"%.1f".format(speed)}x",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (isHovered) 13.sp else 11.sp,
                                ),
                                color = if (isHovered) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val chipScale by animateFloatAsState(
            targetValue = if (dragging) 1.1f else 1f,
            animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
            label = "chipScale",
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .scale(chipScale)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
                .pointerInput(currentSpeed) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            hoveredIndex = speedSteps.indexOf(currentSpeed).coerceAtLeast(0)
                            dragOffsetX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount.x
                            val baseIndex = speedSteps.indexOf(currentSpeed).coerceAtLeast(0)
                            val delta = (dragOffsetX / slotWidthPx).toInt()
                            hoveredIndex = (baseIndex + delta).coerceIn(0, speedSteps.lastIndex)
                        },
                        onDragEnd = {
                            onSpeedSelected(speedSteps[hoveredIndex])
                            dragging = false
                            dragOffsetX = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            dragOffsetX = 0f
                        },
                    )
                },
        ) {
            Text(
                text = "${"%.1f".format(currentSpeed)}x",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
