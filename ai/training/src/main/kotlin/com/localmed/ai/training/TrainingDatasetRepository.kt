package com.localmed.ai.training

import java.time.Instant

data class TrainingDatasetRecord(
    val id: String,
    val displayName: String,
    val sha256: String,
    val byteSize: Long,
    val exampleCount: Int,
    val importedAt: Instant,
    val source: String,
    val license: String
)

interface TrainingDatasetRepository {
    suspend fun list(): List<TrainingDatasetRecord>
    suspend fun register(record: TrainingDatasetRecord): Boolean
    suspend fun remove(id: String): Boolean
}
