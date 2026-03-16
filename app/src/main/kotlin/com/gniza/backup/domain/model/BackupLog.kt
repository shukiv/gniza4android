package com.gniza.backup.domain.model

data class BackupLog(
    val id: Long = 0,
    val sourceId: Long = 0,
    val sourceName: String = "",
    val serverName: String = "",
    val scheduleId: Long = 0,
    val scheduleName: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val status: BackupStatus = BackupStatus.RUNNING,
    val filesTransferred: Int? = null,
    val bytesTransferred: Long? = null,
    val totalFiles: Int? = null,
    val rsyncOutput: String? = null,
    val errorMessage: String? = null,
    val durationSeconds: Int? = null,
    val snapshotName: String? = null
)
