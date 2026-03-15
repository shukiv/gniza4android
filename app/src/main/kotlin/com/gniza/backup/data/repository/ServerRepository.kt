package com.gniza.backup.data.repository

import com.gniza.backup.data.local.dao.ServerDao
import com.gniza.backup.data.local.entity.ServerEntity
import com.gniza.backup.domain.model.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ServerRepository @Inject constructor(
    private val serverDao: ServerDao
) {
    val allServers: Flow<List<Server>> = serverDao.getAll().map { entities ->
        entities.map { it.toServer() }
    }

    val serverCount: Flow<Int> = serverDao.getCount()

    fun getServer(id: Long): Flow<Server?> = serverDao.getById(id).map { it?.toServer() }

    suspend fun getServerSync(id: Long): Server? = serverDao.getByIdSync(id)?.toServer()

    suspend fun saveServer(server: Server): Long {
        val entity = ServerEntity.fromServer(server)
        return if (server.id == 0L) {
            serverDao.insert(entity)
        } else {
            serverDao.update(entity)
            server.id
        }
    }

    suspend fun deleteServer(server: Server) {
        serverDao.delete(ServerEntity.fromServer(server))
    }
}
