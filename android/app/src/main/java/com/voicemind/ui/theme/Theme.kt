package com.voicemind.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VoiceMindColorScheme = lightColorScheme(
    primary = VmDeepViolet,
    onPrimary = VmWhite,
    primaryContainer = VmPastelViolet,
    onPrimaryContainer = VmTextPrimary,
    secondary = VmCoolSkyBlue,
    onSecondary = VmTextPrimary,
    secondaryContainer = VmLightLavender,
    onSecondaryContainer = VmTextPrimary,
    tertiary = VmBlushPink,
    onTertiary = VmWhite,
    background = VmSoftPeriwinkleMist,
    onBackground = VmTextPrimary,
    surface = VmSurface,
    onSurface = VmTextPrimary,
    surfaceVariant = VmLightLavender,
    onSurfaceVariant = VmTextSecondary,
    error = VmError,
    onError = VmWhite,
    outline = Color.White.copy(alpha = 0.5f),
)

@Composable
fun VoiceMindAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VoiceMindColorScheme,
        typography = VmTypography,
        content = content
    )
}
