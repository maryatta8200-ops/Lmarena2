package com.localmed.knowledge.api

import java.time.Instant

enum class KnowledgeRecordType {
    FACT,
    GUIDELINE,
    TRIAL,
    EDUCATIONAL_EXPLANATION,
    CPSP_PAKISTAN_SYNOPSIS
}

enum class EvidenceLevel {
    HIGH,
    MODERATE,
    LOW,
    VERY_LOW,
    NOT_APPLICABLE,
    UNKNOWN
}

enum class ReviewStatus { UNREVIEWED, VERIFIED, REJECTED }

data class KnowledgeRecord(
    val id: String,
    val recordType: KnowledgeRecordType,
    val specialty: String,
    val title: String,
    val content: String,
    val source: String,
    val sourceUrl: String? = null,
    val authorOrOrganization: String,
    val publicationYear: Int? = null,
    val evidenceLevel: EvidenceLevel,
    val reviewStatus: ReviewStatus = ReviewStatus.UNREVIEWED,
    val revision: Int = 1,
    val effectiveDate: String? = null,
    val reviewOrExpirationDate: String? = null,
    val license: String,
    val reviewerNotes: String = "",
    val provenance: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now()
)

data class KnowledgeQuery(
    val text: String,
    val specialty: String? = null,
    val recordTypes: Set<KnowledgeRecordType> = emptySet(),
    val verifiedOnly: Boolean = true,
    val maxResults: Int = 10
)

data class KnowledgeHit(
    val record: KnowledgeRecord,
    val excerpt: String,
    val score: Double
)

interface KnowledgeRepository {
    suspend fun search(query: KnowledgeQuery): List<KnowledgeHit>
    suspend fun get(id: String): KnowledgeRecord?
    suspend fun listUnreviewed(limit: Int = 100): List<KnowledgeRecord>
    suspend fun listAll(limit: Int = 200): List<KnowledgeRecord>
    suspend fun upsert(records: List<KnowledgeRecord>): Int
    suspend fun setReviewStatus(id: String, status: ReviewStatus, reviewerNotes: String): Boolean
    suspend fun delete(id: String)
}
