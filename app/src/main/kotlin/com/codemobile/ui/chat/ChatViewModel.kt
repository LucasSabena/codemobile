package com.codemobile.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemobile.ai.model.AIConfig
import com.codemobile.ai.model.AIMessage
import com.codemobile.ai.model.AIRole
import com.codemobile.ai.model.AIStreamEvent
import com.codemobile.ai.provider.AIProvider
import com.codemobile.ai.provider.AIProviderFactory
import com.codemobile.ai.registry.ProviderRegistry
import com.codemobile.core.data.repository.ProjectRepository
import com.codemobile.core.data.repository.ProviderRepository
import com.codemobile.core.data.repository.SessionRepository
import com.codemobile.core.model.Message
import com.codemobile.core.model.MessageRole
import com.codemobile.core.model.Project
import com.codemobile.core.model.ProviderConfig
import com.codemobile.core.model.Session
import com.codemobile.core.model.SessionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val session: Session? = null,
    val project: Project? = null,
    val messages: List<Message> = emptyList(),
    val providers: List<ProviderConfig> = emptyList(),
    val selectedProvider: ProviderConfig? = null,
    val selectedModelId: String? = null,
    val sessionMode: SessionMode = SessionMode.BUILD,
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val tokenUsage: TokenUsage? = null,
    val error: String? = null,
    val isLoading: Boolean = true,
    val noSession: Boolean = false,
    val hasProviders: Boolean = false
)

