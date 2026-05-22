package com.voicemind.util

import com.voicemind.data.model.Recording

fun List<Recording>.countByFolder(): Map<String, Int> =
    groupingBy { it.folderId }.eachCount()
