package com.localmed.ai.model

import com.localmed.core.common.Hashing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Serializable
data class ModelManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("model_id") val modelId: String,
    @SerialName("model_version") val modelVersion: String,
    val format: String,
    val quantization: String,
    @SerialName("architecture") val architecture: ArchitectureMetadata,
    @SerialName("tokenizer") val tokenizer: TokenizerMetadata,
    @SerialName("model_sha256") val modelSha256: String,
    @SerialName("tokenizer_sha256") val tokenizerSha256: String,
    @SerialName("model_file") val modelFile: String = MODEL_FILE,
    @SerialName("tokenizer_file") val tokenizerFile: String = TOKENIZER_FILE,
    val source: String,
    val license: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("publisher_key_id") val publisherKeyId: String,
    @SerialName("signature_algorithm") val signatureAlgorithm: String,
    @SerialName("signature_base64") val signatureBase64: String
) {
    /** Stable newline-delimited representation signed by the publisher; excludes the signature itself. */
    fun signingPayload(): ByteArray = listOf(
        "localmed-model-bundle-v1",
        schemaVersion.toString(), modelId, modelVersion, format, quantization,
        modelFile, modelSha256.lowercase(), tokenizerFile, tokenizerSha256.lowercase(),
        tokenizer.id, tokenizer.version, tokenizer.vocabularySize.toString(), tokenizer.normalizationDescription,
        architecture.kind, architecture.contextLength.toString(), architecture.vocabularySize.toString(),
        architecture.hiddenSize.toString(), architecture.layerCount.toString(), architecture.attentionHeads.toString(),
        architecture.inputIdsName, architecture.attentionMaskName.orEmpty(), architecture.logitsOutputName,
        architecture.endOfSequenceTokenIds.sorted().joinToString(","), architecture.dynamicSequenceLength.toString(),
        source, license, createdAt, publisherKeyId, signatureAlgorithm
    ).joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)

    fun storageId(): String {
        val base = "${modelId}-${modelVersion}".replace(Regex("[^A-Za-z0-9._-]"), "-").take(80)
        val suffix = Hashing.sha256(signingPayload()).take(16)
        return "$base-$suffix"
    }

    companion object {
        const val MODEL_FILE = "model.onnx"
        const val TOKENIZER_FILE = "tokenizer.json"
        private const val MAX_MANIFEST_CHARS = 100_000
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false; explicitNulls = false }

        fun parse(json: String): ModelManifest {
            require(json.isNotBlank() && json.length <= MAX_MANIFEST_CHARS) { "Model manifest is empty or exceeds the configured size limit." }
            return JSON.decodeFromString(json)
        }
    }

}

@Serializable
data class ArchitectureMetadata(
    val kind: String,
    @SerialName("context_length") val contextLength: Int,
    @SerialName("vocabulary_size") val vocabularySize: Int,
    @SerialName("hidden_size") val hiddenSize: Int,
    @SerialName("layer_count") val layerCount: Int,
    @SerialName("attention_heads") val attentionHeads: Int,
    @SerialName("input_ids_name") val inputIdsName: String = "input_ids",
    @SerialName("attention_mask_name") val attentionMaskName: String? = "attention_mask",
    @SerialName("logits_output_name") val logitsOutputName: String = "logits",
    @SerialName("end_of_sequence_token_ids") val endOfSequenceTokenIds: List<Int> = emptyList(),
    @SerialName("dynamic_sequence_length") val dynamicSequenceLength: Boolean = true
)

@Serializable
data class TokenizerMetadata(
    val id: String,
    val version: String,
    @SerialName("vocabulary_size") val vocabularySize: Int,
    @SerialName("normalization_description") val normalizationDescription: String
)

@Serializable
data class TrustedPublisher(
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key_x509_base64") val publicKeyX509Base64: String,
    val fingerprintSha256: String,
    val displayName: String,
    val trustGrantedAtEpochMs: Long
)

data class ManifestValidation(val valid: Boolean, val errors: List<String>)

