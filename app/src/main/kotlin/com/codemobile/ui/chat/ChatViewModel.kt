package com.codemobile.ui.chat

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemobile.ai.model.AIConfig
import com.codemobile.ai.model.AIMessage
import com.codemobile.ai.model.AIRole
import com.codemobile.ai.model.AIStreamEvent
import com.codemobile.ai.model.AIToolCall
import com.codemobile.ai.prompt.SystemPromptBuilder
import com.codemobile.ai.provider.AIProvider
import com.codemobile.ai.provider.AIProviderFactory
import com.codemobile.ai.registry.ProviderRegistry
import com.codemobile.ai.tools.AgentTools
import com.codemobile.ai.tools.ToolExecutor
import com.codemobile.core.data.repository.ProjectRepository
import com.codemobile.core.data.repository.ProviderRepository
import com.codemobile.core.data.repository.SessionRepository
import com.codemobile.core.model.Message
import com.codemobile.core.model.MessageRole
import com.codemobile.core.model.Project
import com.codemobile.core.model.ProviderConfig
import com.codemobile.core.model.Session
import com.codemobile.core.model.SessionMode
import com.codemobile.preview.PreviewManager
import com.codemobile.preview.PreviewMode
import com.codemobile.preview.PreviewState
import com.codemobile.terminal.TerminalBootstrap
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

data class ChatUiState(
    val session: Session? = null,
    val project: Project? = null,
    val messages: List<Message> = emptyList(),
    val providers: List<ProviderConfig> = emptyList(),
    val selectedProvider: ProviderConfig? = null,
    val selectedModelId: String? = null,
    val availableModels: List<ProviderRegistry.ModelDef> = emptyList(),
    val sessionMode: SessionMode = SessionMode.BUILD,
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val tokenUsage: TokenUsage? = null,
    val error: String? = null,
    val isLoading: Boolean = true,
    val noSession: Boolean = false,
    val hasProviders: Boolean = false,
    val modifiedFiles: List<SessionFileChange> = emptyList(),
    val projectFileTree: List<ProjectFileNode> = emptyList(),
    val selectedFilePath: String? = null,
    val selectedFilePreview: ReadOnlyFilePreview? = null,
    val isLoadingInsights: Boolean = false,
    val isLoadingFilePreview: Boolean = false,
    val insightsError: String? = null,
    val previewState: PreviewState = PreviewState(),
    val showPreview: Boolean = false
)

data class TokenUsage(val input: Int = 0, val output: Int = 0)

data class SessionFileChange(
    val relativePath: String,
    val absolutePath: String,
    val lastModified: Long,
    val sizeBytes: Long
)

data class ProjectFileNode(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val children: List<ProjectFileNode> = emptyList()
)

