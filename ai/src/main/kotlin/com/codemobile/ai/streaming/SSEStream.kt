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
 * Streams SSE (Server-Sent Events) responses from an OkHttp request.
 * Parses `data: ` lines and emits each payload.
 * Emits `null` when `[DONE]` is received.
 */
fun OkHttpClient.streamSSE(request: Request): Flow<String?> = callbackFlow {
    val call = newCall(request)

    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                trySend(null)
                close(IOException("HTTP ${response.code}: $errorBody"))
                return
            }

            try {
                val source = response.body?.source()
                if (source == null) {
                    close(IOException("Empty response body"))
                    return
                }

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break

                    when {
                        line.startsWith("data: [DONE]") -> {
                            trySend(null)
                            break
                        }
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
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

        override fun onFailure(call: Call, e: IOException) {
            close(e)
        }
    })

    awaitClose {
        call.cancel()
    }
}
