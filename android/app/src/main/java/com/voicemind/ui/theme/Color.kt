package com.voicemind.ui.theme

import androidx.compose.ui.graphics.Color

// ── Shimmer animation colors (standalone, not M3 roles) ──────────────────────
val ShimmerBlue   = Color(0xFF5E9EFF)
val ShimmerGold   = Color(0xFFFFD700)
val ShimmerPurple = Color(0xFFB47FFF)

// ── Brand color constants ─────────────────────────────────────────────────────
// Single source of truth for the light-mode brand palette (seed #0061A4).
// Referenced by both the M3 theme (below) and Glance widget colors.
object BrandColors {
    val Primary              = Color(0xFF0061A4)
    val OnPrimary            = Color(0xFFFFFFFF)
    val PrimaryContainer     = Color(0xFFD1E4FF)
    val OnPrimaryContainer   = Color(0xFF001D36)
    val Secondary            = Color(0xFF535F70)
    val OnSecondary          = Color(0xFFFFFFFF)
    val SecondaryContainer   = Color(0xFFD7E3F8)
    val OnSecondaryContainer = Color(0xFF101C2B)
    val Tertiary             = Color(0xFF006D3C)
    val OnTertiary           = Color(0xFFFFFFFF)
    val TertiaryContainer    = Color(0xFF89F8BC)
    val OnTertiaryContainer  = Color(0xFF002111)
    val Error                = Color(0xFFBA1A1A)
    val OnError              = Color(0xFFFFFFFF)
    val ErrorContainer       = Color(0xFFFFDAD6)
    val OnErrorContainer     = Color(0xFF410002)
    val Background           = Color(0xFFFDFCFF)
    val OnBackground         = Color(0xFF1A1C1E)
    val Surface              = Color(0xFFFDFCFF)
    val OnSurface            = Color(0xFF1A1C1E)
    val SurfaceVariant       = Color(0xFFDFE2EB)
    val OnSurfaceVariant     = Color(0xFF43474E)
    val Outline              = Color(0xFF73777F)
    val OutlineVariant       = Color(0xFFC3C7CF)
    val InverseSurface       = Color(0xFF2F3033)
    val InverseOnSurface     = Color(0xFFF1F0F4)
    val InversePrimary       = Color(0xFF9ECAFF)
    val SurfaceTint          = Color(0xFF0061A4)
    val Warning                  = Color(0xFFE6A817)
    val SurfaceContainerLowest   = Color(0xFFFFFFFF)
    val SurfaceContainerHigh     = Color(0xFFDDE3E8)
}

// ── Fallback M3 light color scheme ────────────────────────────────────────────
// Used only on Android < 12 where dynamic color is unavailable.
internal val M3Light_Primary              = BrandColors.Primary
internal val M3Light_OnPrimary            = BrandColors.OnPrimary
internal val M3Light_PrimaryContainer     = BrandColors.PrimaryContainer
internal val M3Light_OnPrimaryContainer   = BrandColors.OnPrimaryContainer
internal val M3Light_Secondary            = BrandColors.Secondary
internal val M3Light_OnSecondary          = BrandColors.OnSecondary
internal val M3Light_SecondaryContainer   = BrandColors.SecondaryContainer
internal val M3Light_OnSecondaryContainer = BrandColors.OnSecondaryContainer
internal val M3Light_Tertiary             = BrandColors.Tertiary
internal val M3Light_OnTertiary           = BrandColors.OnTertiary
internal val M3Light_TertiaryContainer    = BrandColors.TertiaryContainer
internal val M3Light_OnTertiaryContainer  = BrandColors.OnTertiaryContainer
internal val M3Light_Error                = BrandColors.Error
internal val M3Light_OnError              = BrandColors.OnError
internal val M3Light_ErrorContainer       = BrandColors.ErrorContainer
internal val M3Light_OnErrorContainer     = BrandColors.OnErrorContainer
internal val M3Light_Background           = BrandColors.Background
internal val M3Light_OnBackground         = BrandColors.OnBackground
internal val M3Light_Surface              = BrandColors.Surface
internal val M3Light_OnSurface            = BrandColors.OnSurface
internal val M3Light_SurfaceVariant       = BrandColors.SurfaceVariant
internal val M3Light_OnSurfaceVariant     = BrandColors.OnSurfaceVariant
internal val M3Light_Outline              = BrandColors.Outline
internal val M3Light_OutlineVariant       = BrandColors.OutlineVariant
internal val M3Light_InverseSurface       = BrandColors.InverseSurface
internal val M3Light_InverseOnSurface     = BrandColors.InverseOnSurface
internal val M3Light_InversePrimary       = BrandColors.InversePrimary
internal val M3Light_SurfaceTint          = BrandColors.SurfaceTint

// ── Fallback M3 dark color scheme ─────────────────────────────────────────────
internal val M3Dark_Primary              = Color(0xFF9ECAFF)
internal val M3Dark_OnPrimary            = Color(0xFF003258)
internal val M3Dark_PrimaryContainer     = Color(0xFF00497D)
internal val M3Dark_OnPrimaryContainer   = Color(0xFFD1E4FF)
internal val M3Dark_Secondary            = Color(0xFFBBC7DB)
internal val M3Dark_OnSecondary          = Color(0xFF253140)
internal val M3Dark_SecondaryContainer   = Color(0xFF3B4858)
internal val M3Dark_OnSecondaryContainer = Color(0xFFD7E3F8)
internal val M3Dark_Tertiary             = Color(0xFF6EFCAB)
internal val M3Dark_OnTertiary           = Color(0xFF003920)
internal val M3Dark_TertiaryContainer    = Color(0xFF00522E)
internal val M3Dark_OnTertiaryContainer  = Color(0xFF89F8BC)
internal val M3Dark_Error                = Color(0xFFFFB4AB)
internal val M3Dark_OnError              = Color(0xFF690005)
internal val M3Dark_ErrorContainer       = Color(0xFF93000A)
internal val M3Dark_OnErrorContainer     = Color(0xFFFFDAD6)
internal val M3Dark_Background           = Color(0xFF1A1C1E)
internal val M3Dark_OnBackground         = Color(0xFFE2E2E6)
internal val M3Dark_Surface              = Color(0xFF1A1C1E)
internal val M3Dark_OnSurface            = Color(0xFFE2E2E6)
internal val M3Dark_SurfaceVariant       = Color(0xFF43474E)
internal val M3Dark_OnSurfaceVariant     = Color(0xFFC3C7CF)
internal val M3Dark_Outline              = Color(0xFF8D9199)
internal val M3Dark_OutlineVariant       = Color(0xFF43474E)
internal val M3Dark_InverseSurface       = Color(0xFFE2E2E6)
internal val M3Dark_InverseOnSurface     = Color(0xFF2F3033)
internal val M3Dark_InversePrimary       = Color(0xFF0061A4)
internal val M3Dark_SurfaceTint          = Color(0xFF9ECAFF)
