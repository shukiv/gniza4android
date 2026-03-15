package com.gniza.backup.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gniza.backup.data.local.entity.BackupSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSourceDao {

    @Query("SELECT * FROM backup_sources")
    fun getAll(): Flow<List<BackupSourceEntity>>

    @Query("SELECT * FROM backup_sources WHERE id = :id")
    fun getById(id: Long): Flow<BackupSourceEntity?>

    @Query("SELECT * FROM backup_sources WHERE id = :id")
    fun getByIdSync(id: Long): BackupSourceEntity?

    @Insert
    suspend fun insert(source: BackupSourceEntity): Long

    @Update
    suspend fun update(source: BackupSourceEntity)

    @Delete
    suspend fun delete(source: BackupSourceEntity)
}
