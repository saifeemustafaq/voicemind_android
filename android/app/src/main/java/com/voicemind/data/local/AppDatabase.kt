package com.voicemind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.voicemind.data.local.dao.ActionItemDao
import com.voicemind.data.local.dao.CollectiveSummaryDao
import com.voicemind.data.local.dao.FolderDao
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.ActionItemEntity
import com.voicemind.data.local.entity.CollectiveSummaryEntity
import com.voicemind.data.local.entity.FolderEntity
import com.voicemind.data.local.entity.RecordingEntity

@Database(
    entities = [
        RecordingEntity::class,
        ActionItemEntity::class,
        FolderEntity::class,
        CollectiveSummaryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun folderDao(): FolderDao
    abstract fun collectiveSummaryDao(): CollectiveSummaryDao
}
