package com.codemobile.ai.auth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI Codex Authentication (ChatGPT Plus/Pro subscription).
 *
 * Implements the **headless device flow** from OpenCode's codex.ts plugin:
 * 1. POST to auth.openai.com to get a user_code + device_auth_id
 * 2. User visits auth.openai.com/codex/device and enters code
 * 3. Poll for authorization_code
 * 4. Exchange authorization_code for OAuth tokens (access_token + refresh_token)
 *
 * CLIENT_ID and ISSUER from OpenCode: codex.ts
 */
object OpenAICodexAuth {

    private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val ISSUER = "https://auth.openai.com"
    private const val POLLING_SAFETY_MARGIN_MS = 3000L

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Data classes ─────────────────────────────────────────────

    /** Response from /api/accounts/deviceauth/usercode */
    data class DeviceAuthResponse(
        @SerializedName("device_auth_id") val deviceAuthId: String,
        @SerializedName("user_code") val userCode: String,
        val interval: String? = null // polling interval in seconds
    )

    /** Response from /api/accounts/deviceauth/token when user approves */
    data class DeviceAuthTokenResponse(
        @SerializedName("authorization_code") val authorizationCode: String,
        @SerializedName("code_verifier") val codeVerifier: String
    )

    /** Final OAuth token response */
    data class TokenResponse(
        @SerializedName("id_token") val idToken: String? = null,
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String,
        @SerializedName("expires_in") val expiresIn: Int? = null
    )

