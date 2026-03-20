package com.gniza.backup.service.nextcloud

import com.gniza.backup.domain.model.RemoteFileEntry
import com.gniza.backup.domain.model.Server
import com.gniza.backup.service.rsync.RsyncOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.ConnectionPool

class NextcloudSync @Inject constructor() {

    data class SyncResult(
        val success: Boolean,
        val filesTransferred: Int,
        val bytesTransferred: Long,
        val errorMessage: String?
    )

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 300L
        const val WRITE_TIMEOUT_SECONDS = 300L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .build()

    suspend fun sync(
        server: Server,
        sourceFolders: List<String>,
        destinationPath: String,
        onProgress: (RsyncOutput) -> Unit
    ): SyncResult = withContext(Dispatchers.IO) {
        var filesTransferred = 0
        var bytesTransferred = 0L

        try {
            val baseUrl = buildWebDavUrl(server, destinationPath)
            val credential = Credentials.basic(server.username, server.password ?: "")

            onProgress(RsyncOutput.Log("Connecting to Nextcloud: ${server.host}"))

            ensureRemoteDirRecursive(client, baseUrl, credential)

            for (sourceFolder in sourceFolders) {
                onProgress(RsyncOutput.Log("Checking source: $sourceFolder"))
                val localDir = File(sourceFolder)

                if (!localDir.exists() || !localDir.isDirectory) {
                    onProgress(RsyncOutput.Log("Skipping non-existent folder: $sourceFolder"))
                    continue
                }

                val files = localDir.listFiles()
                onProgress(RsyncOutput.Log("Files found: ${files?.size ?: "null (no permission)"}"))

                val remoteDirName = localDir.name
                val remoteBase = "$baseUrl$remoteDirName/"
                ensureRemoteDir(client, remoteBase, credential)

                val remoteSizes = listRemoteFiles(client, remoteBase, credential)

                val result = syncDirectory(client, localDir, remoteBase, credential, remoteSizes, onProgress)
                filesTransferred += result.first
                bytesTransferred += result.second
                onProgress(RsyncOutput.Log("Folder done: ${result.first} files, ${result.second} bytes"))
            }

            SyncResult(
                success = true,
                filesTransferred = filesTransferred,
                bytesTransferred = bytesTransferred,
                errorMessage = null
            )
        } catch (e: Exception) {
            Timber.e(e, "Nextcloud sync failed")
            SyncResult(
                success = false,
                filesTransferred = filesTransferred,
                bytesTransferred = bytesTransferred,
                errorMessage = e.message ?: "Unknown Nextcloud error"
            )
        }
    }

