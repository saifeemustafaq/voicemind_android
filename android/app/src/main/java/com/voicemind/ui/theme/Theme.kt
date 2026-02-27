package com.voicemind.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IosColorScheme = lightColorScheme(
    primary = IosAccent,
    onPrimary = IosWhite,
    primaryContainer = IosAccent,
    onPrimaryContainer = IosWhite,
    secondary = IosAccent,
    onSecondary = IosWhite,
    secondaryContainer = IosTertiaryFill,
    onSecondaryContainer = IosLabel,
    tertiary = IosWarning,
    onTertiary = IosWhite,
    background = IosBackground,
    onBackground = IosLabel,
    surface = IosSecondaryBackground,
    onSurface = IosLabel,
    surfaceVariant = IosTertiaryBackground,
    onSurfaceVariant = IosSecondaryLabel,
    error = IosDestructive,
    onError = IosWhite,
    outline = IosSeparator,
    outlineVariant = IosOpaqueSeparator,
)

private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun VoiceMindAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IosColorScheme,
        typography = VmTypography,
        shapes = IosShapes,
        content = content
    )
}
