package com.localmed.ai.retrieval

import com.localmed.knowledge.api.ReviewStatus
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentEvidenceTest {
    private val today = LocalDate.parse("2026-10-05")

    @Test
    fun excludesEvidenceBeforeItsEffectiveDate() {
        assertFalse(isCurrentVerifiedEvidence(ReviewStatus.VERIFIED, "2026-10-06", null, today))
    }

    @Test
    fun excludesExpiredOrUnverifiedEvidence() {
        assertFalse(isCurrentVerifiedEvidence(ReviewStatus.VERIFIED, null, "2026-10-04", today))
        assertFalse(isCurrentVerifiedEvidence(ReviewStatus.UNREVIEWED, null, null, today))
    }

    @Test
    fun includesVerifiedEvidenceOnInclusiveDateBoundaries() {
        assertTrue(isCurrentVerifiedEvidence(ReviewStatus.VERIFIED, "2026-10-05", "2026-10-05", today))
        assertTrue(isCurrentVerifiedEvidence(ReviewStatus.VERIFIED, null, null, today))
    }

    @Test
    fun excludesMalformedDates() {
        assertFalse(isCurrentVerifiedEvidence(ReviewStatus.VERIFIED, "not-a-date", null, today))
        assertFalse(isCurrentVerifiedEvidence(ReviewStatus.VERIFIED, null, "not-a-date", today))
    }
}
