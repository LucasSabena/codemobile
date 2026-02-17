package com.codemobile.ai.model

/**
 * Tool definition to provide to AI models for function calling.
 */
data class AITool(
    val name: String,
    val description: String,
    val parameters: Map<String, Any> // JSON Schema as map
)
