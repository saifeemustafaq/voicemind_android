package com.voicemind.ui.components

/** Formats [ms] milliseconds as "M:SS.D" (e.g. 1:23.4). Used by recording and shared detail screens. */
internal fun formatMmSsDecimal(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return "%d:%02d.%d".format(minutes, seconds, tenths)
}
