package com.localmed.ai.api

import java.io.File
import java.time.Instant

data class OnnxGenerationContract(
    val inputIdsName: String = "input_ids",
    val attentionMaskName: String? = "attention_mask",
    val logitsOutputName: String = "logits",
    val endOfSequenceTokenIds: Set<Int> = emptySet(),
    val supportsDynamicSequenceLength: Boolean = true
)

/** Runtime-neutral descriptor; Android objects and tensor handles never cross this API. */
data class ModelDescriptor(
    val id: String,
    val version: String,
    val modelFile: File,
    val tokenizerFile: File,
    val modelSha256: String,
    val tokenizerSha256: String,
    val vocabularySize: Int,
    val contextLength: Int,
    val architecture: String,
    val license: String,
    val source: String,
    val generationContract: OnnxGenerationContract = OnnxGenerationContract(),
    val nnapiBenchmarkPassed: Boolean = false,
    /** Private plaintext cache files are removed when load fails or the runtime is unloaded. */
    val temporaryFiles: List<File> = emptyList()
)

data class GenerationRequest(
    val prompt: String,
    val maxNewTokens: Int = 256,
    val stopSequences: List<String> = emptyList()
)

data class GenerationResult(
    val text: String,
    val generatedTokens: Int,
    val modelId: String,
    val modelVersion: String,
    val runtimeName: String,
    val elapsedMillis: Long
)

sealed interface LoadResult {
    data class Loaded(val modelId: String, val version: String, val runtime: String) : LoadResult
    data class Rejected(val reason: String) : LoadResult
}

interface InferenceEngine {
    suspend fun load(model: ModelDescriptor): LoadResult
    suspend fun generate(request: GenerationRequest): GenerationResult
    suspend fun unload()
    fun isLoaded(): Boolean
}

data class ProviderBenchmarkResult(
    val provider: String,
    val supported: Boolean,
    val correct: Boolean,
    val cpuMedianMillis: Double?,
    val providerMedianMillis: Double?,
    val maxAbsoluteDifference: Double?,
    val recommendation: String
)

interface RuntimeProfiler {
    suspend fun benchmarkNnapi(model: ModelDescriptor): ProviderBenchmarkResult
}

interface TokenizerHandle : AutoCloseable {
    val vocabularySize: Int
    fun encode(text: String, maxTokens: Int): IntArray
    fun decode(tokenIds: IntArray): String
}

interface TokenizerFactory {
    fun open(tokenizerJson: ByteArray, expectedVocabularySize: Int): TokenizerHandle
}

data class DatasetDescriptor(
    val displayName: String,
    val sha256: String,
    val byteSize: Long,
    val createdAt: Instant = Instant.now()
)

data class ValidationIssue(val line: Int, val code: String, val message: String)
data class ValidationReport(
    val validExamples: Int,
    val rejectedExamples: Int,
    val datasetSha256: String,
    val issues: List<ValidationIssue>
) {
    val isValid: Boolean get() = validExamples > 0 && rejectedExamples == 0
}

data class TrainingRequest(
    val jobId: String,
    val baseModelId: String,
    val datasetSha256: String,
    val seed: Long,
    val batchSize: Int,
    val gradientAccumulationSteps: Int,
    val learningRate: Double,
    val maxSteps: Long,
    val checkpointEverySteps: Long
)

data class TrainingResult(
    val jobId: String,
    val completedSteps: Long,
    val checkpointId: String?,
    val completed: Boolean
)

interface TrainingEngine {
    suspend fun validate(dataset: DatasetDescriptor): ValidationReport
    suspend fun train(request: TrainingRequest): TrainingResult
    suspend fun resume(checkpointId: String): TrainingResult
    suspend fun cancel(jobId: String)
}
