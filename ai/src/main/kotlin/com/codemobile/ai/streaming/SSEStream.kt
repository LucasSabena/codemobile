package com.codemobile.ai.streaming

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException

/**
 * SSE error prefix used to signal an HTTP error body through the flow.
 * Consumers should check for this prefix to extract the upstream error message.
 */
const val SSE_ERROR_PREFIX = "SSE_ERROR:"

/**
 * Streams SSE (Server-Sent Events) responses from an OkHttp request.
 * Parses `data: ` lines and emits each payload.
 * Emits `null` when `[DONE]` is received.
 *
 * On HTTP errors the raw error body is emitted with [SSE_ERROR_PREFIX]
 * so that callers can surface API-level error messages instead of losing them.
 */
fun OkHttpClient.streamSSE(request: Request): Flow<String?> = callbackFlow {
    val call = newCall(request)

    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "Unknown error"
                    // Emit the error as a special payload so the caller can show it
                    trySend("$SSE_ERROR_PREFIX HTTP ${resp.code}: $errorBody")
                    close()
                    return
                }

                try {
                    val source = resp.body?.source()
                    if (source == null) {
                        trySend("${SSE_ERROR_PREFIX} Empty response body")
                        close()
                        return
                    }

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        when {
                            line.startsWith("data: [DONE]") || line == "data:[DONE]" -> {
                                trySend(null)
                                break
                            }
                            line.startsWith("data:") -> {
                                // Handle both "data: {...}" and "data:{...}" formats
                                val data = line.removePrefix("data:").trim()
                                if (data.isNotEmpty()) {
                                    trySend(data)
                                }
                            }
                            // Ignore other SSE fields (event:, id:, retry:, empty lines)
                        }
                    }
                } catch (e: Exception) {
                    close(e)
                    return
                }

                close()
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            close(e)
        }
    })

    awaitClose {
        call.cancel()
    }
}
