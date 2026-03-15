package com.gniza.backup.data.repository

import com.gniza.backup.data.local.dao.BackupSourceDao
import com.gniza.backup.data.local.entity.BackupSourceEntity
import com.gniza.backup.domain.model.BackupSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BackupSourceRepository @Inject constructor(
    private val backupSourceDao: BackupSourceDao
) {
    val allSources: Flow<List<BackupSource>> = backupSourceDao.getAll().map { entities ->
        entities.map { it.toBackupSource() }
    }

    fun getSource(id: Long): Flow<BackupSource?> =
        backupSourceDao.getById(id).map { it?.toBackupSource() }

    suspend fun getSourceSync(id: Long): BackupSource? =
        backupSourceDao.getByIdSync(id)?.toBackupSource()

    suspend fun saveSource(source: BackupSource): Long {
        val entity = BackupSourceEntity.fromBackupSource(source)
        return if (source.id == 0L) {
            backupSourceDao.insert(entity)
        } else {
            backupSourceDao.update(entity)
            source.id
        }
    }

    suspend fun deleteSource(source: BackupSource) {
        backupSourceDao.delete(BackupSourceEntity.fromBackupSource(source))
    }
}
