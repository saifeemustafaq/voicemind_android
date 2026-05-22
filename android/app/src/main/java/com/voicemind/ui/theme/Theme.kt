package com.voicemind.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val VmLightColorScheme = lightColorScheme(
    primary              = M3Light_Primary,
    onPrimary            = M3Light_OnPrimary,
    primaryContainer     = M3Light_PrimaryContainer,
    onPrimaryContainer   = M3Light_OnPrimaryContainer,
    secondary            = M3Light_Secondary,
    onSecondary          = M3Light_OnSecondary,
    secondaryContainer   = M3Light_SecondaryContainer,
    onSecondaryContainer = M3Light_OnSecondaryContainer,
    tertiary             = M3Light_Tertiary,
    onTertiary           = M3Light_OnTertiary,
    tertiaryContainer    = M3Light_TertiaryContainer,
    onTertiaryContainer  = M3Light_OnTertiaryContainer,
    error                = M3Light_Error,
    onError              = M3Light_OnError,
    errorContainer       = M3Light_ErrorContainer,
    onErrorContainer     = M3Light_OnErrorContainer,
    background           = M3Light_Background,
    onBackground         = M3Light_OnBackground,
    surface              = M3Light_Surface,
    onSurface            = M3Light_OnSurface,
    surfaceVariant       = M3Light_SurfaceVariant,
    onSurfaceVariant     = M3Light_OnSurfaceVariant,
    outline              = M3Light_Outline,
    outlineVariant       = M3Light_OutlineVariant,
    inverseSurface       = M3Light_InverseSurface,
    inverseOnSurface     = M3Light_InverseOnSurface,
    inversePrimary       = M3Light_InversePrimary,
    surfaceTint          = M3Light_SurfaceTint,
)

private val VmDarkColorScheme = darkColorScheme(
    primary              = M3Dark_Primary,
    onPrimary            = M3Dark_OnPrimary,
    primaryContainer     = M3Dark_PrimaryContainer,
    onPrimaryContainer   = M3Dark_OnPrimaryContainer,
    secondary            = M3Dark_Secondary,
    onSecondary          = M3Dark_OnSecondary,
    secondaryContainer   = M3Dark_SecondaryContainer,
    onSecondaryContainer = M3Dark_OnSecondaryContainer,
    tertiary             = M3Dark_Tertiary,
    onTertiary           = M3Dark_OnTertiary,
    tertiaryContainer    = M3Dark_TertiaryContainer,
    onTertiaryContainer  = M3Dark_OnTertiaryContainer,
    error                = M3Dark_Error,
    onError              = M3Dark_OnError,
    errorContainer       = M3Dark_ErrorContainer,
    onErrorContainer     = M3Dark_OnErrorContainer,
    background           = M3Dark_Background,
    onBackground         = M3Dark_OnBackground,
    surface              = M3Dark_Surface,
    onSurface            = M3Dark_OnSurface,
    surfaceVariant       = M3Dark_SurfaceVariant,
    onSurfaceVariant     = M3Dark_OnSurfaceVariant,
    outline              = M3Dark_Outline,
    outlineVariant       = M3Dark_OutlineVariant,
    inverseSurface       = M3Dark_InverseSurface,
    inverseOnSurface     = M3Dark_InverseOnSurface,
    inversePrimary       = M3Dark_InversePrimary,
    surfaceTint          = M3Dark_SurfaceTint,
)

@Composable
fun VoiceMindAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> VmDarkColorScheme
        else -> VmLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = VmTypography,
        content = content,
    )
}
