package com.voicemind.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.VmLightLavender

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    alpha: Float = 0.25f,
    innerPadding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = VmLightLavender.copy(alpha = alpha),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
