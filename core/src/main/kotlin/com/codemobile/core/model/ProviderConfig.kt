package com.codemobile.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Legacy enum kept for Room migration compatibility.
 * New code should use [ProviderConfig.registryId] instead.
 */
enum class ProviderType {
    OPENAI,
    CLAUDE,
    GEMINI,
    COPILOT,
    OPENAI_COMPATIBLE
}

/**
 * A user-configured AI provider with stored credentials (via EncryptedSharedPreferences).
 *
 * [registryId] links to ProviderRegistry.getById() for metadata (models, auth methods, etc.).
 * [type] is kept for backward compat; new providers derive it from the registry.
 */
@Entity(tableName = "provider_configs")
data class ProviderConfig(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: ProviderType,
    val displayName: String,
    val registryId: String = "",          // e.g. "anthropic", "openai", "github-copilot"
    val defaultModelId: String? = null,
    val selectedModelId: String? = null,  // user's current model choice
    val baseUrl: String? = null,
    val isOAuth: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
