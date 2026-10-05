package com.localmed.core.common

import java.io.InputStream
import java.security.MessageDigest

object Hashing {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .toHex()

    fun sha256(input: InputStream, maxBytes: Long): String {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Input exceeds the configured byte limit" }
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    fun isSha256(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
