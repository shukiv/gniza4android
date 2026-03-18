package com.gniza.backup.service.backup

import com.gniza.backup.domain.model.Snapshot
import com.gniza.backup.service.ssh.SshCommandExecutor
import com.gniza.backup.util.Constants
import com.jcraft.jsch.Session
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class SnapshotManager @Inject constructor(
    private val sshCommandExecutor: SshCommandExecutor
) {
    fun generateSnapshotName(): String {
        val sdf = SimpleDateFormat(Constants.SNAPSHOT_DATE_FORMAT, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun ensureSnapshotsDir(session: Session, basePath: String) {
        sshCommandExecutor.exec(session, "mkdir -p '${escapeSingleQuotes(basePath)}/${Constants.SNAPSHOT_DIR_NAME}'")
    }

    suspend fun cleanStalePartials(session: Session, basePath: String) {
        val snapshotsDir = "${escapeSingleQuotes(basePath)}/${Constants.SNAPSHOT_DIR_NAME}"
        val result = sshCommandExecutor.exec(
            session,
            "find '$snapshotsDir' -maxdepth 1 -name '*${Constants.SNAPSHOT_PARTIAL_SUFFIX}' -type d"
        )
        if (result.output.isNotBlank()) {
            for (partial in result.output.lines().filter { it.isNotBlank() }) {
                Timber.d("Cleaning stale partial: $partial")
                sshCommandExecutor.exec(session, "rm -rf '${escapeSingleQuotes(partial)}'")
            }
        }
    }

    suspend fun getLatestSnapshot(session: Session, basePath: String): String? {
        val latestLink = "${escapeSingleQuotes(basePath)}/${Constants.SNAPSHOT_LATEST_LINK}"
        val result = sshCommandExecutor.exec(session, "readlink '$latestLink' 2>/dev/null")
        return if (result.exitCode == 0 && result.output.isNotBlank()) {
            result.output.trim()
        } else {
            null
        }
    }

    suspend fun createPartialDir(session: Session, basePath: String, snapshotName: String) {
        val partialPath = "${escapeSingleQuotes(basePath)}/${Constants.SNAPSHOT_DIR_NAME}/${snapshotName}${Constants.SNAPSHOT_PARTIAL_SUFFIX}"
        sshCommandExecutor.exec(session, "mkdir -p '$partialPath'")
    }

    suspend fun finalizeSnapshot(session: Session, basePath: String, snapshotName: String) {
        val escapedBase = escapeSingleQuotes(basePath)
        val snapshotsDir = "$escapedBase/${Constants.SNAPSHOT_DIR_NAME}"
        val partialPath = "$snapshotsDir/${snapshotName}${Constants.SNAPSHOT_PARTIAL_SUFFIX}"
        val finalPath = "$snapshotsDir/$snapshotName"
        val latestLink = "$escapedBase/${Constants.SNAPSHOT_LATEST_LINK}"

        // Atomic rename
        sshCommandExecutor.exec(session, "mv '$partialPath' '$finalPath'")
        // Update latest symlink (relative path)
        sshCommandExecutor.exec(session, "ln -sfn '${Constants.SNAPSHOT_DIR_NAME}/$snapshotName' '$latestLink'")
    }

    suspend fun enforceRetention(session: Session, basePath: String, retentionCount: Int) {
        if (retentionCount <= 0) return
        val snapshotsDir = "${escapeSingleQuotes(basePath)}/${Constants.SNAPSHOT_DIR_NAME}"
        // List non-partial snapshot dirs, sorted oldest first
        val result = sshCommandExecutor.exec(
            session,
            "ls -1d '$snapshotsDir'/[0-9]* 2>/dev/null | grep -v '${Constants.SNAPSHOT_PARTIAL_SUFFIX}' | sort"
        )
        if (result.output.isBlank()) return

        val snapshots = result.output.lines().filter { it.isNotBlank() }
        val toDelete = snapshots.size - retentionCount
        if (toDelete > 0) {
            for (path in snapshots.take(toDelete)) {
                Timber.d("Pruning old snapshot: $path")
                sshCommandExecutor.exec(session, "rm -rf '${escapeSingleQuotes(path)}'")
            }
        }
    }

    suspend fun listSnapshots(session: Session, basePath: String): List<Snapshot> {
        val snapshotsDir = "${escapeSingleQuotes(basePath)}/${Constants.SNAPSHOT_DIR_NAME}"
        val latestTarget = getLatestSnapshot(session, basePath)

        val result = sshCommandExecutor.exec(
            session,
            "ls -1d '$snapshotsDir'/[0-9]* 2>/dev/null | sort -r"
        )
        if (result.output.isBlank()) return emptyList()

        return result.output.lines()
            .filter { it.isNotBlank() }
            .map { fullPath ->
                val dirName = fullPath.substringAfterLast('/')
                val isPartial = dirName.endsWith(Constants.SNAPSHOT_PARTIAL_SUFFIX)
                val name = if (isPartial) dirName.removeSuffix(Constants.SNAPSHOT_PARTIAL_SUFFIX) else dirName
                val isLatest = latestTarget?.contains(name) == true
                Snapshot(name = name, isPartial = isPartial, isLatest = isLatest)
            }
            .filter { !it.isPartial }
    }

    suspend fun deleteSnapshot(session: Session, basePath: String, snapshotName: String) {
        val escapedBase = escapeSingleQuotes(basePath)
        val snapshotsDir = "$escapedBase/${Constants.SNAPSHOT_DIR_NAME}"
        val snapshotPath = "$snapshotsDir/${escapeSingleQuotes(snapshotName)}"

        Timber.d("Deleting snapshot: %s", snapshotPath)
        sshCommandExecutor.exec(session, "rm -rf '$snapshotPath'")

        // If we deleted the latest, update the symlink to the newest remaining
        val latestTarget = getLatestSnapshot(session, basePath)
        if (latestTarget != null && latestTarget.contains(snapshotName)) {
            val remaining = sshCommandExecutor.exec(
                session,
                "ls -1d '$snapshotsDir'/[0-9]* 2>/dev/null | grep -v '${Constants.SNAPSHOT_PARTIAL_SUFFIX}' | sort -r | head -1"
            )
            val latestLink = "$escapedBase/${Constants.SNAPSHOT_LATEST_LINK}"
            if (remaining.output.isNotBlank()) {
                val newestDir = remaining.output.trim().substringAfterLast('/')
                sshCommandExecutor.exec(
                    session,
                    "ln -sfn '${Constants.SNAPSHOT_DIR_NAME}/$newestDir' '$latestLink'"
                )
            } else {
                sshCommandExecutor.exec(session, "rm -f '$latestLink'")
            }
        }
    }

    private fun escapeSingleQuotes(value: String): String = value.replace("'", "'\\''")
}
