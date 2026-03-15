package com.gniza.backup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gniza.backup.domain.model.BackupSource

@Entity(tableName = "backup_sources")
data class BackupSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sourceFolders: String,
    val includePatterns: String,
    val excludePatterns: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toBackupSource(): BackupSource = BackupSource(
        id = id,
        name = name,
        sourceFolders = jsonToList(sourceFolders),
        includePatterns = jsonToList(includePatterns),
        excludePatterns = jsonToList(excludePatterns),
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromBackupSource(source: BackupSource): BackupSourceEntity = BackupSourceEntity(
            id = source.id,
            name = source.name,
            sourceFolders = listToJson(source.sourceFolders),
            includePatterns = listToJson(source.includePatterns),
            excludePatterns = listToJson(source.excludePatterns),
            enabled = source.enabled,
            createdAt = source.createdAt,
            updatedAt = source.updatedAt
        )

        private fun listToJson(list: List<String>): String {
            val jsonArray = org.json.JSONArray()
            list.forEach { jsonArray.put(it) }
            return jsonArray.toString()
        }

        private fun jsonToList(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            val jsonArray = org.json.JSONArray(json)
            return (0 until jsonArray.length()).map { jsonArray.getString(it) }
        }
    }
}
