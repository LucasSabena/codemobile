package com.codemobile.ui.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemobile.ai.auth.BrowserOAuth
import com.codemobile.ai.auth.GitHubCopilotAuth
import com.codemobile.ai.auth.OpenAICodexAuth
import com.codemobile.ai.provider.AIProviderFactory
import com.codemobile.ai.registry.ProviderRegistry
import com.codemobile.ai.registry.ProviderRegistry.AuthMethod
import com.codemobile.ai.registry.ProviderRegistry.ProviderDef
import com.codemobile.core.data.repository.ProviderRepository
import com.codemobile.core.model.ProviderConfig
import com.codemobile.core.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

// ── UI State ─────────────────────────────────────────────────────

data class ConnectProviderUiState(
    val searchQuery: String = "",
    val popularProviders: List<ProviderDef> = ProviderRegistry.getPopular(),
    val otherProviders: List<ProviderDef> = ProviderRegistry.getOther(),
    val cloudProviders: List<ProviderDef> = ProviderRegistry.getByCategory(ProviderRegistry.ProviderCategory.CLOUD),
    val routerProviders: List<ProviderDef> = ProviderRegistry.getByCategory(ProviderRegistry.ProviderCategory.ROUTER) +
            ProviderRegistry.getByCategory(ProviderRegistry.ProviderCategory.GATEWAY),
    val localProviders: List<ProviderDef> = ProviderRegistry.getByCategory(ProviderRegistry.ProviderCategory.LOCAL),
    val customProviders: List<ProviderDef> = ProviderRegistry.getByCategory(ProviderRegistry.ProviderCategory.OTHER),
    val filteredProviders: List<ProviderDef> = emptyList(),
    val isSearching: Boolean = false,
    val connectedProviderIds: Set<String> = emptySet()
)

data class AuthUiState(
    val provider: ProviderDef? = null,
    val authMethods: List<AuthMethod> = emptyList(),
    val selectedMethod: AuthMethod? = null,
    // API Key auth
    val apiKey: String = "",
    val customBaseUrl: String = "",
    val customModelId: String = "",
    // OAuth state (GitHub Device Flow)
    val oauthUserCode: String = "",
    val oauthVerificationUri: String = "",
    val isOAuthPolling: Boolean = false,
    // Browser OAuth state
    val browserOAuthUrl: String? = null,
    val dashboardUrl: String? = null,
    val isWaitingBrowserAuth: Boolean = false,
    // Common
    val isValidating: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null
)

/** One-shot events for navigation */
sealed class ProviderUiEvent {
    data object NavigateToAuth : ProviderUiEvent()
    data object NavigateBack : ProviderUiEvent()
    data object ProviderConnected : ProviderUiEvent()
    data class OpenBrowser(val url: String) : ProviderUiEvent()
}

// ── ViewModel ────────────────────────────────────────────────────

