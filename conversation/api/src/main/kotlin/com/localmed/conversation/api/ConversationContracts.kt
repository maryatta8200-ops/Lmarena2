package com.localmed.conversation.api

import com.localmed.ai.api.ResponseState
import com.localmed.knowledge.api.KnowledgeHit
import java.time.Instant
import java.util.UUID

enum class ConversationSource { USER, SMS_DRAFT, WHATSAPP_HANDOFF }

data class NormalizedMessage(
    val sessionId: String,
    val text: String,
    val localeTag: String = "en",
    val source: ConversationSource = ConversationSource.USER,
    val messageId: String = UUID.randomUUID().toString(),
    val receivedAt: Instant = Instant.now()
)

data class ResponseCitation(
    val recordId: String,
    val title: String,
    val source: String,
    val sourceUrl: String?,
    val publicationYear: Int?,
    val evidenceLevel: String,
    val excerpt: String,
    val effectiveDate: String? = null,
    val reviewOrExpirationDate: String? = null
)

data class OutgoingMessage(
    val responseState: ResponseState,
    val content: String,
    val citations: List<ResponseCitation> = emptyList(),
    val retrievedEvidence: List<KnowledgeHit> = emptyList(),
    val generatedByModel: Boolean = false,
    val modelVersion: String? = null,
    val createdAt: Instant = Instant.now()
)

interface ConversationGateway {
    suspend fun process(message: NormalizedMessage): OutgoingMessage?
}
