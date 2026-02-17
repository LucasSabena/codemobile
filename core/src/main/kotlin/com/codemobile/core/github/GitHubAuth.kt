package com.codemobile.core.github

import com.codemobile.core.data.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubAuth @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val gitHubApiClient: GitHubApiClient
) {
    sealed class AuthEvent {
        data class ShowCode(
            val userCode: String,
            val verificationUri: String
        ) : AuthEvent()

        data object Polling : AuthEvent()
        data object Success : AuthEvent()
        data class Error(val message: String) : AuthEvent()
    }

    fun isAuthenticated(): Boolean = !providerRepository.getGitHubToken().isNullOrBlank()

    suspend fun getUser(): GitHubUser? = gitHubApiClient.getUser()

    fun startDeviceFlow(): Flow<AuthEvent> = flow {
        emit(
            AuthEvent.Error(
                "OAuth Device Flow no est\u00e1 configurado en esta build. Us\u00e1 un Personal Access Token."
            )
        )
    }

    fun saveToken(token: String) {
        providerRepository.saveGitHubToken(token.trim())
    }

    fun clearToken() {
        providerRepository.saveGitHubToken("")
    }
}
