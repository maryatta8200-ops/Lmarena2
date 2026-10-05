package com.localmed.ai.training

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDatasetValidatorTest {
    private val validator = TrainingDatasetValidator()

    @Test
    fun acceptsAValidExplicitJsonlRecord() {
        val report = validator.validate(ByteArrayInputStream(validRow("example-1").toByteArray()))
        assertTrue(report.isValid)
        assertEquals(1, report.validExamples)
        assertEquals(0, report.rejectedExamples)
        assertEquals(64, report.datasetSha256.length)
    }

    @Test
    fun rejectsMalformedAndDuplicateRowsAsAWholeDataset() {
        val input = listOf(validRow("example-1"), validRow("example-1"), "{not json}").joinToString("\n")
        val report = validator.validate(ByteArrayInputStream(input.toByteArray()))
        assertFalse(report.isValid)
        assertEquals(1, report.validExamples)
        assertEquals(2, report.rejectedExamples)
        assertTrue(report.issues.any { it.code == "INVALID_EXAMPLE" })
        assertTrue(report.issues.any { it.code == "INVALID_JSON" })
    }

    @Test
    fun rejectsDataOverTheConfiguredByteLimit() {
        val report = validator.validate(ByteArrayInputStream("12345678901".toByteArray()), maxBytes = 10)
        assertFalse(report.isValid)
        assertTrue(report.issues.any { it.code == "DATASET_TOO_LARGE" })
    }

    private fun validRow(id: String) = """{"schema_version":1,"id":"$id","task":"instruction","system":"","input":"Explain the research method.","output":"Use a clearly documented method.","specialty":"research methods","source":"test fixture","license":"CC0-1.0","language":"en","safety_labels":[]}"""
}
