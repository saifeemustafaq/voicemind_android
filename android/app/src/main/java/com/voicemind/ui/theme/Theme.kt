package com.voicemind.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val IosLightColorScheme = lightColorScheme(
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

private val IosDarkColorScheme = darkColorScheme(
    primary = IosDarkAccent,
    onPrimary = IosDarkLabel,
    primaryContainer = IosDarkAccent,
    onPrimaryContainer = IosDarkLabel,
    secondary = IosDarkAccent,
    onSecondary = IosDarkLabel,
    secondaryContainer = IosTertiaryFill,
    onSecondaryContainer = IosDarkLabel,
    tertiary = IosWarning,
    onTertiary = IosDarkLabel,
    background = IosDarkBackground,
    onBackground = IosDarkLabel,
    surface = IosDarkSecondaryBackground,
    onSurface = IosDarkLabel,
    surfaceVariant = IosDarkTertiaryBackground,
    onSurfaceVariant = IosDarkSecondaryLabel,
    error = IosDestructive,
    onError = IosDarkLabel,
    outline = IosDarkSeparator,
    outlineVariant = IosDarkSeparator,
)

private val VmShapes = Shapes(
    small = RoundedCornerShape(VmDimens.RadiusSmall),    // 10dp — inputs, chips
    medium = RoundedCornerShape(VmDimens.RadiusMedium),  // 14dp — cards, buttons
    large = RoundedCornerShape(VmDimens.RadiusLarge),    // 22dp — sheets, modals
)

@Composable
fun VoiceMindAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) IosDarkColorScheme else IosLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VmTypography,
        shapes = VmShapes,
        content = content,
    )
}
