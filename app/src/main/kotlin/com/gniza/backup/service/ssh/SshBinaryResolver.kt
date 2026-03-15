package com.gniza.backup.service.ssh

import android.content.Context
import com.gniza.backup.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class SshBinaryResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed class SshBinaryResult {
        data class Found(val path: String, val source: String, val isDropbear: Boolean) : SshBinaryResult()
        data class NotFound(val searchedPaths: List<String>) : SshBinaryResult()
    }

    private val systemSshPaths = listOf(
        "/usr/bin/ssh",
        "/system/bin/ssh",
        "/system/xbin/ssh"
    )

    private val termuxSshPath = Constants.TERMUX_SSH_PATH

    private val systemDbclientPaths = listOf(
        "/usr/bin/dbclient",
        Constants.TERMUX_DBCLIENT_PATH
    )

    fun resolve(): SshBinaryResult {
        val searchedPaths = mutableListOf<String>()

        // 1. System OpenSSH paths
        for (path in systemSshPaths) {
            searchedPaths.add(path)
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return SshBinaryResult.Found(path, "system", isDropbear = false)
            }
        }

        // 2. Termux SSH
        searchedPaths.add(termuxSshPath)
        val termuxFile = File(termuxSshPath)
        if (termuxFile.exists() && termuxFile.canExecute()) {
            return SshBinaryResult.Found(termuxSshPath, "termux", isDropbear = false)
        }

        // 3. System/Termux dbclient
        for (path in systemDbclientPaths) {
            searchedPaths.add(path)
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return SshBinaryResult.Found(path, "system_dbclient", isDropbear = true)
            }
        }

        // 4. Bundled binary from native libs (run directly — nativeLibraryDir is executable)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val bundledLib = File(nativeLibDir, Constants.BUNDLED_SSH_LIB)
        searchedPaths.add(bundledLib.absolutePath)

        if (bundledLib.exists() && bundledLib.canExecute()) {
            return SshBinaryResult.Found(bundledLib.absolutePath, "bundled", isDropbear = true)
        }

        return SshBinaryResult.NotFound(searchedPaths)
    }
}
