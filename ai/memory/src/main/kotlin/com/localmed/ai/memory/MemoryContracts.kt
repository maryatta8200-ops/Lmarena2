package com.localmed.ai.memory

import java.time.Instant

enum class MemoryScope { SESSION, USER_APPROVED_LONG_TERM }
enum class ConsentState { NOT_REQUESTED, DECLINED, GRANTED, REVOKED }

data class MemoryRecord(
    val id: String,
    val scope: MemoryScope,
    val content: String,
    val source: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val confidence: Double,
    val consent: ConsentState,
    val retentionPolicy: String,
    val deleted: Boolean = false
)

interface MemoryRepository {
    suspend fun list(scope: MemoryScope): List<MemoryRecord>
    suspend fun save(record: MemoryRecord): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun clear(scope: MemoryScope)
}
