package com.gniza.backup.service.rsync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class RsyncEngine @Inject constructor(
    private val rsyncBinaryResolver: RsyncBinaryResolver,
    @ApplicationContext private val context: Context
) {

    private val processMutex = Mutex()

    @Volatile
    private var currentProcess: Process? = null

    // Matches rsync progress lines like: "  1,234,567  45%  123.45kB/s    0:01:23"
    private val progressRegex = Regex(
        """^\s*([\d,]+)\s+(\d+)%\s+(\S+/s)\s+"""
    )

    // Matches rsync summary line: "sent X bytes  received Y bytes  Z bytes/sec"
    private val summaryBytesRegex = Regex(
        """sent\s+([\d,]+)\s+bytes\s+received\s+([\d,]+)\s+bytes"""
    )

    // Matches "Number of files transferred: N"
    private val filesTransferredRegex = Regex(
        """Number of (?:regular )?files transferred:\s+(\d+)"""
    )

    // Matches "Total transferred file size: N bytes"
    private val totalSizeRegex = Regex(
        """Total transferred file size:\s+([\d,]+)"""
    )

    // Matches file list entries (non-progress, non-blank lines that look like file paths)
    private val fileEntryRegex = Regex(
        """^([<>ch.]\S+)\s+(\d+)\s+.+\s+(.+)$"""
    )

    suspend fun execute(command: RsyncCommand): Flow<RsyncOutput> = flow {
        processMutex.withLock {
            val commandList = command.toCommandList()

            // Acquire wakelocks to prevent the CPU and WiFi from sleeping during transfer
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "gniza:rsync_transfer"
            )
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "gniza:rsync_transfer"
            )

            wakeLock.acquire(60 * 60 * 1000L) // 60 min timeout safety
            wifiLock.acquire()
            Timber.d("Acquired wake lock and wifi lock for rsync transfer")

            val processBuilder = ProcessBuilder(commandList)
                .redirectErrorStream(true)

            val process = processBuilder.start()
            currentProcess = process

            var filesTransferred = 0
            var totalSize = 0L
            var lastFileName = ""

            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (coroutineContext.isActive) {
                    line = reader.readLine() ?: break

                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty()) continue

                    // Check for progress output
                    val progressMatch = progressRegex.find(trimmedLine)
                    if (progressMatch != null) {
                        val percentage = progressMatch.groupValues[2].toIntOrNull() ?: 0
                        val speed = progressMatch.groupValues[3]
                        emit(RsyncOutput.Progress(lastFileName, percentage, speed))

                        if (percentage == 100) {
                            val sizeStr = progressMatch.groupValues[1].replace(",", "")
                            val size = sizeStr.toLongOrNull() ?: 0L
                            emit(RsyncOutput.FileComplete(lastFileName, size))
                            filesTransferred++
                            totalSize += size
                        }
                        continue
                    }

                    // Check for files transferred summary
                    val filesMatch = filesTransferredRegex.find(trimmedLine)
                    if (filesMatch != null) {
                        val count = filesMatch.groupValues[1].toIntOrNull() ?: filesTransferred
                        filesTransferred = count
                        emit(RsyncOutput.Log(trimmedLine))
                        continue
                    }

                    // Check for total size summary
                    val totalSizeMatch = totalSizeRegex.find(trimmedLine)
                    if (totalSizeMatch != null) {
                        val size = totalSizeMatch.groupValues[1].replace(",", "").toLongOrNull() ?: totalSize
                        totalSize = size
                        emit(RsyncOutput.Log(trimmedLine))
                        continue
                    }

                    // Check for rsync error lines
                    if (trimmedLine.startsWith("rsync error:") || trimmedLine.startsWith("rsync:")) {
                        emit(RsyncOutput.Error(trimmedLine))
                        continue
                    }

                    // Track file names (lines that look like relative paths, not stats)
                    if (!trimmedLine.startsWith("sending") &&
                        !trimmedLine.startsWith("sent ") &&
                        !trimmedLine.startsWith("total size") &&
                        !trimmedLine.startsWith("Number of") &&
                        !trimmedLine.contains("bytes/sec")
                    ) {
                        lastFileName = trimmedLine
                    }

                    emit(RsyncOutput.Log(trimmedLine))
                }

                val exitCode = process.waitFor()

                emit(RsyncOutput.Summary(filesTransferred, totalSize))

                if (exitCode != 0) {
                    emit(RsyncOutput.Error("rsync exited with code $exitCode"))
                }
            } finally {
                currentProcess = null
                process.destroyForcibly()
                if (wakeLock.isHeld) wakeLock.release()
                if (wifiLock.isHeld) wifiLock.release()
                Timber.d("Released wake lock and wifi lock")
            }
        }
    }.flowOn(Dispatchers.IO)

    fun cancel() {
        currentProcess?.destroyForcibly()
        currentProcess = null
    }
}
