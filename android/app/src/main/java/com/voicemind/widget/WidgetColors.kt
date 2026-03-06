package com.voicemind.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

// Glance widget color providers derived from the M3 brand color scheme.
// Glance 1.1.1 does not support day/night ColorProvider overloads; using light-mode values.
object WidgetColors {
    val Background           = ColorProvider(Color(0xFFFDFCFF))
    val Label                = ColorProvider(Color(0xFF1A1C1E))
    val SecondaryLabel       = ColorProvider(Color(0xFF43474E))
    val Accent               = ColorProvider(Color(0xFF0061A4))
    val AccentContainer      = ColorProvider(Color(0xFFD1E4FF))
    val OnAccent             = ColorProvider(Color(0xFFFFFFFF))
    val Destructive          = ColorProvider(Color(0xFFBA1A1A))
    val DestructiveContainer = ColorProvider(Color(0xFFFFDAD6))
    val OnSurface            = ColorProvider(Color(0xFF1A1C1E))
    val White                = ColorProvider(Color(0xFFFFFFFF))
    val Warning              = ColorProvider(Color(0xFFE6A817))
}
