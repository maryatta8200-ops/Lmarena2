package com.localmed.ai.retrieval

import com.localmed.knowledge.api.KnowledgeHit
import com.localmed.knowledge.api.KnowledgeQuery
import com.localmed.knowledge.api.KnowledgeRepository
import com.localmed.knowledge.api.ReviewStatus
import java.time.LocalDate
import java.time.ZoneOffset

class LocalEvidenceRetriever(private val repository: KnowledgeRepository) {
    suspend fun retrieve(query: String, specialty: String? = null, limit: Int = 8): List<KnowledgeHit> {
        val safeLimit = limit.coerceIn(1, 20)
        val asOfDate = LocalDate.now(ZoneOffset.UTC)
        return repository.search(
            KnowledgeQuery(
                text = query.take(MAX_QUERY_CHARS),
                specialty = specialty?.take(MAX_SPECIALTY_CHARS),
                verifiedOnly = true,
                maxResults = safeLimit
            )
        ).filter { hit ->
            val record = hit.record
            isCurrentVerifiedEvidence(record.reviewStatus, record.effectiveDate, record.reviewOrExpirationDate, asOfDate)
        }
    }

    companion object {
        private const val MAX_QUERY_CHARS = 1_000
        private const val MAX_SPECIALTY_CHARS = 100
    }
}

internal fun isCurrentVerifiedEvidence(
    reviewStatus: ReviewStatus,
    effectiveDate: String?,
    reviewOrExpirationDate: String?,
    asOfDate: LocalDate
): Boolean {
    if (reviewStatus != ReviewStatus.VERIFIED) return false
    val isEffective = effectiveDate == null ||
        runCatching { LocalDate.parse(effectiveDate) <= asOfDate }.getOrDefault(false)
    val isCurrent = reviewOrExpirationDate == null ||
        runCatching { LocalDate.parse(reviewOrExpirationDate) >= asOfDate }.getOrDefault(false)
    return isEffective && isCurrent
}
