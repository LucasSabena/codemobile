package com.codemobile.ai.provider

import com.codemobile.ai.model.AIConfig
import com.codemobile.ai.model.AIMessage
import com.codemobile.ai.model.AIModel
import com.codemobile.ai.model.AIRole
import com.codemobile.ai.model.AIStreamEvent
import com.codemobile.ai.model.AITool
import com.codemobile.ai.model.AIToolCall
import com.codemobile.ai.streaming.SSE_ERROR_PREFIX
import com.codemobile.ai.streaming.streamSSE
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI-compatible provider. Works with OpenAI API and any compatible endpoint.
 *
 * @param id Unique provider instance ID
 * @param name Display name
 * @param apiKey API key / access token for authentication
 * @param baseUrl Base URL (default: OpenAI). Override for compatible APIs.
 * @param extraHeaders Additional headers to include in every request (e.g. Copilot headers).
 * @param chatEndpointOverride If set, replaces the full /chat/completions URL (e.g. Codex endpoint).
 * @param skipValidation If true, validateCredentials() always returns true (for providers without /models).
 */
class OpenAIProvider(
    override val id: String,
    override val name: String,
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val chatEndpointOverride: String? = null,
    private val skipValidation: Boolean = false
) : AIProvider {

    private val gson = Gson()
    private val currentCall = AtomicReference<okhttp3.Call?>(null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun sendMessage(
        messages: List<AIMessage>,
        model: String,
        tools: List<AITool>?,
        config: AIConfig
    ): Flow<AIStreamEvent> {
        val body = buildRequestBody(messages, model, tools, config)
        val endpoint = chatEndpointOverride ?: "$baseUrl/chat/completions"
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .apply {
                extraHeaders.forEach { (key, value) -> header(key, value) }
            }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        // Track active tool calls being assembled
        val pendingToolCalls = mutableMapOf<Int, MutableToolCall>()

        return client.streamSSE(request)
            .map<String?, AIStreamEvent> { data ->
                if (data == null) return@map AIStreamEvent.Done

                // Handle SSE-level error payloads from the streaming layer
                if (data.startsWith(SSE_ERROR_PREFIX)) {
                    return@map AIStreamEvent.Error(data.removePrefix(SSE_ERROR_PREFIX).trim())
                }

                val json = try {
                    JsonParser.parseString(data).asJsonObject
                } catch (e: Exception) {
                    return@map AIStreamEvent.Error("Invalid JSON from API: ${data.take(200)}")
                }
                parseSSEChunk(json, pendingToolCalls)
            }
            .catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(AIStreamEvent.Error(e.message ?: "Unknown streaming error"))
            }
            .onCompletion {
                // Emit any remaining complete tool calls
                pendingToolCalls.values.forEach { tc ->
                    val tcId = tc.id
                    val tcName = tc.name
                    if (tcId != null && tcName != null) {
                        emit(
                            AIStreamEvent.ToolCallComplete(
                                AIToolCall(
                                    id = tcId,
                                    name = tcName,
                                    arguments = tc.arguments.toString()
                                )
                            )
                        )
                    }
                }
            }
    }

    private fun parseSSEChunk(
        json: JsonObject,
        pendingToolCalls: MutableMap<Int, MutableToolCall>
    ): AIStreamEvent {
        // Check for usage
        json.getAsJsonObject("usage")?.let { usage ->
            return AIStreamEvent.Usage(
                inputTokens = usage.get("prompt_tokens")?.asInt ?: 0,
                outputTokens = usage.get("completion_tokens")?.asInt ?: 0
            )
        }

        val choices = json.getAsJsonArray("choices")
        if (choices == null || choices.isEmpty) return AIStreamEvent.TextDelta("")

        val choice = choices[0].asJsonObject
        val delta = choice.getAsJsonObject("delta") ?: return AIStreamEvent.TextDelta("")

        // Text content
        delta.get("content")?.let { content ->
            if (!content.isJsonNull) {
                return AIStreamEvent.TextDelta(content.asString)
            }
        }

        // Tool calls
        delta.getAsJsonArray("tool_calls")?.forEach { tcElement ->
            val tc = tcElement.asJsonObject
            val index = tc.get("index")?.asInt ?: 0
            val id = tc.get("id")?.asString
            val function = tc.getAsJsonObject("function")
            val name = function?.get("name")?.asString
            val args = function?.get("arguments")?.asString

            val pending = pendingToolCalls.getOrPut(index) { MutableToolCall() }
            if (id != null) pending.id = id
            if (name != null) pending.name = name
            if (args != null) pending.arguments.append(args)

            return AIStreamEvent.ToolCallDelta(
                index = index,
                id = pending.id,
                name = pending.name,
                argumentsDelta = args
            )
        }

        // Finish reason
        val finishReason = choice.get("finish_reason")?.asString
        if (finishReason == "stop" || finishReason == "tool_calls") {
            return AIStreamEvent.Done
        }

        return AIStreamEvent.TextDelta("")
    }

    override suspend fun listModels(): List<AIModel> {
        // Return well-known models - the API list is too large and includes fine-tunes
        return listOf(
            AIModel("gpt-4o", "GPT-4o", 128_000, supportsTools = true, supportsVision = true),
            AIModel("gpt-4o-mini", "GPT-4o Mini", 128_000, supportsTools = true, supportsVision = true),
            AIModel("o1", "o1", 200_000, supportsTools = true, supportsVision = true),
            AIModel("o1-mini", "o1 Mini", 128_000, supportsTools = true),
            AIModel("o3-mini", "o3 Mini", 200_000, supportsTools = true),
            AIModel("gpt-4-turbo", "GPT-4 Turbo", 128_000, supportsTools = true, supportsVision = true)
        )
    }

    override suspend fun validateCredentials(): Boolean {
        if (skipValidation) return true
        return try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .header("Authorization", "Bearer $apiKey")
                .apply {
                    extraHeaders.forEach { (key, value) -> header(key, value) }
                }
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    override fun cancelRequest() {
        currentCall.getAndSet(null)?.cancel()
    }

    private fun buildRequestBody(
        messages: List<AIMessage>,
        model: String,
        tools: List<AITool>?,
        config: AIConfig
    ): String {
        val body = mutableMapOf<String, Any>(
            "model" to model,
            "stream" to true,
            "stream_options" to mapOf("include_usage" to true)
        )

        // Build messages array
        val msgList = mutableListOf<Map<String, Any>>()
        config.systemPrompt?.let {
            msgList.add(mapOf("role" to "system", "content" to it))
        }
        messages.forEach { msg ->
            val m = mutableMapOf<String, Any>(
                "role" to msg.role.name.lowercase(),
                "content" to msg.content
            )
            msg.toolCalls?.let { calls ->
                m["tool_calls"] = calls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to "function",
                        "function" to mapOf(
                            "name" to tc.name,
                            "arguments" to tc.arguments
                        )
                    )
                }
            }
            msg.toolCallId?.let { m["tool_call_id"] = it }
            msgList.add(m)
        }
        body["messages"] = msgList

        // Config
        config.temperature.let { body["temperature"] = it }
        config.maxTokens?.let { body["max_tokens"] = it }
        config.topP?.let { body["top_p"] = it }
        config.stop?.let { body["stop"] = it }

        // Tools
        tools?.let { toolList ->
            body["tools"] = toolList.map { tool ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "parameters" to tool.parameters
                    )
                )
            }
        }

        return gson.toJson(body)
    }

    /** Mutable helper for assembling tool calls from streaming deltas. */
    private data class MutableToolCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )
}
