package com.lmarena.agent.data

import android.content.Context
import android.content.SharedPreferences

/**
 * User-configurable connection & behaviour settings.
 *
 * The backend is an OpenAI-compatible endpoint that talks to LMArena (for example an
 * LMArena bridge / proxy). The base URL is pre-filled with an LMArena-style default;
 * users point it at their chosen bridge or at a self-hosted proxy.
 */
data class Settings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val persona: String,
    val agentMode: Boolean,
    val temperature: Double
)

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lmarena_agent_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        return Settings(
            baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = prefs.getString(KEY_MODEL, "") ?: "",
            persona = prefs.getString(KEY_PERSONA, DEFAULT_PERSONA) ?: DEFAULT_PERSONA,
            agentMode = prefs.getBoolean(KEY_AGENT_MODE, true),
            temperature = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE.toFloat()).toDouble()
        )
    }

    fun save(settings: Settings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_MODEL, settings.model)
            .putString(KEY_PERSONA, settings.persona)
            .putBoolean(KEY_AGENT_MODE, settings.agentMode)
            .putFloat(KEY_TEMPERATURE, settings.temperature.toFloat())
            .apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.lmarena.ai/v1"
        const val DEFAULT_PERSONA = "You are a capable, helpful AI agent. You reason step by step, ask clarifying questions when needed, and produce clear, well-structured answers."
        const val DEFAULT_TEMPERATURE = 0.7

        // Built-in agent personas that users can quickly switch between.
        val AGENT_MODES = listOf(
            "Assistant" to "You are a capable, helpful AI agent. You reason step by step and produce clear, well-structured answers.",
            "Coder" to "You are an expert software engineer and pair-programming agent. You write correct, idiomatic code, explain your reasoning, and call out edge cases.",
            "Analyst" to "You are a meticulous data & research analyst agent. You evaluate evidence, quantify uncertainty, and present conclusions with caveats.",
            "Writer" to "You are a professional writing agent. You write in a clear, engaging, well-organized style and improve drafts on request."
        )

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_PERSONA = "persona"
        private const val KEY_AGENT_MODE = "agent_mode"
        private const val KEY_TEMPERATURE = "temperature"
    }
}
