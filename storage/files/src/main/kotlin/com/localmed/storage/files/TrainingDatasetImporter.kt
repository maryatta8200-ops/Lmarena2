package com.localmed.storage.files

import android.content.Context
import android.net.Uri
import com.localmed.ai.training.TrainingDatasetRecord
import com.localmed.ai.training.TrainingDatasetRepository
import com.localmed.ai.training.TrainingDatasetValidator
import com.localmed.core.security.EncryptedArtifactStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID

class TrainingDatasetImportException(
    message: String,
    val validationIssues: List<String> = emptyList(),
    cause: Throwable? = null
) : Exception(message, cause)

/** Explicit import only. Validation and encryption are implemented; training is a separate capability. */
class TrainingDatasetImporter(
    context: Context,
    private val validator: TrainingDatasetValidator,
    private val repository: TrainingDatasetRepository,
    private val encryptedStore: EncryptedArtifactStore
) {
    private val appContext = context.applicationContext

    suspend fun importDataset(uri: Uri, displayName: String, source: String, license: String): TrainingDatasetRecord {
        require(displayName.isNotBlank() && displayName.length <= 200) { "Dataset name is required." }
        require(source.isNotBlank() && source.length <= 500) { "Dataset source is required." }
        require(license.isNotBlank() && license.length <= 200) { "Dataset license is required." }
        require(listOf(displayName, source, license).none { value -> value.any { Character.isISOControl(it) } }) {
            "Dataset display metadata must not contain control characters."
        }
        val scratch = File(appContext.cacheDir, "training-import-${UUID.randomUUID()}.jsonl")
        var pendingArtifactId: String? = null
        var persisted = false
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw TrainingDatasetImportException("Selected dataset could not be opened.")
            input.use { sourceStream ->
                FileOutputStream(scratch).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        val count = sourceStream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw TrainingDatasetImportException("Dataset exceeds the 50 MiB import limit.")
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            val report = FileInputStream(scratch).use { validator.validate(it) }
            if (!report.isValid) {
                throw TrainingDatasetImportException(
                    "Training dataset validation failed; no dataset was stored.",
                    report.issues.map { "Line ${it.line}: ${it.message}" }.take(MAX_REPORTED_ISSUES)
                )
            }
            val existing = repository.list().firstOrNull { it.sha256.equals(report.datasetSha256, ignoreCase = true) }
            if (existing != null) {
                if (encryptedStore.encryptedFile(existing.id, DATASET_PATH).isFile) return existing
                pendingArtifactId = existing.id
                encryptedStore.encrypt(FileInputStream(scratch), existing.id, DATASET_PATH, MAX_BYTES)
                persisted = true
                return existing
            }
            val id = "dataset-${report.datasetSha256.take(48)}"
            pendingArtifactId = id
            val record = TrainingDatasetRecord(
                id = id,
                displayName = displayName.trim(),
                sha256 = report.datasetSha256,
                byteSize = scratch.length(),
                exampleCount = report.validExamples,
                importedAt = Instant.now(),
                source = source.trim(),
                license = license.trim()
            )
            encryptedStore.encrypt(FileInputStream(scratch), id, DATASET_PATH, MAX_BYTES)
            val registered = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                repository.register(record).also { persisted = it }
            }
            if (!registered) throw TrainingDatasetImportException("Dataset metadata could not be stored.")
            return record
        } catch (exception: TrainingDatasetImportException) {
            if (!persisted) pendingArtifactId?.let(encryptedStore::deleteArtifact)
            throw exception
        } catch (exception: Exception) {
            if (!persisted) pendingArtifactId?.let(encryptedStore::deleteArtifact)
            throw TrainingDatasetImportException(exception.message ?: "Dataset import failed.", cause = exception)
        } finally {
            scratch.delete()
        }
    }

    suspend fun delete(id: String): Boolean {
        val removed = repository.remove(id)
        if (removed) encryptedStore.deleteArtifact(id)
        return removed
    }

    companion object {
        const val DATASET_PATH = "training/examples.jsonl"
        private const val MAX_BYTES = 50L * 1024 * 1024
        private const val COPY_BUFFER_BYTES = 16 * 1024
        private const val MAX_REPORTED_ISSUES = 20
    }
}
