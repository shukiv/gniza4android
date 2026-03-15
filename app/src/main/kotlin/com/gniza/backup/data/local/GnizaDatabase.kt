package com.gniza.backup.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GnizaDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun backupSourceDao(): BackupSourceDao
    abstract fun backupLogDao(): BackupLogDao
    abstract fun scheduleDao(): ScheduleDao
}
