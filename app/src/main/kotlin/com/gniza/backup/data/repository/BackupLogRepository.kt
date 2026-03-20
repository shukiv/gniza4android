package com.gniza.backup.data.repository

import com.gniza.backup.data.local.dao.BackupLogDao
import com.gniza.backup.data.local.entity.BackupLogEntity
import com.gniza.backup.domain.model.BackupLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BackupLogRepository @Inject constructor(
    private val backupLogDao: BackupLogDao
) {
    val allLogs: Flow<List<BackupLog>> = backupLogDao.getAllWithoutOutput().map { entities ->
        entities.map { it.toBackupLog() }
    }

    val latestPerSchedule: Flow<List<BackupLog>> = backupLogDao.getLatestPerSchedule().map { entities ->
        entities.map { it.toBackupLog() }
    }

    fun getLogsBySource(sourceId: Long): Flow<List<BackupLog>> =
        backupLogDao.getBySourceId(sourceId).map { entities ->
            entities.map { it.toBackupLog() }
        }

    fun getLogsBySchedule(scheduleId: Long): Flow<List<BackupLog>> =
        backupLogDao.getByScheduleId(scheduleId).map { entities ->
            entities.map { it.toBackupLog() }
        }

    fun getLog(id: Long): Flow<BackupLog?> = backupLogDao.getById(id).map { it?.toBackupLog() }

    suspend fun createLog(log: BackupLog): Long {
        return backupLogDao.insert(BackupLogEntity.fromBackupLog(log))
    }

    suspend fun updateLog(log: BackupLog) {
        backupLogDao.update(BackupLogEntity.fromBackupLog(log))
    }

    suspend fun deleteOldLogs(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        backupLogDao.deleteOlderThan(cutoff)
    }

    suspend fun deleteAllLogs() {
        backupLogDao.deleteAll()
    }

    suspend fun markStaleRunningAsFailed() {
        backupLogDao.markStaleRunningAsFailed()
    }
}
