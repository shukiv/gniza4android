package com.gniza.backup.service.ssh

import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties
import javax.inject.Inject

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

class SshCommandExecutor @Inject constructor() {

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val CHANNEL_TIMEOUT_MS = 30_000
    }

    suspend fun openSession(server: Server): Session = withContext(Dispatchers.IO) {
        val jsch = JSch()

        if (server.authMethod == AuthMethod.SSH_KEY && server.privateKeyPath != null) {
            if (server.privateKeyPassphrase != null) {
                jsch.addIdentity(server.privateKeyPath, server.privateKeyPassphrase)
            } else {
                jsch.addIdentity(server.privateKeyPath)
            }
        }

        val session = jsch.getSession(server.username, server.host, server.port)

        if (server.authMethod == AuthMethod.PASSWORD && server.password != null) {
            session.setPassword(server.password)
        }

        val config = Properties()
        config["StrictHostKeyChecking"] = "no"
        session.setConfig(config)
        session.connect(CONNECT_TIMEOUT_MS)
        session
    }

    suspend fun exec(session: Session, command: String): CommandResult = withContext(Dispatchers.IO) {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.inputStream = null

        val stdout = BufferedReader(InputStreamReader(channel.inputStream))
        val stderr = BufferedReader(InputStreamReader(channel.errStream))
        channel.connect(CHANNEL_TIMEOUT_MS)

        val output = StringBuilder()
        var line: String?
        while (stdout.readLine().also { line = it } != null) {
            output.appendLine(line)
        }

        val errorOutput = StringBuilder()
        while (stderr.readLine().also { line = it } != null) {
            errorOutput.appendLine(line)
        }

        val exitCode = channel.exitStatus
        channel.disconnect()

        Timber.d("SSH exec: '$command' -> exit=$exitCode")
        CommandResult(exitCode, output.toString().trim(), errorOutput.toString().trim())
    }

    suspend fun execOnServer(server: Server, command: String): CommandResult {
        val session = openSession(server)
        return try {
            exec(session, command)
        } finally {
            session.disconnect()
        }
    }
}
