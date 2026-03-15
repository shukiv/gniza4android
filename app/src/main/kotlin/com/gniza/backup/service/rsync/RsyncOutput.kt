package com.gniza.backup.service.rsync

sealed class RsyncOutput {
    data class Progress(
        val fileName: String,
        val percentage: Int,
        val speed: String
    ) : RsyncOutput()

    data class FileComplete(
        val fileName: String,
        val size: Long
    ) : RsyncOutput()

    data class Summary(
        val filesTransferred: Int,
        val totalSize: Long
    ) : RsyncOutput()

    data class Error(
        val message: String
    ) : RsyncOutput()

    data class Log(
        val line: String
    ) : RsyncOutput()
}
