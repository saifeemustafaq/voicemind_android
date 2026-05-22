package com.voicemind.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun Date.toFullDateString(tz: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
        .apply { timeZone = tz }
        .format(this)

fun Date.toShortDateString(tz: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("MMM dd 'at' h:mm a", Locale.getDefault())
        .apply { timeZone = tz }
        .format(this)

fun Date.toDefaultTitle(tz: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("MMM dd - h:mm a", Locale.getDefault())
        .apply { timeZone = tz }
        .format(this)

fun Date.toDateSectionKey(tz: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        .apply { timeZone = tz }
        .format(this)
