package com.codemobile.ai.registry

/**
 * Static registry of all known AI providers and their models.
 * Inspired by OpenCode's models.dev approach — hardcoded for offline use on mobile.
 */
object ProviderRegistry {

    /** Auth method that a provider supports */
    enum class AuthMethod {
        API_KEY,
        OAUTH_GITHUB,        // GitHub Device Flow OAuth (Copilot)
        OAUTH_BROWSER,       // Generic browser-based OAuth
        OAUTH_OPENAI_CODEX,  // OpenAI Codex Device Flow (ChatGPT Plus/Pro)
    }

    /** A known model definition */
    data class ModelDef(
        val id: String,
        val name: String,
        val family: String = "",
        val contextWindow: Int = 128_000,
        val maxOutput: Int = 8_192,
        val supportsTools: Boolean = true,
        val supportsVision: Boolean = false,
        val supportsReasoning: Boolean = false,
        val costInputPer1M: Double = 0.0,
        val costOutputPer1M: Double = 0.0,
        val status: ModelStatus = ModelStatus.ACTIVE
    )

    enum class ModelStatus { ACTIVE, BETA, DEPRECATED }

    /** A known provider definition */
    data class ProviderDef(
        val id: String,
        val name: String,
        val description: String,
        val iconRes: String? = null,  // resource name
        val apiBaseUrl: String,
        val npmPackage: String = "@ai-sdk/openai-compatible",
        val envKeys: List<String> = emptyList(),
        val authMethods: List<AuthMethod>,
        val models: List<ModelDef>,
        val isPopular: Boolean = false,
        val category: ProviderCategory = ProviderCategory.OTHER,
        val apiType: ApiType = ApiType.OPENAI_COMPATIBLE,
    )

    enum class ApiType {
        OPENAI,          // OpenAI Chat/Responses API
        ANTHROPIC,       // Anthropic Messages API
        OPENAI_COMPATIBLE, // OpenAI-compatible (majority of providers)
        GOOGLE,          // Google Gemini API
    }

    enum class ProviderCategory {
        POPULAR,
        CLOUD,
        OPEN_SOURCE,
        ROUTER,
        GATEWAY,
        LOCAL,
        OTHER
    }

    // ─── Popular Providers ───────────────────────────────────────────

