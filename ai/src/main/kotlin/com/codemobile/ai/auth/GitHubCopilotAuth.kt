package com.codemobile.ai.auth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub Device Flow OAuth for GitHub Copilot.
 * Implements the same flow as OpenCode's copilot plugin:
 * 1. Request device code
 * 2. User enters code at github.com/login/device
 * 3. Poll for access token
 * 4. Exchange for Copilot token
 *
 * Client ID from OpenCode: "Ov23li8tweQw6odWQebz"
 */
object GitHubCopilotAuth {

    /**
     * GitHub OAuth App client ID used by OpenCode / Copilot CLI tools.
     * This is a public client ID — not a secret.
     */
    private const val CLIENT_ID = "Ov23li8tweQw6odWQebz"
    private const val GITHUB_BASE = "https://github.com"
    private const val GITHUB_API = "https://api.github.com"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Data classes ─────────────────────────────────────────────

    data class DeviceCodeResponse(
        @SerializedName("device_code") val deviceCode: String,
        @SerializedName("user_code") val userCode: String,
        @SerializedName("verification_uri") val verificationUri: String,
        @SerializedName("expires_in") val expiresIn: Int,
        @SerializedName("interval") val interval: Int
    )

    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("token_type") val tokenType: String?,
        val error: String?,
        @SerializedName("error_description") val errorDescription: String?
    )

    /** Events emitted during the OAuth device flow */
    sealed class OAuthEvent {
        /** Show this code to the user: go to [verificationUri] and enter [userCode] */
        data class ShowCode(
            val userCode: String,
            val verificationUri: String,
            val expiresIn: Int
        ) : OAuthEvent()

        /** Still waiting for user to enter the code */
        data object Polling : OAuthEvent()

        /** Successfully authenticated */
        data class Success(val accessToken: String) : OAuthEvent()

        /** Authentication failed */
        data class Error(val message: String) : OAuthEvent()
    }

    // ── Public API ───────────────────────────────────────────────

    /**
     * Start the GitHub Device OAuth flow.
     * Returns a Flow that emits [OAuthEvent]s:
     * 1. [OAuthEvent.ShowCode] — display code to user
     * 2. [OAuthEvent.Polling] — waiting for user to authorize
     * 3. [OAuthEvent.Success] or [OAuthEvent.Error] — final result
     */
    fun startDeviceFlow(): Flow<OAuthEvent> = flow {
        // Step 1: Request device code
        val deviceCode = requestDeviceCode()
        if (deviceCode == null) {
            emit(OAuthEvent.Error("No se pudo iniciar la autenticación con GitHub"))
            return@flow
        }

        // Step 2: Tell UI to show the code
        emit(OAuthEvent.ShowCode(
            userCode = deviceCode.userCode,
            verificationUri = deviceCode.verificationUri,
            expiresIn = deviceCode.expiresIn
        ))

        // Step 3: Poll for token
        val interval = (deviceCode.interval.coerceAtLeast(5)) * 1000L
        val deadline = System.currentTimeMillis() + (deviceCode.expiresIn * 1000L)

        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            emit(OAuthEvent.Polling)

            val tokenResponse = pollForToken(deviceCode.deviceCode)
            if (tokenResponse?.accessToken != null) {
                emit(OAuthEvent.Success(tokenResponse.accessToken))
                return@flow
            }

            when (tokenResponse?.error) {
                "authorization_pending" -> continue
                "slow_down" -> {
                    delay(5000) // extra backoff
                    continue
                }
                "expired_token" -> {
                    emit(OAuthEvent.Error("El código expiró. Intentá de nuevo."))
                    return@flow
                }
                "access_denied" -> {
                    emit(OAuthEvent.Error("Acceso denegado por el usuario."))
                    return@flow
                }
                else -> {
                    if (tokenResponse?.error != null) {
                        emit(OAuthEvent.Error(tokenResponse.errorDescription ?: tokenResponse.error))
                        return@flow
                    }
                    continue
                }
            }
        }

        emit(OAuthEvent.Error("El código expiró. Intentá de nuevo."))
    }

    // ── Private helpers ──────────────────────────────────────────

    private fun requestDeviceCode(): DeviceCodeResponse? {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "read:user")
            .build()

        val request = Request.Builder()
            .url("$GITHUB_BASE/login/device/code")
            .header("Accept", "application/json")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return null
                gson.fromJson(json, DeviceCodeResponse::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun pollForToken(deviceCode: String): TokenResponse? {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()

        val request = Request.Builder()
            .url("$GITHUB_BASE/login/oauth/access_token")
            .header("Accept", "application/json")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return null
            gson.fromJson(json, TokenResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
