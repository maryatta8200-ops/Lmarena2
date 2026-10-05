package com.localmed.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.localmed.core.common.Hashing
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM envelope encryption: per-file DEK, wrapped by a non-exportable Android Keystore key. */
class EncryptedArtifactStore(context: Context) {
    private val appContext = context.applicationContext
    private val secureRoot = File(appContext.filesDir, "secure-artifacts")
    private val cacheRoot = File(appContext.cacheDir, "verified-artifacts")
    private val random = SecureRandom()

    fun encrypt(input: InputStream, artifactId: String, relativePath: String, maxPlainBytes: Long): File =
        input.use { source -> encryptInternal(source, artifactId, relativePath, maxPlainBytes) }

    private fun encryptInternal(input: InputStream, artifactId: String, relativePath: String, maxPlainBytes: Long): File {
        requireSafeIdentifier(artifactId)
        val safePath = normalizeRelativePath(relativePath)
        require(maxPlainBytes in 1..MAX_ARTIFACT_BYTES)
        val destination = secureFile(artifactId, safePath)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.${System.nanoTime()}.tmp")
        val aad = associatedData(artifactId, safePath)
        val dekBytes = ByteArray(32).also(random::nextBytes)
        try {
            val wrapCipher = Cipher.getInstance(AES_GCM)
            wrapCipher.init(Cipher.ENCRYPT_MODE, masterKey())
            wrapCipher.updateAAD(aad + WRAPPED_KEY_AAD_SUFFIX)
            val wrappedDek = wrapCipher.doFinal(dekBytes)
            val fileCipher = Cipher.getInstance(AES_GCM)
            fileCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dekBytes, "AES"))
            fileCipher.updateAAD(aad)

