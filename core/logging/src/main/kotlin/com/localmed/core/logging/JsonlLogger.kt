package com.localmed.core.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID

/** No method accepts message bodies. Caller data is redacted before writing. */
enum class LogSeverity { DEBUG, INFO, WARN, ERROR }

data class StructuredLogEvent(
    val correlationId: String = UUID.randomUUID().toString(),
    val timestamp: Instant = Instant.now(),
    val module: String,
    val severity: LogSeverity,
    val event: String,
    val modelVersion: String? = null,
    val fields: Map<String, String> = emptyMap()
)

fun interface StructuredLogger { fun log(event: StructuredLogEvent) }

class RotatingJsonlLogger(
    private val directory: File,
    private val maxFileBytes: Long = 1L * 1024 * 1024,
    private val filesToKeep: Int = 3
) : StructuredLogger {
    private val lock = Any()
    private val json = Json { prettyPrint = false }

    init {
        require(maxFileBytes in 16 * 1024..10L * 1024 * 1024)
        require(filesToKeep in 2..10)
        directory.mkdirs()
    }

    override fun log(event: StructuredLogEvent) {
        if (!EVENT.matches(event.event) || !MODULE.matches(event.module)) return
        val line = buildJsonObject {
            put("ts", event.timestamp.toString())
            put("correlation_id", event.correlationId.take(64))
            put("module", event.module.take(80))
            put("severity", event.severity.name)
            put("event", event.event.take(100))
            event.modelVersion?.let { put("model_version", it.take(80)) }
            put("fields", buildJsonObject {
                event.fields.entries.take(32).forEach { (key, value) ->
                    if (SAFE_FIELD.matches(key)) put(key, redact(key, value).take(256))
                }
            })
        }.let(json::encodeToString) + "\n"
        synchronized(lock) {
            rotateIfNeeded(line.toByteArray(Charsets.UTF_8).size)
            runCatching { logFile().appendText(line, Charsets.UTF_8) }
        }
    }

    private fun redact(key: String, value: String): String {
        if (SENSITIVE_KEY.containsMatchIn(key)) return "[REDACTED]"
        return value
            .replace(EMAIL, "[EMAIL]")
            .replace(PHONE, "[PHONE]")
            .replace(IDENTIFIER, "[IDENTIFIER]")
            .takeIf { it.length <= 256 } ?: "[TRUNCATED]"
    }

    private fun rotateIfNeeded(incomingBytes: Int) {
        val active = logFile()
        if (!active.exists() || active.length() + incomingBytes <= maxFileBytes) return
        for (index in filesToKeep - 1 downTo 1) {
            val source = File(directory, "events.$index.jsonl")
            val target = File(directory, "events.${index + 1}.jsonl")
            if (source.exists()) {
                if (index + 1 >= filesToKeep) target.delete()
                source.renameTo(target)
            }
        }
        val first = File(directory, "events.1.jsonl")
        active.renameTo(first)
    }

    private fun logFile() = File(directory, "events.jsonl")

    companion object {
        private val EVENT = Regex("[A-Za-z0-9_.-]{1,100}")
        private val MODULE = Regex("[A-Za-z0-9_.:-]{1,80}")
        private val SAFE_FIELD = Regex("[a-z][a-z0-9_]{0,40}")
        private val SENSITIVE_KEY = Regex("(?i)(text|message|prompt|phone|email|patient|medical|token|contact|identifier|content|input|output)")
        private val EMAIL = Regex("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", RegexOption.IGNORE_CASE)
        private val PHONE = Regex("(?<!\\w)(?:\\+?\\d[ .()-]?){7,15}(?!\\w)")
        private val IDENTIFIER = Regex("(?i)\\b(?:mrn|patient\\s*id|national\\s*id)[:# ]+[A-Z0-9-]{3,30}\\b")
    }
}
