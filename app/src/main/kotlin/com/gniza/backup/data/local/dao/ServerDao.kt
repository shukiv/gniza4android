package com.gniza.backup.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gniza.backup.data.local.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers")
    fun getAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    fun getById(id: Long): Flow<ServerEntity?>

    @Query("SELECT * FROM servers WHERE id = :id")
    fun getByIdSync(id: Long): ServerEntity?

    @Insert
    suspend fun insert(server: ServerEntity): Long

    @Update
    suspend fun update(server: ServerEntity)

    @Query("SELECT COUNT(*) FROM servers")
    fun getCount(): Flow<Int>

    @Delete
    suspend fun delete(server: ServerEntity)
}
