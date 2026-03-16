package com.gniza.backup.service.ssh

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.security.spec.RSAPrivateCrtKeySpec
import java.util.Base64
import javax.inject.Inject

class DropbearKeyConverter @Inject constructor() {

    /**
     * Convert a PEM key to Dropbear native format.
     * Returns path to converted key, or original path if not a PEM key or conversion fails.
     * Converted keys are cached alongside the original with a .dropbear suffix.
     */
    fun ensureDropbearFormat(keyPath: String): String {
        val keyFile = File(keyPath)
        if (!keyFile.exists()) return keyPath

        val dropbearFile = File(keyPath + ".dropbear")
        if (dropbearFile.exists() && dropbearFile.lastModified() >= keyFile.lastModified()) {
            return dropbearFile.absolutePath
        }

        val content = keyFile.readText()
        if (!content.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            return keyPath // Not a PEM RSA key, return as-is
        }

        return try {
            val rsaKey = parsePemRsaKey(content)
            val dropbearBytes = toDropbearFormat(rsaKey)
            dropbearFile.writeBytes(dropbearBytes)
            dropbearFile.setReadable(false, false)
            dropbearFile.setReadable(true, true)
            dropbearFile.absolutePath
        } catch (e: Exception) {
            keyPath // Conversion failed, return original
        }
    }

    private fun parsePemRsaKey(pemContent: String): RSAPrivateCrtKeySpec {
        // Strip PEM headers and decode base64
        val base64 = pemContent
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val derBytes = Base64.getDecoder().decode(base64)

        // Parse PKCS#1 DER format (RSA private key)
        // PKCS#1 RSAPrivateKey ::= SEQUENCE {
        //   version INTEGER, n INTEGER, e INTEGER, d INTEGER,
        //   p INTEGER, q INTEGER, dp INTEGER, dq INTEGER, qInv INTEGER }
        return parsePkcs1RsaKey(derBytes)
    }

    private fun parsePkcs1RsaKey(der: ByteArray): RSAPrivateCrtKeySpec {
        var offset = 0

        // SEQUENCE tag
        if (der[offset++].toInt() != 0x30) throw IllegalArgumentException("Not a SEQUENCE")
        val seqLen = readDerLength(der, offset)
        offset = seqLen.second

        // version
        val version = readDerInteger(der, offset)
        offset = version.second

        // n (modulus)
        val n = readDerInteger(der, offset)
        offset = n.second

        // e (public exponent)
        val e = readDerInteger(der, offset)
        offset = e.second

        // d (private exponent)
        val d = readDerInteger(der, offset)
        offset = d.second

        // p (prime1)
        val p = readDerInteger(der, offset)
        offset = p.second

        // q (prime2)
        val q = readDerInteger(der, offset)
        offset = q.second

        // dp (exponent1) - not needed for Dropbear format but must skip
        val dp = readDerInteger(der, offset)
        offset = dp.second

        // dq (exponent2)
        val dq = readDerInteger(der, offset)
        offset = dq.second

        // qInv (coefficient)
        val qInv = readDerInteger(der, offset)

        return RSAPrivateCrtKeySpec(
            n.first, e.first, d.first, p.first, q.first,
            dp.first, dq.first, qInv.first
        )
    }

    private fun readDerLength(der: ByteArray, offset: Int): Pair<Int, Int> {
        var pos = offset
        val firstByte = der[pos++].toInt() and 0xFF
        if (firstByte < 0x80) {
            return Pair(firstByte, pos)
        }
        val numBytes = firstByte and 0x7F
        var length = 0
        for (i in 0 until numBytes) {
            length = (length shl 8) or (der[pos++].toInt() and 0xFF)
        }
        return Pair(length, pos)
    }

    private fun readDerInteger(der: ByteArray, offset: Int): Pair<BigInteger, Int> {
        var pos = offset
        if (der[pos++].toInt() != 0x02) throw IllegalArgumentException("Expected INTEGER tag")
        val lenResult = readDerLength(der, pos)
        pos = lenResult.second
        val intBytes = der.copyOfRange(pos, pos + lenResult.first)
        return Pair(BigInteger(intBytes), pos + lenResult.first)
    }

    private fun toDropbearFormat(key: RSAPrivateCrtKeySpec): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Key type string
        writeSshString(dos, "ssh-rsa")

        // RSA components as mpints
        writeSshMpint(dos, key.publicExponent)   // e
        writeSshMpint(dos, key.modulus)           // n
        writeSshMpint(dos, key.privateExponent)   // d
        writeSshMpint(dos, key.primeP)            // p
        writeSshMpint(dos, key.primeQ)            // q

        dos.flush()
        return baos.toByteArray()
    }

    private fun writeSshString(dos: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        dos.writeInt(bytes.size)
        dos.write(bytes)
    }

    private fun writeSshMpint(dos: DataOutputStream, value: BigInteger) {
        val bytes = value.toByteArray()
        // SSH mpint: BigInteger.toByteArray() already uses the minimal two's complement
        // representation with a leading 0x00 byte when the high bit is set, which matches
        // the SSH mpint encoding for positive values.
        dos.writeInt(bytes.size)
        dos.write(bytes)
    }
}
