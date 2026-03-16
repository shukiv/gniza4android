package com.gniza.backup.service.restore

import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.RemoteFileEntry
import com.gniza.backup.domain.model.Server
import com.gniza.backup.domain.model.Snapshot
import com.gniza.backup.service.backup.SnapshotManager
import com.gniza.backup.service.rsync.RsyncBinaryResolver
import com.gniza.backup.service.rsync.RsyncCommand
import com.gniza.backup.service.rsync.RsyncEngine
import com.gniza.backup.service.rsync.RsyncOutput
import com.gniza.backup.service.ssh.DropbearKeyConverter
import com.gniza.backup.service.ssh.SshBinaryResolver
import com.gniza.backup.service.ssh.SshCommandExecutor
import com.gniza.backup.util.Constants
import javax.inject.Inject

class RestoreService @Inject constructor(
    private val snapshotManager: SnapshotManager,
    private val sshCommandExecutor: SshCommandExecutor,
    private val rsyncBinaryResolver: RsyncBinaryResolver,
    private val sshBinaryResolver: SshBinaryResolver,
    private val rsyncEngine: RsyncEngine,
    private val dropbearKeyConverter: DropbearKeyConverter
) {
    suspend fun listSnapshots(server: Server, destPath: String): List<Snapshot> {
        val session = sshCommandExecutor.openSession(server)
        return try {
            snapshotManager.listSnapshots(session, destPath)
        } finally {
            session.disconnect()
        }
    }

    suspend fun browseSnapshot(
        server: Server,
        destPath: String,
        snapshotName: String,
        relativePath: String = ""
    ): List<RemoteFileEntry> {
        validatePathComponent(snapshotName, "snapshotName")
        if (relativePath.isNotBlank()) {
            validateRelativePath(relativePath)
        }

        val snapshotPath = "$destPath/${Constants.SNAPSHOT_DIR_NAME}/$snapshotName"
        val browsePath = if (relativePath.isNotBlank()) "$snapshotPath/$relativePath" else snapshotPath

        val session = sshCommandExecutor.openSession(server)
        return try {
            val result = sshCommandExecutor.exec(
                session,
                "ls -la --time-style=+%s '${escapeSingleQuotes(browsePath)}' 2>/dev/null"
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
        snapshotName: String,
        remotePath: String,
        localPath: String,
        onProgress: (RsyncOutput) -> Unit
    ): RestoreResult {
        validatePathComponent(snapshotName, "snapshotName")
        if (remotePath.isNotBlank()) {
            validateRelativePath(remotePath)
        }

        val rsyncBinary = rsyncBinaryResolver.resolve()
        val sshBinary = sshBinaryResolver.resolve()

        if (rsyncBinary !is RsyncBinaryResolver.RsyncBinaryResult.Found ||
            sshBinary !is SshBinaryResolver.SshBinaryResult.Found) {
            return RestoreResult(false, "Rsync or SSH binary not available for restore")
        }

        val sshCommand = buildSshCommand(server, sshBinary.path, sshBinary.isDropbear)
        val snapshotFullPath = "$destPath/${Constants.SNAPSHOT_DIR_NAME}/$snapshotName"
        val remoteSource = if (remotePath.isNotBlank()) {
            "${server.username}@${server.host}:${snapshotFullPath}/${remotePath}"
        } else {
            "${server.username}@${server.host}:${snapshotFullPath}/"
        }

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
