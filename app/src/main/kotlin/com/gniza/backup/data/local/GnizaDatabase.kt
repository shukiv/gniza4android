package com.gniza.backup.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gniza.backup.data.local.dao.BackupLogDao
import com.gniza.backup.data.local.dao.BackupSourceDao
import com.gniza.backup.data.local.dao.ScheduleDao
import com.gniza.backup.data.local.dao.ServerDao
import com.gniza.backup.data.local.entity.BackupLogEntity
import com.gniza.backup.data.local.entity.BackupSourceEntity
import com.gniza.backup.data.local.entity.ScheduleEntity
import com.gniza.backup.data.local.entity.ServerEntity

@Database(
    entities = [
        ServerEntity::class,
        BackupSourceEntity::class,
        BackupLogEntity::class,
        ScheduleEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GnizaDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun backupSourceDao(): BackupSourceDao
    abstract fun backupLogDao(): BackupLogDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN serverType TEXT NOT NULL DEFAULT 'SSH'")
            }
        }
    }
}