    internal fun buildWebDavUrl(server: Server, destinationPath: String): String {
        val host = server.host.trimEnd('/')
        val base = if (host.startsWith("https://")) {
            host
        } else if (host.startsWith("http://")) {
            throw IllegalArgumentException("Nextcloud requires HTTPS. Please use https:// in your URL.")
        } else {
            "https://$host"
        }
        val encodedUser = URLEncoder.encode(server.username, "UTF-8").replace("+", "%20")
        val pathSegments = destinationPath.trim('/').split("/").filter { it.isNotEmpty() }
        val encodedPath = pathSegments.joinToString("/") { segment ->
            require(!segment.contains("..")) { "Path must not contain '..' segments" }
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return if (encodedPath.isEmpty()) {
            "$base/remote.php/dav/files/$encodedUser/"
        } else {
            "$base/remote.php/dav/files/$encodedUser/$encodedPath/"
        }
    }

    private fun ensureRemoteDirRecursive(client: OkHttpClient, url: String, credential: String) {
        // Parse the URL to find the path after /remote.php/dav/files/user/
        // and create each directory level
        val davIndex = url.indexOf("/remote.php/dav/files/")
        if (davIndex == -1) return

        val baseUrl = url.substring(0, davIndex)
        val davPath = url.substring(davIndex)
        // Split the path after /remote.php/dav/files/user/ into segments
        val parts = davPath.trimEnd('/').split("/").filter { it.isNotEmpty() }
        // /remote.php/dav/files/user = 5 parts, everything after is directories to create
        if (parts.size <= 5) return

        var currentUrl = "$baseUrl/${parts.take(5).joinToString("/")}/"
        for (i in 5 until parts.size) {
            currentUrl += "${parts[i]}/"
            ensureRemoteDir(client, currentUrl, credential)
        }
    }

    private fun ensureRemoteDir(client: OkHttpClient, url: String, credential: String) {
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .header("Authorization", credential)
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                201, 405 -> { /* Created or already exists - both fine */ }
                409 -> throw IllegalStateException("Cannot create directory (parent missing): $url")
                403 -> throw SecurityException("Permission denied creating directory: $url")
                else -> Timber.w("MKCOL $url -> ${response.code}")
            }
        }
    }

    private fun listRemoteFiles(
        client: OkHttpClient,
        url: String,
        credential: String
    ): Map<String, Long> {
        val propfindBody = """<?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:getcontentlength/>
                    <d:resourcetype/>
                </d:prop>
            </d:propfind>""".trimIndent()

        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
            .header("Authorization", credential)
            .header("Depth", "1")
            .build()

        val remoteSizes = mutableMapOf<String, Long>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) return remoteSizes

            val body = response.body?.string() ?: return remoteSizes
            // Simple XML parsing - extract href and content-length pairs
            val hrefPattern = Regex("<d:href>([^<]+)</d:href>")
            val sizePattern = Regex("<d:getcontentlength>([^<]+)</d:getcontentlength>")

            val responses = body.split("<d:response>")
            for (responseBlock in responses.drop(1)) {
                val href = hrefPattern.find(responseBlock)?.groupValues?.get(1) ?: continue
                val size = sizePattern.find(responseBlock)?.groupValues?.get(1)?.toLongOrNull() ?: continue
                val fileName = href.trimEnd('/').substringAfterLast('/')
                if (fileName.isNotEmpty()) {
                    remoteSizes[java.net.URLDecoder.decode(fileName, "UTF-8")] = size
                }
            }
        }

        return remoteSizes
    }

    private fun syncDirectory(
        client: OkHttpClient,
        localDir: File,
        remoteUrl: String,
        credential: String,
        parentRemoteSizes: Map<String, Long>,
        onProgress: (RsyncOutput) -> Unit
    ): Pair<Int, Long> {
        var filesTransferred = 0
        var bytesTransferred = 0L

        val files = localDir.listFiles() ?: return Pair(0, 0L)

        for (file in files) {
            val remoteFileUrl = "$remoteUrl${java.net.URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")}"

            if (file.isDirectory) {
                val dirUrl = "$remoteFileUrl/"
                ensureRemoteDir(client, dirUrl, credential)
                val childRemoteSizes = listRemoteFiles(client, dirUrl, credential)
                val result = syncDirectory(client, file, dirUrl, credential, childRemoteSizes, onProgress)
                filesTransferred += result.first
                bytesTransferred += result.second
            } else {
                val remoteSize = parentRemoteSizes[file.name]
                if (remoteSize != null && remoteSize == file.length()) {
                    // Size matches, skip upload
                    continue
                }

                onProgress(RsyncOutput.Log("Uploading: ${file.name}"))

                val request = Request.Builder()
                    .url(remoteFileUrl)
                    .put(file.asRequestBody("application/octet-stream".toMediaType()))
                    .header("Authorization", credential)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 201 || response.code == 204) {
                        filesTransferred++
                        bytesTransferred += file.length()
                    } else {
                        onProgress(RsyncOutput.Log("Failed to upload ${file.name}: ${response.code}"))
                        Timber.w("PUT ${file.name} -> ${response.code}")
                    }
                }
            }
        }

        return Pair(filesTransferred, bytesTransferred)
    }

    suspend fun uploadContent(server: Server, remotePath: String, content: String) = withContext(Dispatchers.IO) {
        val url = buildWebDavUrl(server, remotePath)
        val credential = Credentials.basic(server.username, server.password ?: "")
        val requestBody = content.toRequestBody("text/plain".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Authorization", credential)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("Failed to upload log to Nextcloud: ${response.code}")
            }
        }
    }

    suspend fun listRemoteEntries(
        server: Server,
        remotePath: String
    ): List<RemoteFileEntry> = withContext(Dispatchers.IO) {
        val url = buildWebDavUrl(server, remotePath)
        val credential = Credentials.basic(server.username, server.password ?: "")

        val propfindBody = """<?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:displayname/>
                    <d:getcontentlength/>
                    <d:resourcetype/>
                    <d:getlastmodified/>
                </d:prop>
            </d:propfind>""".trimIndent()

        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
            .header("Authorization", credential)
            .header("Depth", "1")
            .build()

        val entries = mutableListOf<RemoteFileEntry>()
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()

            val hrefPattern = Regex("<d:href>([^<]+)</d:href>")
            val sizePattern = Regex("<d:getcontentlength>([^<]+)</d:getcontentlength>")
            val collectionPattern = Regex("<d:collection\\s*/?>")
            val lastModifiedPattern = Regex("<d:getlastmodified>([^<]+)</d:getlastmodified>")

            val responses = body.split("<d:response>")
            // Parse the request URL path to identify and skip the directory-itself entry
            val requestUrlPath = java.net.URL(url).path.trimEnd('/')
            for (responseBlock in responses.drop(1)) { // drop(1): first is XML preamble
                val href = hrefPattern.find(responseBlock)?.groupValues?.get(1) ?: continue
                val isDirectory = collectionPattern.containsMatchIn(responseBlock)
                val size = sizePattern.find(responseBlock)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val lastModifiedStr = lastModifiedPattern.find(responseBlock)?.groupValues?.get(1)
                val modifiedAt = try {
                    lastModifiedStr?.let { dateFormat.parse(it)?.time } ?: 0L
                } catch (e: Exception) {
                    0L
                }

                val decodedPath = java.net.URLDecoder.decode(href.trimEnd('/'), "UTF-8")
                val rawName = decodedPath.substringAfterLast('/')
                if (rawName.isEmpty()) continue
                // Skip the directory-itself entry (its path matches the request URL path)
                if (decodedPath.trimEnd('/') == requestUrlPath) continue
                // Sanitize: reject entries with '..' or path separators in the name
                if (rawName.contains("..") || rawName.contains('/')) continue

                val entryPath = rawName

                entries.add(
                    RemoteFileEntry(
                        name = rawName,
                        path = entryPath,
                        isDirectory = isDirectory,
                        size = size,
                        modifiedAt = modifiedAt
                    )
                )
            }
        }

        entries
    }

    suspend fun download(
        server: Server,
        remotePath: String,
        localFile: File,
        onProgress: (RsyncOutput) -> Unit
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = buildWebDavUrl(server, remotePath)
            val credential = Credentials.basic(server.username, server.password ?: "")

            onProgress(RsyncOutput.Log("Downloading: ${localFile.name}"))

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", credential)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SyncResult(
                        success = false,
                        filesTransferred = 0,
                        bytesTransferred = 0L,
                        errorMessage = "Download failed: HTTP ${response.code}"
                    )
                }

                val inputStream = response.body?.byteStream()
                    ?: return@withContext SyncResult(
                        success = false,
                        filesTransferred = 0,
                        bytesTransferred = 0L,
                        errorMessage = "Empty response body"
                    )

                localFile.parentFile?.mkdirs()
                var bytesTransferred = 0L
                FileOutputStream(localFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesTransferred += bytesRead
                    }
                }

                onProgress(RsyncOutput.Log("Downloaded: ${localFile.name} ($bytesTransferred bytes)"))

                SyncResult(
                    success = true,
                    filesTransferred = 1,
                    bytesTransferred = bytesTransferred,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Nextcloud download failed: ${localFile.name}")
            SyncResult(
                success = false,
                filesTransferred = 0,
                bytesTransferred = 0L,
                errorMessage = e.message ?: "Unknown download error"
            )
        }
    }

    suspend fun downloadDirectory(
        server: Server,
        remotePath: String,
        localDir: File,
        onProgress: (RsyncOutput) -> Unit
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val entries = listRemoteEntries(server, remotePath)
            localDir.mkdirs()

            var totalFiles = 0
            var totalBytes = 0L

            for (entry in entries) {
                if (entry.name.contains("..") || entry.name.contains('/')) continue

                if (entry.isDirectory) {
                    val childDir = File(localDir, entry.name)
                    val childPath = if (remotePath.endsWith("/")) {
                        "$remotePath${entry.name}"
                    } else {
                        "$remotePath/${entry.name}"
                    }
                    val result = downloadDirectory(server, childPath, childDir, onProgress)
                    totalFiles += result.filesTransferred
                    totalBytes += result.bytesTransferred
                    if (!result.success) {
                        return@withContext SyncResult(
                            success = false,
                            filesTransferred = totalFiles,
                            bytesTransferred = totalBytes,
                            errorMessage = result.errorMessage
                        )
                    }
                } else {
                    val localFile = File(localDir, entry.name)
                    val filePath = if (remotePath.endsWith("/")) {
                        "$remotePath${entry.name}"
                    } else {
                        "$remotePath/${entry.name}"
                    }
                    val result = download(server, filePath, localFile, onProgress)
                    totalFiles += result.filesTransferred
                    totalBytes += result.bytesTransferred
                    if (!result.success) {
                        return@withContext SyncResult(
                            success = false,
                            filesTransferred = totalFiles,
                            bytesTransferred = totalBytes,
                            errorMessage = result.errorMessage
                        )
                    }
                }
            }

            SyncResult(
                success = true,
                filesTransferred = totalFiles,
                bytesTransferred = totalBytes,
                errorMessage = null
            )
        } catch (e: Exception) {
            Timber.e(e, "Nextcloud directory download failed")
            SyncResult(
                success = false,
                filesTransferred = 0,
                bytesTransferred = 0L,
                errorMessage = e.message ?: "Unknown directory download error"
            )
        }
    }
}
