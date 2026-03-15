package com.gniza.backup.service.rsync

data class RsyncCommand(
    val rsyncPath: String,
    val sourcePaths: List<String>,
    val destination: String,
    val sshCommand: String,
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val extraFlags: List<String> = listOf("-avz", "--progress", "--partial")
) {

    fun toCommandList(): List<String> = buildList {
        add(rsyncPath)
        addAll(extraFlags)
        add("-e")
        add(sshCommand)

        for (pattern in includePatterns) {
            add("--include=$pattern")
        }
        for (pattern in excludePatterns) {
            add("--exclude=$pattern")
        }

        addAll(sourcePaths)
        add(destination)
    }
}
