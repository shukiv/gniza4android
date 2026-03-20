package com.gniza.backup.util

object Constants {
    const val SSH_DEFAULT_PORT = 22
    const val SSH_KEY_TYPE_RSA = "RSA"
    const val SSH_KEY_DEFAULT_BITS = 4096

    val RSYNC_DEFAULT_FLAGS = listOf("-avz", "--progress", "--partial")

    const val BACKUP_WORK_TAG = "backup_work"
    const val TERMUX_RSYNC_PATH = "/data/data/com.termux/files/usr/bin/rsync"
    const val BUNDLED_RSYNC_LIB = "librsync.so"
    const val BUNDLED_RSYNC_BIN = "bin/rsync"

    const val TERMUX_SSH_PATH = "/data/data/com.termux/files/usr/bin/ssh"
    const val TERMUX_DBCLIENT_PATH = "/data/data/com.termux/files/usr/bin/dbclient"
    const val BUNDLED_SSH_LIB = "libssh.so"
    const val BUNDLED_SSH_BIN = "bin/ssh"

    const val BUNDLED_WORMHOLE_LIB = "libwormhole.so"

    val RSYNC_SNAPSHOT_FLAGS = listOf("-aH", "--numeric-ids", "--delete", "--sparse", "-z", "--progress", "--partial")

    const val SNAPSHOT_DIR_NAME = "snapshots"
    const val SNAPSHOT_LATEST_LINK = "latest"
    const val SNAPSHOT_PARTIAL_SUFFIX = ".partial"
    const val SNAPSHOT_DATE_FORMAT = "yyyy-MM-dd'T'HHmmss"

    const val FLAT_BROWSE_SENTINEL = "__flat__"

    const val REMOTE_LOG_FILENAME = "backup.log"
}
