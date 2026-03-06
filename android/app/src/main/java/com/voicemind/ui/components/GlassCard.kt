package com.voicemind.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.VmDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = VmDimens.RadiusMedium,
    innerPadding: Dp = VmDimens.SpaceLg,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier
            .clip(shape)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            )
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .border(
                width = VmDimens.HairlineBorder,
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .then(clickModifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
