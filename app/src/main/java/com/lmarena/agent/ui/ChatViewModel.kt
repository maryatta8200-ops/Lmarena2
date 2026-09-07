package com.lmarena.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lmarena.agent.data.ChatMessage
import com.lmarena.agent.data.LmArenaApi
import com.lmarena.agent.data.ModelInfo
import com.lmarena.agent.data.Settings
import com.lmarena.agent.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val settings: Settings,
    val messages: List<ChatMessage> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null,
    val showSettings: Boolean = false
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val api = LmArenaApi()

    private val _uiState = MutableStateFlow(UiState(settings = store.load()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Auto-load the model list on first launch so the user can pick a model immediately.
        refreshModels()
    }

    fun refreshModels() {
        viewModelScope.launch {
            val s = _uiState.value.settings
            runCatching { api.listModels(s.baseUrl) }
                .onSuccess { list ->
                    val current = _uiState.value.settings.model
                    val selected = if (list.any { it.id == current }) current
                    else list.firstOrNull()?.id ?: current
                    _uiState.value = _uiState.value.copy(
                        models = list,
                        settings = _uiState.value.settings.copy(model = selected)
                    )
                    persistSettings()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Unable to load models. Check the base URL."
                    )
                }
        }
    }

    fun selectModel(model: String) {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(model = model)
        )
        persistSettings()
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        if (text.isBlank() || state.streaming) return

        val userMsg = ChatMessage("user", text.trim())
        val conversation = state.messages + userMsg

        _uiState.value = state.copy(
            messages = conversation,
            streaming = true,
            error = null
        )

        viewModelScope.launch {
            val s = _uiState.value.settings
            val assistantMsg = ChatMessage("assistant", "")
            _uiState.value = _uiState.value.copy(messages = conversation + assistantMsg)

            val accumulated = StringBuilder()
            val history = conversation // includes the just-added user message

            try {
                api.streamChat(
                    baseUrl = s.baseUrl,
                    apiKey = s.apiKey,
                    model = s.model,
                    messages = history,
                    systemPrompt = s.persona,
                    temperature = s.temperature
                ).collect { token ->
                    accumulated.append(token)
                    val msgs = _uiState.value.messages.toMutableList()
                    if (msgs.isNotEmpty()) {
                        msgs[msgs.lastIndex] = msgs.last().copy(content = accumulated.toString())
                        _uiState.value = _uiState.value.copy(messages = msgs)
                    }
                }
                _uiState.value = _uiState.value.copy(streaming = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    streaming = false,
                    error = e.message ?: "Request failed"
                )
            }
        }
    }

    fun setPersona(persona: String) {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(persona = persona)
        )
        persistSettings()
    }

    fun toggleAgentMode() {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(agentMode = !_uiState.value.settings.agentMode)
        )
        persistSettings()
    }

    fun clearConversation() {
        _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
    }

    fun saveSettings(baseUrl: String, apiKey: String, temperature: Double, agentMode: Boolean) {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                temperature = temperature,
                agentMode = agentMode
            ),
            error = null
        )
        persistSettings()
        refreshModels()
    }

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
    }

    private fun persistSettings() {
        store.save(_uiState.value.settings)
    }
}
