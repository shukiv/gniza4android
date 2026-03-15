package com.gniza.backup.domain.model

data class BackupSource(
    val id: Long = 0,
    val name: String = "",
    val sourceFolders: List<String> = emptyList(),
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
