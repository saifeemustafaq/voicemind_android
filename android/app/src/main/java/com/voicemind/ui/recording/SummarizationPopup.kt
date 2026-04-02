package com.voicemind.ui.recording

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.ShimmerBlue
import com.voicemind.ui.theme.ShimmerGold
import com.voicemind.ui.theme.ShimmerPurple

@Composable
internal fun SummarizationPopup(onHide: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(ShimmerBlue, ShimmerGold, ShimmerPurple, ShimmerBlue),
        start = Offset(animatedOffset * 800f - 400f, 0f),
        end = Offset(animatedOffset * 800f + 400f, 0f),
    )
    val isDark = isSystemInDarkTheme()

    val borderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.65f else 0.90f),
        0.35f to ShimmerBlue.copy(alpha = 0.50f),
        1.0f to ShimmerPurple.copy(alpha = 0.20f),
    )
    val islandBackground = if (isDark) Color(0xFF1E2030) else Color(0xFFF2F5FF)
    val iridescence = Brush.linearGradient(
        colors = listOf(
            ShimmerBlue.copy(alpha = 0.10f),
            ShimmerPurple.copy(alpha = 0.08f),
            Color.Transparent,
        ),
        start = Offset(0f, 0f),
        end = Offset(280f, 56f),
    )
    val liquidSpecular = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = if (isDark) 0.16f else 0.75f),
        0.40f to Color.White.copy(alpha = if (isDark) 0.04f else 0.20f),
        1.0f to Color.Transparent,
    )
    val islandShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(min = 220.dp, max = 320.dp)
            .shadow(
                elevation = 12.dp,
                shape = islandShape,
                spotColor = ShimmerBlue.copy(alpha = 0.6f),
                ambientColor = ShimmerPurple.copy(alpha = 0.25f),
            )
            .border(width = 1.5.dp, brush = borderBrush, shape = islandShape)
            .background(color = islandBackground, shape = islandShape)
            .clip(islandShape),
    ) {
        Box(modifier = Modifier.matchParentSize().background(brush = iridescence))
        Box(modifier = Modifier.matchParentSize().background(brush = liquidSpecular))
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PulseRingIcon()
            Text(
                text = "Generating Summary…",
                style = MaterialTheme.typography.bodyMedium.copy(brush = shimmerBrush),
                maxLines = 1,
            )
            IconButton(
                onClick = onHide,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Hide",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun PulseRingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val innerScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "innerScale",
    )
    val innerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "innerAlpha",
    )
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 1.3f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerScale",
    )
    val outerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerAlpha",
    )

    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(outerScale)
                .alpha(outerAlpha)
                .background(color = ShimmerBlue.copy(alpha = 0.3f), shape = CircleShape),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(innerScale)
                .alpha(innerAlpha)
                .background(color = ShimmerPurple.copy(alpha = 0.4f), shape = CircleShape),
        )
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = ShimmerBlue,
        )
    }
}
