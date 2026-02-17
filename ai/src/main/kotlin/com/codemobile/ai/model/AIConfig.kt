package com.codemobile.ai.model

/**
 * Configuration for AI completion requests.
 */
data class AIConfig(
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val systemPrompt: String? = null,
    val stop: List<String>? = null
)
