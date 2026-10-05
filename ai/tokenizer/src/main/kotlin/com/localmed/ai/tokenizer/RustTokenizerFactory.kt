package com.localmed.ai.tokenizer

import com.localmed.ai.api.TokenizerFactory
import com.localmed.ai.api.TokenizerHandle

class RustTokenizerFactory : TokenizerFactory {
    fun isNativeAvailable(): Boolean = runCatching { NativeTokenizerBridge.ensureLoaded() }.isSuccess

    override fun open(tokenizerJson: ByteArray, expectedVocabularySize: Int): TokenizerHandle {
        require(tokenizerJson.isNotEmpty() && tokenizerJson.size <= MAX_TOKENIZER_BYTES) {
            "Tokenizer artifact is empty or larger than the configured limit."
        }
        require(expectedVocabularySize in 2..MAX_VOCABULARY_SIZE) { "Unsupported vocabulary size." }
        NativeTokenizerBridge.ensureLoaded()
        val handle = NativeTokenizerBridge.nativeCreateTokenizer(tokenizerJson)
        if (handle == 0L) throw IllegalStateException("Rust tokenizer could not be opened.")
        val actualVocabularySize = try {
            NativeTokenizerBridge.nativeGetVocabularySize(handle)
        } catch (exception: Exception) {
            NativeTokenizerBridge.nativeDestroyTokenizer(handle)
            throw exception
        }
        if (actualVocabularySize != expectedVocabularySize) {
            NativeTokenizerBridge.nativeDestroyTokenizer(handle)
            throw IllegalArgumentException(
                "Tokenizer vocabulary size $actualVocabularySize does not match model metadata $expectedVocabularySize."
            )
        }
        return RustTokenizerHandle(handle, actualVocabularySize)
    }

    private class RustTokenizerHandle(
        private var handle: Long,
        override val vocabularySize: Int
    ) : TokenizerHandle {
        @Synchronized
        override fun encode(text: String, maxTokens: Int): IntArray {
            check(handle != 0L) { "Tokenizer is closed." }
            require(text.length <= MAX_INPUT_CHARS) { "Tokenizer input exceeds the configured limit." }
            require(maxTokens in 1..MAX_CONTEXT_TOKENS) { "maxTokens is outside the configured range." }
            return NativeTokenizerBridge.nativeEncode(handle, text, maxTokens)
        }

        @Synchronized
        override fun decode(tokenIds: IntArray): String {
            check(handle != 0L) { "Tokenizer is closed." }
            require(tokenIds.size <= MAX_CONTEXT_TOKENS) { "Token list exceeds the configured limit." }
            return NativeTokenizerBridge.nativeDecode(handle, tokenIds)
        }

        @Synchronized
        override fun close() {
            if (handle != 0L) {
                NativeTokenizerBridge.nativeDestroyTokenizer(handle)
                handle = 0L
            }
        }
    }

    companion object {
        private const val MAX_TOKENIZER_BYTES = 50 * 1024 * 1024
        private const val MAX_VOCABULARY_SIZE = 250_000
        private const val MAX_INPUT_CHARS = 40_000
        private const val MAX_CONTEXT_TOKENS = 8_192
    }
}

internal object NativeTokenizerBridge {
    @Volatile private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        System.loadLibrary("relay_ai_core")
        loaded = true
    }

    external fun nativeCreateTokenizer(tokenizerJson: ByteArray): Long
    external fun nativeGetVocabularySize(handle: Long): Int
    external fun nativeEncode(handle: Long, text: String, maxTokens: Int): IntArray
    external fun nativeDecode(handle: Long, tokenIds: IntArray): String
    external fun nativeDestroyTokenizer(handle: Long)
}
