package com.gniza.backup.service.rsync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class RsyncBinaryResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed class RsyncBinaryResult {
        data class Found(val path: String, val source: String) : RsyncBinaryResult()
        data class NotFound(val searchedPaths: List<String>) : RsyncBinaryResult()
    }

    private val systemPaths = listOf(
        "/usr/bin/rsync",
        "/system/bin/rsync",
        "/system/xbin/rsync"
    )

    private val termuxPath = "/data/data/com.termux/files/usr/bin/rsync"

    fun resolve(userOverridePath: String? = null): RsyncBinaryResult {
        val searchedPaths = mutableListOf<String>()

        // 1. User override path
        if (!userOverridePath.isNullOrBlank()) {
            searchedPaths.add(userOverridePath)
            val file = File(userOverridePath)
            if (file.exists() && file.canExecute()) {
                return RsyncBinaryResult.Found(userOverridePath, "user_override")
            }
        }

        // 2. System paths
        for (path in systemPaths) {
            searchedPaths.add(path)
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return RsyncBinaryResult.Found(path, "system")
            }
        }

        // 3. Termux
        searchedPaths.add(termuxPath)
        val termuxFile = File(termuxPath)
        if (termuxFile.exists() && termuxFile.canExecute()) {
            return RsyncBinaryResult.Found(termuxPath, "termux")
        }

        // 4. Bundled binary from native libs (run directly — nativeLibraryDir is executable)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val bundledLib = File(nativeLibDir, "librsync.so")
        searchedPaths.add(bundledLib.absolutePath)

        if (bundledLib.exists() && bundledLib.canExecute()) {
            return RsyncBinaryResult.Found(bundledLib.absolutePath, "bundled")
        }

        return RsyncBinaryResult.NotFound(searchedPaths)
    }
}
