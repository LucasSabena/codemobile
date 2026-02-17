package com.codemobile.ui.chat

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
import com.codemobile.terminal.TerminalBootstrap
import com.codemobile.terminal.TerminalSession
import com.codemobile.preview.DevServerDetector
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
    val devServerUrl: String? = null,
    val isStartingDevServer: Boolean = false,
    val devServerError: String? = null
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: "none"
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProvider: AIProvider? = null
    private var streamingJob: Job? = null
    private var messagesJob: Job? = null
    private var insightsJob: Job? = null
    private var devServerSession: TerminalSession? = null
    private var devServerOutputJob: Job? = null
    private var devServerExitWatcherJob: Job? = null
    private var devServerStartupTimeoutJob: Job? = null
    private var recentDevServerOutput: String = ""
    private var devServerFallbackTried: Boolean = false

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
                val selected = _uiState.value.selectedProvider ?: providers.firstOrNull()
                val modelId = selected?.selectedModelId ?: selected?.defaultModelId

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
            currentProvider = aiProviderFactory.createOrNullWithRefresh(config)
            if (currentProvider == null) {
                android.util.Log.w("ChatVM", "Provider creation returned null for ${config.displayName} (${config.registryId})")
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

        var provider = currentProvider
        val modelId = state.selectedModelId

        // If provider is null but we have a selected config, try to create it on-demand
        if (provider == null && state.selectedProvider != null) {
            provider = aiProviderFactory.createOrNull(state.selectedProvider)
            if (provider != null) {
                currentProvider = provider
            }
        }

        if (provider == null || modelId == null) {
            val reason = when {
                state.providers.isEmpty() -> "No hay proveedores conectados. Conectá uno primero."
                state.selectedProvider == null -> "Seleccioná un proveedor de IA."
                provider == null -> "Error al inicializar ${state.selectedProvider.displayName}. Verificá tus credenciales."
                else -> "Seleccioná un modelo de IA."
            }
            _uiState.update { it.copy(error = reason) }
            return
        }

        _uiState.update { it.copy(inputText = "", isStreaming = true, streamingText = "") }

        streamingJob = viewModelScope.launch {
            try {
                var responseInputTokens = 0
                var responseOutputTokens = 0

                // Save user message
                val userMessage = Message(
                    sessionId = state.session.id,
                    role = MessageRole.USER,
                    content = text
                )
                sessionRepository.addMessage(userMessage)

                // Build system prompt
                val systemPrompt = buildSystemPrompt(state)
                val config = AIConfig(
                    systemPrompt = systemPrompt,
                    temperature = 0.7f,
                    maxTokens = 8192
                )

                // Determine tools to pass (only in BUILD mode with a project)
                val tools = if (state.sessionMode == SessionMode.BUILD && state.project != null) {
                    AgentTools.allTools()
                } else null

                // Create the tool executor for the current project
                val toolExecutor = state.project?.let { project ->
                    ToolExecutor(
                        projectRoot = File(project.path),
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
                                _uiState.update { it.copy(error = event.message) }
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
                            sessionId = state.session.id,
                            role = MessageRole.ASSISTANT,
                            content = responseBuilder.toString().trim(),
                            toolCalls = toolCallsJson
                        )
                        sessionRepository.addMessage(assistantMsg)

                        // Execute each tool call and add results to conversation
                        val toolResultMessages = mutableListOf<AIMessage>()
                        for (toolCall in pendingToolCalls) {
                            _uiState.update {
                                it.copy(streamingText = responseBuilder.toString() + "\n⚙️ Running ${toolCall.name}...")
                            }

                            val result = withContext(Dispatchers.IO) {
                                toolExecutor.execute(toolCall)
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
                                sessionId = state.session.id,
                                role = MessageRole.TOOL,
                                content = result.output,
                                toolCallId = toolCall.id
                            )
                            sessionRepository.addMessage(toolMessage)

                            // Add to conversation for next round
                            toolResultMessages.add(AIMessage(
                                role = AIRole.TOOL,
                                content = result.output,
                                toolCallId = toolCall.id
                            ))
                        }

                        maybeStartDevServerAfterToolCalls(
                            project = state.project,
                            toolCalls = pendingToolCalls
                        )

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
                            sessionId = state.session.id,
                            role = MessageRole.ASSISTANT,
                            content = responseBuilder.toString()
                        )
                        sessionRepository.addMessage(assistantMessage)
                    }

                    // Update token counts
                    if (responseInputTokens > 0 || responseOutputTokens > 0) {
                        sessionRepository.updateTokens(
                            sessionId = state.session.id,
                            inputTokens = responseInputTokens,
                            outputTokens = responseOutputTokens
                        )
                        _uiState.update { current ->
                            val currentSession = current.session
                            if (currentSession?.id != state.session.id) return@update current
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
        val models = ProviderRegistry.getById(provider.registryId)?.models ?: emptyList()
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedModelId = provider.selectedModelId ?: provider.defaultModelId,
                availableModels = models
            )
        }
        viewModelScope.launch {
            refreshAIProvider()
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

    fun clearInsightsError() {
        _uiState.update { it.copy(insightsError = null) }
    }

    fun onCompileAndPreview() {
        val state = _uiState.value
        val project = state.project ?: run {
            _uiState.update { it.copy(error = "No hay proyecto activo para compilar") }
            return
        }

        if (project.path.startsWith("content://")) {
            _uiState.update {
                it.copy(error = "Este proyecto usa content:// y no permite compilar con terminal")
            }
            return
        }

        val projectDir = File(project.path)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            _uiState.update { it.copy(error = "El directorio del proyecto no existe") }
            return
        }

        val packageJson = File(projectDir, "package.json")
        if (!packageJson.exists()) {
            _uiState.update {
                it.copy(error = "No se encontró package.json. No se puede ejecutar npm run dev")
            }
            return
        }

        if (terminalBootstrap.getNativeBinaryPath("node") == null) {
            if (fallbackToStaticPreview(projectDir)) {
                _uiState.update {
                    it.copy(
                        error = "Preview estatico: esta build no incluye Node runtime"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        error = "Esta build no incluye Node runtime. Agrega libnode.so/libdash.so en app/src/main/jniLibs/arm64-v8a"
                    )
                }
            }
            return
        }

        startDevServerIfPossible(project)
    }

    private fun maybeStartDevServerAfterToolCalls(
        project: Project?,
        toolCalls: List<AIToolCall>
    ) {
        val currentProject = project ?: return
        val touchedProjectFiles = toolCalls.any { toolCall ->
            toolCall.name == AgentTools.WRITE_FILE ||
                toolCall.name == AgentTools.EDIT_FILE ||
                toolCall.name == AgentTools.DELETE_FILE ||
                toolCall.name == AgentTools.RUN_COMMAND
        }
        if (!touchedProjectFiles) return

        startDevServerIfPossible(currentProject)
    }

    private fun startDevServerIfPossible(project: Project) {
        if (project.path.startsWith("content://")) return
        if (devServerSession?.isRunning?.value == true) return

        val projectDir = File(project.path)
        val packageJson = File(projectDir, "package.json")
        if (!projectDir.exists() || !projectDir.isDirectory || !packageJson.exists()) {
            return
        }

        stopDevServer()

        val envArray = terminalBootstrap.environment
            .map { "${it.key}=${it.value}" }
            .toTypedArray()

        val newSession = TerminalSession(
            shellCommand = terminalBootstrap.getShellCommand(),
            workingDir = projectDir,
            environment = envArray
        )
        newSession.start()
        devServerSession = newSession
        recentDevServerOutput = ""
        devServerFallbackTried = false
        _uiState.update {
            it.copy(
                devServerUrl = null,
                isStartingDevServer = true,
                devServerError = null
            )
        }

        val devCommand = resolveDevCommand(packageJson) ?: "npm run dev"

        devServerOutputJob = viewModelScope.launch {
            newSession.output.collect { chunk ->
                recentDevServerOutput = (recentDevServerOutput + chunk).takeLast(10_000)
                val detectedUrl = DevServerDetector.detectUrl(recentDevServerOutput)
                if (detectedUrl != null) {
                    _uiState.update {
                        it.copy(
                            devServerUrl = detectedUrl,
                            isStartingDevServer = false,
                            devServerError = null
                        )
                    }
                }

                val lower = chunk.lowercase()
                val npmMissing = "npm: inaccessible or not found" in lower ||
                    "npm: not found" in lower ||
                    "command not found" in lower
                val nodeMissing = "node: inaccessible or not found" in lower ||
                    "node: not found" in lower

                if (_uiState.value.isStartingDevServer && npmMissing && !devServerFallbackTried) {
                    devServerFallbackTried = true
                    val hasNodeBinary = terminalBootstrap.getNativeBinaryPath("node") != null
                    val hasServerJs = File(projectDir, "server.js").exists()
                    if (hasNodeBinary && hasServerJs) {
                        newSession.sendCommand("node server.js")
                    } else if (fallbackToStaticPreview(projectDir)) {
                        stopDevServer()
                    } else {
                        val message = "No hay runtime Node/NPM en Android y no existe index.html para preview estatico"
                        _uiState.update {
                            it.copy(
                                isStartingDevServer = false,
                                devServerError = message,
                                error = message
                            )
                        }
                    }
                } else if (_uiState.value.isStartingDevServer && nodeMissing) {
                    val message = "No hay runtime Node embebido en esta build. Agrega libnode.so/libdash.so en jniLibs/arm64-v8a"
                    _uiState.update {
                        it.copy(
                            isStartingDevServer = false,
                            devServerError = message,
                            error = message
                        )
                    }
                } else if (_uiState.value.isStartingDevServer && (
                        "enoent" in lower ||
                            "cannot find module" in lower ||
                            "missing script: dev" in lower
                        )
                ) {
                    val message = "No se pudo iniciar '$devCommand'. Revisa scripts.dev y dependencias"
                    _uiState.update {
                        it.copy(
                            isStartingDevServer = false,
                            devServerError = message,
                            error = message
                        )
                    }
                }
            }
        }

        devServerExitWatcherJob = viewModelScope.launch {
            newSession.exitCode.collect { code ->
                if (code != null && _uiState.value.devServerUrl == null) {
                    val tail = recentDevServerOutput
                        .lines()
                        .takeLast(3)
                        .joinToString(" ")
                        .trim()
                    val detail = if (tail.isNotEmpty()) " - $tail" else ""
                    _uiState.update {
                        it.copy(
                            isStartingDevServer = false,
                            devServerError = "'$devCommand' finalizo con codigo $code$detail",
                            error = "'$devCommand' finalizo con codigo $code"
                        )
                    }
                }
            }
        }

        devServerStartupTimeoutJob = viewModelScope.launch {
            delay(20_000)
            if (_uiState.value.isStartingDevServer && _uiState.value.devServerUrl == null) {
                val tail = recentDevServerOutput
                    .lines()
                    .takeLast(3)
                    .joinToString(" ")
                    .trim()
                val detail = if (tail.isNotEmpty()) "\nDetalle: $tail" else ""
                val message = "El servidor no respondió en 20s ejecutando '$devCommand'.$detail"
                _uiState.update {
                    it.copy(
                        isStartingDevServer = false,
                        devServerError = message,
                        error = message
                    )
                }
            }
        }

        newSession.sendCommand(devCommand)
    }

    private fun fallbackToStaticPreview(projectDir: File): Boolean {
        val previewFile = File(projectDir, "preview-static.html")
            .takeIf { it.exists() }
            ?: File(projectDir, "index.html").takeIf { it.exists() }
            ?: return false

        _uiState.update {
            it.copy(
                devServerUrl = previewFile.toURI().toString(),
                isStartingDevServer = false,
                devServerError = null
            )
        }
        return true
    }

    private fun resolveDevCommand(packageJson: File): String? {
        return runCatching {
            val json = JSONObject(packageJson.readText())
            json.optJSONObject("scripts")
                ?.optString("dev")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun stopDevServer() {
        devServerOutputJob?.cancel()
        devServerExitWatcherJob?.cancel()
        devServerStartupTimeoutJob?.cancel()
        devServerSession?.destroy()
        devServerOutputJob = null
        devServerExitWatcherJob = null
        devServerStartupTimeoutJob = null
        devServerSession = null
    }

    override fun onCleared() {
        stopDevServer()
        super.onCleared()
    }

    fun switchSession(newSessionId: String) {
        viewModelScope.launch {
            val session = sessionRepository.getById(newSessionId) ?: return@launch
            val project = projectRepository.getById(session.projectId)
            stopDevServer()

            _uiState.update {
                it.copy(
                    session = session,
                    project = project,
                    sessionMode = session.mode,
                    noSession = false,
                    messages = emptyList(),
                    selectedFilePath = null,
                    selectedFilePreview = null,
                    devServerUrl = null,
                    isStartingDevServer = false,
                    devServerError = null
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
                    readFilePreview(
                        projectRoot = File(project.path),
                        targetFile = File(absolutePath)
                    )
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

    private data class InsightsSnapshot(
        val modifiedFiles: List<SessionFileChange>,
        val fileTree: List<ProjectFileNode>
    )
}