data class ReadOnlyFilePreview(
    val relativePath: String,
    val content: String,
    val isBinary: Boolean,
    val isTruncated: Boolean
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val projectRepository: ProjectRepository,
    private val providerRepository: ProviderRepository,
    private val aiProviderFactory: AIProviderFactory,
    private val terminalBootstrap: TerminalBootstrap,
    val previewManager: PreviewManager,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: "none"
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProvider: AIProvider? = null
    private var streamingJob: Job? = null
    private var messagesJob: Job? = null
    private var insightsJob: Job? = null

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
                        noSession = false,
                        selectedFilePath = null,
                        selectedFilePreview = null
                    )
                }

                observeMessages(session.id)
                refreshSessionInsights()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    private fun observeMessages(activeSessionId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            sessionRepository.getMessages(activeSessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun observeProviders() {
        viewModelScope.launch {
            providerRepository.getActiveProviders().collect { providers ->
                val currentSelected = _uiState.value.selectedProvider
                val defaultSelected = providers.maxByOrNull { it.createdAt }
                val selected = currentSelected
                    ?.takeIf { current -> providers.any { it.id == current.id } }
                    ?: defaultSelected
                val rawModelId = selected?.selectedModelId ?: selected?.defaultModelId
                val modelId = selected?.let { normalizeModelId(it, rawModelId) }

                val models = selected?.let {
                    ProviderRegistry.getById(it.registryId)?.models
                } ?: emptyList()

                _uiState.update { state ->
                    state.copy(
                        providers = providers,
                        selectedProvider = selected,
                        selectedModelId = modelId,
                        availableModels = models,
                        hasProviders = providers.isNotEmpty()
                    )
                }

                // Persist automatic model normalization (legacy -> current)
                if (selected != null && modelId != null && modelId != rawModelId) {
                    providerRepository.update(selected.copy(selectedModelId = modelId))
                }

                // Rebuild AI provider when active providers change
                // Called directly (not in a new coroutine) to avoid race conditions
                refreshAIProvider()
            }
        }
    }

    /**
     * Rebuild the AIProvider instance from current selected ProviderConfig.
     * Uses suspend variant to auto-refresh expired Codex tokens.
     */
    private suspend fun refreshAIProvider() {
        val config = _uiState.value.selectedProvider ?: run {
            currentProvider = null
            return
        }
        try {
            val state = _uiState.value
            val resolved = resolveProviderWithRefresh(config, state.providers)
            currentProvider = resolved?.second
            if (resolved == null) {
                if (config.isOAuth && config.registryId == "openai") {
                    val hasAccess = !providerRepository.getAccessToken(config.id).isNullOrBlank() ||
                        !providerRepository.getOAuthToken(config.id).isNullOrBlank()
                    val hasRefresh = !providerRepository.getRefreshToken(config.id).isNullOrBlank()
                    val expiry = providerRepository.getTokenExpiry(config.id)
                    android.util.Log.e(
                        "ChatVM",
                        "OpenAI OAuth credentials missing/invalid for config.id=${config.id}: hasAccess=$hasAccess, hasRefresh=$hasRefresh, expiry=$expiry"
                    )
                }
                android.util.Log.w(
                    "ChatVM",
                    "Provider creation returned null for ${config.displayName} (${config.registryId})"
                )
            } else if (resolved.first.id != config.id) {
                applyResolvedProviderSelection(resolved.first)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatVM", "Failed to create provider ${config.displayName}: ${e.message}", e)
            currentProvider = null
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty() || state.isStreaming || state.session == null) return
        val activeSessionId = state.session.id

        var provider = currentProvider
        var modelId = state.selectedModelId

        // If provider is missing or stale (different from selected provider), resolve on-demand.
        if (state.selectedProvider != null &&
            (provider == null || provider.id != state.selectedProvider.id)
        ) {
            val resolved = resolveProviderNow(state.selectedProvider, state.providers)
            provider = resolved?.second
            if (resolved != null) {
                currentProvider = provider
                modelId = normalizeModelId(
                    resolved.first,
                    resolved.first.selectedModelId ?: resolved.first.defaultModelId
                )
                if (resolved.first.id != state.selectedProvider.id) {
                    applyResolvedProviderSelection(resolved.first)
                }
            }
        }

        if (provider == null || modelId == null) {
            val selectedProvider = state.selectedProvider
            val oauthInitError = selectedProvider
                ?.takeIf { it.isOAuth && it.registryId == "openai" }
                ?.let { cfg ->
                    val hasAccess = !providerRepository.getAccessToken(cfg.id).isNullOrBlank() ||
                        !providerRepository.getOAuthToken(cfg.id).isNullOrBlank()
                    val hasRefresh = !providerRepository.getRefreshToken(cfg.id).isNullOrBlank()
                    when {
                        hasAccess -> null
                        hasRefresh -> "No se encontro access token activo para ${cfg.displayName}. Reintenta en unos segundos para refrescarlo automaticamente."
                        else -> "No se encontraron credenciales OAuth para ${cfg.displayName}. Volve a iniciar sesion."
                    }
                }

            val reason = when {
                state.providers.isEmpty() -> "No hay proveedores conectados. Conecta uno primero."
                state.selectedProvider == null -> "Selecciona un proveedor de IA."
                provider == null -> oauthInitError
                    ?: "Error al inicializar ${state.selectedProvider.displayName}. Verifica tus credenciales."
                else -> "Selecciona un modelo de IA."
            }
            _uiState.update { it.copy(error = reason) }
            return
        }

        _uiState.update { it.copy(inputText = "", isStreaming = true, streamingText = "") }

        streamingJob = viewModelScope.launch {
            try {
                if (!sessionExists(activeSessionId)) {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            error = "La sesión actual ya no existe. Volvé a abrirla o creá una nueva."
                        )
                    }
                    return@launch
                }

                var responseInputTokens = 0
                var responseOutputTokens = 0

                // Save user message
                val userMessage = Message(
                    sessionId = activeSessionId,
                    role = MessageRole.USER,
                    content = text
                )
                if (!safeAddMessage(userMessage)) {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            error = "No se pudo guardar el mensaje en la sesión actual. Reabrí el chat e intentá de nuevo."
                        )
                    }
                    return@launch
                }

                // Build system prompt
                val systemPrompt = buildSystemPrompt(state)
                val config = AIConfig(
                    systemPrompt = systemPrompt,
                    temperature = 0.7f,
                    maxTokens = 8192
                )

                // Determine tools to pass (only in BUILD mode with a project)
                // Codex OAuth uses a different API format that doesn't support tools
                val disableToolsForProvider = state.selectedProvider?.let { selected ->
                    selected.registryId == "openai" && selected.isOAuth
                } ?: false

                val tools = if (
                    state.sessionMode == SessionMode.BUILD &&
                    state.project != null &&
                    !disableToolsForProvider
                ) {
                    AgentTools.allTools()
                } else null

                // Create the tool executor for the current project
                val toolExecutor = state.project?.let { project ->
                    ToolExecutor(
                        projectRootPath = project.path,
                        context = appContext,
                        shellCommand = terminalBootstrap.getShellCommand(),
                        environment = terminalBootstrap.environment
                    )
                }

                // Agentic loop: keep calling the LLM until it responds with text only
                var round = 0
                var conversationMessages = buildConversationMessages(state.messages + userMessage)

                while (round < AgentTools.MAX_TOOL_ROUNDS) {
                    round++
                    val responseBuilder = StringBuilder()
                    val pendingToolCalls = mutableListOf<AIToolCall>()

                    provider.sendMessage(
                        messages = conversationMessages,
                        model = modelId,
                        tools = tools,
                        config = config
                    ).collect { event ->
                        when (event) {
                            is AIStreamEvent.TextDelta -> {
                                responseBuilder.append(event.text)
                                _uiState.update {
                                    it.copy(streamingText = responseBuilder.toString())
                                }
                            }

                            is AIStreamEvent.ToolCallComplete -> {
                                pendingToolCalls.add(event.toolCall)
                                // Show tool call in streaming text
                                val toolInfo = "\n🔧 ${event.toolCall.name}(...)\n"
                                responseBuilder.append(toolInfo)
                                _uiState.update {
                                    it.copy(streamingText = responseBuilder.toString())
                                }
                            }

                            is AIStreamEvent.Usage -> {
                                responseInputTokens += event.inputTokens
                                responseOutputTokens += event.outputTokens
                                _uiState.update {
                                    it.copy(tokenUsage = TokenUsage(responseInputTokens, responseOutputTokens))
                                }
                            }

                            is AIStreamEvent.Error -> {
                                val selectedNow = _uiState.value.selectedProvider
                                val mappedMessage = if (isKimiAccessTerminated(selectedNow, event.message)) {
                                    val fallbackProvider = switchFromKimiToAlternativeProvider()
                                    if (fallbackProvider != null) {
                                        "Kimi For Coding devolvió 403 por allowlist. Se cambió automáticamente a ${fallbackProvider.displayName}. Reenviá el mensaje."
                                    } else {
                                        "Kimi For Coding bloqueó esta app por política de allowlist (403) y no hay un provider alternativo conectado. Conectá Moonshot/OpenRouter/Zen para continuar."
                                    }
                                } else {
                                    event.message
                                }
                                _uiState.update { it.copy(error = mappedMessage) }
                            }

                            is AIStreamEvent.Done -> {
                                // Done is handled after collect
                            }

                            else -> {}
                        }
                    }

                    // If the AI called tools, execute them and loop again
                    if (pendingToolCalls.isNotEmpty() && toolExecutor != null) {
                        // Save the assistant message with tool calls
                        val toolCallsJson = JSONArray().apply {
                            pendingToolCalls.forEach { tc ->
                                put(JSONObject().apply {
                                    put("id", tc.id)
                                    put("name", tc.name)
                                    put("arguments", tc.arguments)
                                })
                            }
                        }.toString()
                        val assistantMsg = Message(
                            sessionId = activeSessionId,
                            role = MessageRole.ASSISTANT,
                            content = responseBuilder.toString().trim(),
                            toolCalls = toolCallsJson
                        )
                        if (!safeAddMessage(assistantMsg)) {
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    streamingText = "",
                                    error = "No se pudo guardar la respuesta del asistente."
                                )
                            }
                            return@launch
                        }

                        // Execute each tool call and add results to conversation
                        val toolResultMessages = mutableListOf<AIMessage>()
                        for (toolCall in pendingToolCalls) {
                            _uiState.update {
                                it.copy(streamingText = responseBuilder.toString() + "\n⚙️ Running ${toolCall.name}...")
                            }

                            val result = withContext(Dispatchers.IO) {
                                toolExecutor.execute(toolCall)
                            }

                            // Scan run_command output for dev server URLs
                            if (toolCall.name == AgentTools.RUN_COMMAND && result.success) {
                                previewManager.scanForDevServer(
                                    output = result.output,
                                    projectPath = _uiState.value.project?.path
                                )
                            }

                            // Show result briefly
                            val resultPreview = result.output.take(200)
                            val statusIcon = if (result.success) "✅" else "❌"
                            responseBuilder.append("$statusIcon $resultPreview\n")
                            _uiState.update {
                                it.copy(streamingText = responseBuilder.toString())
                            }

                            // Save tool result to DB
                            val toolMessage = Message(
                                sessionId = activeSessionId,
                                role = MessageRole.TOOL,
                                content = result.output,
                                toolCallId = toolCall.id
                            )
                            if (!safeAddMessage(toolMessage)) {
                                _uiState.update {
                                    it.copy(
                                        isStreaming = false,
                                        streamingText = "",
                                        error = "No se pudo guardar el resultado de herramienta en la sesión."
                                    )
                                }
                                return@launch
                            }

                            // Add to conversation for next round
                            toolResultMessages.add(AIMessage(
                                role = AIRole.TOOL,
                                content = result.output,
                                toolCallId = toolCall.id
                            ))
                        }

                        // Rebuild conversation with tool results for next round
                        val assistantAIMsg = AIMessage(
                            role = AIRole.ASSISTANT,
                            content = responseBuilder.toString().trim(),
                            toolCalls = pendingToolCalls
                        )
                        conversationMessages = conversationMessages + assistantAIMsg + toolResultMessages

                        // Clear streaming text for next round
                        _uiState.update { it.copy(streamingText = "") }
                        continue // Next round of the agentic loop
                    }

                    // No tool calls — save the final text response
                    if (responseBuilder.isNotEmpty()) {
                        val assistantMessage = Message(
                            sessionId = activeSessionId,
                            role = MessageRole.ASSISTANT,
                            content = responseBuilder.toString()
                        )
                        if (!safeAddMessage(assistantMessage)) {
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    streamingText = "",
                                    error = "No se pudo guardar el mensaje del asistente."
                                )
                            }
                            return@launch
                        }
                    }

                    // Update token counts
                    if (responseInputTokens > 0 || responseOutputTokens > 0) {
                        sessionRepository.updateTokens(
                            sessionId = activeSessionId,
                            inputTokens = responseInputTokens,
                            outputTokens = responseOutputTokens
                        )
                        _uiState.update { current ->
                            val currentSession = current.session
                            if (currentSession?.id != activeSessionId) return@update current
                            current.copy(
                                session = currentSession.copy(
                                    totalInputTokens = currentSession.totalInputTokens + responseInputTokens,
                                    totalOutputTokens = currentSession.totalOutputTokens + responseOutputTokens
                                )
                            )
                        }
                    }

                    refreshSessionInsights()
                    break // Exit agentic loop — we got a text-only response
                }

                _uiState.update { it.copy(isStreaming = false, streamingText = "") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(isStreaming = false, streamingText = "") }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isStreaming = false, streamingText = "", error = e.message)
                }
            }
        }
    }

    /**
     * Build the conversation messages list for the AI provider,
     * properly mapping tool calls and tool results.
     */
    private fun buildConversationMessages(messages: List<Message>): List<AIMessage> {
        return messages.map { msg ->
            val toolCalls = msg.toolCalls?.let { json ->
                try {
                    val arr = JSONArray(json)
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        AIToolCall(
                            id = obj.optString("id", ""),
                            name = obj.optString("name", ""),
                            arguments = obj.optString("arguments", "{}")
                        )
                    }
                } catch (e: Exception) { null }
            }

            AIMessage(
                role = when (msg.role) {
                    MessageRole.USER -> AIRole.USER
                    MessageRole.ASSISTANT -> AIRole.ASSISTANT
                    MessageRole.SYSTEM -> AIRole.SYSTEM
                    MessageRole.TOOL -> AIRole.TOOL
                },
                content = msg.content,
                toolCalls = toolCalls,
                toolCallId = msg.toolCallId
            )
        }
    }

    fun onStopStreaming() {
        streamingJob?.cancel()
        currentProvider?.cancelRequest()
        _uiState.update { it.copy(isStreaming = false, streamingText = "") }
    }

    fun onSelectProvider(provider: ProviderConfig) {
        val defaultModelId = normalizeModelId(provider, provider.selectedModelId ?: provider.defaultModelId)
        onSelectProviderModel(provider, defaultModelId)
    }

    fun onSelectProviderModel(provider: ProviderConfig, modelId: String?) {
        val models = ProviderRegistry.getById(provider.registryId)?.models ?: emptyList()
        val rawModelId = modelId ?: provider.selectedModelId ?: provider.defaultModelId
        val normalizedModelId = normalizeModelId(provider, rawModelId)
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedModelId = normalizedModelId,
                availableModels = models
            )
        }
        viewModelScope.launch {
            if (normalizedModelId != null && normalizedModelId != rawModelId) {
                providerRepository.update(provider.copy(selectedModelId = normalizedModelId))
            }
            refreshAIProvider()
            _uiState.value.session?.let { session ->
                persistSessionProviderSelection(
                    sessionId = session.id,
                    providerId = provider.id,
                    modelId = normalizedModelId ?: ""
                )
            }
        }
    }

    private fun normalizeModelId(provider: ProviderConfig, modelId: String?): String? {
        if (modelId.isNullOrBlank()) return modelId
        // Legacy kimi-coding submodel IDs → collapse to base model
        if (provider.registryId == "kimi-coding" && modelId.startsWith("kimi-for-coding/")) {
            return "kimi-for-coding"
        }
        return modelId
    }

    private fun isKimiAccessTerminated(provider: ProviderConfig?, message: String): Boolean {
        return provider?.registryId == "kimi-coding" &&
            message.contains("access_terminated_error", ignoreCase = true)
    }

    private suspend fun switchFromKimiToAlternativeProvider(): ProviderConfig? {
        val state = _uiState.value
        val current = state.selectedProvider ?: return null
        if (current.registryId != "kimi-coding") return null

        val preferredRegistryOrder = listOf("moonshot", "openrouter")

        val preferredCandidates = state.providers
            .filter { it.id != current.id && it.registryId in preferredRegistryOrder }
            .sortedWith(
                compareBy<ProviderConfig> { preferredRegistryOrder.indexOf(it.registryId) }
                    .thenByDescending { it.createdAt }
            )

        val otherCandidates = state.providers
            .filter {
                it.id != current.id &&
                    it.registryId != "kimi-coding" &&
                    it.registryId !in preferredRegistryOrder
            }
            .sortedByDescending { it.createdAt }

        val candidates = preferredCandidates + otherCandidates

        for (candidate in candidates) {
            val provider = aiProviderFactory.createOrNullWithRefresh(candidate) ?: continue
            currentProvider = provider
            applyResolvedProviderSelection(candidate)

            val normalizedModelId = normalizeModelId(candidate, candidate.selectedModelId ?: candidate.defaultModelId)
            state.session?.let { session ->
                persistSessionProviderSelection(
                    sessionId = session.id,
                    providerId = candidate.id,
                    modelId = normalizedModelId ?: ""
                )
            }
            return candidate
        }

        return null
    }

    private fun buildProviderCandidates(
        preferred: ProviderConfig,
        allProviders: List<ProviderConfig>
    ): List<ProviderConfig> {
        val sameRegistry = allProviders
            .filter { it.id != preferred.id && it.registryId == preferred.registryId }
            .sortedByDescending { it.createdAt }

        return listOf(preferred) + sameRegistry
    }

    private fun resolveProviderNow(
        preferred: ProviderConfig,
        allProviders: List<ProviderConfig>
    ): Pair<ProviderConfig, AIProvider>? {
        val candidates = buildProviderCandidates(preferred, allProviders)
        for (candidate in candidates) {
            val provider = aiProviderFactory.createOrNull(candidate) ?: continue
            return candidate to provider
        }
        return null
    }

    private suspend fun resolveProviderWithRefresh(
        preferred: ProviderConfig,
        allProviders: List<ProviderConfig>
    ): Pair<ProviderConfig, AIProvider>? {
        val candidates = buildProviderCandidates(preferred, allProviders)
        for (candidate in candidates) {
            val provider = aiProviderFactory.createOrNullWithRefresh(candidate) ?: continue
            return candidate to provider
        }
        return null
    }

    private fun applyResolvedProviderSelection(provider: ProviderConfig) {
        val models = ProviderRegistry.getById(provider.registryId)?.models ?: emptyList()
        val rawModelId = provider.selectedModelId ?: provider.defaultModelId
        val normalizedModelId = normalizeModelId(provider, rawModelId)
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedModelId = normalizedModelId,
                availableModels = models
            )
        }
        viewModelScope.launch {
            if (normalizedModelId != null && normalizedModelId != rawModelId) {
                providerRepository.update(provider.copy(selectedModelId = normalizedModelId))
            }
        }
    }

    private suspend fun sessionExists(sessionId: String): Boolean {
        return sessionRepository.getById(sessionId) != null
    }

    private suspend fun safeAddMessage(message: Message): Boolean {
        return try {
            sessionRepository.addMessage(message)
            true
        } catch (e: Exception) {
            android.util.Log.e("ChatVM", "Failed to persist message for sessionId=${message.sessionId}: ${e.message}", e)
            false
        }
    }

    private suspend fun persistSessionProviderSelection(
        sessionId: String,
        providerId: String,
        modelId: String
    ) {
        runCatching {
            sessionRepository.updateProvider(sessionId, providerId, modelId)
        }.onFailure { e ->
            android.util.Log.e(
                "ChatVM",
                "Failed to update session provider selection for sessionId=$sessionId, providerId=$providerId: ${e.message}",
                e
            )
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

    fun clearInsightsError() {
        _uiState.update { it.copy(insightsError = null) }
    }

    // ── Preview ──────────────────────────────────────────────────

    /**
     * Toggle the preview panel on/off.
     * If turning on and no preview is running, starts the static server.
     */
    fun togglePreview() {
        val current = _uiState.value
        if (current.showPreview) {
            // Hide preview (keep server running for quick re-open)
            _uiState.update { it.copy(showPreview = false) }
        } else {
            // Show preview — start server if not running
            val projectPath = current.project?.path
            if (projectPath != null && !previewManager.isRunning()) {
                previewManager.startStaticServer(projectPath)
            }
            _uiState.update {
                it.copy(
                    showPreview = true,
                    previewState = previewManager.state.value
                )
            }
            // Observe preview state changes
            observePreviewState()
        }
    }

    fun stopPreview() {
        previewManager.stop()
        _uiState.update { it.copy(showPreview = false, previewState = PreviewState()) }
    }

    fun refreshPreview() {
        val projectPath = _uiState.value.project?.path ?: return
        val currentState = previewManager.state.value
        // If static mode, restart server to pick up new files
        if (currentState.mode == PreviewMode.STATIC) {
            previewManager.startStaticServer(projectPath)
        }
        _uiState.update { it.copy(previewState = previewManager.state.value) }
    }

    private fun observePreviewState() {
        viewModelScope.launch {
            previewManager.state.collect { previewState ->
                _uiState.update { it.copy(previewState = previewState) }
            }
        }
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
                    messages = emptyList(),
                    selectedFilePath = null,
                    selectedFilePreview = null
                )
            }

            observeMessages(newSessionId)
            refreshSessionInsights()
        }
    }

    private fun buildSystemPrompt(state: ChatUiState): String {
        return SystemPromptBuilder()
            .mode(
                when (state.sessionMode) {
                    SessionMode.BUILD -> SystemPromptBuilder.Mode.BUILD
                    SessionMode.PLAN -> SystemPromptBuilder.Mode.PLAN
                }
            )
            .project(
                name = state.project?.name,
                path = state.project?.path
            )
            .openFile(
                relativePath = state.selectedFilePath,
                contentPreview = state.selectedFilePreview?.content
            )
            .modifiedFiles(
                state.modifiedFiles.map { it.relativePath }
            )
            .fileTree(
                state.projectFileTree.takeIf { it.isNotEmpty() }?.let { nodes ->
                    renderFileTree(nodes, depth = 0, maxLines = 60)
                }
            )
            .build()
    }

    /**
     * Renders a list of [ProjectFileNode] into a compact text tree for the system prompt.
     * Example output:
     * ```
     * app/
     *   src/
     *     main/
     *       kotlin/
     *   build.gradle.kts
     * ```
     */
    private fun renderFileTree(
        nodes: List<ProjectFileNode>,
        depth: Int,
        maxLines: Int,
        currentLines: MutableList<String> = mutableListOf()
    ): String {
        val indent = "  ".repeat(depth)
        for (node in nodes) {
            if (currentLines.size >= maxLines) {
                currentLines.add("${indent}... (truncated)")
                break
            }
            val suffix = if (node.isDirectory) "/" else ""
            currentLines.add("$indent${node.name}$suffix")
            if (node.isDirectory && node.children.isNotEmpty()) {
                renderFileTree(node.children, depth + 1, maxLines, currentLines)
            }
        }
        return if (depth == 0) currentLines.joinToString("\n") else ""
    }

    fun refreshSessionInsights() {
        val state = _uiState.value
        val session = state.session ?: return
        val project = state.project ?: return

        insightsJob?.cancel()
        insightsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInsights = true, insightsError = null) }
            try {
                val snapshot = loadInsightsSnapshot(project, session)
                _uiState.update { current ->
                    current.copy(
                        modifiedFiles = snapshot.modifiedFiles,
                        projectFileTree = snapshot.fileTree,
                        isLoadingInsights = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingInsights = false,
                        insightsError = e.message ?: "No se pudo cargar contexto del proyecto"
                    )
                }
            }
        }
    }

    fun openReadOnlyFile(absolutePath: String) {
        val project = _uiState.value.project ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFilePreview = true, insightsError = null) }
            try {
                val preview = withContext(Dispatchers.IO) {
                    if (absolutePath.startsWith("content://")) {
                        readFilePreviewSaf(absolutePath)
                    } else {
                        readFilePreview(
                            projectRoot = File(project.path),
                            targetFile = File(absolutePath)
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        selectedFilePath = absolutePath,
                        selectedFilePreview = preview,
                        isLoadingFilePreview = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingFilePreview = false,
                        insightsError = e.message ?: "No se pudo abrir el archivo"
                    )
                }
            }
        }
    }

    private suspend fun loadInsightsSnapshot(
        project: Project,
        session: Session
    ): InsightsSnapshot = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            return@withContext loadInsightsSnapshotSaf(project, session)
        }

        val root = File(project.path)
        if (!root.exists() || !root.isDirectory) {
            throw IllegalStateException("El directorio del proyecto no existe: ${project.path}")
        }

        val canonicalRoot = root.canonicalFile
        val modifiedFiles = mutableListOf<SessionFileChange>()
        val visitedForChanges = mutableSetOf<String>()
        collectModifiedFiles(
            directory = canonicalRoot,
            root = canonicalRoot,
            sinceTimestamp = session.createdAt,
            out = modifiedFiles,
            visitedDirectories = visitedForChanges
        )

        val visitedForTree = mutableSetOf<String>()
        val fileTree = buildTree(
            directory = canonicalRoot,
            root = canonicalRoot,
            visitedDirectories = visitedForTree
        )

        InsightsSnapshot(
            modifiedFiles = modifiedFiles.sortedByDescending { it.lastModified },
            fileTree = fileTree
        )
    }

    private fun loadInsightsSnapshotSaf(
        project: Project,
        session: Session
    ): InsightsSnapshot {
        val uri = Uri.parse(project.path)
        val root = DocumentFile.fromTreeUri(appContext, uri)
            ?: return InsightsSnapshot(emptyList(), emptyList())

        val modifiedFiles = mutableListOf<SessionFileChange>()
        collectModifiedFilesSaf(
            directory = root,
            parentRelativePath = "",
            sinceTimestamp = session.createdAt,
            out = modifiedFiles
        )

        val fileTree = buildTreeSaf(
            directory = root,
            parentRelativePath = ""
        )

        return InsightsSnapshot(
            modifiedFiles = modifiedFiles.sortedByDescending { it.lastModified },
            fileTree = fileTree
        )
    }

    private fun collectModifiedFilesSaf(
        directory: DocumentFile,
        parentRelativePath: String,
        sinceTimestamp: Long,
        out: MutableList<SessionFileChange>
    ) {
        val children = directory.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val relativePath = if (parentRelativePath.isEmpty()) name else "$parentRelativePath/$name"
            if (child.isDirectory) {
                collectModifiedFilesSaf(child, relativePath, sinceTimestamp, out)
            } else if (child.lastModified() >= sinceTimestamp) {
                out += SessionFileChange(
                    relativePath = relativePath,
                    absolutePath = child.uri.toString(),
                    lastModified = child.lastModified(),
                    sizeBytes = child.length()
                )
            }
        }
    }

    private fun buildTreeSaf(
        directory: DocumentFile,
        parentRelativePath: String
    ): List<ProjectFileNode> {
        val children = directory.listFiles()
            .filter { !(it.name ?: "").startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { (it.name ?: "").lowercase() }))

        return children.mapNotNull { child ->
            val name = child.name ?: return@mapNotNull null
            val relativePath = if (parentRelativePath.isEmpty()) name else "$parentRelativePath/$name"
            if (child.isDirectory) {
                ProjectFileNode(
                    name = name,
                    relativePath = relativePath,
                    absolutePath = child.uri.toString(),
                    isDirectory = true,
                    children = buildTreeSaf(child, relativePath)
                )
            } else {
                ProjectFileNode(
                    name = name,
                    relativePath = relativePath,
                    absolutePath = child.uri.toString(),
                    isDirectory = false
                )
            }
        }
    }

    private fun collectModifiedFiles(
        directory: File,
        root: File,
        sinceTimestamp: Long,
        out: MutableList<SessionFileChange>,
        visitedDirectories: MutableSet<String>
    ) {
        val directoryPath = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)
        if (!visitedDirectories.add(directoryPath)) return

        listChildrenSafe(directory).forEach { child ->
            if (shouldSkip(child)) return@forEach

            if (child.isDirectory) {
                collectModifiedFiles(
                    directory = child,
                    root = root,
                    sinceTimestamp = sinceTimestamp,
                    out = out,
                    visitedDirectories = visitedDirectories
                )
            } else if (child.lastModified() >= sinceTimestamp) {
                val relativePath = child.relativeTo(root).invariantSeparatorsPath
                out += SessionFileChange(
                    relativePath = relativePath,
                    absolutePath = child.absolutePath,
                    lastModified = child.lastModified(),
                    sizeBytes = child.length()
                )
            }
        }
    }

    private fun buildTree(
        directory: File,
        root: File,
        visitedDirectories: MutableSet<String>
    ): List<ProjectFileNode> {
        val directoryPath = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)
        if (!visitedDirectories.add(directoryPath)) return emptyList()

        return listChildrenSafe(directory)
            .asSequence()
            .filterNot(::shouldSkip)
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { child ->
                val relativePath = child.relativeTo(root).invariantSeparatorsPath
                if (child.isDirectory) {
                    ProjectFileNode(
                        name = child.name,
                        relativePath = relativePath,
                        absolutePath = child.absolutePath,
                        isDirectory = true,
                        children = buildTree(
                            directory = child,
                            root = root,
                            visitedDirectories = visitedDirectories
                        )
                    )
                } else {
                    ProjectFileNode(
                        name = child.name,
                        relativePath = relativePath,
                        absolutePath = child.absolutePath,
                        isDirectory = false
                    )
                }
            }
            .toList()
    }

    private fun shouldSkip(file: File): Boolean {
        return file.name == ".git"
    }

    private fun listChildrenSafe(directory: File): List<File> {
        return runCatching {
            directory.listFiles()?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun readFilePreview(projectRoot: File, targetFile: File): ReadOnlyFilePreview {
        val canonicalRoot = projectRoot.canonicalFile
        val canonicalTarget = targetFile.canonicalFile

        val rootPath = canonicalRoot.path
        val targetPath = canonicalTarget.path
        val isInsideRoot = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
        if (!isInsideRoot) {
            throw IllegalArgumentException("Archivo fuera del proyecto")
        }
        if (!canonicalTarget.exists() || !canonicalTarget.isFile) {
            throw IllegalArgumentException("Archivo no encontrado")
        }

        val maxPreviewBytes = 200 * 1024
        val output = ByteArrayOutputStream()
        var totalBytes = 0
        var truncated = false

        canonicalTarget.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break

                if (totalBytes + read > maxPreviewBytes) {
                    val remaining = maxPreviewBytes - totalBytes
                    if (remaining > 0) {
                        output.write(buffer, 0, remaining)
                    }
                    truncated = true
                    break
                }

                output.write(buffer, 0, read)
                totalBytes += read
            }
        }

        val bytes = output.toByteArray()
        val isBinary = bytes.take(1024).any { byte -> byte == 0.toByte() }
        val relativePath = canonicalTarget.relativeTo(canonicalRoot).invariantSeparatorsPath

        return ReadOnlyFilePreview(
            relativePath = relativePath,
            content = if (isBinary) "" else bytes.toString(Charsets.UTF_8),
            isBinary = isBinary,
            isTruncated = truncated
        )
    }

    private fun readFilePreviewSaf(uriString: String): ReadOnlyFilePreview {
        val uri = Uri.parse(uriString)
        val docFile = DocumentFile.fromSingleUri(appContext, uri)
        val name = docFile?.name ?: uri.lastPathSegment ?: "unknown"

        val maxPreviewBytes = 200 * 1024
        val output = ByteArrayOutputStream()
        var totalBytes = 0
        var truncated = false

        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir el archivo")

        inputStream.use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                if (totalBytes + read > maxPreviewBytes) {
                    val remaining = maxPreviewBytes - totalBytes
                    if (remaining > 0) output.write(buffer, 0, remaining)
                    truncated = true
                    break
                }
                output.write(buffer, 0, read)
                totalBytes += read
            }
        }

        val bytes = output.toByteArray()
        val isBinary = bytes.take(1024).any { byte -> byte == 0.toByte() }

        return ReadOnlyFilePreview(
            relativePath = name,
            content = if (isBinary) "" else bytes.toString(Charsets.UTF_8),
            isBinary = isBinary,
            isTruncated = truncated
        )
    }

    private data class InsightsSnapshot(
        val modifiedFiles: List<SessionFileChange>,
        val fileTree: List<ProjectFileNode>
    )
}

