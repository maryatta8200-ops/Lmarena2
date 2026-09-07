package com.lmarena.agent.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ModelInfo(val id: String)

data class ChatMessage(val role: String, val content: String)

/**
 * OpenAI-compatible client used to reach an LMArena backend.
 *
 * The LMArena website itself does not expose a stable public API; the community standard is
 * to front it with an OpenAI-compatible bridge (e.g. lmarena2api / LMArenaBridge). This client
 * speaks that OpenAI-compatible protocol (GET /models, POST /chat/completions with SSE), so it
 * works with any such bridge, a self-hosted proxy, or a gateway that exposes LMArena models.
 */
class LmArenaApi {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Fetches the list of available models from the backend. */
    fun listModels(baseUrl: String): List<ModelInfo> {
        val url = normalizeBase(baseUrl) + "/models"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("Empty response body")
            val root = runCatching { JsonParser.parseString(body).asJsonObject }
                .getOrElse { throw IOException("Malformed models response") }
            val data = root.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            return buildList {
                for (el in data) {
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.asString
                    if (!id.isNullOrBlank()) add(ModelInfo(id))
                }
            }
        }
    }

    /**
     * Streams a chat completion token-by-token as a cold [Flow] of text deltas.
     * Uses the OpenAI Server-Sent Events (SSE) protocol.
     */
    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Double
    ): Flow<String> = callbackFlow {
        val url = normalizeBase(baseUrl) + "/chat/completions"
        val body = buildRequestBody(model, messages, systemPrompt, temperature)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .header("Accept", "text/event-stream")
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val delta = extractContent(data)
                if (delta != null && delta.isNotEmpty()) trySend(delta)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = response?.let { "HTTP ${it.code}" } ?: (t?.message ?: "Connection failed")
                close(IOException(msg))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(requestBuilder.build(), listener)
        awaitClose { eventSource.cancel() }
    }

    private fun normalizeBase(baseUrl: String): String = baseUrl.trimEnd('/')

    private fun extractContent(data: String): String? {
        if (data == "[DONE]") return null
        return try {
            val root = JsonParser.parseString(data).asJsonObject
            val choices = root.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            if (choices.size() == 0) return null
            val choice = choices[0].asJsonObject
            val delta = choice.get("delta")?.takeIf { it.isJsonObject }?.asJsonObject
            val content = delta?.get("content")?.asString
            if (content != null) return content
            // Some local tools return the full message rather than deltas.
            val message = choice.get("message")?.takeIf { it.isJsonObject }?.asJsonObject
            message?.get("content")?.asString
        } catch (e: Exception) {
            null
        }
    }

    private fun buildRequestBody(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Double
    ): JsonObject {
        val root = JsonObject()
        root.addProperty("model", model)
        root.addProperty("stream", true)
        root.addProperty("temperature", temperature)
        root.addProperty("max_tokens", 4096)

        val arr = JsonArray()
        if (systemPrompt.isNotBlank()) {
            val sys = JsonObject()
            sys.addProperty("role", "system")
            sys.addProperty("content", systemPrompt)
            arr.add(sys)
        }
        for (m in messages) {
            val o = JsonObject()
            o.addProperty("role", m.role)
            o.addProperty("content", m.content)
            arr.add(o)
        }
        root.add("messages", arr)
        return root
    }
}