            var written = 0L
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                output.write(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeByte(wrapCipher.iv.size)
                output.write(wrapCipher.iv)
                output.writeInt(wrappedDek.size)
                output.write(wrappedDek)
                output.writeByte(fileCipher.iv.size)
                output.write(fileCipher.iv)
                input.use { source ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= maxPlainBytes) { "Artifact exceeds the configured plaintext limit." }
                        val encrypted = fileCipher.update(buffer, 0, count)
                        if (encrypted != null) output.write(encrypted)
                    }
                    output.write(fileCipher.doFinal())
                    output.flush()
                }
            }
            FileOutputStream(temporary, true).use { it.fd.sync() }
            atomicReplace(temporary, destination)
            return destination
        } catch (exception: Exception) {
            temporary.delete()
            throw exception
        } finally {
            dekBytes.fill(0)
        }
    }

    /** Decrypts into a private temporary file, then returns only after GCM authentication succeeds. */
    fun decryptToCache(
        encryptedFile: File,
        artifactId: String,
        relativePath: String,
        maxPlainBytes: Long
    ): File {
        requireSafeIdentifier(artifactId)
        val safePath = normalizeRelativePath(relativePath)
        require(maxPlainBytes in 1..MAX_ARTIFACT_BYTES)
        val expected = secureFile(artifactId, safePath)
        require(encryptedFile.canonicalFile == expected.canonicalFile) { "Encrypted path is outside the secure artifact store." }
        cacheRoot.mkdirs()
        val targetDirectory = File(cacheRoot, artifactId).also { it.mkdirs() }
        val target = File(targetDirectory, safePath.substringAfterLast('/'))
        val temporary = File(targetDirectory, ".${target.name}.${System.nanoTime()}.decrypting")
        val aad = associatedData(artifactId, safePath)
        var dekBytes: ByteArray? = null
        try {
            DataInputStream(BufferedInputStream(FileInputStream(encryptedFile))).use { input ->
                val magic = ByteArray(MAGIC.size)
                input.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Encrypted artifact has an invalid header." }
                require(input.readInt() == FORMAT_VERSION) { "Encrypted artifact version is unsupported." }
                val wrapIv = readBoundedIv(input)
                val wrappedLength = input.readInt()
                require(wrappedLength in MIN_WRAPPED_KEY_BYTES..MAX_WRAPPED_KEY_BYTES) { "Wrapped key length is invalid." }
                val wrappedDek = ByteArray(wrappedLength)
                input.readFully(wrappedDek)
                val unwrapCipher = Cipher.getInstance(AES_GCM)
                unwrapCipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, wrapIv))
                unwrapCipher.updateAAD(aad + WRAPPED_KEY_AAD_SUFFIX)
                dekBytes = unwrapCipher.doFinal(wrappedDek)
                require(dekBytes.size == 32) { "Unwrapped data key has an invalid length." }

                val dataIv = readBoundedIv(input)
                val decryptCipher = Cipher.getInstance(AES_GCM)
                decryptCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dekBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, dataIv))
                decryptCipher.updateAAD(aad)
                var written = 0L
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val plain = decryptCipher.update(buffer, 0, count)
                        if (plain != null) {
                            written += plain.size
                            require(written <= maxPlainBytes) { "Decrypted artifact exceeds the configured limit." }
                            output.write(plain)
                        }
                    }
                    val finalPlain = decryptCipher.doFinal()
                    written += finalPlain.size
                    require(written <= maxPlainBytes) { "Decrypted artifact exceeds the configured limit." }
                    output.write(finalPlain)
                    output.flush()
                    output.fd.sync()
                }
            }
            atomicReplace(temporary, target)
            return target
        } catch (exception: Exception) {
            temporary.delete()
            target.delete()
            throw exception
        } finally {
            dekBytes?.fill(0)
        }
    }

    fun deleteArtifact(artifactId: String) {
        requireSafeIdentifier(artifactId)
        File(secureRoot, artifactId).deleteRecursively()
        File(cacheRoot, artifactId).deleteRecursively()
    }

    /** Removes crash-left decrypted model files before a new process restores an active model. */
    fun purgePlaintextCache() {
        cacheRoot.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun encryptedFile(artifactId: String, relativePath: String): File {
        requireSafeIdentifier(artifactId)
        return secureFile(artifactId, normalizeRelativePath(relativePath))
    }

    private fun secureFile(artifactId: String, path: String): File {
        val root = File(secureRoot, artifactId).canonicalFile
        val file = File(root, "$path.enc").canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Artifact path escapes the secure storage directory." }
        return file
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // StrongBox is deliberately not required; many valid devices do not provide it.
                    setIsStrongBoxBacked(false)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun readBoundedIv(input: DataInputStream): ByteArray {
        val size = input.readUnsignedByte()
        require(size == GCM_IV_BYTES) { "GCM nonce length is invalid." }
        return ByteArray(size).also(input::readFully)
    }

    private fun normalizeRelativePath(value: String): String {
        require(value.isNotBlank() && value.length <= 240) { "Artifact relative path is invalid." }
        val normalized = value.replace('\\', '/')
        require(!normalized.startsWith('/') && normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Artifact relative path is invalid."
        }
        require(normalized.all { it.isLetterOrDigit() || it in "/._-" }) { "Artifact path contains unsupported characters." }
        return normalized
    }

    private fun requireSafeIdentifier(value: String) {
        require(value.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Artifact ID is invalid." }
    }

    private fun associatedData(artifactId: String, path: String) = "localmed-artifact-v1\u0000$artifactId\u0000$path".toByteArray(Charsets.UTF_8)

    private fun atomicReplace(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "localmed_artifact_master_v1"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION = 1
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_ARTIFACT_BYTES = 2L * 1024 * 1024 * 1024
        private const val MIN_WRAPPED_KEY_BYTES = 48
        private const val MAX_WRAPPED_KEY_BYTES = 512
        private val MAGIC = byteArrayOf('L'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
        private val WRAPPED_KEY_AAD_SUFFIX = "\u0000dek".toByteArray(Charsets.UTF_8)
    }
}
