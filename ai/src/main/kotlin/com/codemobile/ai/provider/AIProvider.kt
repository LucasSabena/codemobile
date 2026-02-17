package com.codemobile.ai.provider

import com.codemobile.ai.model.AIConfig
import com.codemobile.ai.model.AIMessage
import com.codemobile.ai.model.AIModel
import com.codemobile.ai.model.AIStreamEvent
import com.codemobile.ai.model.AITool
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for AI providers. All providers must implement this interface
 * to be used in Code Mobile's chat system.
 */
interface AIProvider {
    /** Unique identifier for this provider instance */
    val id: String

    /** Human-readable name */
    val name: String

    /**
     * Send messages and receive streaming response.
     *
     * @param messages Conversation history
     * @param model Model ID to use (e.g., "gpt-4o", "claude-sonnet-4-20250514")
     * @param tools Optional list of tools available for function calling
     * @param config Request configuration (temperature, max tokens, etc.)
     * @return Flow of streaming events
     */
    fun sendMessage(
        messages: List<AIMessage>,
        model: String,
        tools: List<AITool>? = null,
        config: AIConfig = AIConfig()
    ): Flow<AIStreamEvent>

    /**
     * List models available for this provider.
     */
    suspend fun listModels(): List<AIModel>

    /**
     * Validate that the configured credentials work.
     * @return true if credentials are valid
     */
    suspend fun validateCredentials(): Boolean

    /**
     * Cancel any in-progress streaming request.
     */
    fun cancelRequest()
}
