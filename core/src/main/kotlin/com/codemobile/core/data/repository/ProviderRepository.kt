package com.codemobile.core.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.codemobile.core.data.dao.ProviderConfigDao
import com.codemobile.core.model.ProviderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val providerConfigDao: ProviderConfigDao,
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_provider_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getActiveProviders(): Flow<List<ProviderConfig>> = providerConfigDao.getActive()

    fun getAllProviders(): Flow<List<ProviderConfig>> = providerConfigDao.getAll()

    suspend fun getById(id: String): ProviderConfig? = providerConfigDao.getById(id)

    suspend fun save(config: ProviderConfig) = providerConfigDao.insert(config)

    suspend fun update(config: ProviderConfig) = providerConfigDao.update(config)

    suspend fun delete(config: ProviderConfig) {
        providerConfigDao.delete(config)
        // Also remove all stored credentials
        securePrefs.edit()
            .remove("key_${config.id}")
            .remove("token_${config.id}")
            .remove("refresh_${config.id}")
            .remove("access_${config.id}")
            .remove("expires_${config.id}")
            .remove("account_${config.id}")
            .apply()
    }

    // Secure credential storage
    fun saveApiKey(providerId: String, apiKey: String) {
        securePrefs.edit().putString("key_$providerId", apiKey.trim()).commit()
    }

    fun getApiKey(providerId: String): String? {
        return securePrefs.getString("key_$providerId", null)
    }

    fun saveOAuthToken(providerId: String, token: String) {
        securePrefs.edit().putString("token_$providerId", token.trim()).commit()
    }

    fun getOAuthToken(providerId: String): String? {
        return securePrefs.getString("token_$providerId", null)
    }

    // ── Extended OAuth credential storage (Codex, etc.) ──────────

    fun saveRefreshToken(providerId: String, token: String) {
        securePrefs.edit().putString("refresh_$providerId", token.trim()).commit()
    }

    fun getRefreshToken(providerId: String): String? {
        return securePrefs.getString("refresh_$providerId", null)
    }

    fun saveAccessToken(providerId: String, token: String) {
        securePrefs.edit().putString("access_$providerId", token.trim()).commit()
    }

    fun getAccessToken(providerId: String): String? {
        return securePrefs.getString("access_$providerId", null)
    }

    fun saveTokenExpiry(providerId: String, expiresAt: Long) {
        securePrefs.edit().putLong("expires_$providerId", expiresAt).commit()
    }

    fun getTokenExpiry(providerId: String): Long {
        return securePrefs.getLong("expires_$providerId", 0L)
    }

    fun saveAccountId(providerId: String, accountId: String) {
        securePrefs.edit().putString("account_$providerId", accountId.trim()).commit()
    }

    fun getAccountId(providerId: String): String? {
        return securePrefs.getString("account_$providerId", null)
    }

    fun saveGitHubToken(token: String) {
        securePrefs.edit().putString("github_token", token.trim()).commit()
    }

    fun getGitHubToken(): String? {
        return securePrefs.getString("github_token", null)
    }
}
