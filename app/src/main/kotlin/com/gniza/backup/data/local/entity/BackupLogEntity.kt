package com.gniza.backup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gniza.backup.domain.model.BackupLog
import com.gniza.backup.domain.model.BackupStatus

@Entity(tableName = "backup_logs")
data class BackupLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val sourceName: String,
    val serverName: String,
    val scheduleId: Long,
    val scheduleName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
    val filesTransferred: Int?,
    val bytesTransferred: Long?,
    val totalFiles: Int?,
    val rsyncOutput: String?,
    val errorMessage: String?,
    val durationSeconds: Int?
) {
    fun toBackupLog(): BackupLog = BackupLog(
        id = id,
        sourceId = sourceId,
        sourceName = sourceName,
        serverName = serverName,
        scheduleId = scheduleId,
        scheduleName = scheduleName,
        startedAt = startedAt,
        completedAt = completedAt,
        status = BackupStatus.valueOf(status),
        filesTransferred = filesTransferred,
        bytesTransferred = bytesTransferred,
        totalFiles = totalFiles,
        rsyncOutput = rsyncOutput,
        errorMessage = errorMessage,
        durationSeconds = durationSeconds
    )

    companion object {
        fun fromBackupLog(log: BackupLog): BackupLogEntity = BackupLogEntity(
            id = log.id,
            sourceId = log.sourceId,
            sourceName = log.sourceName,
            serverName = log.serverName,
            scheduleId = log.scheduleId,
            scheduleName = log.scheduleName,
            startedAt = log.startedAt,
            completedAt = log.completedAt,
            status = log.status.name,
            filesTransferred = log.filesTransferred,
            bytesTransferred = log.bytesTransferred,
            totalFiles = log.totalFiles,
            rsyncOutput = log.rsyncOutput,
            errorMessage = log.errorMessage,
            durationSeconds = log.durationSeconds
        )
    }
}
