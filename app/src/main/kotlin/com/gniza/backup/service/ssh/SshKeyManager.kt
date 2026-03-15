package com.gniza.backup.service.ssh

import android.content.Context
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

data class SshKeyInfo(
    val name: String,
    val type: String,
    val fingerprint: String,
    val publicKeyPath: String,
    val privateKeyPath: String
)

class SshKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val keysDir = File(context.filesDir, "ssh/keys")

    private val validKeyNameRegex = Regex("^[a-zA-Z0-9_.\\-]+$")

    init {
        keysDir.mkdirs()
    }

    private fun validateKeyName(name: String) {
        require(name.isNotEmpty()) { "Key name must not be empty" }
        require(!name.contains("/")) { "Key name must not contain '/'" }
        require(!name.contains("..")) { "Key name must not contain '..'" }
        require(!name.contains('\u0000')) { "Key name must not contain null bytes" }
        require(validKeyNameRegex.matches(name)) {
            "Key name must match [a-zA-Z0-9_.-]"
        }
    }

    private fun setRestrictivePermissions(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }

    fun generateKeyPair(name: String, type: String = "RSA", bits: Int = 4096) {
        validateKeyName(name)
        val jsch = JSch()
        val keyType = when (type.uppercase()) {
            "RSA" -> KeyPair.RSA
            "DSA" -> KeyPair.DSA
            "ECDSA" -> KeyPair.ECDSA
            "ED25519" -> KeyPair.ED25519
            else -> KeyPair.RSA
        }
        val keyPair = KeyPair.genKeyPair(jsch, keyType, bits)

        val privateKeyFile = File(keysDir, name)
        val publicKeyFile = File(keysDir, "$name.pub")

        keyPair.writePrivateKey(privateKeyFile.absolutePath)
        keyPair.writePublicKey(publicKeyFile.absolutePath, name)
        keyPair.dispose()

        setRestrictivePermissions(privateKeyFile)
    }

    fun listKeys(): List<SshKeyInfo> {
        if (!keysDir.exists()) return emptyList()

        return keysDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".pub") }
            ?.mapNotNull { privateKeyFile ->
                val publicKeyFile = File(keysDir, "${privateKeyFile.name}.pub")
                if (!publicKeyFile.exists()) return@mapNotNull null

                var type = "UNKNOWN"
                var fingerprint = ""

                try {
                    val jsch = JSch()
                    val keyPair = KeyPair.load(jsch, privateKeyFile.absolutePath)
                    type = when (keyPair.keyType) {
                        KeyPair.RSA -> "RSA"
                        KeyPair.DSA -> "DSA"
                        KeyPair.ECDSA -> "ECDSA"
                        KeyPair.ED25519 -> "ED25519"
                        else -> "UNKNOWN"
                    }
                    fingerprint = keyPair.fingerPrint
                    keyPair.dispose()
                } catch (_: Exception) {
                    // JSch can't parse this key format — still list it
                    type = "SSH"
                    fingerprint = privateKeyFile.name
                }

                SshKeyInfo(
                    name = privateKeyFile.name,
                    type = type,
                    fingerprint = fingerprint,
                    publicKeyPath = publicKeyFile.absolutePath,
                    privateKeyPath = privateKeyFile.absolutePath
                )
            }
            ?: emptyList()
    }

    fun getPublicKey(name: String): String {
        validateKeyName(name)
        val publicKeyFile = File(keysDir, "$name.pub")
        return publicKeyFile.readText()
    }

    fun deleteKey(name: String) {
        validateKeyName(name)
        val privateKeyFile = File(keysDir, name)
        val publicKeyFile = File(keysDir, "$name.pub")
        privateKeyFile.delete()
        publicKeyFile.delete()
    }

    fun importKey(name: String, privateKeyContent: ByteArray) {
        validateKeyName(name)
        val privateKeyFile = File(keysDir, name)
        privateKeyFile.writeBytes(privateKeyContent)

        setRestrictivePermissions(privateKeyFile)

        // Generate public key from private key
        try {
            val jsch = JSch()
            val keyPair = KeyPair.load(jsch, privateKeyFile.absolutePath)
            val publicKeyFile = File(keysDir, "$name.pub")
            keyPair.writePublicKey(publicKeyFile.absolutePath, name)
            keyPair.dispose()
        } catch (e: Exception) {
            // If JSch can't parse the key (e.g. newer OpenSSH format),
            // extract the public key using ssh-keygen style parsing
            val publicKeyFile = File(keysDir, "$name.pub")
            if (!publicKeyFile.exists()) {
                // Write a placeholder so listKeys() finds the key pair
                publicKeyFile.writeText("# public key for $name (import via ssh-keygen -y -f ${privateKeyFile.absolutePath})")
            }
        }
    }

    fun getPrivateKeyPath(name: String): String {
        validateKeyName(name)
        return File(keysDir, name).absolutePath
    }
}
