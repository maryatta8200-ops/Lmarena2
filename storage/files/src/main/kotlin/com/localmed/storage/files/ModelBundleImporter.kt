package com.localmed.storage.files

import android.content.Context
import android.net.Uri
import com.localmed.ai.api.ModelDescriptor
import com.localmed.ai.api.OnnxGenerationContract
import com.localmed.ai.model.ModelManifest
import com.localmed.ai.model.ModelManifestValidator
import com.localmed.ai.model.ModelSignatureVerifier
import com.localmed.ai.model.TrustedPublisher
import com.localmed.core.common.Hashing
import com.localmed.core.security.EncryptedArtifactStore
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ModelBundleImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ImportedModelBundle(
    val manifest: ModelManifest,
    val manifestJson: String,
    val artifactStorageId: String,
    val modelCiphertext: File,
    val tokenizerCiphertext: File,
    val reusedExistingEncryptedArtifacts: Boolean = false
)

/** Imports only the documented bundle layout; authenticity requires a user-trusted publisher key. */
class ModelBundleImporter(
    context: Context,
    private val encryptedStore: EncryptedArtifactStore
) {
    private val appContext = context.applicationContext

    fun importBundle(uri: Uri, trustedPublisher: TrustedPublisher): ImportedModelBundle {
        val scratchRoot = File(appContext.cacheDir, "model-import").also { it.mkdirs() }
        val scratch = File(scratchRoot, UUID.randomUUID().toString()).also { it.mkdirs() }
        var artifactId: String? = null
        var hadExistingEncryptedFiles = false
        try {
            val files = extractBundle(uri, scratch)
            val manifestFile = files[MANIFEST_PATH] ?: throw ModelBundleImportException("Model bundle is missing manifest.json.")
            val modelFile = files[ModelManifest.MODEL_FILE] ?: throw ModelBundleImportException("Model bundle is missing ${ModelManifest.MODEL_FILE}.")
            val tokenizerFile = files[ModelManifest.TOKENIZER_FILE] ?: throw ModelBundleImportException("Model bundle is missing ${ModelManifest.TOKENIZER_FILE}.")
            val manifestJson = manifestFile.readText(Charsets.UTF_8)
            val manifest = try {
                ModelManifest.parse(manifestJson)
            } catch (exception: Exception) {
                throw ModelBundleImportException("Model manifest JSON is invalid or has an unsupported schema.", exception)
            }
            val manifestCheck = ModelManifestValidator.validate(manifest)
            if (!manifestCheck.valid) throw ModelBundleImportException(manifestCheck.errors.joinToString(" "))
            if (manifest.publisherKeyId != trustedPublisher.keyId) throw ModelBundleImportException("The bundle signer is not the selected trusted publisher.")
            if (!ModelSignatureVerifier.verify(manifest, trustedPublisher)) throw ModelBundleImportException("Publisher signature verification failed.")

            val modelHash = FileInputStream(modelFile).use { Hashing.sha256(it, MAX_MODEL_BYTES) }
            if (!modelHash.equals(manifest.modelSha256, ignoreCase = true)) throw ModelBundleImportException("ONNX model SHA-256 does not match the signed manifest.")
            val tokenizerHash = FileInputStream(tokenizerFile).use { Hashing.sha256(it, MAX_TOKENIZER_BYTES) }
            if (!tokenizerHash.equals(manifest.tokenizerSha256, ignoreCase = true)) throw ModelBundleImportException("Tokenizer SHA-256 does not match the signed manifest.")
            if (manifest.tokenizer.vocabularySize != manifest.architecture.vocabularySize) {
                throw ModelBundleImportException("Tokenizer and model vocabulary sizes differ.")
            }

            artifactId = artifactStorageId(manifest)
            val existingModel = encryptedStore.encryptedFile(artifactId, ModelManifest.MODEL_FILE)
            val existingTokenizer = encryptedStore.encryptedFile(artifactId, ModelManifest.TOKENIZER_FILE)
            val hadEncryptedArtifacts = existingModel.isFile || existingTokenizer.isFile
            hadExistingEncryptedFiles = hadEncryptedArtifacts
            val storedModel = if (existingModel.isFile) existingModel else {
                encryptedStore.encrypt(FileInputStream(modelFile), artifactId, ModelManifest.MODEL_FILE, MAX_MODEL_BYTES)
            }
            val storedTokenizer = if (existingTokenizer.isFile) existingTokenizer else {
                encryptedStore.encrypt(FileInputStream(tokenizerFile), artifactId, ModelManifest.TOKENIZER_FILE, MAX_TOKENIZER_BYTES)
            }
            return ImportedModelBundle(
                manifest, manifestJson, artifactId, storedModel, storedTokenizer,
                reusedExistingEncryptedArtifacts = hadEncryptedArtifacts
            )
        } catch (exception: ModelBundleImportException) {
            if (!hadExistingEncryptedFiles) artifactId?.let(encryptedStore::deleteArtifact)
            throw exception
        } catch (exception: Exception) {
            if (!hadExistingEncryptedFiles) artifactId?.let(encryptedStore::deleteArtifact)
            throw ModelBundleImportException(exception.message ?: "Model bundle import failed.", exception)
        } finally {
            scratch.deleteRecursively()
        }
    }

    private fun extractBundle(uri: Uri, scratch: File): Map<String, File> {
        val extracted = linkedMapOf<String, File>()
        var total = 0L
        var entries = 0
        val source = appContext.contentResolver.openInputStream(uri)
            ?: throw ModelBundleImportException("The selected model archive could not be opened.")
        ZipInputStream(BufferedInputStream(source)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > MAX_FILES) throw ModelBundleImportException("Model bundle contains too many entries.")
                if (entry.isDirectory) throw ModelBundleImportException("Model bundle subdirectories are not supported.")
                val name = normalizeZipPath(entry.name)
                val limit = when (name) {
                    MANIFEST_PATH -> MAX_MANIFEST_BYTES.toLong()
                    ModelManifest.MODEL_FILE -> MAX_MODEL_BYTES
                    ModelManifest.TOKENIZER_FILE -> MAX_TOKENIZER_BYTES
                    else -> throw ModelBundleImportException("Unexpected file in model bundle: $name")
                }
                if (name in extracted) throw ModelBundleImportException("Duplicate file path in model bundle: $name")
                if (entry.size > limit) throw ModelBundleImportException("Bundle entry exceeds its size limit: $name")
                val output = File(scratch, name)
                output.parentFile?.mkdirs()
                var entryBytes = 0L
                FileOutputStream(output).use { fileOutput ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        total += count
                        if (entryBytes > limit || total > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            throw ModelBundleImportException("Model archive exceeds configured unpacked size limits.")
                        }
                        fileOutput.write(buffer, 0, count)
                    }
                    fileOutput.flush()
                    fileOutput.fd.sync()
                }
                if (entryBytes == 0L) throw ModelBundleImportException("Model bundle entry is empty: $name")
                extracted[name] = output
                zip.closeEntry()
            }
        }
        return extracted
    }

    private fun normalizeZipPath(path: String): String {
        if (path.isBlank() || path.length > 240 || path.startsWith('/') || path.contains('\\')) {
            throw ModelBundleImportException("Model bundle contains an invalid path.")
        }
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) throw ModelBundleImportException("Model bundle contains a path traversal entry.")
        if (!path.all { it.isLetterOrDigit() || it in "/._-" }) throw ModelBundleImportException("Model bundle path contains unsupported characters.")
        return path
    }

    private fun artifactStorageId(manifest: ModelManifest): String = manifest.storageId()

    companion object {
        const val MANIFEST_PATH = "manifest.json"
        private const val MAX_FILES = 3
        private const val MAX_MANIFEST_BYTES = 100 * 1024
        private const val MAX_MODEL_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAX_TOKENIZER_BYTES = 50L * 1024 * 1024
        private const val MAX_TOTAL_UNCOMPRESSED_BYTES = MAX_MODEL_BYTES + MAX_TOKENIZER_BYTES + MAX_MANIFEST_BYTES
        private const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

class ModelArtifactResolver(
    private val encryptedStore: EncryptedArtifactStore,
    private val cacheRoot: File
) {
    fun materialize(bundle: ImportedModelBundle): ModelDescriptor {
        val modelEncrypted = encryptedStore.encryptedFile(bundle.artifactStorageId, ModelManifest.MODEL_FILE)
        val tokenizerEncrypted = encryptedStore.encryptedFile(bundle.artifactStorageId, ModelManifest.TOKENIZER_FILE)
        require(modelEncrypted.isFile && tokenizerEncrypted.isFile) { "Encrypted model files are missing." }
        val modelFile = encryptedStore.decryptToCache(
            modelEncrypted, bundle.artifactStorageId, ModelManifest.MODEL_FILE, MAX_MODEL_BYTES
        )
        val tokenizerFile = try {
            encryptedStore.decryptToCache(
                tokenizerEncrypted, bundle.artifactStorageId, ModelManifest.TOKENIZER_FILE, MAX_TOKENIZER_BYTES
            )
        } catch (exception: Exception) {
            modelFile.delete()
            throw exception
        }
        val cachePath = runCatching { cacheRoot.canonicalFile.path + File.separator }.getOrElse {
            modelFile.delete()
            tokenizerFile.delete()
            throw IllegalStateException("Verified cache root is not accessible.", it)
        }
        if (!modelFile.canonicalPath.startsWith(cachePath) || !tokenizerFile.canonicalPath.startsWith(cachePath)) {
            modelFile.delete()
            tokenizerFile.delete()
            throw IllegalStateException("Decrypted model files escaped the app-private verified cache.")
        }
        val a = bundle.manifest.architecture
        return ModelDescriptor(
            id = bundle.manifest.modelId,
            version = bundle.manifest.modelVersion,
            modelFile = modelFile,
            tokenizerFile = tokenizerFile,
            modelSha256 = bundle.manifest.modelSha256,
            tokenizerSha256 = bundle.manifest.tokenizerSha256,
            vocabularySize = bundle.manifest.tokenizer.vocabularySize,
            contextLength = a.contextLength,
            architecture = a.kind,
            license = bundle.manifest.license,
            source = bundle.manifest.source,
            generationContract = OnnxGenerationContract(
                inputIdsName = a.inputIdsName,
                attentionMaskName = a.attentionMaskName,
                logitsOutputName = a.logitsOutputName,
                endOfSequenceTokenIds = a.endOfSequenceTokenIds.toSet(),
                supportsDynamicSequenceLength = a.dynamicSequenceLength
            ),
            temporaryFiles = listOf(modelFile, tokenizerFile)
        )
    }

    companion object {
        private const val MAX_MODEL_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAX_TOKENIZER_BYTES = 50L * 1024 * 1024
    }
}
