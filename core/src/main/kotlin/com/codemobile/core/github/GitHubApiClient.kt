package com.codemobile.core.github

import com.codemobile.core.data.repository.ProviderRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApiClient @Inject constructor(
    private val providerRepository: ProviderRepository
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun getUser(): GitHubUser? = withContext(Dispatchers.IO) {
        val token = providerRepository.getGitHubToken().orEmpty()
        if (token.isBlank()) return@withContext null

        val request = Request.Builder()
            .url("$apiBaseUrl/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext null

            val obj = gson.fromJson(body, JsonObject::class.java)
            GitHubUser(
                login = obj.get("login")?.asString.orEmpty(),
                name = obj.get("name")?.asString,
                avatarUrl = obj.get("avatar_url")?.asString
            )
        }
    }

    suspend fun listUserRepos(): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val token = providerRepository.getGitHubToken().orEmpty()
        if (token.isBlank()) return@withContext emptyList()

        val request = Request.Builder()
            .url("$apiBaseUrl/user/repos?sort=updated&per_page=30")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext emptyList()

            val array = gson.fromJson(body, JsonArray::class.java)
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                val id = obj.get("id")?.asLong ?: return@mapNotNull null
                val name = obj.get("name")?.asString ?: return@mapNotNull null
                val fullName = obj.get("full_name")?.asString ?: name
                val htmlUrl = obj.get("html_url")?.asString.orEmpty()
                val cloneUrl = obj.get("clone_url")?.asString.orEmpty()
                GitHubRepo(
                    id = id,
                    name = name,
                    fullName = fullName,
                    htmlUrl = htmlUrl,
                    cloneUrl = cloneUrl,
                    description = obj.get("description")?.asString,
                    isPrivate = obj.get("private")?.asBoolean ?: false,
                    updatedAt = obj.get("updated_at")?.asString
                )
            }
        }
    }

    private companion object {
        const val apiBaseUrl = "https://api.github.com"
    }
}
