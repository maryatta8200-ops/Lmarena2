package com.localmed.ai.training

import com.localmed.ai.api.ValidationIssue
import com.localmed.ai.api.ValidationReport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

@Serializable
data class TrainingExampleJsonlV1(
    @SerialName("schema_version") val schemaVersion: Int,
    val id: String,
    val task: String,
    val system: String,
    val input: String,
    val output: String,
    val specialty: String,
    val source: String,
    val license: String,
    val language: String,
    @SerialName("safety_labels") val safetyLabels: List<String> = emptyList()
)

/** Validates explicit user-selected JSONL data. It does not train or read conversation history. */
class TrainingDatasetValidator {
    fun validate(input: InputStream, maxBytes: Long = MAX_BYTES): ValidationReport {
        require(maxBytes in 1..MAX_BYTES) { "Dataset byte limit is outside supported bounds." }
        val digest = MessageDigest.getInstance("SHA-256")
        val ids = hashSetOf<String>()
        val issues = mutableListOf<ValidationIssue>()
        var valid = 0
        var rejected = 0
        var lineNumber = 0
        var nonBlankRows = 0
        val bounded = BoundedDigestInputStream(input, maxBytes, digest)
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            BufferedReader(InputStreamReader(bounded, decoder)).use { reader ->
                while (true) {
                    val raw = reader.readLine() ?: break
                    lineNumber++
                    val row = raw.trim()
                    if (row.isEmpty()) continue
                    nonBlankRows++
                    if (nonBlankRows > MAX_ROWS) {
                        rejected++
                        issues += ValidationIssue(lineNumber, "TOO_MANY_ROWS", "Dataset exceeds $MAX_ROWS examples.")
                        break
                    }
                    if (row.length > MAX_LINE_CHARS) {
                        rejected++
                        addIssue(issues, ValidationIssue(lineNumber, "ROW_TOO_LARGE", "JSONL row is too large."))
                        continue
                    }
                    val example = runCatching { JSON.decodeFromString<TrainingExampleJsonlV1>(row) }.getOrNull()
                    if (example == null) {
                        rejected++
                        addIssue(issues, ValidationIssue(lineNumber, "INVALID_JSON", "Row does not match the training schema."))
                        continue
                    }
                    val errors = validate(example)
                    if (example.id in ids) errors += "Duplicate example ID."
                    ids += example.id
                    if (errors.isEmpty()) valid++ else {
                        rejected++
                        addIssue(issues, ValidationIssue(lineNumber, "INVALID_EXAMPLE", errors.joinToString(" ")))
                    }
                }
            }
        } catch (_: DatasetLimitExceeded) {
            rejected++
            addIssue(issues, ValidationIssue(lineNumber, "DATASET_TOO_LARGE", "Dataset exceeds the configured byte limit."))
        } catch (_: java.nio.charset.CharacterCodingException) {
            rejected++
            addIssue(issues, ValidationIssue(lineNumber, "INVALID_UTF8", "Dataset must be valid UTF-8."))
        } catch (exception: Exception) {
            rejected++
            addIssue(issues, ValidationIssue(lineNumber, "READ_FAILURE", "Dataset could not be read safely (${exception.javaClass.simpleName})."))
        }
        if (nonBlankRows == 0 && rejected == 0) {
            rejected++
            issues += ValidationIssue(0, "EMPTY_DATASET", "No examples found.")
        }
        return ValidationReport(valid, rejected, digest.digest().toHex(), issues.take(MAX_ISSUES))
    }

    private fun validate(example: TrainingExampleJsonlV1): MutableList<String> = mutableListOf<String>().apply {
        if (example.schemaVersion != 1) add("schema_version must be 1.")
        if (!ID.matches(example.id)) add("Stable example ID is invalid.")
        if (example.task !in SUPPORTED_TASKS) add("task is unsupported.")
        if (example.system.length > 2_000) add("system exceeds 2,000 characters.")
        if (example.input.isBlank() || example.input.length > 8_000) add("input must contain 1–8,000 characters.")
        if (example.output.isBlank() || example.output.length > 8_000) add("output must contain 1–8,000 characters.")
        if (example.specialty.isBlank() || example.specialty.length > 120) add("specialty is required.")
        if (example.source.isBlank() || example.source.length > 500) add("source is required.")
        if (example.license.isBlank() || example.license.length > 200) add("license is required.")
        if (example.language.isBlank() || example.language.length > 40) add("language is required.")
        if (example.safetyLabels.size > 32 || example.safetyLabels.any { it.length !in 1..64 }) add("safety_labels are invalid.")
        if (containsControlChars(example.input) || containsControlChars(example.output) || containsControlChars(example.system)) {
            add("System/input/output contains unsupported control characters.")
        }
        if (listOf(example.specialty, example.source, example.license, example.language).any(::hasMetadataControlChars) ||
            example.safetyLabels.any(::hasMetadataControlChars)
        ) add("Dataset metadata contains unsupported control characters.")
    }

    private fun addIssue(issues: MutableList<ValidationIssue>, issue: ValidationIssue) {
        if (issues.size < MAX_ISSUES) issues += issue
    }

    private fun containsControlChars(value: String) = value.any {
        Character.isISOControl(it) && it != '\n' && it != '\r' && it != '\t'
    }

    private fun hasMetadataControlChars(value: String) = value.any { Character.isISOControl(it) }

    private class BoundedDigestInputStream(
        input: InputStream,
        private val maximumBytes: Long,
        private val digest: MessageDigest
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                addBytes(1)
                digest.update(value.toByte())
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) {
                addBytes(read.toLong())
                digest.update(buffer, offset, read)
            }
            return read
        }

        private fun addBytes(amount: Long) {
            count += amount
            if (count > maximumBytes) throw DatasetLimitExceeded()
        }
    }

    private class DatasetLimitExceeded : RuntimeException()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_BYTES = 50L * 1024 * 1024
        private const val MAX_ROWS = 10_000
        private const val MAX_LINE_CHARS = 32_000
        private const val MAX_ISSUES = 200
        private val ID = Regex("[A-Za-z0-9._:-]{1,128}")
        private val SUPPORTED_TASKS = setOf("instruction", "completion", "supervised_finetuning")
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false; explicitNulls = false }
    }
}
