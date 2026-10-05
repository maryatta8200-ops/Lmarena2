package com.localmed.ui

import com.localmed.ai.model.ModelValidationState
import com.localmed.conversation.api.OutgoingMessage
import com.localmed.knowledge.api.KnowledgeRecord
import java.time.Instant

/** UI-only projections keep Android presentation independent of Room entities and network DTOs. */
data class LocalMedUiState(
    val isBusy: Boolean = false,
    val isInitialized: Boolean = false,
    val loadedModelLabel: String? = null,
    val models: List<ModelUi> = emptyList(),
    val knowledgeRecords: List<KnowledgeRecord> = emptyList(),
    val trustedPublishers: List<TrustedPublisherUi> = emptyList(),
    val trainingDatasets: List<TrainingDatasetUi> = emptyList(),
    val response: OutgoingMessage? = null,
    val webSearchBuildAvailable: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val researchResults: List<ResearchArticleUi> = emptyList(),
    val researchQueryHash: String? = null,
    val deviceStatus: String = "Checking device capabilities…",
    val notice: String? = null
)

data class ModelUi(
    val id: String,
    val version: String,
    val status: ModelValidationState,
    val isActive: Boolean,
    val sha256: String,
    val tokenizerSha256: String,
    val source: String,
    val license: String,
    val architecture: String,
    val importedAt: Instant
)

data class TrustedPublisherUi(
    val keyId: String,
    val displayName: String,
    val fingerprint: String
)

data class TrainingDatasetUi(
    val id: String,
    val displayName: String,
    val sha256: String,
    val examples: Int,
    val source: String,
    val license: String,
    val importedAt: Instant
)

data class ResearchArticleUi(
    val pmid: String,
    val title: String,
    val journal: String,
    val publicationDate: String,
    val abstractText: String,
    val url: String,
    val sha256: String
)
