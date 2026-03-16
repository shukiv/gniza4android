package com.gniza.backup.di

import android.content.Context
import androidx.room.Room
import com.gniza.backup.data.local.GnizaDatabase
import com.gniza.backup.data.local.dao.BackupLogDao
import com.gniza.backup.data.local.dao.BackupSourceDao
import com.gniza.backup.data.local.dao.ScheduleDao
import com.gniza.backup.data.local.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): GnizaDatabase {
        return Room.databaseBuilder(
            context,
            GnizaDatabase::class.java,
            "gniza.db"
        ).addMigrations(GnizaDatabase.MIGRATION_4_5, GnizaDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideServerDao(db: GnizaDatabase): ServerDao = db.serverDao()

    @Provides
    fun provideBackupSourceDao(db: GnizaDatabase): BackupSourceDao = db.backupSourceDao()

    @Provides
    fun provideBackupLogDao(db: GnizaDatabase): BackupLogDao = db.backupLogDao()

    @Provides
    fun provideScheduleDao(db: GnizaDatabase): ScheduleDao = db.scheduleDao()
}
