package com.gniza.backup.service.ssh

import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject

class SftpSyncFallback @Inject constructor() {

    data class SyncResult(
        val success: Boolean,
        val filesTransferred: Int,
        val bytesTransferred: Long,
        val errorMessage: String?
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
    }

    suspend fun sync(
        server: Server,
        sourceFolders: List<String>,
        destinationPath: String,
        onProgress: (String) -> Unit
    ): SyncResult = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        var filesTransferred = 0
        var bytesTransferred = 0L

        try {
            val jsch = JSch()

            if (server.authMethod == AuthMethod.SSH_KEY && server.privateKeyPath != null) {
                if (server.privateKeyPassphrase != null) {
                    jsch.addIdentity(server.privateKeyPath, server.privateKeyPassphrase)
                } else {
                    jsch.addIdentity(server.privateKeyPath)
                }
            }

            session = jsch.getSession(server.username, server.host, server.port)

            if (server.authMethod == AuthMethod.PASSWORD && server.password != null) {
                session.setPassword(server.password)
            }

            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            session.connect(CONNECT_TIMEOUT_MS)

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(CONNECT_TIMEOUT_MS)

            ensureRemoteDir(channel, destinationPath)

            for (sourceFolder in sourceFolders) {
                Timber.d("Checking source: $sourceFolder")
                onProgress("Checking source: $sourceFolder")
                val localDir = File(sourceFolder)
                Timber.d("exists=${localDir.exists()} isDir=${localDir.isDirectory} canRead=${localDir.canRead()} path=${localDir.absolutePath}")
                onProgress("exists=${localDir.exists()} isDir=${localDir.isDirectory} canRead=${localDir.canRead()}")

                if (!localDir.exists() || !localDir.isDirectory) {
                    onProgress("Skipping non-existent folder: $sourceFolder")
                    continue
                }

                val files = localDir.listFiles()
                Timber.d("Files found: ${files?.size ?: "null (no permission)"} in $sourceFolder")
                if (files != null && files.size <= 20) {
                    val fileList = files.map { "${it.name} (${if (it.isDirectory) "dir" else "${it.length()}b"})" }
                    Timber.d("File list: $fileList")
                }
                onProgress("Files found: ${files?.size ?: "null (no permission)"}")

                val remoteDirName = localDir.name
                val remoteBase = "$destinationPath/$remoteDirName"
                ensureRemoteDir(channel, remoteBase)

                val result = syncDirectory(channel, localDir, remoteBase, onProgress)
                filesTransferred += result.first
                bytesTransferred += result.second
                onProgress("Folder done: ${result.first} files, ${result.second} bytes")
            }

            SyncResult(
                success = true,
                filesTransferred = filesTransferred,
                bytesTransferred = bytesTransferred,
                errorMessage = null
            )
        } catch (e: Exception) {
            Timber.e("Exception: ${e.javaClass.simpleName}: ${e.message}", e)
            SyncResult(
                success = false,
                filesTransferred = filesTransferred,
                bytesTransferred = bytesTransferred,
                errorMessage = e.message ?: "Unknown SFTP error"
            )
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }

    private fun syncDirectory(
        channel: ChannelSftp,
        localDir: File,
        remotePath: String,
        onProgress: (String) -> Unit
    ): Pair<Int, Long> {
        var filesTransferred = 0
        var bytesTransferred = 0L

        val files = localDir.listFiles() ?: return Pair(0, 0L)

        for (file in files) {
            val remoteFilePath = "$remotePath/${file.name}"

            if (file.isDirectory) {
                ensureRemoteDir(channel, remoteFilePath)
                val result = syncDirectory(channel, file, remoteFilePath, onProgress)
                filesTransferred += result.first
                bytesTransferred += result.second
            } else {
                if (shouldUpload(channel, file, remoteFilePath)) {
                    onProgress("Uploading: ${file.name}")
                    FileInputStream(file).use { inputStream ->
                        channel.put(inputStream, remoteFilePath, ChannelSftp.OVERWRITE)
                    }
                    filesTransferred++
                    bytesTransferred += file.length()
                }
            }
        }

        return Pair(filesTransferred, bytesTransferred)
    }

    private fun shouldUpload(channel: ChannelSftp, localFile: File, remotePath: String): Boolean {
        return try {
            val attrs: SftpATTRS = channel.stat(remotePath)
            val sizesDiffer = attrs.size != localFile.length()
            val localMtimeSeconds = localFile.lastModified() / 1000
            val localIsNewer = localMtimeSeconds > attrs.mTime
            sizesDiffer || localIsNewer
        } catch (e: SftpException) {
            // File doesn't exist remotely
            true
        }
    }

    private fun ensureRemoteDir(channel: ChannelSftp, path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current = "$current/$part"
            try {
                channel.stat(current)
            } catch (e: SftpException) {
                channel.mkdir(current)
            }
        }
    }
}
