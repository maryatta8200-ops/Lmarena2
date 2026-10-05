package com.localmed.knowledge.medical

import com.localmed.knowledge.api.EvidenceLevel
import com.localmed.knowledge.api.KnowledgeRecord
import com.localmed.knowledge.api.KnowledgeRecordType
import com.localmed.knowledge.api.ReviewStatus
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class KnowledgeJsonlV1(
    @SerialName("schema_version") val schemaVersion: Int,
    val id: String,
    @SerialName("record_type") val recordType: String,
    val specialty: String,
    val title: String,
    val content: String,
    val source: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("author_or_organization") val authorOrOrganization: String,
    @SerialName("publication_year") val publicationYear: Int? = null,
    @SerialName("evidence_level") val evidenceLevel: String,
    val version: Int = 1,
    @SerialName("effective_date") val effectiveDate: String? = null,
    @SerialName("review_or_expiration_date") val reviewOrExpirationDate: String? = null,
    val license: String,
    val provenance: Map<String, String> = emptyMap()
)

data class ImportIssue(val line: Int, val code: String, val message: String)
data class KnowledgeImportResult(val records: List<KnowledgeRecord>, val issues: List<ImportIssue>)

class KnowledgeJsonlImporter {
    fun importJsonl(input: InputStream): KnowledgeImportResult {
        val bytes = input.use { readBounded(it, MAX_FILE_BYTES) }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) {
            return KnowledgeImportResult(emptyList(), listOf(ImportIssue(0, "INVALID_UTF8", "JSONL must be valid UTF-8.")))
        }

        val records = mutableListOf<KnowledgeRecord>()
        val issues = mutableListOf<ImportIssue>()
        val seenIds = hashSetOf<String>()
        var offset = 0
        var lineNumber = 0
        var nonBlankRows = 0

        while (offset <= text.length) {
            val newline = text.indexOf('\n', offset)
            val end = if (newline < 0) text.length else newline
            val rawLine = text.substring(offset, end).removeSuffix("\r")
            lineNumber++

            if (lineNumber > MAX_PHYSICAL_LINES) {
                return KnowledgeImportResult(
                    emptyList(),
                    listOf(ImportIssue(lineNumber, "TOO_MANY_LINES", "Import exceeds the $MAX_PHYSICAL_LINES-line processing limit."))
                )
            }

            val line = rawLine.trim()
            if (line.isNotEmpty()) {
                nonBlankRows++
                if (nonBlankRows > MAX_RECORDS) {
                    return KnowledgeImportResult(
                        emptyList(),
                        listOf(ImportIssue(lineNumber, "TOO_MANY_RECORDS", "Import exceeds $MAX_RECORDS records; no records were imported."))
                    )
                }
                if (line.length > MAX_LINE_CHARS) {
                    issues += ImportIssue(lineNumber, "LINE_TOO_LARGE", "A JSONL row exceeds the per-record limit.")
                } else {
                    val dto = runCatching { JSON.decodeFromString<KnowledgeJsonlV1>(line) }.getOrNull()
                    if (dto == null) {
                        issues += ImportIssue(lineNumber, "INVALID_JSON", "Row is not valid schema-v1 JSON.")
                    } else {
                        val rowErrors = validate(dto)
                        when {
                            rowErrors.isNotEmpty() -> issues += ImportIssue(lineNumber, "INVALID_RECORD", rowErrors.joinToString(" "))
                            !seenIds.add(dto.id) -> issues += ImportIssue(lineNumber, "DUPLICATE_ID", "Duplicate stable record ID ${dto.id}.")
                            else -> records += dto.toDomain()
                        }
                    }
                }
            }

            if (newline < 0) break
            offset = newline + 1
            if (offset == text.length) break
        }

