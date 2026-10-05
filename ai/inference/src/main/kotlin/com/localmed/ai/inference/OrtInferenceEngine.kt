package com.localmed.ai.inference

import android.os.Build
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.localmed.ai.api.GenerationRequest
import com.localmed.ai.api.GenerationResult
import com.localmed.ai.api.InferenceEngine
import com.localmed.ai.api.LoadResult
import com.localmed.ai.api.ModelDescriptor
import com.localmed.ai.api.ProviderBenchmarkResult
import com.localmed.ai.api.RuntimeProfiler
import com.localmed.ai.api.TokenizerFactory
import com.localmed.ai.api.TokenizerHandle
import com.localmed.core.common.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.LongBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Real ONNX Runtime Mobile decoder loop. CPU is always the initial provider and fallback. */
class OrtInferenceEngine(
    private val tokenizerFactory: TokenizerFactory,
    private val temporaryArtifactRoot: java.io.File
) : InferenceEngine, RuntimeProfiler {
    private val lock = Mutex()
    @Volatile private var loaded: LoadedModel? = null

    override suspend fun load(model: ModelDescriptor): LoadResult = withContext(Dispatchers.IO) {
        lock.withLock {
            closeLoaded()
            val manifestErrors = validateDescriptor(model)
            if (manifestErrors != null) {
                deleteTemporaryFiles(model.temporaryFiles)
                return@withLock LoadResult.Rejected(manifestErrors)
            }

            val tokenizerBytes = try {
                model.tokenizerFile.readBytes()
            } catch (exception: Exception) {
                deleteTemporaryFiles(model.temporaryFiles)
                return@withLock LoadResult.Rejected("Tokenizer file could not be read: ${exception.message ?: "I/O error"}")
            }
            val tokenizer = try {
                tokenizerFactory.open(tokenizerBytes, model.vocabularySize)
            } catch (exception: Exception) {
                tokenizerBytes.fill(0)
                deleteTemporaryFiles(model.temporaryFiles)
                return@withLock LoadResult.Rejected("Tokenizer compatibility check failed: ${exception.message ?: "invalid tokenizer"}")
            }
            tokenizerBytes.fill(0)

            val tryNnapi = model.nnapiBenchmarkPassed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                runCatching { OrtProvider.NNAPI in OrtEnvironment.getAvailableProviders() }.getOrDefault(false)
            val sessionResult = createSession(model, tryNnapi)
            val session = sessionResult.first ?: run {
                tokenizer.close()
                deleteTemporaryFiles(model.temporaryFiles)
                return@withLock LoadResult.Rejected(sessionResult.second ?: "ONNX model could not be loaded.")
            }
            val shapeError = validateSession(model, session)
            if (shapeError != null) {
                session.close()
                tokenizer.close()
                deleteTemporaryFiles(model.temporaryFiles)
                return@withLock LoadResult.Rejected(shapeError)
            }
            val runtime = if (tryNnapi && sessionResult.second == null) "ONNX Runtime Mobile / NNAPI (previously benchmarked)" else "ONNX Runtime Mobile / CPU"
            loaded = LoadedModel(model, session, tokenizer, runtime)
            LoadResult.Loaded(model.id, model.version, runtime)
        }
    }

    override suspend fun generate(request: GenerationRequest): GenerationResult = withContext(Dispatchers.Default) {
        lock.withLock {
            val current = loaded ?: throw IllegalStateException("No verified ONNX model is loaded.")
            require(request.prompt.isNotBlank()) { "Prompt cannot be empty." }
            require(request.prompt.length <= MAX_PROMPT_CHARS) { "Prompt exceeds the configured character limit." }
            require(request.maxNewTokens in 1..MAX_GENERATED_TOKENS) { "maxNewTokens must be between 1 and $MAX_GENERATED_TOKENS." }

            val startNanos = System.nanoTime()
            val promptIds = current.tokenizer.encode(request.prompt, current.descriptor.contextLength)
            if (promptIds.isEmpty()) throw IllegalStateException("Tokenizer returned no input tokens.")
            val generated = ArrayList<Int>(request.maxNewTokens)
            for (index in 0 until request.maxNewTokens) {
                currentCoroutineContext().ensureActive()
                val allTokens = (promptIds.asList() + generated).takeLast(current.descriptor.contextLength).toIntArray()
                val nextToken = nextToken(current, allTokens)
                if (nextToken !in 0 until current.descriptor.vocabularySize) {
                    throw IllegalStateException("Model emitted a token outside the declared vocabulary.")
                }
                if (nextToken in current.descriptor.generationContract.endOfSequenceTokenIds) break
                generated += nextToken
                val currentText = current.tokenizer.decode(generated.toIntArray())
                val stop = request.stopSequences.firstOrNull { it.isNotEmpty() && currentText.contains(it) }
                if (stop != null) break
            }
            val text = current.tokenizer.decode(generated.toIntArray()).let { decoded ->
                request.stopSequences.filter(String::isNotEmpty).mapNotNull { stop -> decoded.indexOf(stop).takeIf { it >= 0 } }
                    .minOrNull()?.let(decoded::substring) ?: decoded
            }.trim()
            GenerationResult(
                text = text,
                generatedTokens = generated.size,
                modelId = current.descriptor.id,
                modelVersion = current.descriptor.version,
                runtimeName = current.runtimeName,
                elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            )
        }
    }

    override suspend fun unload() {
        lock.withLock { closeLoaded() }
    }

    override fun isLoaded(): Boolean = loaded != null

    override suspend fun benchmarkNnapi(model: ModelDescriptor): ProviderBenchmarkResult = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@withContext unsupportedBenchmark("Android API 28 or later is required for the NNAPI path.")
        val providers = runCatching { OrtEnvironment.getAvailableProviders() }.getOrElse {
            return@withContext unsupportedBenchmark("ONNX Runtime provider discovery failed.")
        }
        if (OrtProvider.NNAPI !in providers) return@withContext unsupportedBenchmark("This ONNX Runtime build does not expose NNAPI.")
        if (!model.modelFile.isFile || !model.tokenizerFile.isFile) return@withContext unsupportedBenchmark("Model artifacts are not available for benchmarking.")

        val tokenizerBytes = runCatching { model.tokenizerFile.readBytes() }.getOrElse {
            return@withContext unsupportedBenchmark("Tokenizer could not be read.")
        }
        val tokenizer = runCatching { tokenizerFactory.open(tokenizerBytes, model.vocabularySize) }.getOrElse {
            tokenizerBytes.fill(0)
            return@withContext unsupportedBenchmark("Tokenizer compatibility validation failed.")
        }
        tokenizerBytes.fill(0)
        try {
            val cpuSession = createSession(model, false).first
                ?: return@withContext unsupportedBenchmark("CPU session could not be created.")
            val nnapiSession = createSession(model, true).first
                ?: run { cpuSession.close(); return@withContext unsupportedBenchmark("NNAPI session could not be created.") }
            cpuSession.use { cpu ->
                nnapiSession.use { nnapi ->
                    val sample = tokenizer.encode("clinical evidence", minOf(8, model.contextLength)).ifEmpty { intArrayOf(0) }
                    val cpuWarm = runLogits(cpu, model, sample)
                    val nnapiWarm = runLogits(nnapi, model, sample)
                    if (cpuWarm.size != nnapiWarm.size || cpuWarm.isEmpty()) return@withContext unsupportedBenchmark("Provider output shapes differ.")
                    val maxDifference = cpuWarm.indices.maxOf { abs(cpuWarm[it] - nnapiWarm[it]) }
                    val cpuTimes = measure(cpu, model, sample)
                    val nnapiTimes = measure(nnapi, model, sample)
                    val cpuMedian = median(cpuTimes)
                    val nnapiMedian = median(nnapiTimes)
                    val correct = maxDifference.isFinite() && maxDifference <= DEFAULT_MAX_ABSOLUTE_DIFFERENCE
                    val faster = nnapiMedian < cpuMedian * MIN_SPEEDUP_RATIO
                    ProviderBenchmarkResult(
                        provider = "NNAPI",
                        supported = true,
                        correct = correct,
                        cpuMedianMillis = cpuMedian,
                        providerMedianMillis = nnapiMedian,
                        maxAbsoluteDifference = maxDifference,
                        recommendation = when {
                            !correct -> "Keep CPU: NNAPI output differed beyond the configured tolerance."
                            !faster -> "Keep CPU: NNAPI did not exceed the minimum measured speedup."
                            else -> "NNAPI passed a model-specific smoke comparison and was faster on this device. User confirmation is still required to activate it."
                        }
                    )
                }
            }
        } catch (_: Exception) {
            unsupportedBenchmark("NNAPI model benchmark failed; CPU remains the fallback.")
        } finally {
            tokenizer.close()
        }
    }

    private fun createSession(model: ModelDescriptor, nnapi: Boolean): Pair<OrtSession?, String?> {
        return try {
            val options = OrtSession.SessionOptions()
            options.use {
                if (nnapi) it.addNnapi()
                OrtEnvironment.getEnvironment().createSession(model.modelFile.absolutePath, it) to null
            }
        } catch (exception: Exception) {
            if (nnapi) {
                // A requested, benchmarked accelerator can still fail on a vendor driver; retry CPU.
                return createSession(model.copy(nnapiBenchmarkPassed = false), nnapi = false).let { (session, _) ->
                    session to "NNAPI unavailable at load; CPU fallback selected (${exception.javaClass.simpleName})."
                }
            }
            null to (exception.message ?: "ONNX Runtime rejected the model.")
        }
    }

    private fun validateSession(model: ModelDescriptor, session: OrtSession): String? {
        val input = session.inputInfo[model.generationContract.inputIdsName]?.info as? TensorInfo
            ?: return "Model is missing the declared int64 input_ids tensor."
        if (input.type != OnnxJavaType.INT64 || input.shape.size != 2) return "input_ids must be an int64 tensor with shape [batch, sequence]."
        if (input.shape[0] > 0 && input.shape[0] != 1L) return "Only a batch size of one is supported."
        if (!model.generationContract.supportsDynamicSequenceLength || input.shape[1] > 0) {
            return "This runtime requires a dynamically sized input sequence; fixed-shape ONNX models are unsupported."
        }
        val maskName = model.generationContract.attentionMaskName
        if (maskName != null) {
            val mask = session.inputInfo[maskName]?.info as? TensorInfo
                ?: return "Model is missing the declared attention_mask tensor."
            if (mask.type != OnnxJavaType.INT64 || mask.shape.size != 2) return "attention_mask must be an int64 tensor with shape [batch, sequence]."
            if (mask.shape[0] > 0 && mask.shape[0] != 1L) return "attention_mask batch size must be one."
            if (mask.shape[1] > 0) return "attention_mask must use a dynamically sized sequence dimension."
        }
        val output = session.outputInfo[model.generationContract.logitsOutputName]?.info as? TensorInfo
            ?: return "Model is missing the declared logits output."
        if (output.type != OnnxJavaType.FLOAT || output.shape.size != 3) return "logits must be a float tensor with shape [batch, sequence, vocabulary]."
        if (output.shape[0] > 0 && output.shape[0] != 1L) return "logits batch size must be one."
        if (output.shape[1] > 0) return "logits must use a dynamically sized sequence dimension."
        val outputVocab = output.shape[2]
        if (outputVocab > 0 && outputVocab != model.vocabularySize.toLong()) return "Model output vocabulary does not match the tokenizer metadata."
        return null
    }

    private fun nextToken(model: LoadedModel, ids: IntArray): Int {
        val logits = runLogits(model.session, model.descriptor, ids)
        if (logits.size != model.descriptor.vocabularySize) throw IllegalStateException("ONNX logits width does not match tokenizer vocabulary.")
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        logits.forEachIndexed { index, value ->
            if (value.isFinite() && value > bestValue) {
                bestValue = value
                bestIndex = index
            }
        }
        if (!bestValue.isFinite()) throw IllegalStateException("ONNX model produced non-finite logits.")
        return bestIndex
    }

    private fun runLogits(session: OrtSession, model: ModelDescriptor, ids: IntArray): FloatArray {
        val environment = OrtEnvironment.getEnvironment()
        val longIds = LongArray(ids.size) { ids[it].toLong() }
        val shape = longArrayOf(1, ids.size.toLong())
        val inputTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(longIds), shape)
        val attention = if (model.generationContract.attentionMaskName != null) {
            OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape)
        } else null
        try {
            val feeds = linkedMapOf<String, OnnxTensor>(model.generationContract.inputIdsName to inputTensor)
            if (attention != null) feeds[model.generationContract.attentionMaskName!!] = attention
            session.run(feeds).use { result ->
                val outputIndex = session.outputInfo.keys.toList().indexOf(model.generationContract.logitsOutputName)
                if (outputIndex < 0) throw IllegalStateException("Declared logits output is missing.")
                val tensor = result.get(outputIndex) as? OnnxTensor ?: throw IllegalStateException("Logits output is not a tensor.")
                val info = tensor.info as? TensorInfo ?: throw IllegalStateException("Logits output metadata is invalid.")
                if (info.type != OnnxJavaType.FLOAT || info.shape.size != 3) throw IllegalStateException("Logits output has an unsupported tensor type/shape.")
                val sequenceLength = info.shape[1].toInt()
                val vocabularySize = info.shape[2].toInt()
                if (sequenceLength <= 0 || vocabularySize != model.vocabularySize) throw IllegalStateException("Logits dimensions are invalid.")
                val buffer = tensor.floatBuffer ?: throw IllegalStateException("Runtime did not expose float logits.")
                val start = (sequenceLength - 1) * vocabularySize
                return FloatArray(vocabularySize) { offset -> buffer.get(start + offset) }
            }
        } finally {
            inputTensor.close()
            attention?.close()
        }
    }

    private fun measure(session: OrtSession, model: ModelDescriptor, sample: IntArray): List<Double> {
        repeat(BENCHMARK_WARMUPS) { runLogits(session, model, sample) }
        return List(BENCHMARK_RUNS) {
            val start = System.nanoTime()
            runLogits(session, model, sample)
            TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start) / 1000.0
        }
    }

    private fun validateDescriptor(model: ModelDescriptor): String? {
        if (!model.modelFile.isFile || !model.tokenizerFile.isFile) return "The decrypted model/tokenizer files are missing."
        if (model.temporaryFiles.any { file ->
                val root = runCatching { temporaryArtifactRoot.canonicalFile.path }.getOrNull() ?: return@any true
                val path = runCatching { file.canonicalFile.path }.getOrNull() ?: return@any true
                !path.startsWith(root + java.io.File.separator)
            }) return "Temporary model files are outside the app-private model cache."
        if (model.modelFile.length() !in 1..MAX_MODEL_BYTES) return "ONNX model size is outside supported bounds."
        if (model.tokenizerFile.length() !in 1..MAX_TOKENIZER_BYTES) return "Tokenizer size is outside supported bounds."
        if (model.contextLength !in 32..MAX_CONTEXT_TOKENS) return "Model context length is outside supported bounds."
        if (model.vocabularySize !in 2..MAX_VOCABULARY_SIZE) return "Model vocabulary size is outside supported bounds."
        val modelHash = runCatching { FileInputStream(model.modelFile).use { Hashing.sha256(it, MAX_MODEL_BYTES) } }.getOrNull()
        if (!modelHash.equals(model.modelSha256, ignoreCase = true)) return "ONNX model SHA-256 check failed."
        val tokenizerHash = runCatching { FileInputStream(model.tokenizerFile).use { Hashing.sha256(it, MAX_TOKENIZER_BYTES) } }.getOrNull()
        if (!tokenizerHash.equals(model.tokenizerSha256, ignoreCase = true)) return "Tokenizer SHA-256 check failed."
        if (model.license.isBlank() || model.source.isBlank()) return "Model license and provenance are required."
        return null
    }

    private fun closeLoaded() {
        val current = loaded ?: return
        loaded = null
        runCatching { current.session.close() }
        runCatching { current.tokenizer.close() }
        deleteTemporaryFiles(current.descriptor.temporaryFiles)
    }

    private fun deleteTemporaryFiles(files: List<java.io.File>) {
        val root = runCatching { temporaryArtifactRoot.canonicalFile }.getOrNull() ?: return
        files.forEach { file ->
            runCatching {
                val canonical = file.canonicalFile
                if (canonical.path.startsWith(root.path + java.io.File.separator)) canonical.delete()
            }
        }
    }

    private fun median(values: List<Double>): Double = values.sorted().let { sorted -> sorted[sorted.size / 2] }
    private fun unsupportedBenchmark(reason: String) = ProviderBenchmarkResult("NNAPI", false, false, null, null, null, reason)

    private data class LoadedModel(
        val descriptor: ModelDescriptor,
        val session: OrtSession,
        val tokenizer: TokenizerHandle,
        val runtimeName: String
    )

    companion object {
        private const val MAX_PROMPT_CHARS = 20_000
        private const val MAX_GENERATED_TOKENS = 512
        private const val MAX_CONTEXT_TOKENS = 8_192
        private const val MAX_VOCABULARY_SIZE = 250_000
        private const val MAX_TOKENIZER_BYTES = 50L * 1024 * 1024
        private const val MAX_MODEL_BYTES = 2L * 1024 * 1024 * 1024
        private const val BENCHMARK_WARMUPS = 2
        private const val BENCHMARK_RUNS = 5
        private const val DEFAULT_MAX_ABSOLUTE_DIFFERENCE = 0.02f
        private const val MIN_SPEEDUP_RATIO = 1.10
    }
}