    private val anthropic = ProviderDef(
        id = "anthropic",
        name = "Anthropic",
        description = "Acceso directo a modelos Claude, incluyendo Pro y Max",
        apiBaseUrl = "https://api.anthropic.com/v1",
        envKeys = listOf("ANTHROPIC_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        isPopular = true,
        category = ProviderCategory.POPULAR,
        apiType = ApiType.ANTHROPIC,
        models = listOf(
            ModelDef(
                id = "claude-sonnet-4-5", name = "Claude Sonnet 4.5",
                family = "claude-sonnet", contextWindow = 200_000, maxOutput = 64_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 3.0, costOutputPer1M = 15.0
            ),
            ModelDef(
                id = "claude-haiku-4-5", name = "Claude Haiku 4.5",
                family = "claude-haiku", contextWindow = 200_000, maxOutput = 64_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 1.1, costOutputPer1M = 5.5
            ),
            ModelDef(
                id = "claude-opus-4-5-20251101", name = "Claude Opus 4.5",
                family = "claude-opus", contextWindow = 200_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 5.0, costOutputPer1M = 25.0
            ),
            ModelDef(
                id = "claude-opus-4-1", name = "Claude Opus 4.1",
                family = "claude-opus", contextWindow = 200_000, maxOutput = 32_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 15.0, costOutputPer1M = 75.0
            ),
        )
    )

    private val githubCopilot = ProviderDef(
        id = "github-copilot",
        name = "GitHub Copilot",
        description = "Modelos Claude para asistencia de codificación",
        apiBaseUrl = "https://api.githubcopilot.com",
        authMethods = listOf(AuthMethod.OAUTH_GITHUB),
        isPopular = true,
        category = ProviderCategory.POPULAR,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(
                id = "claude-sonnet-4-5", name = "Claude Sonnet 4.5",
                family = "claude-sonnet", contextWindow = 200_000, maxOutput = 64_000,
                supportsTools = true, supportsVision = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "gpt-5", name = "GPT-5",
                family = "gpt", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "gpt-5-mini", name = "GPT-5 Mini",
                family = "gpt-mini", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "gemini-3-flash", name = "Gemini 3 Flash",
                family = "gemini-flash", contextWindow = 1_048_576, maxOutput = 65_536,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "claude-haiku-4-5", name = "Claude Haiku 4.5",
                family = "claude-haiku", contextWindow = 200_000, maxOutput = 64_000,
                supportsTools = true, supportsVision = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
        )
    )

    private val openai = ProviderDef(
        id = "openai",
        name = "OpenAI",
        description = "Modelos GPT para tareas de IA generales rápidas y capaces",
        apiBaseUrl = "https://api.openai.com/v1",
        envKeys = listOf("OPENAI_API_KEY"),
        authMethods = listOf(AuthMethod.OAUTH_OPENAI_CODEX, AuthMethod.API_KEY),
        isPopular = true,
        category = ProviderCategory.POPULAR,
        apiType = ApiType.OPENAI,
        models = listOf(
            ModelDef(
                id = "gpt-5", name = "GPT-5",
                family = "gpt", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 2.0, costOutputPer1M = 8.0
            ),
            ModelDef(
                id = "gpt-5-mini", name = "GPT-5 Mini",
                family = "gpt-mini", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.3, costOutputPer1M = 1.2
            ),
            ModelDef(
                id = "gpt-5-nano", name = "GPT-5 Nano",
                family = "gpt-nano", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "gpt-5.1", name = "GPT-5.1",
                family = "gpt", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 2.5, costOutputPer1M = 10.0
            ),
            ModelDef(
                id = "gpt-5.1-codex", name = "GPT-5.1 Codex",
                family = "gpt-codex", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0 // Free with ChatGPT subscription
            ),
            ModelDef(
                id = "gpt-5.1-codex-mini", name = "GPT-5.1 Codex Mini",
                family = "gpt-codex", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "gpt-5.2", name = "GPT-5.2",
                family = "gpt", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 2.5, costOutputPer1M = 10.0
            ),
            ModelDef(
                id = "gpt-5.2-codex", name = "GPT-5.2 Codex",
                family = "gpt-codex", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "gpt-5.3-codex", name = "GPT-5.3 Codex",
                family = "gpt-codex", contextWindow = 400_000, maxOutput = 128_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.0, costOutputPer1M = 0.0
            ),
            ModelDef(
                id = "gpt-5-pro", name = "GPT-5 Pro",
                family = "gpt-pro", contextWindow = 400_000, maxOutput = 272_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 15.0, costOutputPer1M = 120.0
            ),
        )
    )

    private val google = ProviderDef(
        id = "google",
        name = "Google",
        description = "Modelos Gemini con contexto extendido y capacidades multimodales",
        apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        envKeys = listOf("GOOGLE_GENERATIVE_AI_API_KEY", "GEMINI_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        isPopular = true,
        category = ProviderCategory.POPULAR,
        apiType = ApiType.GOOGLE,
        models = listOf(
            ModelDef(
                id = "gemini-3-flash", name = "Gemini 3 Flash",
                family = "gemini-flash", contextWindow = 1_048_576, maxOutput = 65_536,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.5, costOutputPer1M = 3.0
            ),
            ModelDef(
                id = "gemini-3-pro-preview", name = "Gemini 3 Pro Preview",
                family = "gemini-pro", contextWindow = 1_000_000, maxOutput = 64_000,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 2.0, costOutputPer1M = 12.0
            ),
            ModelDef(
                id = "gemini-2.5-flash", name = "Gemini 2.5 Flash",
                family = "gemini-flash", contextWindow = 1_048_576, maxOutput = 65_536,
                supportsTools = true, supportsVision = true, supportsReasoning = true,
                costInputPer1M = 0.3, costOutputPer1M = 2.5
            ),
        )
    )

    // ─── Routers ─────────────────────────────────────────────────────

    private val openRouter = ProviderDef(
        id = "openrouter",
        name = "OpenRouter",
        description = "Acceso a múltiples providers desde una sola API key",
        apiBaseUrl = "https://openrouter.ai/api/v1",
        envKeys = listOf("OPENROUTER_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        isPopular = true,
        category = ProviderCategory.ROUTER,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "anthropic/claude-sonnet-4-5", name = "Claude Sonnet 4.5", family = "claude-sonnet", contextWindow = 200_000, maxOutput = 64_000, supportsTools = true, supportsVision = true, costInputPer1M = 3.0, costOutputPer1M = 15.0),
            ModelDef(id = "openai/gpt-5", name = "GPT-5", family = "gpt", contextWindow = 400_000, maxOutput = 128_000, supportsTools = true, supportsVision = true, costInputPer1M = 2.0, costOutputPer1M = 8.0),
            ModelDef(id = "google/gemini-3-flash", name = "Gemini 3 Flash", family = "gemini-flash", contextWindow = 1_048_576, maxOutput = 65_536, supportsTools = true, supportsVision = true, costInputPer1M = 0.5, costOutputPer1M = 3.0),
            ModelDef(id = "google/gemini-3-pro-preview", name = "Gemini 3 Pro Preview", family = "gemini-pro", contextWindow = 1_000_000, maxOutput = 64_000, supportsTools = true, supportsVision = true, costInputPer1M = 2.0, costOutputPer1M = 12.0),
            ModelDef(id = "deepseek/deepseek-v3.2", name = "DeepSeek V3.2", family = "deepseek", contextWindow = 163_840, maxOutput = 163_840, supportsTools = true, costInputPer1M = 0.27, costOutputPer1M = 0.41),
            ModelDef(id = "x-ai/grok-4", name = "Grok 4", family = "grok", contextWindow = 256_000, maxOutput = 64_000, supportsTools = true, supportsReasoning = true, costInputPer1M = 3.0, costOutputPer1M = 15.0),
        )
    )

    // ─── Cloud Providers ─────────────────────────────────────────────

    private val xai = ProviderDef(
        id = "xai",
        name = "xAI",
        description = "Modelos Grok de xAI",
        apiBaseUrl = "https://api.x.ai/v1",
        envKeys = listOf("XAI_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "grok-4", name = "Grok 4", family = "grok", contextWindow = 256_000, maxOutput = 64_000, supportsTools = true, supportsReasoning = true, costInputPer1M = 3.0, costOutputPer1M = 15.0),
            ModelDef(id = "grok-3-latest", name = "Grok 3", family = "grok", contextWindow = 131_072, maxOutput = 8_192, supportsTools = true, costInputPer1M = 3.0, costOutputPer1M = 15.0),
            ModelDef(id = "grok-3-mini-latest", name = "Grok 3 Mini", family = "grok", contextWindow = 131_072, maxOutput = 8_192, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.3, costOutputPer1M = 0.5),
        )
    )

    private val deepseek = ProviderDef(
        id = "deepseek",
        name = "DeepSeek",
        description = "Modelos open-source potentes y económicos",
        apiBaseUrl = "https://api.deepseek.com",
        envKeys = listOf("DEEPSEEK_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-chat", name = "DeepSeek V3", family = "deepseek", contextWindow = 128_000, maxOutput = 32_000, supportsTools = true, costInputPer1M = 0.14, costOutputPer1M = 0.28),
            ModelDef(id = "deepseek-reasoner", name = "DeepSeek Reasoner", family = "deepseek-thinking", contextWindow = 128_000, maxOutput = 32_000, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.55, costOutputPer1M = 2.19),
        )
    )

