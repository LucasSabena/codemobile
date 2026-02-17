package com.codemobile.ai.model

/**
 * Information about an AI model.
 */
data class AIModel(
    val id: String,
    val name: String,
    val contextWindow: Int = 0,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val maxOutputTokens: Int? = null
)
