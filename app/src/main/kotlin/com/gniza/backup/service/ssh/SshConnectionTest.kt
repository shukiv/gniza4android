package com.gniza.backup.service.ssh

import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties
import javax.inject.Inject

class SshConnectionTest @Inject constructor() {

    data class ConnectionTestResult(
        val success: Boolean,
        val message: String,
        val rsyncAvailable: Boolean = false
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val CHANNEL_TIMEOUT_MS = 5_000
    }

    suspend fun testConnection(server: Server): ConnectionTestResult = withContext(Dispatchers.IO) {
        var session: Session? = null
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

            // Test basic connectivity
            val echoResult = execCommand(session, "echo gniza_ok")
            if (!echoResult.trim().contains("gniza_ok")) {
                return@withContext ConnectionTestResult(
                    success = false,
                    message = "Connected but command execution failed"
                )
            }

            // Check rsync availability
            val rsyncResult = execCommand(session, "which rsync")
            val rsyncAvailable = rsyncResult.trim().isNotEmpty() && rsyncResult.contains("rsync")

            ConnectionTestResult(
                success = true,
                message = "Connection successful",
                rsyncAvailable = rsyncAvailable
            )
        } catch (e: Exception) {
            ConnectionTestResult(
                success = false,
                message = "Connection failed: ${e.message ?: "Unknown error"}"
            )
        } finally {
            session?.disconnect()
        }
    }

    private fun execCommand(session: Session, command: String): String {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.inputStream = null

        val reader = BufferedReader(InputStreamReader(channel.inputStream))
        channel.connect(CHANNEL_TIMEOUT_MS)

        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.appendLine(line)
        }

        channel.disconnect()
        return output.toString()
    }
}
