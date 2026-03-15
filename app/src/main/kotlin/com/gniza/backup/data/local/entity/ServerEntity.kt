package com.gniza.backup.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMethod: String,
    val password: String?,
    val privateKeyPath: String?,
    val privateKeyPassphrase: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toServer(): Server = Server(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authMethod = AuthMethod.valueOf(authMethod),
        password = password,
        privateKeyPath = privateKeyPath,
        privateKeyPassphrase = privateKeyPassphrase,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromServer(server: Server): ServerEntity = ServerEntity(
            id = server.id,
            name = server.name,
            host = server.host,
            port = server.port,
            username = server.username,
            authMethod = server.authMethod.name,
            password = server.password,
            privateKeyPath = server.privateKeyPath,
            privateKeyPassphrase = server.privateKeyPassphrase,
            createdAt = server.createdAt,
            updatedAt = server.updatedAt
        )
    }
}
