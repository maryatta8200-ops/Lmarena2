package com.localmed.storage.files

import com.localmed.ai.model.TrustedPublisher
import com.localmed.core.common.Hashing
import java.io.InputStream
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class TrustedPublisherKeyImporter {
    /** Requires the user to compare and type the fingerprint published through an independent channel. */
    fun importRsaPublicKey(
        input: InputStream,
        keyId: String,
        displayName: String,
        userConfirmedFingerprint: String
    ): TrustedPublisher {
        require(keyId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) { "Publisher key ID is invalid." }
        require(displayName.isNotBlank() && displayName.length <= 120) { "Publisher display name is required." }
        val bytes = input.use { readBounded(it) }
        val decoded = if (bytes.firstOrNull()?.toInt()?.and(0xff) == 0x30) bytes else decodePublicKey(bytes.toString(Charsets.US_ASCII))
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded))
        val rsaKey = key as? java.security.interfaces.RSAPublicKey
            ?: throw IllegalArgumentException("Only RSA X.509 public keys are supported by this importer.")
        require(rsaKey.modulus.bitLength() >= MIN_RSA_BITS) { "RSA key must be at least $MIN_RSA_BITS bits." }
        val fingerprint = Hashing.sha256(decoded)
        require(normalizeFingerprint(userConfirmedFingerprint) == fingerprint) {
            "Fingerprint confirmation did not match the selected public key."
        }
        return TrustedPublisher(
            keyId = keyId,
            publicKeyX509Base64 = Base64.getEncoder().encodeToString(decoded),
            fingerprintSha256 = fingerprint,
            displayName = displayName.trim(),
            trustGrantedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun decodePublicKey(text: String): ByteArray {
        val stripped = text
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filterNot(Char::isWhitespace)
        require(stripped.isNotEmpty() && stripped.length <= MAX_BASE64_CHARS) { "Public key file is empty or too large." }
        return runCatching { Base64.getDecoder().decode(stripped) }
            .getOrElse { throw IllegalArgumentException("Expected an X.509 DER or PEM RSA public key.") }
    }

    private fun normalizeFingerprint(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

    private fun readBounded(input: InputStream): ByteArray {
        val bytes = input.readBytesLimited(MAX_KEY_BYTES)
        require(bytes.isNotEmpty()) { "Public key file is empty." }
        return bytes
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Public key file exceeds the configured size limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val MIN_RSA_BITS = 2048
        private const val MAX_KEY_BYTES = 16 * 1024
        private const val MAX_BASE64_CHARS = 12 * 1024
    }
}
