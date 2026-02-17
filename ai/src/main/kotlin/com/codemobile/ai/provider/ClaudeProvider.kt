package com.codemobile.ai.provider

import com.codemobile.ai.model.AIConfig
import com.codemobile.ai.model.AIMessage
import com.codemobile.ai.model.AIModel
import com.codemobile.ai.model.AIStreamEvent
import com.codemobile.ai.model.AITool
import com.codemobile.ai.model.AIToolCall
import com.codemobile.ai.streaming.SSE_ERROR_PREFIX
import com.codemobile.ai.streaming.streamSSE
import com.google.gson.Gson
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
 * Anthropic Claude provider using the Messages API with SSE streaming.
 */
class ClaudeProvider(
    override val id: String,
    override val name: String,
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1",
    private val extraHeaders: Map<String, String> = emptyMap(),
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
        val request = Request.Builder()
            .url("$baseUrl/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        var totalInputTokens = 0
        var totalOutputTokens = 0
        val pendingToolCalls = mutableMapOf<Int, MutableToolCall>()

        return client.streamSSE(request)
            .map<String?, AIStreamEvent> { data ->
                if (data == null) return@map AIStreamEvent.Done

                // Handle SSE-level error payloads from the streaming layer
                if (data.startsWith(SSE_ERROR_PREFIX)) {
                    return@map AIStreamEvent.Error(data.removePrefix(SSE_ERROR_PREFIX).trim())
                }

                android.util.Log.d("ClaudeSSE", "Raw: ${data.take(300)}")

                val json = try {
                    JsonParser.parseString(data).asJsonObject
                } catch (e: Exception) {
                    return@map AIStreamEvent.Error("Invalid JSON from API: ${data.take(200)}")
                }
                val type = json.get("type")?.asString ?: ""
                android.util.Log.d("ClaudeSSE", "Event type=$type")

                when (type) {
                    "message_start" -> {
                        val usage = json.getAsJsonObject("message")
                            ?.getAsJsonObject("usage")
                        totalInputTokens += usage?.get("input_tokens")?.asInt ?: 0
                        AIStreamEvent.TextDelta("") // no-op
                    }

                    "content_block_start" -> {
                        val index = json.get("index")?.asInt ?: 0
                        val block = json.getAsJsonObject("content_block")
                        val blockType = block?.get("type")?.asString

                        if (blockType == "tool_use") {
                            val id = block.get("id")?.asString
                            val name = block.get("name")?.asString
                            pendingToolCalls[index] = MutableToolCall(id = id, name = name)
                            AIStreamEvent.ToolCallDelta(index, id, name, null)
                        } else {
                            AIStreamEvent.TextDelta("")
                        }
                    }

                    "content_block_delta" -> {
                        val index = json.get("index")?.asInt ?: 0
                        val delta = json.getAsJsonObject("delta")
                        val deltaType = delta?.get("type")?.asString
                        android.util.Log.d("ClaudeSSE", "Delta type=$deltaType")

                        when (deltaType) {
                            "text_delta" -> {
                                val text = delta.get("text")?.asString ?: ""
                                // Kimi may also include reasoning_content alongside text
                                android.util.Log.d("ClaudeSSE", "text_delta: '${text.take(80)}'")
                                AIStreamEvent.TextDelta(text)
                            }

                            // Kimi / DeepSeek thinking models send reasoning as thinking_delta
                            "thinking_delta" -> {
                                val thinking = delta.get("thinking")?.asString ?: ""
                                android.util.Log.d("ClaudeSSE", "thinking_delta: '${thinking.take(80)}'")
                                // Skip thinking content — only show final answer
                                AIStreamEvent.TextDelta("")
                            }

                            "input_json_delta" -> {
                                val partialJson = delta.get("partial_json")?.asString ?: ""
                                pendingToolCalls[index]?.arguments?.append(partialJson)
                                AIStreamEvent.ToolCallDelta(
                                    index = index,
                                    id = pendingToolCalls[index]?.id,
                                    name = pendingToolCalls[index]?.name,
                                    argumentsDelta = partialJson
                                )
                            }

                            else -> {
                                // Fallback: try to extract text from any field in delta
                                val fallbackText = delta?.get("text")?.asString
                                    ?: delta?.get("reasoning_content")?.asString
                                    ?: ""
                                android.util.Log.d("ClaudeSSE", "Unknown delta '$deltaType', fallback='${fallbackText.take(80)}'")
                                AIStreamEvent.TextDelta(fallbackText)
                            }
                        }
                    }

                    "content_block_stop" -> {
                        val index = json.get("index")?.asInt ?: 0
                        val pending = pendingToolCalls.remove(index)
                        if (pending?.id != null && pending.name != null) {
                            AIStreamEvent.ToolCallComplete(
                                AIToolCall(
                                    id = pending.id!!,
                                    name = pending.name!!,
                                    arguments = pending.arguments.toString()
                                )
                            )
                        } else {
                            AIStreamEvent.TextDelta("")
                        }
                    }

                    "message_delta" -> {
                        val usage = json.getAsJsonObject("usage")
                        totalOutputTokens += usage?.get("output_tokens")?.asInt ?: 0
                        AIStreamEvent.Usage(totalInputTokens, totalOutputTokens)
                    }

                    "message_stop" -> AIStreamEvent.Done

                    "error" -> {
                        val error = json.getAsJsonObject("error")
                        AIStreamEvent.Error(error?.get("message")?.asString ?: "Claude error")
                    }

                    else -> AIStreamEvent.TextDelta("")
                }
            }
            .catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(AIStreamEvent.Error(e.message ?: "Unknown streaming error"))
            }
            .onCompletion { cause ->
                // Ensure Done is always emitted when the stream ends.
                // Kimi's Anthropic endpoint may not send message_stop.
                if (cause == null) {
                    emit(AIStreamEvent.Done)
                }
            }
    }

    override suspend fun listModels(): List<AIModel> {
        return listOf(
            AIModel("claude-sonnet-4-20250514", "Claude Sonnet 4", 200_000, supportsTools = true, supportsVision = true),
            AIModel("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", 200_000, supportsTools = true, supportsVision = true),
            AIModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", 200_000, supportsTools = true, supportsVision = true),
            AIModel("claude-3-opus-20240229", "Claude 3 Opus", 200_000, supportsTools = true, supportsVision = true)
        )
    }

    override suspend fun validateCredentials(): Boolean {
        return try {
            val body = gson.toJson(
                mapOf(
                    "model" to "claude-3-5-haiku-20241022",
                    "max_tokens" to 1,
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to "hi")
                    )
                )
            )
            if (skipValidation) return true
            val request = Request.Builder()
                .url("$baseUrl/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
                .post(body.toRequestBody("application/json".toMediaType()))
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
            "max_tokens" to (config.maxTokens ?: 8192)
        )

        // System prompt goes as top-level field in Claude API
        config.systemPrompt?.let { body["system"] = it }

        // Messages
        val msgList = messages.map { msg ->
            val m = mutableMapOf<String, Any>(
                "role" to when (msg.role) {
                    com.codemobile.ai.model.AIRole.USER -> "user"
                    com.codemobile.ai.model.AIRole.ASSISTANT -> "assistant"
                    com.codemobile.ai.model.AIRole.TOOL -> "user" // Tool results go as user in Claude
                    com.codemobile.ai.model.AIRole.SYSTEM -> "user"
                }
            )
            if (msg.role == com.codemobile.ai.model.AIRole.TOOL && msg.toolCallId != null) {
                m["content"] = listOf(
                    mapOf(
                        "type" to "tool_result",
                        "tool_use_id" to msg.toolCallId,
                        "content" to msg.content
                    )
                )
            } else {
                m["content"] = msg.content
            }
            m
        }
        body["messages"] = msgList

        // Config
        config.temperature.let { body["temperature"] = it }
        config.topP?.let { body["top_p"] = it }

        // Tools
        tools?.let { toolList ->
            body["tools"] = toolList.map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "input_schema" to tool.parameters
                )
            }
        }

        return gson.toJson(body)
    }

    private data class MutableToolCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )
}
