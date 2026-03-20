package com.gniza.backup.service.nextcloud

import com.gniza.backup.domain.model.Server
import com.gniza.backup.service.ssh.SshConnectionTest.ConnectionTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.ConnectionPool

class NextcloudConnectionTest @Inject constructor() {

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 10L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .build()

    suspend fun testConnection(server: Server): ConnectionTestResult = withContext(Dispatchers.IO) {
        try {
            val host = server.host.trimEnd('/')
            val base = if (host.startsWith("https://")) {
                host
            } else if (host.startsWith("http://")) {
                return@withContext ConnectionTestResult(
                    success = false,
                    message = "Nextcloud requires HTTPS. Please use https:// in your URL."
                )
            } else {
                "https://$host"
            }
            val encodedUser = URLEncoder.encode(server.username, "UTF-8").replace("+", "%20")
            val url = "$base/remote.php/dav/files/$encodedUser/"
            val credential = Credentials.basic(server.username, server.password ?: "")

            val propfindBody = """<?xml version="1.0" encoding="UTF-8"?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop>
                        <d:resourcetype/>
                    </d:prop>
                </d:propfind>""".trimIndent()

            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                .header("Authorization", credential)
                .header("Depth", "0")
                .build()

            Timber.d("Testing Nextcloud connection: $url")

            client.newCall(request).execute().use { response ->
                when {
                    response.code == 207 -> {
                        Timber.d("Nextcloud connection successful")
                        ConnectionTestResult(
                            success = true,
                            message = "Connection successful",
                            rsyncAvailable = false
                        )
                    }
                    response.code == 401 -> {
                        ConnectionTestResult(
                            success = false,
                            message = "Authentication failed: check username and app token"
                        )
                    }
                    response.code == 404 -> {
                        ConnectionTestResult(
                            success = false,
                            message = "WebDAV endpoint not found: check Nextcloud URL"
                        )
                    }
                    else -> {
                        ConnectionTestResult(
                            success = false,
                            message = "Connection failed: HTTP ${response.code}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Nextcloud connection test failed")
            ConnectionTestResult(
                success = false,
                message = "Connection failed: ${e.message ?: "Unknown error"}"
            )
        }
    }
}
