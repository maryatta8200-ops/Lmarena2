package com.localmed.knowledge.medical

import com.localmed.knowledge.api.EvidenceLevel
import com.localmed.knowledge.api.ReviewStatus
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeJsonlImporterTest {
    private val importer = KnowledgeJsonlImporter()

    @Test
    fun importedKnowledgeIsAlwaysUnreviewed() {
        val result = importer.importJsonl(ByteArrayInputStream(validRecord.toByteArray()))
        assertEquals(1, result.records.size)
        assertTrue(result.issues.isEmpty())
        assertEquals(ReviewStatus.UNREVIEWED, result.records.single().reviewStatus)
        assertEquals(EvidenceLevel.UNKNOWN, result.records.single().evidenceLevel)
    }

    @Test
    fun invalidRowsAreNotConvertedIntoKnowledgeRecords() {
        val result = importer.importJsonl(ByteArrayInputStream("{bad json}\n$validRecord".toByteArray()))
        assertEquals(1, result.records.size)
        assertEquals(1, result.issues.size)
        assertEquals("INVALID_JSON", result.issues.single().code)
    }

    @Test
    fun rejectsInvalidIsoDatesAndMetadataControlCharacters() {
        val invalidDate = validRecord.replace(
            "\"license\":\"CC0-1.0\"",
            "\"license\":\"CC0-1.0\",\"review_or_expiration_date\":\"2025-02-30\""
        )
        val dateResult = importer.importJsonl(ByteArrayInputStream(invalidDate.toByteArray()))
        assertTrue(dateResult.records.isEmpty())
        assertEquals("INVALID_RECORD", dateResult.issues.single().code)

        val invalidMetadata = validRecord.replace(
            "\"source\":\"test fixture\"",
            "\"source\":\"test\\nfixture\""
        )
        val metadataResult = importer.importJsonl(ByteArrayInputStream(invalidMetadata.toByteArray()))
        assertTrue(metadataResult.records.isEmpty())
        assertEquals("INVALID_RECORD", metadataResult.issues.single().code)
    }

    @Test
    fun rejectsMalformedUtf8WithoutProducingPartialRecords() {
        val result = importer.importJsonl(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        assertTrue(result.records.isEmpty())
        assertEquals("INVALID_UTF8", result.issues.single().code)
    }

    private val validRecord = """{"schema_version":1,"id":"fixture-record-1","record_type":"EDUCATIONAL_EXPLANATION","specialty":"research methods","title":"Test fixture record","content":"This is test-only content and is not clinical guidance.","source":"test fixture","author_or_organization":"test suite","evidence_level":"UNKNOWN","license":"CC0-1.0"}"""
}
