package com.voicemind.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.IosWhite

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    @Suppress("UNUSED_PARAMETER") alpha: Float = 1f,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = IosWhite,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        content = content
    )
}
