package com.gniza.backup.service.restore

import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.RemoteFileEntry
import com.gniza.backup.domain.model.Server
import com.gniza.backup.domain.model.ServerType
import com.gniza.backup.domain.model.Snapshot
import com.gniza.backup.service.backup.SnapshotManager
import com.gniza.backup.service.nextcloud.NextcloudSync
import com.gniza.backup.service.rsync.RsyncBinaryResolver
import com.gniza.backup.service.rsync.RsyncCommand
import com.gniza.backup.service.rsync.RsyncEngine
import com.gniza.backup.service.rsync.RsyncOutput
import com.gniza.backup.service.ssh.DropbearKeyConverter
import com.gniza.backup.service.ssh.SftpSyncFallback
import com.gniza.backup.service.ssh.SshBinaryResolver
import com.gniza.backup.service.ssh.SshCommandExecutor
import com.gniza.backup.util.Constants
import java.io.File
import javax.inject.Inject

class RestoreService @Inject constructor(
    private val snapshotManager: SnapshotManager,
    private val sshCommandExecutor: SshCommandExecutor,
    private val rsyncBinaryResolver: RsyncBinaryResolver,
    private val sshBinaryResolver: SshBinaryResolver,
    private val rsyncEngine: RsyncEngine,
    private val dropbearKeyConverter: DropbearKeyConverter,
    private val nextcloudSync: NextcloudSync,
    private val sftpSyncFallback: SftpSyncFallback
) {
    suspend fun listSnapshots(server: Server, destPath: String): List<Snapshot> {
        return when (server.serverType) {
            ServerType.SSH -> {
                val session = sshCommandExecutor.openSession(server)
                try {
                    snapshotManager.listSnapshots(session, destPath)
                } finally {
                    session.disconnect()
                }
            }
            ServerType.NEXTCLOUD -> {
                val snapshotDir = "$destPath/${Constants.SNAPSHOT_DIR_NAME}"
                val entries = nextcloudSync.listRemoteEntries(server, snapshotDir)
                val directories = entries
                    .filter { it.isDirectory }
                    .filter { !it.name.endsWith(Constants.SNAPSHOT_PARTIAL_SUFFIX) }
                val sortedNames = directories.map { it.name }.sortedDescending()
                sortedNames.mapIndexed { index, name ->
                    Snapshot(
                        name = name,
                        isPartial = false,
                        isLatest = index == 0
                    )
                }
            }
        }
    }

    suspend fun deleteSnapshot(server: Server, destPath: String, snapshotName: String): RestoreResult {
        if (server.serverType == ServerType.NEXTCLOUD) {
            return RestoreResult(
                success = false,
                errorMessage = "Snapshot deletion is not supported for Nextcloud servers",
                filesRestored = 0
            )
        }
        validatePathComponent(snapshotName, "snapshotName")
        val session = sshCommandExecutor.openSession(server)
        try {
            snapshotManager.deleteSnapshot(session, destPath, snapshotName)
            return RestoreResult(success = true, filesRestored = 0)
        } finally {
            session.disconnect()
        }
    }

    suspend fun browse(
        server: Server,
        destPath: String,
        snapshotName: String?,
        relativePath: String = ""
    ): List<RemoteFileEntry> {
        if (snapshotName != null) {
            validatePathComponent(snapshotName, "snapshotName")
        }
        if (relativePath.isNotBlank()) {
            validateRelativePath(relativePath)
        }

        val fullRemotePath = if (snapshotName != null) {
            val snapshotPath = "$destPath/${Constants.SNAPSHOT_DIR_NAME}/$snapshotName"
            if (relativePath.isNotBlank()) "$snapshotPath/$relativePath" else snapshotPath
        } else {
            if (relativePath.isNotBlank()) "$destPath/$relativePath" else destPath
        }

        return when (server.serverType) {
            ServerType.SSH -> {
                val session = sshCommandExecutor.openSession(server)
                try {
                    val result = sshCommandExecutor.exec(
                        session,
                        "ls -la --time-style=+%s '${escapeSingleQuotes(fullRemotePath)}' 2>/dev/null"
                    )
                    if (result.exitCode != 0 || result.output.isBlank()) return emptyList()

                    result.output.lines()
                        .drop(1) // Skip "total N" line
                        .filter { it.isNotBlank() }
                        .mapNotNull { line -> parseLsLine(line, relativePath) }
                } finally {
                    session.disconnect()
                }
            }
            ServerType.NEXTCLOUD -> {
                nextcloudSync.listRemoteEntries(server, fullRemotePath)
            }
        }
    }

    private fun parseLsLine(line: String, parentPath: String): RemoteFileEntry? {
        // Format: drwxr-xr-x 2 user group 4096 1710600000 dirname
        val parts = line.split(Regex("\\s+"), limit = 7)
        if (parts.size < 7) return null
        val name = parts[6]
        if (name == "." || name == "..") return null

        val isDirectory = parts[0].startsWith('d')
        val size = parts[4].toLongOrNull() ?: 0L
        val modifiedAt = (parts[5].toLongOrNull() ?: 0L) * 1000
        val path = if (parentPath.isNotBlank()) "$parentPath/$name" else name

        return RemoteFileEntry(
            name = name,
            path = path,
            isDirectory = isDirectory,
            size = size,
            modifiedAt = modifiedAt
        )
    }

    suspend fun restore(
        server: Server,
        destPath: String,
        snapshotName: String?,
        remotePath: String,
        localPath: String,
        onProgress: (RsyncOutput) -> Unit
    ): RestoreResult {
        if (snapshotName != null) {
            validatePathComponent(snapshotName, "snapshotName")
        }
        if (remotePath.isNotBlank()) {
            validateRelativePath(remotePath)
        }

        val fullRemotePath = if (snapshotName != null) {
            val snapshotFullPath = "$destPath/${Constants.SNAPSHOT_DIR_NAME}/$snapshotName"
            if (remotePath.isNotBlank()) "$snapshotFullPath/$remotePath" else snapshotFullPath
        } else {
            if (remotePath.isNotBlank()) "$destPath/$remotePath" else destPath
        }

        return when (server.serverType) {
            ServerType.SSH -> restoreViaSsh(server, fullRemotePath, localPath, onProgress)
            ServerType.NEXTCLOUD -> restoreViaNextcloud(server, fullRemotePath, localPath, onProgress)
        }
    }

    private suspend fun restoreViaSsh(
        server: Server,
        fullRemotePath: String,
        localPath: String,
        onProgress: (RsyncOutput) -> Unit
    ): RestoreResult {
        val rsyncBinary = rsyncBinaryResolver.resolve()
        val sshBinary = sshBinaryResolver.resolve()

        if (rsyncBinary !is RsyncBinaryResolver.RsyncBinaryResult.Found ||
            sshBinary !is SshBinaryResolver.SshBinaryResult.Found) {
            // Fallback to SFTP
            onProgress(RsyncOutput.Log("Rsync/SSH not available, using SFTP fallback"))
            val localFile = File(localPath)
            val sftpResult = sftpSyncFallback.downloadFile(server, fullRemotePath, localFile, onProgress)
            return RestoreResult(
                success = sftpResult.success,
                errorMessage = sftpResult.errorMessage,
                filesRestored = sftpResult.filesTransferred
            )
        }

        val sshCommand = buildSshCommand(server, sshBinary.path, sshBinary.isDropbear)
        val remoteSource = "${server.username}@${server.host}:${fullRemotePath}"

        val command = RsyncCommand(
            rsyncPath = rsyncBinary.path,
            sourcePaths = listOf(remoteSource),
            destination = localPath,
            sshCommand = sshCommand,
            extraFlags = listOf("-avz", "--progress")
        )

        var filesTransferred = 0
        var errorMessage: String? = null
        var success = true

        rsyncEngine.execute(command).collect { output ->
            onProgress(output)
            when (output) {
                is RsyncOutput.Summary -> {
                    filesTransferred = output.filesTransferred
                }
                is RsyncOutput.Error -> {
                    errorMessage = output.message
                    success = false
                }
                else -> {}
            }
        }

        return RestoreResult(success, errorMessage, filesTransferred)
    }

    private suspend fun restoreViaNextcloud(
        server: Server,
        fullRemotePath: String,
        localPath: String,
        onProgress: (RsyncOutput) -> Unit
    ): RestoreResult {
        val localFile = File(localPath)

        // Try to determine if the remote path is a directory by listing it
        return try {
            val entries = nextcloudSync.listRemoteEntries(server, fullRemotePath)
            if (entries.isNotEmpty()) {
                // It's a directory
                val result = nextcloudSync.downloadDirectory(server, fullRemotePath, localFile, onProgress)
                RestoreResult(
                    success = result.success,
                    errorMessage = result.errorMessage,
                    filesRestored = result.filesTransferred
                )
            } else {
                // Try as a single file
                val result = nextcloudSync.download(server, fullRemotePath, localFile, onProgress)
                RestoreResult(
                    success = result.success,
                    errorMessage = result.errorMessage,
                    filesRestored = result.filesTransferred
                )
            }
        } catch (e: Exception) {
            // If listing fails, try as a single file
            val result = nextcloudSync.download(server, fullRemotePath, localFile, onProgress)
            RestoreResult(
                success = result.success,
                errorMessage = result.errorMessage,
                filesRestored = result.filesTransferred
            )
        }
    }

    private fun buildSshCommand(server: Server, sshPath: String, isDropbear: Boolean): String {
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
            parts.add("-i")
            parts.add(keyPath)
        }

        return parts.joinToString(" ")
    }

    private fun validatePathComponent(value: String, fieldName: String) {
        require(value.isNotEmpty()) { "$fieldName must not be empty" }
        require(!value.contains("..")) { "$fieldName must not contain '..' segments" }
        require(!value.contains('/')) { "$fieldName must not contain path separators" }
        require(SAFE_NAME_REGEX.matches(value)) { "$fieldName contains invalid characters" }
    }

    private fun validateRelativePath(path: String) {
        require(!path.contains("..")) { "Path must not contain '..' segments" }
        require(SAFE_PATH_REGEX.matches(path)) { "Path contains invalid characters" }
    }

    private fun escapeSingleQuotes(value: String): String = value.replace("'", "'\\''")

    private companion object {
        val SAFE_NAME_REGEX = Regex("^[a-zA-Z0-9_.:@\\-]+$")
        val SAFE_PATH_REGEX = Regex("^[a-zA-Z0-9/_.:@\\-]+$")
    }

    data class RestoreResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val filesRestored: Int = 0
    )
}
