package com.voicemind.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppTypeConverters {

    private val gson = Gson()

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus =
        value?.let { SyncStatus.valueOf(it) } ?: SyncStatus.SYNCED

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromStringList(list: List<String>): String = gson.toJson(list)
}
