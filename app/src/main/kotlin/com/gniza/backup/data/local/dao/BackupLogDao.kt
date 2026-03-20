package com.gniza.backup.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gniza.backup.data.local.entity.BackupLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupLogDao {

    @Query("SELECT * FROM backup_logs ORDER BY startedAt DESC")
    fun getAll(): Flow<List<BackupLogEntity>>

    @Query("SELECT id, sourceId, sourceName, serverName, scheduleId, scheduleName, startedAt, completedAt, status, filesTransferred, bytesTransferred, totalFiles, NULL as rsyncOutput, errorMessage, durationSeconds, snapshotName FROM backup_logs ORDER BY startedAt DESC")
    fun getAllWithoutOutput(): Flow<List<BackupLogEntity>>

    @Query("SELECT * FROM backup_logs WHERE sourceId = :sourceId ORDER BY startedAt DESC")
    fun getBySourceId(sourceId: Long): Flow<List<BackupLogEntity>>

    @Query("SELECT * FROM backup_logs WHERE scheduleId = :scheduleId ORDER BY startedAt DESC")
    fun getByScheduleId(scheduleId: Long): Flow<List<BackupLogEntity>>

    @Query("SELECT id, sourceId, sourceName, serverName, scheduleId, scheduleName, startedAt, completedAt, status, filesTransferred, bytesTransferred, totalFiles, NULL as rsyncOutput, errorMessage, durationSeconds, snapshotName FROM backup_logs WHERE id IN (SELECT MAX(id) FROM backup_logs GROUP BY scheduleId)")
    fun getLatestPerSchedule(): Flow<List<BackupLogEntity>>

    @Query("SELECT * FROM backup_logs WHERE id = :id")
    fun getById(id: Long): Flow<BackupLogEntity?>

    @Insert
    suspend fun insert(log: BackupLogEntity): Long

    @Update
    suspend fun update(log: BackupLogEntity)

    @Query("DELETE FROM backup_logs WHERE startedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM backup_logs")
    suspend fun deleteAll()

    @Query("UPDATE backup_logs SET status = 'FAILED', errorMessage = 'Backup interrupted (app was killed)' WHERE status = 'RUNNING'")
    suspend fun markStaleRunningAsFailed()
}
