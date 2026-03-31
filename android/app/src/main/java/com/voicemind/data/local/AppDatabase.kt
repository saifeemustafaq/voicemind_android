package com.voicemind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.voicemind.data.local.dao.ActionItemDao
import com.voicemind.data.local.dao.CollectiveSummaryDao
import com.voicemind.data.local.dao.FolderDao
import com.voicemind.data.local.dao.PendingDeleteDao
import com.voicemind.data.local.dao.RecordingDao
import com.voicemind.data.local.entity.ActionItemEntity
import com.voicemind.data.local.entity.CollectiveSummaryEntity
import com.voicemind.data.local.entity.FolderEntity
import com.voicemind.data.local.entity.PendingDeleteEntity
import com.voicemind.data.local.entity.RecordingEntity

@Database(
    entities = [
        RecordingEntity::class,
        ActionItemEntity::class,
        FolderEntity::class,
        CollectiveSummaryEntity::class,
        PendingDeleteEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun folderDao(): FolderDao
    abstract fun collectiveSummaryDao(): CollectiveSummaryDao
    abstract fun pendingDeleteDao(): PendingDeleteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_deletes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