    private val mistral = ProviderDef(
        id = "mistral",
        name = "Mistral",
        description = "Modelos europeos con gran rendimiento en código",
        apiBaseUrl = "https://api.mistral.ai/v1",
        envKeys = listOf("MISTRAL_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "devstral-2512", name = "Devstral 2", family = "devstral", contextWindow = 262_144, maxOutput = 262_144, supportsTools = true, costInputPer1M = 0.4, costOutputPer1M = 2.0),
            ModelDef(id = "devstral-small-2507", name = "Devstral Small", family = "devstral", contextWindow = 128_000, maxOutput = 128_000, supportsTools = true, costInputPer1M = 0.1, costOutputPer1M = 0.3),
            ModelDef(id = "mistral-large-latest", name = "Mistral Large", family = "mistral-large", contextWindow = 128_000, maxOutput = 128_000, supportsTools = true, costInputPer1M = 2.0, costOutputPer1M = 6.0),
        )
    )

    private val groq = ProviderDef(
        id = "groq",
        name = "Groq",
        description = "Inferencia ultra-rápida con hardware especializado",
        apiBaseUrl = "https://api.groq.com/openai/v1",
        envKeys = listOf("GROQ_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "llama-3.3-70b-versatile", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 32_768, supportsTools = true, costInputPer1M = 0.59, costOutputPer1M = 0.79),
            ModelDef(id = "meta-llama/llama-4-maverick-17b-128e-instruct", name = "Llama 4 Maverick", family = "llama", contextWindow = 131_072, maxOutput = 8_192, supportsTools = true, supportsVision = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val fireworks = ProviderDef(
        id = "fireworks-ai",
        name = "Fireworks AI",
        description = "Inferencia optimizada para modelos open-source",
        apiBaseUrl = "https://api.fireworks.ai/inference/v1/",
        envKeys = listOf("FIREWORKS_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "accounts/fireworks/models/deepseek-v3p2", name = "DeepSeek V3.2", family = "deepseek", contextWindow = 160_000, maxOutput = 160_000, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.56, costOutputPer1M = 1.68),
            ModelDef(id = "accounts/fireworks/models/qwen3-235b-a22b", name = "Qwen3 235B", family = "qwen", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.22, costOutputPer1M = 0.88),
        )
    )

    private val together = ProviderDef(
        id = "togetherai",
        name = "Together AI",
        description = "Plataforma de inferencia para modelos open-source",
        apiBaseUrl = "https://api.together.xyz/v1",
        envKeys = listOf("TOGETHER_AI_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 1.25, costOutputPer1M = 1.25),
            ModelDef(id = "Qwen/Qwen3-235B-A22B-Thinking-2507", name = "Qwen3 235B Thinking", family = "qwen", contextWindow = 262_144, maxOutput = 131_072, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.3, costOutputPer1M = 3.0),
        )
    )

    private val perplexity = ProviderDef(
        id = "perplexity",
        name = "Perplexity",
        description = "Modelos con búsqueda web integrada",
        apiBaseUrl = "https://api.perplexity.ai",
        envKeys = listOf("PERPLEXITY_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "sonar-reasoning-pro", name = "Sonar Reasoning Pro", family = "sonar-reasoning", contextWindow = 200_000, maxOutput = 8_000, supportsReasoning = true, costInputPer1M = 2.0, costOutputPer1M = 8.0),
            ModelDef(id = "sonar-pro", name = "Sonar Pro", family = "sonar-pro", contextWindow = 200_000, maxOutput = 8_000, costInputPer1M = 3.0, costOutputPer1M = 15.0),
        )
    )

    private val moonshot = ProviderDef(
        id = "moonshot",
        name = "Moonshot AI",
        description = "Modelos Kimi con contexto extendido y búsqueda integrada",
        apiBaseUrl = "https://api.moonshot.ai/v1",
        envKeys = listOf("MOONSHOT_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "kimi-k2", name = "Kimi K2", family = "kimi", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.6, costOutputPer1M = 2.5),
            ModelDef(id = "kimi-k2-0711-preview", name = "Kimi K2 Preview", family = "kimi", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.6, costOutputPer1M = 2.5),
            ModelDef(id = "kimi-k1.5-long", name = "Kimi K1.5 Long", family = "kimi", contextWindow = 131_072, maxOutput = 32_768, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.5, costOutputPer1M = 2.0),
            ModelDef(id = "moonshot-v1-128k", name = "Moonshot V1 128K", family = "moonshot", contextWindow = 128_000, maxOutput = 8_192, supportsTools = true, costInputPer1M = 0.6, costOutputPer1M = 0.6),
        )
    )

    private val kimiCoding = ProviderDef(
        id = "kimi-coding",
        name = "Kimi for Coding",
        description = "Agente de código especializado de Moonshot AI — usa API Anthropic-compatible",
        apiBaseUrl = "https://api.kimi.com/coding/v1",
        envKeys = listOf("KIMI_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.ANTHROPIC,
        models = listOf(
            ModelDef(id = "kimi-for-coding", name = "Kimi for Coding", family = "kimi-coding", contextWindow = 262_144, maxOutput = 32_768, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.6, costOutputPer1M = 3.0),
        )
    )

    private val minimax = ProviderDef(
        id = "minimax",
        name = "MiniMax",
        description = "Modelos multimodales avanzados con contexto de 1M tokens",
        apiBaseUrl = "https://api.minimaxi.chat/v1",
        envKeys = listOf("MINIMAX_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "MiniMax-M1", name = "MiniMax M1", family = "minimax", contextWindow = 1_000_000, maxOutput = 128_000, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.8, costOutputPer1M = 4.0),
            ModelDef(id = "MiniMax-Text-01", name = "MiniMax Text 01", family = "minimax", contextWindow = 1_000_000, maxOutput = 128_000, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 2.5),
        )
    )

    private val cerebras = ProviderDef(
        id = "cerebras",
        name = "Cerebras",
        description = "Inferencia extremadamente rápida en hardware especializado",
        apiBaseUrl = "https://api.cerebras.ai/v1",
        envKeys = listOf("CEREBRAS_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "llama-4-scout-17b-16e-instruct", name = "Llama 4 Scout 17B", family = "llama", contextWindow = 131_072, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "llama-3.3-70b", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "qwen-3-32b", name = "Qwen3 32B", family = "qwen", contextWindow = 32_768, maxOutput = 16_384, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val deepInfra = ProviderDef(
        id = "deepinfra",
        name = "Deep Infra",
        description = "Hospedaje optimizado de modelos open-source a bajo costo",
        apiBaseUrl = "https://api.deepinfra.com/v1/openai",
        envKeys = listOf("DEEPINFRA_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 1.5),
            ModelDef(id = "Qwen/Qwen3-235B-A22B", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.2, costOutputPer1M = 0.6),
            ModelDef(id = "meta-llama/Llama-4-Maverick-17B-128E-Instruct", name = "Llama 4 Maverick", family = "llama", contextWindow = 131_072, maxOutput = 8_192, supportsTools = true, supportsVision = true, costInputPer1M = 0.18, costOutputPer1M = 0.59),
        )
    )

    private val azureOpenai = ProviderDef(
        id = "azure-openai",
        name = "Azure OpenAI",
        description = "Modelos OpenAI en Microsoft Azure con seguridad empresarial",
        apiBaseUrl = "https://YOUR_RESOURCE.openai.azure.com/openai",
        envKeys = listOf("AZURE_OPENAI_API_KEY", "AZURE_OPENAI_ENDPOINT"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "gpt-5", name = "GPT-5", family = "gpt", contextWindow = 400_000, maxOutput = 128_000, supportsTools = true, supportsVision = true, supportsReasoning = true, costInputPer1M = 2.0, costOutputPer1M = 8.0),
            ModelDef(id = "gpt-5-mini", name = "GPT-5 Mini", family = "gpt-mini", contextWindow = 400_000, maxOutput = 128_000, supportsTools = true, supportsVision = true, costInputPer1M = 0.3, costOutputPer1M = 1.2),
        )
    )

    private val vertexAi = ProviderDef(
        id = "vertexai",
        name = "Google Vertex AI",
        description = "Gemini y otros modelos en Google Cloud Platform",
        apiBaseUrl = "https://us-central1-aiplatform.googleapis.com/v1",
        envKeys = listOf("GOOGLE_CLOUD_PROJECT"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.GOOGLE,
        models = listOf(
            ModelDef(id = "gemini-3-flash", name = "Gemini 3 Flash", family = "gemini-flash", contextWindow = 1_048_576, maxOutput = 65_536, supportsTools = true, supportsVision = true, supportsReasoning = true, costInputPer1M = 0.5, costOutputPer1M = 3.0),
            ModelDef(id = "gemini-3-pro-preview", name = "Gemini 3 Pro Preview", family = "gemini-pro", contextWindow = 1_000_000, maxOutput = 64_000, supportsTools = true, supportsVision = true, supportsReasoning = true, costInputPer1M = 2.0, costOutputPer1M = 12.0),
        )
    )

    private val bedrock = ProviderDef(
        id = "bedrock",
        name = "Amazon Bedrock",
        description = "Modelos de IA en AWS con seguridad y compliance empresarial",
        apiBaseUrl = "https://bedrock-runtime.us-east-1.amazonaws.com",
        envKeys = listOf("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_REGION"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "anthropic.claude-sonnet-4-5-v2:0", name = "Claude Sonnet 4.5", family = "claude", contextWindow = 200_000, maxOutput = 64_000, supportsTools = true, supportsVision = true, costInputPer1M = 3.0, costOutputPer1M = 15.0),
            ModelDef(id = "amazon.nova-pro-v1:0", name = "Amazon Nova Pro", family = "nova", contextWindow = 300_000, maxOutput = 5_000, supportsTools = true, supportsVision = true, costInputPer1M = 0.8, costOutputPer1M = 3.2),
        )
    )

    private val huggingFace = ProviderDef(
        id = "huggingface",
        name = "Hugging Face",
        description = "API de inferencia con miles de modelos open-source",
        apiBaseUrl = "https://api-inference.huggingface.co/v1",
        envKeys = listOf("HF_TOKEN"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "Qwen/Qwen3-235B-A22B", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "meta-llama/Llama-3.3-70B-Instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val nebius = ProviderDef(
        id = "nebius",
        name = "Nebius",
        description = "Token Factory con modelos open-source de alto rendimiento",
        apiBaseUrl = "https://api.studio.nebius.ai/v1",
        envKeys = listOf("NEBIUS_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.3, costOutputPer1M = 0.9),
            ModelDef(id = "Qwen/Qwen3-235B-A22B", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.15, costOutputPer1M = 0.6),
            ModelDef(id = "meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8", name = "Llama 4 Maverick", family = "llama", contextWindow = 131_072, maxOutput = 8_192, supportsTools = true, supportsVision = true, costInputPer1M = 0.15, costOutputPer1M = 0.5),
        )
    )

    private val venice = ProviderDef(
        id = "venice",
        name = "Venice AI",
        description = "IA privada sin censura — no almacena datos del usuario",
        apiBaseUrl = "https://api.venice.ai/api/v1",
        envKeys = listOf("VENICE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-r1-671b", name = "DeepSeek R1", family = "deepseek", contextWindow = 128_000, maxOutput = 32_768, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "llama-3.3-70b", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 32_768, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val scaleway = ProviderDef(
        id = "scaleway",
        name = "Scaleway",
        description = "Cloud europeo con endpoints de IA y soberanía de datos",
        apiBaseUrl = "https://api.scaleway.ai/v1",
        envKeys = listOf("SCALEWAY_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-chat", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 32_768, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "qwen3-32b", name = "Qwen3 32B", family = "qwen", contextWindow = 32_768, maxOutput = 16_384, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val ovhcloud = ProviderDef(
        id = "ovhcloud",
        name = "OVHcloud AI",
        description = "Endpoints de IA europeos con soberanía de datos",
        apiBaseUrl = "https://ai.endpoints.ovh.net/v1",
        envKeys = listOf("OVH_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-r1", name = "DeepSeek R1", family = "deepseek", contextWindow = 128_000, maxOutput = 32_768, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "llama-3.3-70b-instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val opencodeZen = ProviderDef(
        id = "opencode-zen",
        name = "OpenCode Zen",
        description = "Modelos curados y verificados por el equipo de OpenCode",
        apiBaseUrl = "https://api.opencode.ai/v1",
        envKeys = listOf("OPENCODE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "qwen3-coder-480b", name = "Qwen3 Coder 480B", family = "qwen", contextWindow = 262_144, maxOutput = 131_072, supportsTools = true, supportsReasoning = true, costInputPer1M = 1.0, costOutputPer1M = 5.0),
            ModelDef(id = "deepseek-v3", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 1.5),
        )
    )

    private val ai302 = ProviderDef(
        id = "302ai",
        name = "302.AI",
        description = "Plataforma multi-modelo con acceso a proveedores globales",
        apiBaseUrl = "https://api.302.ai/v1",
        envKeys = listOf("AI302_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-chat", name = "DeepSeek V3", family = "deepseek", contextWindow = 128_000, maxOutput = 32_000, supportsTools = true, costInputPer1M = 0.14, costOutputPer1M = 0.28),
            ModelDef(id = "claude-sonnet-4-5-20250514", name = "Claude Sonnet 4.5", family = "claude", contextWindow = 200_000, maxOutput = 64_000, supportsTools = true, supportsVision = true, costInputPer1M = 3.0, costOutputPer1M = 15.0),
        )
    )

    private val gitlab = ProviderDef(
        id = "gitlab",
        name = "GitLab Duo",
        description = "Asistente de IA integrado en el flujo de trabajo GitLab",
        apiBaseUrl = "https://gitlab.com/api/v4/ai",
        envKeys = listOf("GITLAB_TOKEN"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "claude-sonnet-4-5", name = "Claude Sonnet 4.5", family = "claude", contextWindow = 200_000, maxOutput = 64_000, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val baseten = ProviderDef(
        id = "baseten",
        name = "Baseten",
        description = "Infraestructura para desplegar y servir modelos ML",
        apiBaseUrl = "https://bridge.baseten.co/v1/direct",
        envKeys = listOf("BASETEN_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 1.5),
            ModelDef(id = "Qwen/Qwen3-235B-A22B", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.2, costOutputPer1M = 0.8),
        )
    )

    private val ioNet = ProviderDef(
        id = "ionet",
        name = "IO.NET",
        description = "Red descentralizada de GPU para inferencia de IA",
        apiBaseUrl = "https://api.intelligence.io.net/api/v1",
        envKeys = listOf("IONET_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.3, costOutputPer1M = 0.9),
        )
    )

    private val zAi = ProviderDef(
        id = "zai",
        name = "Z.AI",
        description = "Modelos Zhipu GLM y ChatGLM de alto rendimiento",
        apiBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        envKeys = listOf("ZHIPU_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "glm-4-plus", name = "GLM-4 Plus", family = "glm", contextWindow = 128_000, maxOutput = 8_192, supportsTools = true, supportsVision = true, costInputPer1M = 0.7, costOutputPer1M = 0.7),
            ModelDef(id = "glm-4-flash", name = "GLM-4 Flash", family = "glm", contextWindow = 128_000, maxOutput = 8_192, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val cohere = ProviderDef(
        id = "cohere",
        name = "Cohere",
        description = "Modelos Command R+ para RAG y tareas empresariales",
        apiBaseUrl = "https://api.cohere.com/v2",
        envKeys = listOf("COHERE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "command-r-plus-08-2024", name = "Command R+", family = "command", contextWindow = 128_000, maxOutput = 4_096, supportsTools = true, costInputPer1M = 2.5, costOutputPer1M = 10.0),
            ModelDef(id = "command-r-08-2024", name = "Command R", family = "command", contextWindow = 128_000, maxOutput = 4_096, supportsTools = true, costInputPer1M = 0.15, costOutputPer1M = 0.6),
            ModelDef(id = "command-a-03-2025", name = "Command A", family = "command", contextWindow = 256_000, maxOutput = 16_384, supportsTools = true, supportsReasoning = true, costInputPer1M = 2.5, costOutputPer1M = 10.0),
        )
    )

    private val ai21 = ProviderDef(
        id = "ai21",
        name = "AI21 Labs",
        description = "Modelos Jamba de alta calidad para texto y código",
        apiBaseUrl = "https://api.ai21.com/studio/v1",
        envKeys = listOf("AI21_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "jamba-1.5-large", name = "Jamba 1.5 Large", family = "jamba", contextWindow = 256_000, maxOutput = 4_096, supportsTools = true, costInputPer1M = 2.0, costOutputPer1M = 8.0),
            ModelDef(id = "jamba-1.5-mini", name = "Jamba 1.5 Mini", family = "jamba", contextWindow = 256_000, maxOutput = 4_096, supportsTools = true, costInputPer1M = 0.2, costOutputPer1M = 0.4),
        )
    )

    private val sambanova = ProviderDef(
        id = "sambanova",
        name = "SambaNova",
        description = "Inferencia ultra-rápida en hardware RDU especializado",
        apiBaseUrl = "https://api.sambanova.ai/v1",
        envKeys = listOf("SAMBANOVA_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "Meta-Llama-3.3-70B-Instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
            ModelDef(id = "Qwen3-235B-A22B", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val dashscope = ProviderDef(
        id = "dashscope",
        name = "Alibaba DashScope",
        description = "Modelos Qwen directos desde Alibaba Cloud",
        apiBaseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        envKeys = listOf("DASHSCOPE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "qwen-max-latest", name = "Qwen Max", family = "qwen", contextWindow = 131_072, maxOutput = 16_384, supportsTools = true, supportsVision = true, costInputPer1M = 1.6, costOutputPer1M = 6.4),
            ModelDef(id = "qwen-plus-latest", name = "Qwen Plus", family = "qwen", contextWindow = 131_072, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.3, costOutputPer1M = 1.2),
            ModelDef(id = "qwen3-235b-a22b", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.8, costOutputPer1M = 3.2),
            ModelDef(id = "qwen-turbo-latest", name = "Qwen Turbo", family = "qwen", contextWindow = 131_072, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.05, costOutputPer1M = 0.2),
        )
    )

    private val volcengine = ProviderDef(
        id = "volcengine",
        name = "ByteDance Volcengine",
        description = "Modelos Doubao de ByteDance/TikTok",
        apiBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        envKeys = listOf("VOLCENGINE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "doubao-pro-256k", name = "Doubao Pro 256K", family = "doubao", contextWindow = 256_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.7, costOutputPer1M = 0.9),
            ModelDef(id = "doubao-lite-128k", name = "Doubao Lite 128K", family = "doubao", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.15, costOutputPer1M = 0.15),
        )
    )

    private val novitaAi = ProviderDef(
        id = "novita",
        name = "Novita AI",
        description = "Modelos open-source a bajo costo con velocidad optimizada",
        apiBaseUrl = "https://api.novita.ai/v3/openai",
        envKeys = listOf("NOVITA_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek/deepseek-v3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 1.5),
            ModelDef(id = "meta-llama/llama-3.3-70b-instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.39, costOutputPer1M = 0.39),
            ModelDef(id = "qwen/qwen3-235b-a22b", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.2, costOutputPer1M = 0.7),
        )
    )

    private val replicate = ProviderDef(
        id = "replicate",
        name = "Replicate",
        description = "Plataforma para ejecutar modelos ML via API",
        apiBaseUrl = "https://api.replicate.com/v1",
        envKeys = listOf("REPLICATE_API_TOKEN"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "meta/meta-llama-3.1-405b-instruct", name = "Llama 3.1 405B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 9.5, costOutputPer1M = 9.5),
            ModelDef(id = "meta/meta-llama-3.3-70b-instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.37, costOutputPer1M = 0.37),
        )
    )

    private val siliconflow = ProviderDef(
        id = "siliconflow",
        name = "SiliconFlow",
        description = "Inferencia china económica y rápida para modelos open-source",
        apiBaseUrl = "https://api.siliconflow.cn/v1",
        envKeys = listOf("SILICONFLOW_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3", name = "DeepSeek V3", family = "deepseek", contextWindow = 128_000, maxOutput = 32_000, supportsTools = true, costInputPer1M = 0.14, costOutputPer1M = 0.28),
            ModelDef(id = "Qwen/Qwen3-235B-A22B", name = "Qwen3 235B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.14, costOutputPer1M = 0.56),
            ModelDef(id = "deepseek-ai/DeepSeek-R1", name = "DeepSeek R1", family = "deepseek", contextWindow = 128_000, maxOutput = 32_000, supportsTools = true, supportsReasoning = true, costInputPer1M = 0.55, costOutputPer1M = 2.19),
        )
    )

    private val hyperbolic = ProviderDef(
        id = "hyperbolic",
        name = "Hyperbolic",
        description = "GPU descentralizada para inferencia de modelos a bajo costo",
        apiBaseUrl = "https://api.hyperbolic.xyz/v1",
        envKeys = listOf("HYPERBOLIC_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3", name = "DeepSeek V3", family = "deepseek", contextWindow = 128_000, maxOutput = 32_000, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 1.5),
            ModelDef(id = "Qwen/Qwen2.5-Coder-32B-Instruct", name = "Qwen2.5 Coder 32B", family = "qwen", contextWindow = 131_072, maxOutput = 65_536, supportsTools = true, costInputPer1M = 0.2, costOutputPer1M = 0.2),
            ModelDef(id = "meta-llama/Llama-3.3-70B-Instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.4, costOutputPer1M = 0.4),
        )
    )

    private val lambda = ProviderDef(
        id = "lambda",
        name = "Lambda",
        description = "Inferencia en cloud GPU con precios competitivos",
        apiBaseUrl = "https://api.lambdalabs.com/v1",
        envKeys = listOf("LAMBDA_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "llama-3.3-70b-instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.2, costOutputPer1M = 0.2),
            ModelDef(id = "deepseek-llm-67b-chat", name = "DeepSeek 67B", family = "deepseek", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.3, costOutputPer1M = 0.3),
        )
    )

    private val leptonAi = ProviderDef(
        id = "lepton",
        name = "Lepton AI",
        description = "Modelos open-source optimizados para velocidad y costo",
        apiBaseUrl = "https://api.lepton.ai/api/v1",
        envKeys = listOf("LEPTON_API_TOKEN"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "llama-3.3-70b", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.4, costOutputPer1M = 0.4),
            ModelDef(id = "deepseek-v3", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 1.5),
        )
    )

    private val coze = ProviderDef(
        id = "coze",
        name = "Coze",
        description = "Plataforma de ByteDance para bots con acceso a múltiples modelos",
        apiBaseUrl = "https://api.coze.com/open_api/v2",
        envKeys = listOf("COZE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "gpt-4o", name = "GPT-4o (via Coze)", family = "gpt", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, supportsVision = true, costInputPer1M = 0.0, costOutputPer1M = 0.0),
        )
    )

    private val stepfun = ProviderDef(
        id = "stepfun",
        name = "Stepfun",
        description = "Modelos Step de alto rendimiento para código y razonamiento",
        apiBaseUrl = "https://api.stepfun.com/v1",
        envKeys = listOf("STEPFUN_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "step-2-16k", name = "Step 2 16K", family = "step", contextWindow = 16_384, maxOutput = 4_096, supportsTools = true, costInputPer1M = 1.4, costOutputPer1M = 7.0),
            ModelDef(id = "step-1-256k", name = "Step 1 256K", family = "step", contextWindow = 256_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 1.5, costOutputPer1M = 5.0),
            ModelDef(id = "step-1-flash", name = "Step 1 Flash", family = "step", contextWindow = 128_000, maxOutput = 8_192, supportsTools = true, costInputPer1M = 0.1, costOutputPer1M = 0.4),
        )
    )

    private val yi = ProviderDef(
        id = "01ai",
        name = "01.AI",
        description = "Modelos Yi grandes y eficientes de 01.AI",
        apiBaseUrl = "https://api.01.ai/v1",
        envKeys = listOf("YI_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "yi-large", name = "Yi Large", family = "yi", contextWindow = 32_768, maxOutput = 4_096, supportsTools = true, costInputPer1M = 3.0, costOutputPer1M = 3.0),
            ModelDef(id = "yi-large-turbo", name = "Yi Large Turbo", family = "yi", contextWindow = 16_384, maxOutput = 4_096, supportsTools = true, costInputPer1M = 0.2, costOutputPer1M = 0.2),
            ModelDef(id = "yi-medium", name = "Yi Medium", family = "yi", contextWindow = 16_384, maxOutput = 4_096, supportsTools = true, costInputPer1M = 0.6, costOutputPer1M = 0.6),
        )
    )

    private val inferencenet = ProviderDef(
        id = "inferencenet",
        name = "Inference.net",
        description = "Inferencia distribuida con excelente relación precio-rendimiento",
        apiBaseUrl = "https://api.inference.net/v1",
        envKeys = listOf("INFERENCENET_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "meta-llama/Llama-3.3-70B-Instruct", name = "Llama 3.3 70B", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.18, costOutputPer1M = 0.18),
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.5, costOutputPer1M = 1.0),
        )
    )

    private val kluster = ProviderDef(
        id = "kluster",
        name = "Kluster AI",
        description = "Clúster de GPUs distribuidas para inferencia económica",
        apiBaseUrl = "https://api.kluster.ai/v1",
        envKeys = listOf("KLUSTER_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.3, costOutputPer1M = 0.9),
            ModelDef(id = "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo", name = "Llama 3.3 70B Turbo", family = "llama", contextWindow = 128_000, maxOutput = 16_384, supportsTools = true, costInputPer1M = 0.2, costOutputPer1M = 0.2),
        )
    )

    private val chutes = ProviderDef(
        id = "chutes",
        name = "Chutes AI",
        description = "Inferencia serverless con GPUs bajo demanda",
        apiBaseUrl = "https://api.chutes.ai/v1",
        envKeys = listOf("CHUTES_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = listOf(
            ModelDef(id = "deepseek-ai/DeepSeek-V3-0324", name = "DeepSeek V3", family = "deepseek", contextWindow = 131_072, maxOutput = 131_072, supportsTools = true, costInputPer1M = 0.3, costOutputPer1M = 0.9),
            ModelDef(id = "meta-llama/Llama-4-Maverick-17B-128E-Instruct", name = "Llama 4 Maverick", family = "llama", contextWindow = 131_072, maxOutput = 8_192, supportsTools = true, supportsVision = true, costInputPer1M = 0.15, costOutputPer1M = 0.5),
        )
    )

    // ─── Routers / Gateways ─────────────────────────────────────────

    private val cloudflare = ProviderDef(
        id = "cloudflare",
        name = "Cloudflare AI Gateway",
        description = "Gateway con caché, rate limiting y analytics sobre otros providers",
        apiBaseUrl = "https://gateway.ai.cloudflare.com/v1",
        envKeys = listOf("CLOUDFLARE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.GATEWAY,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // depends on backend provider configuration
    )

    private val helicone = ProviderDef(
        id = "helicone",
        name = "Helicone",
        description = "Observabilidad y gateway para LLMs con logging y analytics",
        apiBaseUrl = "https://ai-gateway.helicone.ai",
        envKeys = listOf("HELICONE_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.GATEWAY,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // routes to multiple providers
    )

    private val vercelGateway = ProviderDef(
        id = "vercel-ai",
        name = "Vercel AI Gateway",
        description = "Gateway unificado para múltiples providers de IA",
        apiBaseUrl = "https://api.vercel.ai/v1",
        envKeys = listOf("VERCEL_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.GATEWAY,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // routes to multiple providers
    )

    // ─── Local Providers ─────────────────────────────────────────────

    private val ollama = ProviderDef(
        id = "ollama",
        name = "Ollama",
        description = "Ejecuta modelos de IA localmente — conecta a un servidor Ollama en red",
        apiBaseUrl = "http://localhost:11434/v1",
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.LOCAL,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // user-defined based on pulled models
    )

    private val ollamaCloud = ProviderDef(
        id = "ollama-cloud",
        name = "Ollama Cloud",
        description = "Modelos Ollama hospedados en la nube",
        apiBaseUrl = "https://api.ollama.com/v1",
        envKeys = listOf("OLLAMA_API_KEY"),
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.CLOUD,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // depends on available cloud models
    )

    private val lmStudio = ProviderDef(
        id = "lmstudio",
        name = "LM Studio",
        description = "Conecta a modelos locales servidos por LM Studio",
        apiBaseUrl = "http://localhost:1234/v1",
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.LOCAL,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // user-defined based on loaded models
    )

    private val llamaCpp = ProviderDef(
        id = "llamacpp",
        name = "llama.cpp",
        description = "Servidor llama.cpp local para inferencia optimizada en CPU/GPU",
        apiBaseUrl = "http://localhost:8080/v1",
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.LOCAL,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // user-defined
    )

    // ─── Custom ──────────────────────────────────────────────────────

    private val customProvider = ProviderDef(
        id = "custom",
        name = "Personalizado",
        description = "Cualquier provider compatible con la API de OpenAI",
        apiBaseUrl = "",
        authMethods = listOf(AuthMethod.API_KEY),
        category = ProviderCategory.OTHER,
        apiType = ApiType.OPENAI_COMPATIBLE,
        models = emptyList() // user defines these
    )

    // ─── All providers ───────────────────────────────────────────────

    private val allProviders: List<ProviderDef> = listOf(
        // Popular
        anthropic,
        githubCopilot,
        openai,
        google,
        // Routers
        openRouter,
        // Cloud
        xai,
        deepseek,
        mistral,
        groq,
        fireworks,
        together,
        perplexity,
        moonshot,
        kimiCoding,
        minimax,
        cerebras,
        deepInfra,
        azureOpenai,
        vertexAi,
        bedrock,
        huggingFace,
        nebius,
        venice,
        scaleway,
        ovhcloud,
        opencodeZen,
        ai302,
        gitlab,
        baseten,
        ioNet,
        zAi,
        ollamaCloud,
        cohere,
        ai21,
        sambanova,
        dashscope,
        volcengine,
        novitaAi,
        replicate,
        siliconflow,
        hyperbolic,
        lambda,
        leptonAi,
        coze,
        stepfun,
        yi,
        inferencenet,
        kluster,
        chutes,
        // Gateways
        cloudflare,
        helicone,
        vercelGateway,
        // Local
        ollama,
        lmStudio,
        llamaCpp,
        // Custom
        customProvider
    )

    /** Get all provider definitions */
    fun getAll(): List<ProviderDef> = allProviders

    /** Get popular providers */
    fun getPopular(): List<ProviderDef> = allProviders.filter { it.isPopular }

    /** Get non-popular providers (everything else) */
    fun getOther(): List<ProviderDef> = allProviders.filter { !it.isPopular }

    /** Get providers by category */
    fun getByCategory(category: ProviderCategory): List<ProviderDef> =
        allProviders.filter { it.category == category && !it.isPopular }

    /** Get by ID */
    fun getById(id: String): ProviderDef? = allProviders.find { it.id == id }

    /** Search providers by name (fuzzy) */
    fun search(query: String): List<ProviderDef> {
        if (query.isBlank()) return allProviders
        val q = query.lowercase()
        return allProviders.filter {
            it.name.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.id.lowercase().contains(q)
        }
    }

    /** Get models for a provider  */
    fun getModels(providerId: String): List<ModelDef> {
        return getById(providerId)?.models ?: emptyList()
    }
}
