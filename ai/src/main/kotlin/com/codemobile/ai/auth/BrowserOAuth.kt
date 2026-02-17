package com.codemobile.ai.auth

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object BrowserOAuth {
    data class OAuthConfig(
        val providerId: String,
        val authorizeUrl: String,
        val tokenUrl: String,
        val clientId: String,
        val redirectUri: String = "codemobile://oauth/callback",
        val scopes: List<String> = emptyList(),
        val dashboardUrl: String? = null,
        val clientSecret: String? = null,
        val isFullOAuth: Boolean = true
    )

    data class PkceData(
        val codeVerifier: String,
        val codeChallenge: String
    )

    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String? = null,
        @SerializedName("token_type") val tokenType: String? = null,
        val error: String? = null,
        @SerializedName("error_description") val errorDescription: String? = null
    )

    class OAuthException(message: String) : Exception(message)

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val callbackWaiters = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    private val dashboardUrls = mapOf(
        "openai" to "https://platform.openai.com/api-keys",
        "anthropic" to "https://console.anthropic.com/settings/keys",
        "gemini" to "https://aistudio.google.com/app/apikey",
        "openrouter" to "https://openrouter.ai/keys",
        "groq" to "https://console.groq.com/keys",
        "xai" to "https://console.x.ai/",
        "deepseek" to "https://platform.deepseek.com/api_keys",
        "perplexity" to "https://www.perplexity.ai/settings/api",
        "custom" to null
    )

    fun getDashboardUrl(providerId: String): String? = dashboardUrls[providerId]

    fun getOAuthConfig(providerId: String): OAuthConfig? {
        val dashboardUrl = getDashboardUrl(providerId) ?: return null
        return OAuthConfig(
            providerId = providerId,
            authorizeUrl = "",
            tokenUrl = "",
            clientId = "",
            dashboardUrl = dashboardUrl,
            isFullOAuth = false
        )
    }

    fun generatePkce(): PkceData {
        val verifierBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return PkceData(codeVerifier = verifier, codeChallenge = challenge)
    }

    fun buildAuthorizationUrl(
        config: OAuthConfig,
        pkce: PkceData,
        state: String
    ): String {
        if (!config.isFullOAuth) return config.dashboardUrl.orEmpty()
        val scope = config.scopes.joinToString(" ")
        val query = buildList {
            add("response_type=code")
            add("client_id=${urlEncode(config.clientId)}")
            add("redirect_uri=${urlEncode(config.redirectUri)}")
            add("state=${urlEncode(state)}")
            if (scope.isNotBlank()) add("scope=${urlEncode(scope)}")
            add("code_challenge=${urlEncode(pkce.codeChallenge)}")
            add("code_challenge_method=S256")
        }.joinToString("&")
        return "${config.authorizeUrl}?$query"
    }

    suspend fun waitForCallback(state: String, timeoutMs: Long = 180_000L): String? {
        val deferred = CompletableDeferred<String?>()
        callbackWaiters[state] = deferred
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            callbackWaiters.remove(state)
        }
    }

    fun onCallbackReceived(uriString: String) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val state = uri.getQueryParameter("state") ?: return
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        val waiter = callbackWaiters.remove(state) ?: return
        if (!error.isNullOrBlank()) {
            waiter.completeExceptionally(OAuthException(error))
            return
        }
        waiter.complete(code)
    }

    suspend fun exchangeCodeForToken(
        config: OAuthConfig,
        code: String,
        pkce: PkceData
    ): TokenResponse? = withContext(Dispatchers.IO) {
        if (!config.isFullOAuth) return@withContext null
        if (config.tokenUrl.isBlank() || config.clientId.isBlank()) return@withContext null

        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", config.clientId)
            .add("redirect_uri", config.redirectUri)
            .add("code_verifier", pkce.codeVerifier)
            .apply {
                config.clientSecret?.takeIf { it.isNotBlank() }?.let { add("client_secret", it) }
            }
            .build()

        val request = Request.Builder()
            .url(config.tokenUrl)
            .header("Accept", "application/json")
            .post(form)
            .build()

        return@withContext runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                gson.fromJson(body, TokenResponse::class.java)
            }
        }.getOrNull()
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
