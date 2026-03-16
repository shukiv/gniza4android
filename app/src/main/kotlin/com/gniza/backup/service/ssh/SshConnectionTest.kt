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
            // Ensure EdDSA provider is available for ed25519 keys
            if (java.security.Security.getProvider("EdDSA") == null) {
                java.security.Security.insertProviderAt(net.i2p.crypto.eddsa.EdDSASecurityProvider(), 1)
            }
            android.util.Log.e("GNIZA_SSH", "EdDSA provider: ${java.security.Security.getProvider("EdDSA")}")
            android.util.Log.e("GNIZA_SSH", "All providers: ${java.security.Security.getProviders().map { it.name }}")

            val jsch = JSch()
            // Force JSch to recognize ed25519
            try {
                JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.jce.SignatureEd25519")
                JSch.setConfig("ssh-ed448", "com.jcraft.jsch.jce.SignatureEd448")
            } catch (e: Exception) {
                android.util.Log.e("GNIZA_SSH", "Failed to set ed25519 config: ${e.message}")
            }
            JSch.setLogger(object : com.jcraft.jsch.Logger {
                override fun isEnabled(level: Int) = true
                override fun log(level: Int, message: String) {
                    android.util.Log.d("GNIZA_JSCH", message)
                }
            })

            android.util.Log.e("GNIZA_SSH", "Testing: ${server.username}@${server.host}:${server.port} auth=${server.authMethod} keyPath=${server.privateKeyPath}")

            if (server.authMethod == AuthMethod.SSH_KEY && server.privateKeyPath != null) {
                val keyFile = java.io.File(server.privateKeyPath)
                android.util.Log.e("GNIZA_SSH", "Key file: exists=${keyFile.exists()} readable=${keyFile.canRead()} size=${keyFile.length()}")
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
            android.util.Log.e("GNIZA_SSH", "Connection failed", e)
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
