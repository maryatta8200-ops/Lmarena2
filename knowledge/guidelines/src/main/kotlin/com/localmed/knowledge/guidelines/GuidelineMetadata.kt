package com.localmed.knowledge.guidelines

/** Registry metadata only. No guideline text is bundled without a verified source and license. */
data class GuidelineMetadata(
    val id: String,
    val organization: String,
    val title: String,
    val specialty: String,
    val publicationDate: String,
    val effectiveDate: String?,
    val reviewByDate: String?,
    val sourceUrl: String,
    val license: String,
    val reviewStatus: String
)
