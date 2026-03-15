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

    @Query("SELECT * FROM backup_logs WHERE sourceId = :sourceId ORDER BY startedAt DESC")
    fun getBySourceId(sourceId: Long): Flow<List<BackupLogEntity>>

    @Query("SELECT * FROM backup_logs WHERE scheduleId = :scheduleId ORDER BY startedAt DESC")
    fun getByScheduleId(scheduleId: Long): Flow<List<BackupLogEntity>>

    @Query("SELECT * FROM backup_logs WHERE id = :id")
    fun getById(id: Long): Flow<BackupLogEntity?>

    @Insert
    suspend fun insert(log: BackupLogEntity): Long

    @Update
    suspend fun update(log: BackupLogEntity)

    @Query("DELETE FROM backup_logs WHERE startedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
