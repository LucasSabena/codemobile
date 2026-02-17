package com.codemobile.ai.model

/**
 * Events emitted during AI streaming response.
 */
sealed class AIStreamEvent {
    /** Incremental text content */
    data class TextDelta(val text: String) : AIStreamEvent()

    /** Tool call being assembled incrementally */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsDelta: String?
    ) : AIStreamEvent()

    /** Complete tool call ready to execute */
    data class ToolCallComplete(val toolCall: AIToolCall) : AIStreamEvent()

    /** Token usage information */
    data class Usage(val inputTokens: Int, val outputTokens: Int) : AIStreamEvent()

    /** Stream completed successfully */
    data object Done : AIStreamEvent()

    /** Error during streaming */
    data class Error(val message: String, val code: Int? = null) : AIStreamEvent()
}
