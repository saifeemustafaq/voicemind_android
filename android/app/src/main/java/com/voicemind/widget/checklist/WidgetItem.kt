package com.voicemind.widget.checklist

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class WidgetItem(val id: String, val title: String, val completed: Boolean)

private val gson = Gson()
private val listType = object : TypeToken<List<WidgetItem>>() {}.type

fun serializeWidgetItems(items: List<WidgetItem>): String = gson.toJson(items)

fun deserializeWidgetItems(json: String): List<WidgetItem> =
    runCatching { gson.fromJson<List<WidgetItem>>(json, listType) ?: emptyList() }
        .getOrElse { emptyList() }
