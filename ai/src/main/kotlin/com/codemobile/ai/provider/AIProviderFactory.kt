package com.codemobile.ai.provider

import com.codemobile.ai.registry.ProviderRegistry
import com.codemobile.ai.registry.ProviderRegistry.ApiType
import com.codemobile.core.data.repository.ProviderRepository
import com.codemobile.core.model.ProviderConfig
import com.codemobile.core.model.ProviderType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates AIProvider instances from stored ProviderConfig.
 * Uses the ProviderRegistry to determine API type, base URL, etc.
 * Falls back to legacy ProviderType enum for backward compatibility.
 */
@Singleton
class AIProviderFactory @Inject constructor(
    private val providerRepository: ProviderRepository
) {

    companion object {
        /** OpenAI Codex endpoint (ChatGPT Plus/Pro subscription) */
        private const val CODEX_API_ENDPOINT = "https://chatgpt.com/backend-api/codex/responses"

        /** Providers that don't support GET /models for validation */
        private val SKIP_VALIDATION_PROVIDERS = setOf(
            "github-copilot", "github-copilot-enterprise",
            "moonshot", "minimax", "cohere", "ai21",
            "sambanova", "dashscope", "volcengine", "novita",
            "replicate", "siliconflow", "hyperbolic", "lambda",
            "lepton", "coze", "stepfun", "01ai",
            "inferencenet", "klusterai", "chutes"
        )

        /** Build Copilot-specific headers matching OpenCode's copilot.ts */
        fun buildCopilotHeaders(): Map<String, String> = mapOf(
            "Openai-Intent" to "conversation-edits",
            "User-Agent" to "CodeMobile/1.0",
            "x-initiator" to "agent",
            "Copilot-Vision-Request" to "true",
            "originator" to "codemobile"
        )

        /** Build Codex-specific headers matching OpenCode's codex.ts */
        fun buildCodexHeaders(accountId: String? = null): Map<String, String> = buildMap {
            put("originator", "codemobile")
            put("User-Agent", "CodeMobile/1.0")
            accountId?.let { put("ChatGPT-Account-Id", it) }
        }
    }

    /**
     * Create an AIProvider instance for the given configuration.
     * @throws IllegalStateException if credentials are missing
     */
    fun create(config: ProviderConfig): AIProvider {
        // New path: use registryId if available
        val registryDef = config.registryId.takeIf { it.isNotBlank() }
            ?.let { ProviderRegistry.getById(it) }

        if (registryDef != null) {
            return createFromRegistry(config, registryDef)
        }

        // Legacy path: fall back to ProviderType enum
        return createLegacy(config)
    }

    private fun createFromRegistry(
        config: ProviderConfig,
        def: ProviderRegistry.ProviderDef
    ): AIProvider {
        val apiKey = if (config.isOAuth) {
            // For Codex OAuth, use the stored access token (refreshed externally)
            providerRepository.getAccessToken(config.id)
                ?: providerRepository.getOAuthToken(config.id)
                ?: throw IllegalStateException("No OAuth token for ${config.displayName}. Please authenticate.")
        } else {
            providerRepository.getApiKey(config.id)
                ?: throw IllegalStateException("No API key configured for ${config.displayName}")
        }

        val baseUrl = config.baseUrl ?: def.apiBaseUrl
        val shouldSkipValidation = def.id in SKIP_VALIDATION_PROVIDERS

        return when (def.apiType) {
            ApiType.ANTHROPIC -> ClaudeProvider(
                id = config.id,
                name = config.displayName,
                apiKey = apiKey
            )
            ApiType.OPENAI, ApiType.OPENAI_COMPATIBLE, ApiType.GOOGLE -> {
                // Determine extra headers and endpoint for special providers
                val (headers, endpoint) = when (def.id) {
                    "github-copilot", "github-copilot-enterprise" -> {
                        buildCopilotHeaders() to null
                    }
                    "openai" -> {
                        // Check if this is a Codex (ChatGPT Plus/Pro) auth
                        if (config.isOAuth) {
                            val accountId = providerRepository.getAccountId(config.id)
                            buildCodexHeaders(accountId) to CODEX_API_ENDPOINT
                        } else {
                            emptyMap<String, String>() to null
                        }
                    }
                    else -> emptyMap<String, String>() to null
                }

                OpenAIProvider(
                    id = config.id,
                    name = config.displayName,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    extraHeaders = headers,
                    chatEndpointOverride = endpoint,
                    skipValidation = shouldSkipValidation
                )
            }
        }
    }

    private fun createLegacy(config: ProviderConfig): AIProvider {
        return when (config.type) {
            ProviderType.OPENAI -> {
                val apiKey = providerRepository.getApiKey(config.id)
                    ?: throw IllegalStateException("No API key configured for ${config.displayName}")
                OpenAIProvider(id = config.id, name = config.displayName, apiKey = apiKey)
            }
            ProviderType.CLAUDE -> {
                val apiKey = providerRepository.getApiKey(config.id)
                    ?: throw IllegalStateException("No API key configured for ${config.displayName}")
                ClaudeProvider(id = config.id, name = config.displayName, apiKey = apiKey)
            }
            ProviderType.GEMINI -> {
                val apiKey = providerRepository.getApiKey(config.id)
                    ?: throw IllegalStateException("No API key configured for ${config.displayName}")
                OpenAIProvider(
                    id = config.id, name = config.displayName, apiKey = apiKey,
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
                )
            }
            ProviderType.COPILOT -> {
                val token = providerRepository.getOAuthToken(config.id)
                    ?: throw IllegalStateException("No OAuth token for ${config.displayName}.")
                OpenAIProvider(
                    id = config.id, name = config.displayName, apiKey = token,
                    baseUrl = "https://api.githubcopilot.com",
                    extraHeaders = buildCopilotHeaders(),
                    skipValidation = true
                )
            }
            ProviderType.OPENAI_COMPATIBLE -> {
                val apiKey = providerRepository.getApiKey(config.id) ?: ""
                val baseUrl = config.baseUrl
                    ?: throw IllegalStateException("No base URL configured for ${config.displayName}")
                OpenAIProvider(
                    id = config.id, name = config.displayName, apiKey = apiKey, baseUrl = baseUrl,
                    skipValidation = true
                )
            }
        }
    }

    /**
     * Create a provider only if credentials exist. Returns null otherwise.
     */
    fun createOrNull(config: ProviderConfig): AIProvider? {
        return try {
            create(config)
        } catch (e: IllegalStateException) {
            null
        }
    }
}
