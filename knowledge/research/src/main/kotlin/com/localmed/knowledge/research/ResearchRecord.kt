package com.localmed.knowledge.research

/** Research record metadata; clinical trials and educational synopsis templates are distinct types. */
data class ResearchRecord(
    val id: String,
    val title: String,
    val specialty: String,
    val content: String,
    val source: String,
    val authorOrOrganization: String,
    val publicationYear: Int?,
    val evidenceLevel: String,
    val reviewStatus: String,
    val revision: Int,
    val effectiveDate: String?,
    val reviewByDate: String?,
    val license: String,
    val reviewerNotes: String
)
