package com.voicemind.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fullDateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
private val shortDateFormat = SimpleDateFormat("MMM dd 'at' h:mm a", Locale.getDefault())
private val defaultTitleFormat = SimpleDateFormat("MMM dd - h:mm a", Locale.getDefault())
private val dateSectionFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

fun Date.toFullDateString(): String = fullDateFormat.format(this)

fun Date.toShortDateString(): String = shortDateFormat.format(this)

fun Date.toDefaultTitle(): String = defaultTitleFormat.format(this)

fun Date.toDateSectionKey(): String = dateSectionFormat.format(this)
