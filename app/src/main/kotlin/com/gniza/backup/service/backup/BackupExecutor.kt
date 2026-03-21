package com.gniza.backup.service.backup

import com.gniza.backup.data.repository.BackupLogRepository
import com.gniza.backup.data.repository.BackupSourceRepository
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.BackupLog
import com.gniza.backup.domain.model.BackupSource
import com.gniza.backup.domain.model.BackupStatus
import com.gniza.backup.domain.model.Schedule
import com.gniza.backup.domain.model.ServerType
import com.gniza.backup.service.nextcloud.NextcloudSync
import com.gniza.backup.service.rsync.RsyncBinaryResolver
import com.gniza.backup.service.rsync.RsyncCommand
import com.gniza.backup.service.rsync.RsyncEngine
import com.gniza.backup.service.rsync.RsyncOutput
import com.gniza.backup.service.ssh.SftpSyncFallback
import com.gniza.backup.service.ssh.DropbearKeyConverter
import com.gniza.backup.service.ssh.SshBinaryResolver
import com.gniza.backup.service.ssh.SshCommandExecutor
import com.gniza.backup.util.Constants
import timber.log.Timber
import javax.inject.Inject

class BackupExecutor @Inject constructor(
    private val rsyncBinaryResolver: RsyncBinaryResolver,
    private val sshBinaryResolver: SshBinaryResolver,
    private val rsyncEngine: RsyncEngine,
    private val sftpSyncFallback: SftpSyncFallback,
    private val nextcloudSync: NextcloudSync,
    private val dropbearKeyConverter: DropbearKeyConverter,
    private val backupLogRepository: BackupLogRepository,
    private val serverRepository: ServerRepository,
    private val backupSourceRepository: BackupSourceRepository,
    private val snapshotManager: SnapshotManager,
    private val sshCommandExecutor: SshCommandExecutor
) {

    data class BackupResult(
        val success: Boolean,
        val filesTransferred: Int,
        val bytesTransferred: Long,
        val durationSeconds: Int,
        val output: String,
        val errorMessage: String?,
        val snapshotName: String? = null
    )

    suspend fun execute(
        source: BackupSource,
        schedule: Schedule,
        onProgress: (RsyncOutput) -> Unit = {}
    ): BackupResult {
        val server = serverRepository.getServerSync(schedule.serverId)
            ?: run {
                return BackupResult(
                    success = false,
                    filesTransferred = 0,
                    bytesTransferred = 0L,
                    durationSeconds = 0,
                    output = "",
                    errorMessage = "Server not found for schedule: ${schedule.name}"
                )
            }

        val startTime = System.currentTimeMillis()

        // Create initial log entry
        val logId = backupLogRepository.createLog(
            BackupLog(
                sourceId = source.id,
                sourceName = source.name,
                serverName = server.name,
                scheduleId = schedule.id,
                scheduleName = schedule.name,
                startedAt = startTime,
                status = BackupStatus.RUNNING
            )
        )

        return try {
            val result = when (server.serverType) {
                ServerType.NEXTCLOUD -> executeWithNextcloud(source, schedule, server, onProgress)
                ServerType.SSH -> when (val rsyncResult = rsyncBinaryResolver.resolve()) {
                    is RsyncBinaryResolver.RsyncBinaryResult.Found -> {
                        when (val sshResult = sshBinaryResolver.resolve()) {
                            is SshBinaryResolver.SshBinaryResult.Found -> {
                                if (sshResult.isDropbear && server.authMethod == AuthMethod.PASSWORD) {
                                    executeWithSftp(source, schedule, server, onProgress)
                                } else {
                                    executeWithRsync(rsyncResult.path, sshResult.path, sshResult.isDropbear, source, schedule, server, onProgress)
                                }
                            }
                            is SshBinaryResolver.SshBinaryResult.NotFound -> {
                                executeWithSftp(source, schedule, server, onProgress)
                            }
                        }
                    }
                    is RsyncBinaryResolver.RsyncBinaryResult.NotFound -> {
                        executeWithSftp(source, schedule, server, onProgress)
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            val durationSeconds = ((endTime - startTime) / 1000).toInt()

            val backupResult = BackupResult(
                success = result.success,
                filesTransferred = result.filesTransferred,
                bytesTransferred = result.bytesTransferred,
                durationSeconds = durationSeconds,
                output = result.output,
                errorMessage = result.errorMessage,
                snapshotName = result.snapshotName
            )

            // Update log entry
            backupLogRepository.updateLog(
                BackupLog(
                    id = logId,
                    sourceId = source.id,
                    sourceName = source.name,
                    serverName = server.name,
                    scheduleId = schedule.id,
                    scheduleName = schedule.name,
                    startedAt = startTime,
                    completedAt = endTime,
                    status = if (result.success) BackupStatus.SUCCESS else BackupStatus.FAILED,
                    filesTransferred = result.filesTransferred,
                    bytesTransferred = result.bytesTransferred,
                    rsyncOutput = result.output,
                    errorMessage = result.errorMessage,
                    durationSeconds = durationSeconds,
                    snapshotName = result.snapshotName
                )
            )

            uploadRemoteLog(server, schedule, result, startTime, durationSeconds)

            backupResult
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val durationSeconds = ((endTime - startTime) / 1000).toInt()
            val errorMsg = e.message ?: "Unknown error"

            backupLogRepository.updateLog(
                BackupLog(
                    id = logId,
                    sourceId = source.id,
                    sourceName = source.name,
                    serverName = server.name,
                    scheduleId = schedule.id,
                    scheduleName = schedule.name,
                    startedAt = startTime,
                    completedAt = endTime,
                    status = BackupStatus.FAILED,
                    errorMessage = errorMsg,
                    durationSeconds = durationSeconds
                )
            )

            val failedResult = InternalResult(
                success = false,
                filesTransferred = 0,
                bytesTransferred = 0L,
                output = "",
                errorMessage = errorMsg
            )
            uploadRemoteLog(server, schedule, failedResult, startTime, durationSeconds)

            BackupResult(
                success = false,
                filesTransferred = 0,
                bytesTransferred = 0L,
                durationSeconds = durationSeconds,
                output = "",
                errorMessage = errorMsg
            )
        }
    }

    private suspend fun executeWithRsync(
        rsyncPath: String,
        sshPath: String,
        isDropbear: Boolean,
        source: BackupSource,
        schedule: Schedule,
        server: com.gniza.backup.domain.model.Server,
        onProgress: (RsyncOutput) -> Unit
    ): InternalResult {
        validateShellSafe(server.username, "username")
        validateShellSafe(server.host, "host")
        validatePath(schedule.destinationPath, "destinationPath")

        val sshCommand = buildSshCommand(server, sshPath, isDropbear)
        val useSnapshots = schedule.snapshotRetention > 0

        if (!useSnapshots) {
            // Legacy flat backup (no snapshots)
            return executeRsyncFlat(rsyncPath, sshCommand, source, schedule, server, onProgress)
        }

        // Snapshot-based backup
        return executeRsyncSnapshot(rsyncPath, sshCommand, source, schedule, server, onProgress)
    }

    private suspend fun executeRsyncFlat(
        rsyncPath: String,
        sshCommand: String,
        source: BackupSource,
        schedule: Schedule,
        server: com.gniza.backup.domain.model.Server,
        onProgress: (RsyncOutput) -> Unit
    ): InternalResult {
        val remoteDestination = "${server.username}@${server.host}:${schedule.destinationPath}"

        val command = RsyncCommand(
            rsyncPath = rsyncPath,
            sourcePaths = source.sourceFolders,
            destination = remoteDestination,
            sshCommand = sshCommand,
            includePatterns = source.includePatterns,
            excludePatterns = source.excludePatterns,
            extraFlags = Constants.RSYNC_DEFAULT_FLAGS
        )

        return collectRsyncOutput(command, onProgress)
    }

    private suspend fun executeRsyncSnapshot(
        rsyncPath: String,
        sshCommand: String,
        source: BackupSource,
        schedule: Schedule,
        server: com.gniza.backup.domain.model.Server,
        onProgress: (RsyncOutput) -> Unit
    ): InternalResult {
        val session = sshCommandExecutor.openSession(server)
        val basePath = schedule.destinationPath
        val snapshotName = snapshotManager.generateSnapshotName()

        try {
            // 1. Ensure snapshots directory exists
            snapshotManager.ensureSnapshotsDir(session, basePath)

            // 2. Clean up any stale partial transfers
            snapshotManager.cleanStalePartials(session, basePath)

            // 3. Get the latest completed snapshot for --link-dest
            val latestSnapshot = snapshotManager.getLatestSnapshot(session, basePath)
            Timber.d("Latest snapshot: $latestSnapshot")

            // 4. Create partial directory for this snapshot
            snapshotManager.createPartialDir(session, basePath, snapshotName)

            // 5. Run rsync for each source folder into the partial snapshot dir
            val partialDir = "${Constants.SNAPSHOT_DIR_NAME}/${snapshotName}${Constants.SNAPSHOT_PARTIAL_SUFFIX}"
            val outputLines = StringBuilder()
            var totalFilesTransferred = 0
            var totalBytesTransferred = 0L
            var errorMessage: String? = null
            var success = true

            for (sourceFolder in source.sourceFolders) {
                val folderBaseName = sourceFolder.trimEnd('/').substringAfterLast('/')
                val remoteDestination = "${server.username}@${server.host}:${basePath}/${partialDir}/${folderBaseName}/"

                // --link-dest is relative to the destination
                // Destination is: basePath/snapshots/<new>.partial/<folder>/
                // Previous is:    basePath/snapshots/<prev>/<folder>/
                // So relative:    ../../<prev>/<folder>/
                val linkDest = if (latestSnapshot != null) {
                    // latestSnapshot from readlink is like "snapshots/<prev>" — strip the prefix
                    val prevName = latestSnapshot.removePrefix("${Constants.SNAPSHOT_DIR_NAME}/")
                        .removePrefix("/")
                        .trimEnd('/')
                    "../../${prevName}/${folderBaseName}/"
                } else {
                    null
                }

                val command = RsyncCommand(
                    rsyncPath = rsyncPath,
                    sourcePaths = listOf(sourceFolder),
                    destination = remoteDestination,
                    sshCommand = sshCommand,
                    includePatterns = source.includePatterns,
                    excludePatterns = source.excludePatterns,
                    extraFlags = Constants.RSYNC_SNAPSHOT_FLAGS,
                    linkDest = linkDest
                )

                val result = collectRsyncOutput(command, onProgress)
                outputLines.appendLine(result.output)
                totalFilesTransferred += result.filesTransferred
                totalBytesTransferred += result.bytesTransferred

                if (!result.success) {
                    errorMessage = result.errorMessage
                    success = false
                    break
                }
            }

            if (success) {
                // 6. Finalize: rename .partial to final, update latest symlink
                snapshotManager.finalizeSnapshot(session, basePath, snapshotName)
            } else {
                // Clean up failed partial transfer
                try {
                    snapshotManager.cleanStalePartials(session, basePath)
                    outputLines.appendLine("Cleaned up partial snapshot after failure")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to clean up partial snapshot (non-fatal)")
                }
            }

            // 7. Enforce retention (on both success and failure)
            if (schedule.snapshotRetention > 0) {
                try {
                    snapshotManager.enforceRetention(session, basePath, schedule.snapshotRetention)
                } catch (e: Exception) {
                    Timber.w(e, "Snapshot retention enforcement failed (non-fatal)")
                    outputLines.appendLine("Warning: retention cleanup failed: ${e.message}")
                }
            }

            return InternalResult(
                success = success,
                filesTransferred = totalFilesTransferred,
                bytesTransferred = totalBytesTransferred,
                output = outputLines.toString(),
                errorMessage = errorMessage,
                snapshotName = if (success) snapshotName else null
            )
        } finally {
            session.disconnect()
        }
    }

    private suspend fun collectRsyncOutput(
        command: RsyncCommand,
        onProgress: (RsyncOutput) -> Unit
    ): InternalResult {
        val outputLines = StringBuilder()
        var filesTransferred = 0
        var bytesTransferred = 0L
        var errorMessage: String? = null
        var success = true

        rsyncEngine.execute(command).collect { output ->
            onProgress(output)
            when (output) {
                is RsyncOutput.Summary -> {
                    filesTransferred = output.filesTransferred
                    bytesTransferred = output.totalSize
                }
                is RsyncOutput.Error -> {
                    errorMessage = output.message
                    success = false
                }
                is RsyncOutput.Log -> {
                    outputLines.appendLine(output.line)
                }
                is RsyncOutput.Progress -> {}
                is RsyncOutput.FileComplete -> {
                    outputLines.appendLine("Completed: ${output.fileName} (${output.size} bytes)")
                }
            }
        }

        return InternalResult(
            success = success,
            filesTransferred = filesTransferred,
            bytesTransferred = bytesTransferred,
            output = outputLines.toString(),
            errorMessage = errorMessage
        )
    }

    private suspend fun executeWithSftp(
        source: BackupSource,
        schedule: Schedule,
        server: com.gniza.backup.domain.model.Server,
        onProgress: (RsyncOutput) -> Unit
    ): InternalResult {
        val progressLog = StringBuilder()
        val sftpResult = sftpSyncFallback.sync(
            server = server,
            sourceFolders = source.sourceFolders,
            destinationPath = schedule.destinationPath,
            onProgress = { message ->
                progressLog.appendLine(message)
                onProgress(RsyncOutput.Log(message))
            }
        )

        val outputMsg = buildString {
            appendLine("Rsync not available, using SFTP fallback")
            appendLine(progressLog.toString())
            if (sftpResult.success) {
                appendLine("Transferred ${sftpResult.filesTransferred} files")
            } else {
                appendLine("SFTP error: ${sftpResult.errorMessage}")
            }
        }

        return InternalResult(
            success = sftpResult.success,
            filesTransferred = sftpResult.filesTransferred,
            bytesTransferred = sftpResult.bytesTransferred,
            output = outputMsg,
            errorMessage = sftpResult.errorMessage
        )
    }

    private suspend fun executeWithNextcloud(
        source: BackupSource,
        schedule: Schedule,
        server: com.gniza.backup.domain.model.Server,
        onProgress: (RsyncOutput) -> Unit
    ): InternalResult {
        require(!schedule.destinationPath.contains("..")) {
            "destinationPath must not contain '..' segments"
        }

        val progressLog = StringBuilder()
        val nextcloudResult = nextcloudSync.sync(
            server = server,
            sourceFolders = source.sourceFolders,
            destinationPath = schedule.destinationPath,
            onProgress = { output ->
                if (output is RsyncOutput.Log) {
                    progressLog.appendLine(output.line)
                }
                onProgress(output)
            }
        )

        val outputMsg = buildString {
            appendLine("Using Nextcloud WebDAV sync")
            appendLine(progressLog.toString())
            if (nextcloudResult.success) {
                appendLine("Transferred ${nextcloudResult.filesTransferred} files")
            } else {
                appendLine("Nextcloud error: ${nextcloudResult.errorMessage}")
            }
        }

        return InternalResult(
            success = nextcloudResult.success,
            filesTransferred = nextcloudResult.filesTransferred,
            bytesTransferred = nextcloudResult.bytesTransferred,
            output = outputMsg,
            errorMessage = nextcloudResult.errorMessage
        )
    }

    private suspend fun uploadRemoteLog(
        server: com.gniza.backup.domain.model.Server,
        schedule: Schedule,
        result: InternalResult,
        startTime: Long,
        durationSeconds: Int
    ) {
        try {
            val logContent = buildRemoteLogContent(result, startTime, durationSeconds)
            val logPath = buildRemoteLogPath(schedule.destinationPath, result.snapshotName)

            when (server.serverType) {
                ServerType.SSH -> {
                    when (rsyncBinaryResolver.resolve()) {
                        is RsyncBinaryResolver.RsyncBinaryResult.Found -> {
                            val session = sshCommandExecutor.openSession(server)
                            try {
                                sshCommandExecutor.writeRemoteFile(session, logPath, logContent)
                            } finally {
                                session.disconnect()
                            }
                        }
                        is RsyncBinaryResolver.RsyncBinaryResult.NotFound -> {
                            sftpSyncFallback.uploadContent(server, logPath, logContent)
                        }
                    }
                }
                ServerType.NEXTCLOUD -> {
                    nextcloudSync.uploadContent(server, logPath, logContent)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to upload backup log to remote destination")
        }
    }

    private fun buildRemoteLogContent(result: InternalResult, startTime: Long, durationSeconds: Int): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val status = if (result.success) "SUCCESS" else "FAILED"
        return buildString {
            appendLine("=== Gniza Backup Log ===")
            appendLine("Date: ${dateFormat.format(java.util.Date(startTime))} UTC")
            appendLine("Duration: ${durationSeconds}s")
            appendLine("Status: $status")
            appendLine("Files: ${result.filesTransferred}")
            appendLine("Bytes: ${result.bytesTransferred}")
            if (!result.success && result.errorMessage != null) {
                appendLine("Error: ${result.errorMessage}")
            }
            appendLine("========================")
            appendLine()
            append(result.output)
        }
    }

    private fun buildRemoteLogPath(destinationPath: String, snapshotName: String?): String {
        return if (snapshotName != null) {
            "$destinationPath/${Constants.SNAPSHOT_DIR_NAME}/$snapshotName/${Constants.REMOTE_LOG_FILENAME}"
        } else {
            "$destinationPath/${Constants.REMOTE_LOG_FILENAME}"
        }
    }

    private companion object {
        val SHELL_METACHAR_REGEX = Regex("[;|&\$`\"'\\\\(){}\\[\\]!#~<>?\\s]")
        val SAFE_PATH_REGEX = Regex("^[a-zA-Z0-9/_.:@\\-]+$")
    }

    private fun validateShellSafe(value: String, fieldName: String) {
        require(value.isNotEmpty()) { "$fieldName must not be empty" }
        require(!SHELL_METACHAR_REGEX.containsMatchIn(value)) {
            "$fieldName contains unsafe shell metacharacters"
        }
    }

    private fun validatePath(path: String, fieldName: String) {
        require(path.isNotEmpty()) { "$fieldName must not be empty" }
        require(!path.contains("..")) { "$fieldName must not contain '..' segments" }
        require(SAFE_PATH_REGEX.matches(path)) {
            "$fieldName contains invalid characters"
        }
    }

    private fun buildSshCommand(
        server: com.gniza.backup.domain.model.Server,
        sshPath: String,
        isDropbear: Boolean
    ): String {
        val parts = mutableListOf(sshPath)

        if (isDropbear) {
            parts.add("-y")
            parts.add("-y")
            parts.add("-p")
            parts.add(server.port.toString())
        } else {
            parts.add("-p")
            parts.add(server.port.toString())
            parts.add("-o")
            parts.add("StrictHostKeyChecking=no")
        }

        if (server.authMethod == AuthMethod.SSH_KEY && server.privateKeyPath != null) {
            val keyPath = if (isDropbear) {
                dropbearKeyConverter.ensureDropbearFormat(server.privateKeyPath)
            } else {
                server.privateKeyPath
            }
            validatePath(keyPath, "privateKeyPath")
            parts.add("-i")
            parts.add(keyPath)
        }

        return parts.joinToString(" ")
    }

    private data class InternalResult(
        val success: Boolean,
        val filesTransferred: Int,
        val bytesTransferred: Long,
        val output: String,
        val errorMessage: String?,
        val snapshotName: String? = null
    )
}
