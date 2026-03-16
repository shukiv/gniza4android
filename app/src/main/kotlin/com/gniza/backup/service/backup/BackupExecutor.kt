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
import com.gniza.backup.util.Constants
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
    private val backupSourceRepository: BackupSourceRepository
) {

    data class BackupResult(
        val success: Boolean,
        val filesTransferred: Int,
        val bytesTransferred: Long,
        val durationSeconds: Int,
        val output: String,
        val errorMessage: String?
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
                errorMessage = result.errorMessage
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
                    durationSeconds = durationSeconds
                )
            )

            backupResult
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val durationSeconds = ((endTime - startTime) / 1000).toInt()

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
                    errorMessage = e.message ?: "Unknown error",
                    durationSeconds = durationSeconds
                )
            )

            BackupResult(
                success = false,
                filesTransferred = 0,
                bytesTransferred = 0L,
                durationSeconds = durationSeconds,
                output = "",
                errorMessage = e.message ?: "Unknown error"
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
                is RsyncOutput.Progress -> {
                    // Progress is reported via onProgress callback
                }
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
        val errorMessage: String?
    )
}
