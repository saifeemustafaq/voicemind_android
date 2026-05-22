package com.voicemind.ui.theme

import androidx.compose.ui.unit.dp

// All shape/radius tokens are intentionally removed.
// Use MaterialTheme.shapes.* everywhere (M3 shape system):
//   extraSmall  →  4 dp   (chips, text fields, menus, snackbars)
//   small       →  8 dp   (small chips)
//   medium      → 12 dp   (cards, small FABs)
//   large       → 16 dp   (FABs, extended FABs, nav drawers)
//   extraLarge  → 28 dp   (large FABs, bottom sheets)

object VmDimens {
    // Spacing — 4 dp grid
    val SpaceXxs = 2.dp
    val SpaceXs  = 4.dp
    val SpaceSm  = 8.dp
    val SpaceMd  = 12.dp
    val SpaceLg  = 16.dp
    val SpaceXl  = 24.dp
    val SpaceXxl = 32.dp
    val SpaceXxxl = 48.dp

    // FAB clearance — space reserved below scrollable content so the FAB never covers last item
    val FabClearance = 80.dp

    // Component sizes
    val ButtonHeight      = 40.dp   // M3 Button standard height
    val IconSm            = 16.dp
    val IconMd            = 22.dp
    val IconLg            = 32.dp
    val IconXl            = 48.dp
    val TouchTarget       = 48.dp   // M3 minimum touch target

    // Screen padding
    val ScreenHorizontalPadding = 16.dp

    // Border thickness
    val HairlineBorder = 1.dp
    val ThinBorder     = 1.dp
}