@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val aiProviderFactory: AIProviderFactory
) : ViewModel() {

    private val _connectState = MutableStateFlow(ConnectProviderUiState())
    val connectState: StateFlow<ConnectProviderUiState> = _connectState.asStateFlow()

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _events = MutableSharedFlow<ProviderUiEvent>()
    val events: SharedFlow<ProviderUiEvent> = _events.asSharedFlow()

    init {
        loadConnectedProviders()
    }

    private fun loadConnectedProviders() {
        viewModelScope.launch {
            providerRepository.getAllProviders().collect { providers ->
                val connectedIds = providers.map { it.registryId }.toSet()
                _connectState.update { it.copy(connectedProviderIds = connectedIds) }
            }
        }
    }

    // ── Connect Provider Screen actions ──────────────────────────

    fun onSearchQueryChanged(query: String) {
        val filtered = if (query.isBlank()) emptyList() else ProviderRegistry.search(query)
        _connectState.update {
            it.copy(
                searchQuery = query,
                isSearching = query.isNotBlank(),
                filteredProviders = filtered
            )
        }
    }

    fun onProviderSelected(provider: ProviderDef) {
        val methods = provider.authMethods
        _authState.update {
            AuthUiState(
                provider = provider,
                authMethods = methods,
                selectedMethod = if (methods.size == 1) methods.first() else null,
                customBaseUrl = if (provider.id == "custom") "" else provider.apiBaseUrl
            )
        }

        // If only one auth method, go straight to auth
        viewModelScope.launch {
            _events.emit(ProviderUiEvent.NavigateToAuth)
        }
    }

    // ── Auth Screen actions ──────────────────────────────────────

    fun onAuthMethodSelected(method: AuthMethod) {
        _authState.update { it.copy(selectedMethod = method, error = null) }
    }

    fun onApiKeyChanged(key: String) {
        _authState.update { it.copy(apiKey = key, error = null) }
    }

    fun onCustomBaseUrlChanged(url: String) {
        _authState.update { it.copy(customBaseUrl = url) }
    }

    fun onCustomModelIdChanged(modelId: String) {
        _authState.update { it.copy(customModelId = modelId) }
    }

    fun onConnect() {
        val state = _authState.value
        val provider = state.provider ?: return

        when (state.selectedMethod) {
            AuthMethod.API_KEY -> connectWithApiKey(provider, state)
            AuthMethod.OAUTH_GITHUB -> startGitHubOAuth(provider)
            AuthMethod.OAUTH_BROWSER -> startBrowserOAuth(provider)
            AuthMethod.OAUTH_OPENAI_CODEX -> startOpenAICodexAuth(provider)
            null -> _authState.update { it.copy(error = "Elegí un método de autenticación") }
        }
    }

    private fun connectWithApiKey(provider: ProviderDef, state: AuthUiState) {
        if (state.apiKey.isBlank()) {
            _authState.update { it.copy(error = "Ingresá tu API key") }
            return
        }
        if (provider.id == "custom" && state.customBaseUrl.isBlank()) {
            _authState.update { it.copy(error = "Ingresá la URL base del provider") }
            return
        }

        _authState.update { it.copy(isValidating = true, error = null) }

        viewModelScope.launch {
            try {
                val providerType = mapRegistryToProviderType(provider)
                val baseUrl = if (provider.id == "custom") state.customBaseUrl else provider.apiBaseUrl
                val defaultModel = provider.models.firstOrNull()?.id

                val config = ProviderConfig(
                    type = providerType,
                    displayName = provider.name,
                    registryId = provider.id,
                    defaultModelId = defaultModel,
                    selectedModelId = defaultModel,
                    baseUrl = baseUrl,
                    isOAuth = false,
                    isActive = true
                )

                // Save config first
                providerRepository.save(config)
                providerRepository.saveApiKey(config.id, state.apiKey)

                android.util.Log.d("ProviderVM", "Saved provider ${provider.id} with config.id=${config.id}, registryId=${config.registryId}")

                // Validate
                val aiProvider = aiProviderFactory.createOrNull(config)
                val skipList = setOf("github-copilot", "moonshot", "kimi-coding", "minimax")
                android.util.Log.d("ProviderVM", "Created provider: ${aiProvider != null}, skipValidation=${provider.id in skipList}")
                val isValid = aiProvider?.validateCredentials() ?: false
                android.util.Log.d("ProviderVM", "Validation result: $isValid")

                if (isValid) {
                    _authState.update { it.copy(isValidating = false, isConnected = true) }
                    _events.emit(ProviderUiEvent.ProviderConnected)
                } else if (aiProvider != null) {
                    // Provider was created but validation failed.
                    // Many OpenAI-compatible providers don't support GET /models.
                    // Keep the config and let the user try using it.
                    android.util.Log.w("ProviderVM", "Validation failed for ${provider.id} — saving anyway")
                    _authState.update { it.copy(isValidating = false, isConnected = true) }
                    _events.emit(ProviderUiEvent.ProviderConnected)
                } else {
                    // Provider could not even be created — credentials missing
                    providerRepository.delete(config)
                    _authState.update {
                        it.copy(
                            isValidating = false,
                            error = "No se pudo crear el proveedor. Verificá tus credenciales."
                        )
                    }
                }
            } catch (e: Exception) {
                _authState.update {
                    it.copy(
                        isValidating = false,
                        error = e.message ?: "Error al conectar"
                    )
                }
            }
        }
    }

    private fun startGitHubOAuth(provider: ProviderDef) {
        _authState.update { it.copy(isValidating = true, error = null) }

        viewModelScope.launch {
            GitHubCopilotAuth.startDeviceFlow().collect { event ->
                when (event) {
                    is GitHubCopilotAuth.OAuthEvent.ShowCode -> {
                        _authState.update {
                            it.copy(
                                oauthUserCode = event.userCode,
                                oauthVerificationUri = event.verificationUri,
                                isOAuthPolling = true,
                                isValidating = false
                            )
                        }
                        _events.emit(ProviderUiEvent.OpenBrowser(event.verificationUri))
                    }

                    is GitHubCopilotAuth.OAuthEvent.Polling -> {
                        // Keep polling indicator active
                    }

                    is GitHubCopilotAuth.OAuthEvent.Success -> {
                        // Save the token
                        val config = ProviderConfig(
                            type = ProviderType.COPILOT,
                            displayName = provider.name,
                            registryId = provider.id,
                            defaultModelId = provider.models.firstOrNull()?.id,
                            selectedModelId = provider.models.firstOrNull()?.id,
                            baseUrl = provider.apiBaseUrl,
                            isOAuth = true,
                            isActive = true
                        )
                        providerRepository.save(config)
                        providerRepository.saveOAuthToken(config.id, event.accessToken)

                        _authState.update {
                            it.copy(
                                isOAuthPolling = false,
                                isConnected = true
                            )
                        }
                        _events.emit(ProviderUiEvent.ProviderConnected)
                    }

                    is GitHubCopilotAuth.OAuthEvent.Error -> {
                        _authState.update {
                            it.copy(
                                isOAuthPolling = false,
                                isValidating = false,
                                error = event.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearError() {
        _authState.update { it.copy(error = null) }
    }

    // ── OpenAI Codex Device Flow (ChatGPT Plus/Pro) ─────────────

    private fun startOpenAICodexAuth(provider: ProviderDef) {
        _authState.update { it.copy(isValidating = true, error = null) }

        viewModelScope.launch {
            OpenAICodexAuth.startDeviceFlow().collect { event ->
                when (event) {
                    is OpenAICodexAuth.CodexEvent.ShowCode -> {
                        _authState.update {
                            it.copy(
                                oauthUserCode = event.userCode,
                                oauthVerificationUri = event.verificationUri,
                                isOAuthPolling = true,
                                isValidating = false
                            )
                        }
                        _events.emit(ProviderUiEvent.OpenBrowser(event.verificationUri))
                    }

                    is OpenAICodexAuth.CodexEvent.Polling -> {
                        // Keep polling indicator active
                    }

                    is OpenAICodexAuth.CodexEvent.Success -> {
                        val result = event.result
                        // Select a Codex model by default when using ChatGPT subscription
                        val codexModel = provider.models.firstOrNull { it.id.contains("codex") }
                            ?: provider.models.firstOrNull()

                        val config = ProviderConfig(
                            type = ProviderType.OPENAI,
                            displayName = "ChatGPT Plus/Pro",
                            registryId = provider.id,
                            defaultModelId = codexModel?.id,
                            selectedModelId = codexModel?.id,
                            baseUrl = provider.apiBaseUrl,
                            isOAuth = true,
                            isActive = true
                        )
                        providerRepository.save(config)

                        // Store all Codex tokens
                        providerRepository.saveAccessToken(config.id, result.accessToken)
                        providerRepository.saveRefreshToken(config.id, result.refreshToken)
                        providerRepository.saveTokenExpiry(config.id, result.expiresAt)
                        providerRepository.saveOAuthToken(config.id, result.accessToken)
                        result.accountId?.let {
                            providerRepository.saveAccountId(config.id, it)
                        }

                        _authState.update {
                            it.copy(
                                isOAuthPolling = false,
                                isConnected = true
                            )
                        }
                        _events.emit(ProviderUiEvent.ProviderConnected)
                    }

                    is OpenAICodexAuth.CodexEvent.Error -> {
                        _authState.update {
                            it.copy(
                                isOAuthPolling = false,
                                isValidating = false,
                                error = event.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Open the provider's API key dashboard in the browser.
     * This is shown as a convenience on all API_KEY providers.
     */
    fun openDashboard() {
        val provider = _authState.value.provider ?: return
        val url = BrowserOAuth.getDashboardUrl(provider.id) ?: return
        viewModelScope.launch {
            _events.emit(ProviderUiEvent.OpenBrowser(url))
        }
    }

    private fun startBrowserOAuth(provider: ProviderDef) {
        val oauthConfig = BrowserOAuth.getOAuthConfig(provider.id)

        if (oauthConfig != null && oauthConfig.isFullOAuth) {
            // Full OAuth Authorization Code + PKCE flow
            startFullBrowserOAuth(provider, oauthConfig)
        } else {
            // Fallback: Open dashboard for manual API key copy
            val dashboardUrl = BrowserOAuth.getDashboardUrl(provider.id)
            if (dashboardUrl != null) {
                _authState.update {
                    it.copy(
                        dashboardUrl = dashboardUrl,
                        // Switch to API key method so user can paste the key
                        selectedMethod = AuthMethod.API_KEY,
                        error = null
                    )
                }
                viewModelScope.launch {
                    _events.emit(ProviderUiEvent.OpenBrowser(dashboardUrl))
                }
            } else {
                _authState.update {
                    it.copy(error = "Este provider no tiene OAuth por navegador. Usá API Key.")
                }
            }
        }
    }

    private fun startFullBrowserOAuth(
        provider: ProviderDef,
        oauthConfig: BrowserOAuth.OAuthConfig
    ) {
        _authState.update { it.copy(isValidating = true, isWaitingBrowserAuth = true, error = null) }

        val pkce = BrowserOAuth.generatePkce()
        val state = UUID.randomUUID().toString()

        val authUrl = BrowserOAuth.buildAuthorizationUrl(oauthConfig, pkce, state)
        _authState.update { it.copy(browserOAuthUrl = authUrl, isValidating = false) }

        viewModelScope.launch {
            // Open browser
            _events.emit(ProviderUiEvent.OpenBrowser(authUrl))

            try {
                // Wait for callback
                val code = BrowserOAuth.waitForCallback(state)
                if (code == null) {
                    _authState.update {
                        it.copy(
                            isWaitingBrowserAuth = false,
                            error = "Timeout: no se recibió respuesta del navegador"
                        )
                    }
                    return@launch
                }

                _authState.update { it.copy(isValidating = true) }

                // Exchange code for token
                val tokenResponse = withContext(Dispatchers.IO) {
                    BrowserOAuth.exchangeCodeForToken(oauthConfig, code, pkce)
                }

                val accessToken = tokenResponse?.accessToken
                if (!accessToken.isNullOrBlank()) {
                    val providerType = mapRegistryToProviderType(provider)
                    val config = ProviderConfig(
                        type = providerType,
                        displayName = provider.name,
                        registryId = provider.id,
                        defaultModelId = provider.models.firstOrNull()?.id,
                        selectedModelId = provider.models.firstOrNull()?.id,
                        baseUrl = provider.apiBaseUrl,
                        isOAuth = true,
                        isActive = true
                    )
                    providerRepository.save(config)
                    providerRepository.saveOAuthToken(config.id, accessToken)

                    _authState.update {
                        it.copy(
                            isValidating = false,
                            isWaitingBrowserAuth = false,
                            isConnected = true
                        )
                    }
                    _events.emit(ProviderUiEvent.ProviderConnected)
                } else {
                    _authState.update {
                        it.copy(
                            isValidating = false,
                            isWaitingBrowserAuth = false,
                            error = tokenResponse?.errorDescription
                                ?: tokenResponse?.error
                                ?: "Error al obtener el token"
                        )
                    }
                }
            } catch (e: BrowserOAuth.OAuthException) {
                _authState.update {
                    it.copy(
                        isValidating = false,
                        isWaitingBrowserAuth = false,
                        error = e.message
                    )
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Map registry provider ID to legacy ProviderType for DB compat.
     */
    private fun mapRegistryToProviderType(provider: ProviderDef): ProviderType {
        return when (provider.apiType) {
            ProviderRegistry.ApiType.ANTHROPIC -> ProviderType.CLAUDE
            ProviderRegistry.ApiType.OPENAI -> ProviderType.OPENAI
            ProviderRegistry.ApiType.GOOGLE -> ProviderType.GEMINI
            ProviderRegistry.ApiType.OPENAI_COMPATIBLE -> ProviderType.OPENAI_COMPATIBLE
        }
    }
}