    /** Result of a successful Codex authentication */
    data class CodexAuthResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long, // epoch millis
        val accountId: String? = null
    )

    /** Events emitted during the Codex device flow */
    sealed class CodexEvent {
        /** Show this code to the user. They should go to [verificationUri] and enter [userCode] */
        data class ShowCode(
            val userCode: String,
            val verificationUri: String
        ) : CodexEvent()

        /** Still waiting for user to enter the code */
        data object Polling : CodexEvent()

        /** Successfully authenticated */
        data class Success(val result: CodexAuthResult) : CodexEvent()

        /** Authentication failed */
        data class Error(val message: String) : CodexEvent()
    }

    // ── Public API ───────────────────────────────────────────────

    /**
     * Start the OpenAI Codex headless device flow.
     * Returns a Flow that emits [CodexEvent]s.
     */
    fun startDeviceFlow(): Flow<CodexEvent> = flow {
        // Step 1: Request device auth
        val deviceAuth = requestDeviceAuth()
        if (deviceAuth == null) {
            emit(CodexEvent.Error("No se pudo iniciar la autenticación con OpenAI"))
            return@flow
        }

        // Step 2: Show code to user
        emit(CodexEvent.ShowCode(
            userCode = deviceAuth.userCode,
            verificationUri = "$ISSUER/codex/device"
        ))

        // Step 3: Poll for authorization
        val interval = ((deviceAuth.interval?.toIntOrNull() ?: 5).coerceAtLeast(1) * 1000L) + POLLING_SAFETY_MARGIN_MS
        val deadline = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 minute timeout

        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            emit(CodexEvent.Polling)

            val authToken = pollForAuthorization(deviceAuth.deviceAuthId, deviceAuth.userCode)
            if (authToken != null) {
                // Step 4: Exchange for actual tokens
                val tokens = exchangeForTokens(authToken.authorizationCode, authToken.codeVerifier)
                if (tokens != null) {
                    val accountId = extractAccountId(tokens)
                    emit(CodexEvent.Success(CodexAuthResult(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresAt = System.currentTimeMillis() + ((tokens.expiresIn ?: 3600) * 1000L),
                        accountId = accountId
                    )))
                    return@flow
                } else {
                    emit(CodexEvent.Error("Error al intercambiar el código de autorización"))
                    return@flow
                }
            }
            // If pollForAuthorization returned null, keep polling (pending/slow_down)
        }

        emit(CodexEvent.Error("El código expiró. Intentá de nuevo."))
    }

    /**
     * Refresh an expired access token using the refresh token.
     * @return New [CodexAuthResult] or null on failure.
     */
    suspend fun refreshAccessToken(refreshToken: String): CodexAuthResult? {
        return try {
            val body = buildString {
                append("grant_type=refresh_token")
                append("&refresh_token=").append(refreshToken)
                append("&client_id=").append(CLIENT_ID)
            }

            val request = Request.Builder()
                .url("$ISSUER/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val json = response.body?.string() ?: return null
            val tokens = gson.fromJson(json, TokenResponse::class.java)
            val accountId = extractAccountId(tokens)

            CodexAuthResult(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAt = System.currentTimeMillis() + ((tokens.expiresIn ?: 3600) * 1000L),
                accountId = accountId
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Private helpers ──────────────────────────────────────────

    private fun requestDeviceAuth(): DeviceAuthResponse? {
        val jsonBody = gson.toJson(mapOf("client_id" to CLIENT_ID))

        val request = Request.Builder()
            .url("$ISSUER/api/accounts/deviceauth/usercode")
            .header("Content-Type", "application/json")
            .header("User-Agent", "CodeMobile/1.0")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return null
                gson.fromJson(json, DeviceAuthResponse::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Poll for device authorization result.
     * Returns [DeviceAuthTokenResponse] when approved, null when still pending.
     * If the flow is expired or rejected, also returns null (caller handles timeout).
     */
    private fun pollForAuthorization(deviceAuthId: String, userCode: String): DeviceAuthTokenResponse? {
        val jsonBody = gson.toJson(mapOf(
            "device_auth_id" to deviceAuthId,
            "user_code" to userCode
        ))

        val request = Request.Builder()
            .url("$ISSUER/api/accounts/deviceauth/token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "CodeMobile/1.0")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return null
                gson.fromJson(json, DeviceAuthTokenResponse::class.java)
            } else {
                // 403 or 404 = still pending, keep polling
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun exchangeForTokens(authorizationCode: String, codeVerifier: String): TokenResponse? {
        val body = buildString {
            append("grant_type=authorization_code")
            append("&code=").append(authorizationCode)
            append("&redirect_uri=").append("$ISSUER/deviceauth/callback")
            append("&client_id=").append(CLIENT_ID)
            append("&code_verifier=").append(codeVerifier)
        }

        val request = Request.Builder()
            .url("$ISSUER/oauth/token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return null
                gson.fromJson(json, TokenResponse::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract ChatGPT account ID from the JWT tokens.
     * Checks id_token first, then access_token.
     */
    private fun extractAccountId(tokens: TokenResponse): String? {
        // Try id_token first
        tokens.idToken?.let { token ->
            parseJwtClaim(token, "chatgpt_account_id")?.let { return it }
            parseJwtNestedClaim(token)?.let { return it }
        }
        // Try access_token
        parseJwtClaim(tokens.accessToken, "chatgpt_account_id")?.let { return it }
        parseJwtNestedClaim(tokens.accessToken)?.let { return it }
        return null
    }

    private fun parseJwtClaim(token: String, claimKey: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            val json = gson.fromJson(payload, Map::class.java)
            json[claimKey] as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Look for nested claim at "https://api.openai.com/auth".chatgpt_account_id
     * or organizations[0].id
     */
    private fun parseJwtNestedClaim(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            val json = gson.fromJson(payload, Map::class.java)

            // Try nested auth claim
            @Suppress("UNCHECKED_CAST")
            val authClaim = json["https://api.openai.com/auth"] as? Map<String, Any>
            authClaim?.get("chatgpt_account_id")?.toString()?.let { return it }

            // Try organizations[0].id
            @Suppress("UNCHECKED_CAST")
            val orgs = json["organizations"] as? List<Map<String, Any>>
            orgs?.firstOrNull()?.get("id")?.toString()
        } catch (e: Exception) {
            null
        }
    }
}
