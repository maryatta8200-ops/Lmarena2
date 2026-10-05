package com.localmed.ai.model

import com.localmed.core.common.Hashing
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestSignatureTest {
    @Test
    fun signatureCoversTokenizerArchitectureAndProvenanceMetadata() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val unsigned = testManifest(signatureBase64 = "")
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(unsigned.signingPayload())
        val signed = unsigned.copy(signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()))
        val encodedPublicKey = keyPair.public.encoded
        val publisher = TrustedPublisher(
            keyId = "publisher-key-1",
            publicKeyX509Base64 = Base64.getEncoder().encodeToString(encodedPublicKey),
            fingerprintSha256 = Hashing.sha256(encodedPublicKey),
            displayName = "Test key",
            trustGrantedAtEpochMs = 1L
        )

        assertTrue(ModelManifestValidator.validate(signed).valid)
        assertTrue(ModelSignatureVerifier.verify(signed, publisher))
        assertFalse(ModelSignatureVerifier.verify(signed.copy(source = "changed provenance"), publisher))
        assertFalse(ModelSignatureVerifier.verify(signed.copy(tokenizer = signed.tokenizer.copy(normalizationDescription = "changed")), publisher))
    }

    @Test
    fun manifestParserIsStrictAndEnforcesItsSizeBound() {
        val manifest = testManifest(signatureBase64 = Base64.getEncoder().encodeToString(ByteArray(256)))
        val encoded = Json.encodeToString(manifest)
        assertEquals(manifest, ModelManifest.parse(encoded))
        assertThrows(IllegalArgumentException::class.java) { ModelManifest.parse(" ") }
        assertThrows(IllegalArgumentException::class.java) { ModelManifest.parse(encoded.dropLast(1) + ",\"unexpected\":true}") }
        assertThrows(IllegalArgumentException::class.java) { ModelManifest.parse(" ".repeat(100_001)) }
    }

    private fun testManifest(signatureBase64: String) = ModelManifest(
        schemaVersion = 1,
        modelId = "research-transformer",
        modelVersion = "1.0.0",
        format = "onnx",
        quantization = "int8",
        architecture = ArchitectureMetadata(
            kind = "decoder-only-transformer",
            contextLength = 2048,
            vocabularySize = 32_000,
            hiddenSize = 1024,
            layerCount = 12,
            attentionHeads = 16
        ),
        tokenizer = TokenizerMetadata("example-tokenizer", "1", 32_000, "documented test fixture"),
        modelSha256 = "a".repeat(64),
        tokenizerSha256 = "b".repeat(64),
        source = "test fixture only",
        license = "CC0-1.0",
        createdAt = "2026-10-05T12:00:00Z",
        publisherKeyId = "publisher-key-1",
        signatureAlgorithm = "SHA256withRSA",
        signatureBase64 = signatureBase64
    )
}