data class TokenUsage(val input: Int = 0, val output: Int = 0)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val projectRepository: ProjectRepository,
    private val providerRepository: ProviderRepository,
    private val aiProviderFactory: AIProviderFactory,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: "none"

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProvider: AIProvider? = null
    private var streamingJob: Job? = null

    init {
        loadSession()
        observeProviders()
    }

    private fun loadSession() {
        if (sessionId == "none") {
            _uiState.update { it.copy(isLoading = false, noSession = true) }
            return
        }

        viewModelScope.launch {
            try {
                val session = sessionRepository.getById(sessionId)
                if (session == null) {
                    _uiState.update { it.copy(isLoading = false, noSession = true) }
                    return@launch
                }

                val project = projectRepository.getById(session.projectId)

                _uiState.update {
                    it.copy(
                        session = session,
                        project = project,
                        sessionMode = session.mode,
                        isLoading = false,
                        noSession = false
                    )
                }

                sessionRepository.getMessages(sessionId).collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    private fun observeProviders() {
        viewModelScope.launch {
            providerRepository.getActiveProviders().collect { providers ->
                _uiState.update { state ->
                    val selected = state.selectedProvider ?: providers.firstOrNull()
                    val modelId = selected?.selectedModelId ?: selected?.defaultModelId
                    state.copy(
                        providers = providers,
                        selectedProvider = selected,
                        selectedModelId = modelId,
                        hasProviders = providers.isNotEmpty()
                    )
                }
                // Rebuild AI provider when active providers change
                refreshAIProvider()
            }
        }
    }

    /**
     * Rebuild the AIProvider instance from current selected ProviderConfig.
     */
    private fun refreshAIProvider() {
        val config = _uiState.value.selectedProvider ?: return
        currentProvider = aiProviderFactory.createOrNull(config)
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty() || state.isStreaming || state.session == null) return

        val provider = currentProvider
        val modelId = state.selectedModelId

        if (provider == null || modelId == null) {
            _uiState.update { it.copy(error = "Conectá un proveedor de IA primero") }
            return
        }

        _uiState.update { it.copy(inputText = "", isStreaming = true, streamingText = "") }

        streamingJob = viewModelScope.launch {
            try {
                // Save user message
                val userMessage = Message(
                    sessionId = state.session.id,
                    role = MessageRole.USER,
                    content = text
                )
                sessionRepository.addMessage(userMessage)

                // Build conversation history for API
                val allMessages = state.messages + userMessage
                val aiMessages = allMessages.map { msg ->
                    AIMessage(
                        role = when (msg.role) {
                            MessageRole.USER -> AIRole.USER
                            MessageRole.ASSISTANT -> AIRole.ASSISTANT
                            MessageRole.SYSTEM -> AIRole.SYSTEM
                            MessageRole.TOOL -> AIRole.TOOL
                        },
                        content = msg.content
                    )
                }

                // Build system prompt
                val systemPrompt = buildSystemPrompt(state)

                // Stream the response
                val responseBuilder = StringBuilder()
                provider.sendMessage(
                    messages = aiMessages,
                    model = modelId,
                    config = AIConfig(
                        systemPrompt = systemPrompt,
                        temperature = 0.7f,
                        maxTokens = 8192
                    )
                ).collect { event ->
                    when (event) {
                        is AIStreamEvent.TextDelta -> {
                            responseBuilder.append(event.text)
                            _uiState.update {
                                it.copy(streamingText = responseBuilder.toString())
                            }
                        }
                        is AIStreamEvent.Usage -> {
                            _uiState.update {
                                it.copy(tokenUsage = TokenUsage(event.inputTokens, event.outputTokens))
                            }
                        }
                        is AIStreamEvent.Error -> {
                            _uiState.update { it.copy(error = event.message) }
                        }
                        is AIStreamEvent.Done -> {
                            // Save the complete assistant message
                            if (responseBuilder.isNotEmpty()) {
                                val assistantMessage = Message(
                                    sessionId = state.session.id,
                                    role = MessageRole.ASSISTANT,
                                    content = responseBuilder.toString()
                                )
                                sessionRepository.addMessage(assistantMessage)
                            }
                        }
                        else -> {} // ToolCallDelta, ToolCallComplete handled later
                    }
                }

                _uiState.update { it.copy(isStreaming = false, streamingText = "") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isStreaming = false, streamingText = "", error = e.message)
                }
            }
        }
    }

    fun onStopStreaming() {
        streamingJob?.cancel()
        currentProvider?.cancelRequest()
        _uiState.update { it.copy(isStreaming = false, streamingText = "") }
    }

    fun onSelectProvider(provider: ProviderConfig) {
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedModelId = provider.selectedModelId ?: provider.defaultModelId
            )
        }
        refreshAIProvider()
        viewModelScope.launch {
            _uiState.value.session?.let { session ->
                sessionRepository.updateProvider(
                    session.id,
                    provider.id,
                    provider.selectedModelId ?: provider.defaultModelId ?: ""
                )
            }
        }
    }

    fun onSelectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId) }
        // Persist model selection
        viewModelScope.launch {
            val provider = _uiState.value.selectedProvider ?: return@launch
            providerRepository.update(provider.copy(selectedModelId = modelId))
        }
    }

    fun onToggleMode() {
        val newMode = when (_uiState.value.sessionMode) {
            SessionMode.BUILD -> SessionMode.PLAN
            SessionMode.PLAN -> SessionMode.BUILD
        }
        _uiState.update { it.copy(sessionMode = newMode) }
        viewModelScope.launch {
            _uiState.value.session?.let { session ->
                sessionRepository.updateMode(session.id, newMode)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun switchSession(newSessionId: String) {
        viewModelScope.launch {
            val session = sessionRepository.getById(newSessionId) ?: return@launch
            val project = projectRepository.getById(session.projectId)

            _uiState.update {
                it.copy(
                    session = session,
                    project = project,
                    sessionMode = session.mode,
                    noSession = false,
                    messages = emptyList()
                )
            }

            sessionRepository.getMessages(newSessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun buildSystemPrompt(state: ChatUiState): String {
        val mode = when (state.sessionMode) {
            SessionMode.BUILD -> "Sos un asistente de programación experto. Escribí código directamente, hacé cambios en archivos, y explicá brevemente."
            SessionMode.PLAN -> "Sos un asistente de planificación. Ayudá a pensar la arquitectura y el approach sin escribir código."
        }
        val projectContext = state.project?.let {
            "\nProyecto: ${it.name}\nDirectorio: ${it.path}"
        } ?: ""
        return mode + projectContext
    }
}
