package com.codemobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemobile.core.data.repository.ProviderRepository
import com.codemobile.core.model.ProviderConfig
import com.codemobile.core.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeProviders()
    }

    private fun observeProviders() {
        viewModelScope.launch {
            providerRepository.getAllProviders().collect { providers ->
                _uiState.update { it.copy(providers = providers, isLoading = false) }
            }
        }
    }

    fun addProvider(
        type: ProviderType,
        displayName: String,
        baseUrl: String?,
        apiKey: String
    ) {
        viewModelScope.launch {
            val config = ProviderConfig(
                type = type,
                displayName = displayName,
                baseUrl = baseUrl
            )
            providerRepository.save(config)
            if (apiKey.isNotBlank()) {
                providerRepository.saveApiKey(config.id, apiKey)
            }
        }
    }

    fun deleteProvider(provider: ProviderConfig) {
        viewModelScope.launch {
            providerRepository.delete(provider)
        }
    }

    fun updateApiKey(providerId: String, apiKey: String) {
        providerRepository.saveApiKey(providerId, apiKey)
    }
}
