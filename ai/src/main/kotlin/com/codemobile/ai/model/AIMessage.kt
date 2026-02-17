package com.codemobile.ai.model

/**
 * Message to send to an AI provider.
 */
data class AIMessage(
    val role: AIRole,
    val content: String,
    val toolCalls: List<AIToolCall>? = null,
    val toolCallId: String? = null
)

enum class AIRole {
    USER, ASSISTANT, SYSTEM, TOOL
}

data class AIToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)
