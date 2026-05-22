package com.voicemind.util

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.TimeZone

/**
 * Composition-local that carries the user's preferred timezone throughout the
 * composable tree.  Provided once near the root (MainActivity) so every screen
 * can read it without explicit parameter threading.
 */
val LocalAppTimeZone = staticCompositionLocalOf { TimeZone.getDefault() }
