package com.relaymessages.data

import kotlin.math.ln
import kotlin.math.sqrt

/** A user-provided inbound-message/reply pair stored only on this device. */
data class TrainingExample(
    val intent: String,
    val input: String,
    val reply: String
)

data class ReplySuggestion(
    val intent: String,
    val reply: String,
    /** A conservative local match score in the range 0.0..1.0, not a calibrated probability. */
    val confidence: Double,
    val matchedInput: String
)

fun interface ReplySuggestionEngine {
    fun suggest(message: String): ReplySuggestion?
}

/**
 * Small offline learner for private SMS use.
 *
 * It combines an intent-level multinomial Naive Bayes classifier with TF-IDF cosine retrieval.
 * The returned text is always copied from a training example: the engine never generates new
 * claims or calls a remote model. Weak matches are rejected rather than guessed.
 */
class LocalLearningEngine(trainingExamples: List<TrainingExample>) : ReplySuggestionEngine {
    private val examples = trainingExamples.filter {
        it.intent.isNotBlank() && it.input.isNotBlank() && it.reply.isNotBlank()
    }
    private val documents = examples.map { tokenize(it.input) }
    private val documentFrequency: Map<String, Int> = buildMap {
        documents.forEach { tokens ->
            tokens.distinct().forEach { token -> put(token, (get(token) ?: 0) + 1) }
        }
    }
    private val vocabulary = documentFrequency.keys
    private val intentModels: Map<String, IntentModel> = trainIntentModels()

    /** Return a saved reply only when at least one meaningful term overlaps. */
    override fun suggest(message: String): ReplySuggestion? = suggest(message, DEFAULT_MINIMUM_SIMILARITY)

    fun suggest(message: String, minimumSimilarity: Double): ReplySuggestion? {
        if (examples.isEmpty()) return null
        val queryTokens = tokenize(message)
        if (queryTokens.isEmpty()) return null
        val queryCounts = termCounts(queryTokens)
        val queryVector = tfIdfVector(queryCounts)

        var bestIndex = -1
        var bestSimilarity = 0.0
        documents.forEachIndexed { index, tokens ->
            val similarity = cosine(queryVector, tfIdfVector(termCounts(tokens)))
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = index
            }
        }
        if (bestIndex < 0 || bestSimilarity < minimumSimilarity) return null

        val example = examples[bestIndex]
        val posterior = posteriorFor(example.intent, queryTokens)
        // Similarity dominates; the classifier only provides a modest class-consistency signal.
        val confidence = (0.80 * bestSimilarity + 0.20 * posterior).coerceIn(0.0, 1.0)
        return ReplySuggestion(
            intent = example.intent,
            reply = example.reply,
            confidence = confidence,
            matchedInput = example.input
        )
    }

    private fun trainIntentModels(): Map<String, IntentModel> {
        if (examples.isEmpty()) return emptyMap()
        val grouped = examples.indices.groupBy { examples[it].intent }
        return grouped.mapValues { (_, indexes) ->
            val counts = HashMap<String, Int>()
            var totalTerms = 0
            indexes.forEach { index ->
                documents[index].forEach { token ->
                    counts[token] = (counts[token] ?: 0) + 1
                    totalTerms++
                }
            }
            IntentModel(
                documentCount = indexes.size,
                termCounts = counts,
                totalTerms = totalTerms
            )
        }
    }

    private fun posteriorFor(intent: String, tokens: List<String>): Double {
        if (intentModels.isEmpty()) return 0.0
        val totalDocuments = examples.size.toDouble()
        val classCount = intentModels.size.toDouble()
        val vocabularySize = (vocabulary.size + 1).toDouble()
        val logScores = intentModels.mapValues { (_, model) ->
            var score = ln((model.documentCount + 1.0) / (totalDocuments + classCount))
            tokens.forEach { token ->
                val count = model.termCounts[token] ?: 0
                score += ln((count + 1.0) / (model.totalTerms + vocabularySize))
            }
            score
        }
        val maxLog = logScores.values.maxOrNull() ?: return 0.0
        val exponents = logScores.mapValues { (_, value) -> kotlin.math.exp(value - maxLog) }
        val denominator = exponents.values.sum().takeIf { it > 0.0 } ?: return 0.0
        return (exponents[intent] ?: 0.0) / denominator
    }

    private fun tfIdfVector(counts: Map<String, Int>): Map<String, Double> {
        if (counts.isEmpty()) return emptyMap()
        val documentCount = examples.size.toDouble()
        return counts.mapValues { (token, count) ->
            val df = documentFrequency[token] ?: 0
            val idf = ln((documentCount + 1.0) / (df + 1.0)) + 1.0
            (1.0 + ln(count.toDouble())) * idf
        }
    }

    private fun cosine(left: Map<String, Double>, right: Map<String, Double>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val dot = left.entries.sumOf { (token, weight) -> weight * (right[token] ?: 0.0) }
        if (dot <= 0.0) return 0.0
        val leftNorm = sqrt(left.values.sumOf { it * it })
        val rightNorm = sqrt(right.values.sumOf { it * it })
        if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
        return (dot / (leftNorm * rightNorm)).coerceIn(0.0, 1.0)
    }

    private fun termCounts(tokens: List<String>): Map<String, Int> = tokens.groupingBy { it }.eachCount()

    private fun tokenize(text: String): List<String> = TOKEN_REGEX.findAll(text.lowercase())
        .map { it.value }
        .filter { it.length > 1 && it !in STOP_WORDS }
        .toList()

    private data class IntentModel(
        val documentCount: Int,
        val termCounts: Map<String, Int>,
        val totalTerms: Int
    )

    companion object {
        const val DEFAULT_MINIMUM_SIMILARITY = 0.16
        const val AUTO_REPLY_MINIMUM_CONFIDENCE = 0.78

        private val TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
        private val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "could",
            "did", "do", "does", "for", "from", "had", "has", "have", "he", "her",
            "here", "hers", "him", "his", "how", "i", "if", "in", "into", "is", "it",
            "its", "me", "my", "of", "on", "or", "our", "ours", "she", "so", "that",
            "the", "their", "them", "there", "they", "this", "to", "us", "was", "we",
            "were", "what", "when", "where", "which", "who", "will", "with", "would",
            "you", "your", "yours", "am"
        )
    }
}
