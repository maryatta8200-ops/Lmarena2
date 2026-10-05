package com.localmed.ai.model

import java.time.Instant

enum class ModelValidationState { IMPORTED, SMOKE_TESTED, ACTIVE, REJECTED }

data class ModelRegistryEntry(
    val modelId: String,
    val modelVersion: String,
    val manifestJson: String,
    val status: ModelValidationState,
    val importedAt: Instant,
    val modelSha256: String,
    val tokenizerSha256: String,
    val isActive: Boolean
)

interface ModelRegistry {
    suspend fun list(): List<ModelRegistryEntry>
    suspend fun get(modelId: String, version: String): ModelRegistryEntry?
    suspend fun register(entry: ModelRegistryEntry): Boolean
    suspend fun markSmokeTested(modelId: String, version: String): Boolean
    suspend fun markRejected(modelId: String, version: String, reason: String): Boolean
    suspend fun activate(modelId: String, version: String): Boolean
    suspend fun deactivateAll()
}

interface TrustedPublisherRepository {
    suspend fun listTrustedPublishers(): List<TrustedPublisher>
    suspend fun addTrustedPublisher(publisher: TrustedPublisher): Boolean
    suspend fun removeTrustedPublisher(keyId: String)
    suspend fun getTrustedPublisher(keyId: String): TrustedPublisher?
}