        if (records.isEmpty() && issues.isEmpty()) {
            issues += ImportIssue(0, "EMPTY_DATASET", "No JSONL records were found.")
        }
        return KnowledgeImportResult(records, issues)
    }

    private fun validate(dto: KnowledgeJsonlV1): List<String> = buildList {
        if (dto.schemaVersion != 1) add("schema_version must be 1.")
        if (!ID.matches(dto.id)) add("id must be a stable identifier using letters, numbers, dot, underscore, colon, or hyphen.")
        if (runCatching { KnowledgeRecordType.valueOf(dto.recordType.uppercase()) }.isFailure) add("record_type is unsupported.")
        if (dto.specialty.isBlank() || dto.specialty.length > 120) add("specialty is required and must be under 120 characters.")
        if (dto.title.isBlank() || dto.title.length > 500) add("title is required and must be under 500 characters.")
        if (dto.content.isBlank() || dto.content.length > MAX_CONTENT_CHARS) add("content is required and exceeds the allowed size.")
        if (dto.source.isBlank() || dto.source.length > 500) add("source provenance is required.")
        if (dto.authorOrOrganization.isBlank() || dto.authorOrOrganization.length > 250) add("author_or_organization is required.")
        if (dto.publicationYear != null && dto.publicationYear !in 1500..Instant.now().atZone(ZoneOffset.UTC).year + 1) add("publication_year is invalid.")
        if (runCatching { EvidenceLevel.valueOf(dto.evidenceLevel.uppercase()) }.isFailure) add("evidence_level is unsupported.")
        if (dto.version < 1) add("version must be a positive revision.")
        if (dto.license.isBlank() || dto.license.length > 200) add("license or usage rights are required.")
        if (!validIsoDate(dto.effectiveDate) || !validIsoDate(dto.reviewOrExpirationDate)) {
            add("effective_date and review_or_expiration_date must use ISO-8601 yyyy-MM-dd format.")
        }
        val metadata = listOf(dto.specialty, dto.title, dto.source, dto.authorOrOrganization, dto.license)
        if (metadata.any { containsControlChars(it, allowLineBreaks = false) } ||
            dto.provenance.any { (key, value) -> containsControlChars(key, allowLineBreaks = false) || containsControlChars(value, allowLineBreaks = false) }
        ) {
            add("Control characters are not allowed in provenance or display metadata.")
        }
        dto.sourceUrl?.let { url ->
            val uri = runCatching { URI(url) }.getOrNull()
            if (url.length > MAX_SOURCE_URL_CHARS || containsControlChars(url, allowLineBreaks = false) ||
                uri?.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null
            ) add("source_url must be a bounded HTTPS URL with a host and no embedded credentials.")
        }
        if (containsControlChars(dto.content)) add("Control characters are not allowed in content.")
        if (dto.provenance.size > MAX_PROVENANCE_ENTRIES || dto.provenance.any { (key, value) ->
                key.length > MAX_PROVENANCE_KEY_CHARS || value.length > MAX_PROVENANCE_VALUE_CHARS
            }
        ) add("provenance metadata exceeds allowed bounds.")
    }

    private fun KnowledgeJsonlV1.toDomain() = KnowledgeRecord(
        id = id,
        recordType = KnowledgeRecordType.valueOf(recordType.uppercase()),
        specialty = specialty.trim(),
        title = title.trim(),
        content = content.trim(),
        source = source.trim(),
        sourceUrl = sourceUrl?.trim(),
        authorOrOrganization = authorOrOrganization.trim(),
        publicationYear = publicationYear,
        evidenceLevel = EvidenceLevel.valueOf(evidenceLevel.uppercase()),
        reviewStatus = ReviewStatus.UNREVIEWED,
        revision = version,
        effectiveDate = effectiveDate?.trim()?.takeIf(String::isNotBlank),
        reviewOrExpirationDate = reviewOrExpirationDate?.trim()?.takeIf(String::isNotBlank),
        license = license.trim(),
        provenance = provenance.toMap()
    )

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "JSONL file exceeds the ${maxBytes / (1024 * 1024)} MiB import limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun containsControlChars(value: String, allowLineBreaks: Boolean = true): Boolean = value.any {
        Character.isISOControl(it) && (!allowLineBreaks || it != '\n' && it != '\r' && it != '\t')
    }

    private fun validIsoDate(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)
    }

    companion object {
        const val MAX_RECORDS = 5_000
        private const val MAX_FILE_BYTES = 20 * 1024 * 1024
        private const val MAX_LINE_CHARS = 128 * 1024
        private const val MAX_CONTENT_CHARS = 80_000
        private const val MAX_PHYSICAL_LINES = MAX_RECORDS + 100
        private const val MAX_SOURCE_URL_CHARS = 2_048
        private const val MAX_PROVENANCE_ENTRIES = 32
        private const val MAX_PROVENANCE_KEY_CHARS = 100
        private const val MAX_PROVENANCE_VALUE_CHARS = 1_000
        private val ID = Regex("[A-Za-z0-9._:-]{1,128}")
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false; explicitNulls = false }
    }
}