object ModelManifestValidator {
    fun validate(manifest: ModelManifest): ManifestValidation {
        val errors = mutableListOf<String>()
        if (manifest.schemaVersion != 1) errors += "Unsupported model manifest schema version."
        if (!ID.matches(manifest.modelId)) errors += "model_id is not a stable identifier."
        if (!VERSION.matches(manifest.modelVersion)) errors += "model_version is invalid."
        if (manifest.format != "onnx") errors += "Only ONNX model artifacts are supported by this runtime."
        if (!QUANTIZATION.matches(manifest.quantization)) errors += "quantization is missing or invalid."
        if (manifest.modelFile != ModelManifest.MODEL_FILE) errors += "Model path must be ${ModelManifest.MODEL_FILE}."
        if (manifest.tokenizerFile != ModelManifest.TOKENIZER_FILE) errors += "Tokenizer path must be ${ModelManifest.TOKENIZER_FILE}."
        if (!Hashing.isSha256(manifest.modelSha256)) errors += "model_sha256 must be a 64-character hexadecimal SHA-256."
        if (!Hashing.isSha256(manifest.tokenizerSha256)) errors += "tokenizer_sha256 must be a 64-character hexadecimal SHA-256."
        if (manifest.source.isBlank() || manifest.source.length > 500 || hasControlChars(manifest.source)) errors += "A bounded source/provenance field is required."
        if (manifest.license.isBlank() || manifest.license.length > 200 || hasControlChars(manifest.license)) errors += "A model license is required."
        if (runCatching { java.time.Instant.parse(manifest.createdAt) }.isFailure) errors += "created_at must be an ISO-8601 UTC timestamp."
        if (!KEY_ID.matches(manifest.publisherKeyId)) errors += "publisher_key_id is invalid."
        if (manifest.signatureAlgorithm != "SHA256withRSA") errors += "Only SHA256withRSA signatures are currently supported."
        if (runCatching { Base64.getDecoder().decode(manifest.signatureBase64) }.getOrNull()?.size !in 128..1024) errors += "signature_base64 is malformed."

        val a = manifest.architecture
        if (a.kind != "decoder-only-transformer") errors += "Unsupported architecture kind."
        if (a.contextLength !in 32..8192) errors += "context_length must be between 32 and 8192."
        if (a.vocabularySize !in 2..250_000) errors += "vocabulary_size is outside the supported range."
        if (a.hiddenSize !in 1..8192 || a.layerCount !in 1..128 || a.attentionHeads !in 1..128) errors += "Architecture dimensions are outside supported bounds."
        if (a.attentionHeads > 0 && a.hiddenSize % a.attentionHeads != 0) errors += "hidden_size must be divisible by attention_heads."
        if (a.vocabularySize != manifest.tokenizer.vocabularySize) errors += "Architecture and tokenizer vocabulary sizes differ."
        if (!TENSOR_NAME.matches(a.inputIdsName) || !TENSOR_NAME.matches(a.logitsOutputName)) errors += "ONNX input/output tensor names are invalid."
        if (a.attentionMaskName != null && !TENSOR_NAME.matches(a.attentionMaskName)) errors += "attention_mask tensor name is invalid."
        if (a.endOfSequenceTokenIds.size > 16 || a.endOfSequenceTokenIds.distinct().size != a.endOfSequenceTokenIds.size || a.endOfSequenceTokenIds.any { it !in 0 until a.vocabularySize }) errors += "End-of-sequence token IDs are invalid."
        if (!TOKENIZER_ID.matches(manifest.tokenizer.id) || !TOKENIZER_ID.matches(manifest.tokenizer.version)) errors += "Tokenizer ID and version are invalid."
        if (manifest.tokenizer.normalizationDescription.isBlank() || manifest.tokenizer.normalizationDescription.length > 1_000 || hasControlChars(manifest.tokenizer.normalizationDescription)) errors += "Tokenizer normalization rules must be described in a bounded single-line field."

        return ManifestValidation(errors.isEmpty(), errors.distinct())
    }

    private fun hasControlChars(value: String): Boolean = value.any { Character.isISOControl(it) }

    private val ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._+:-]{0,63}")
    private val QUANTIZATION = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")
    private val KEY_ID = Regex("[A-Za-z0-9._:-]{1,128}")
    private val TENSOR_NAME = Regex("[A-Za-z_][A-Za-z0-9._/-]{0,127}")
    private val TOKENIZER_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:+-]{0,127}")
}

object ModelSignatureVerifier {
    fun verify(manifest: ModelManifest, trustedPublisher: TrustedPublisher): Boolean {
        if (manifest.publisherKeyId != trustedPublisher.keyId || manifest.signatureAlgorithm != "SHA256withRSA") return false
        return runCatching {
            val keyBytes = Base64.getDecoder().decode(trustedPublisher.publicKeyX509Base64)
            if (Hashing.sha256(keyBytes).equals(trustedPublisher.fingerprintSha256, ignoreCase = true).not()) return false
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            if (publicKey !is java.security.interfaces.RSAPublicKey || publicKey.modulus.bitLength() < 2048) return false
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(manifest.signingPayload())
            verifier.verify(Base64.getDecoder().decode(manifest.signatureBase64))
        }.getOrDefault(false)
    }
}
