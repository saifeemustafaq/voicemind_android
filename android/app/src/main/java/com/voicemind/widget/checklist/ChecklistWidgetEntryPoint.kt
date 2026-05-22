package com.voicemind.widget.checklist

import com.voicemind.data.local.dao.ActionItemDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChecklistWidgetEntryPoint {
    fun actionItemDao(): ActionItemDao
}
