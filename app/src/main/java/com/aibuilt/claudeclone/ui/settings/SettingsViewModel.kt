package com.aibuilt.claudeclone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibuilt.claudeclone.data.ApiDefaults
import com.aibuilt.claudeclone.data.ApiProtocol
import com.aibuilt.claudeclone.data.ApiSettings
import com.aibuilt.claudeclone.data.ChatApi
import com.aibuilt.claudeclone.data.SettingsRepository
import com.aibuilt.claudeclone.data.normalizeBaseUrl
import com.aibuilt.claudeclone.data.resolveProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val protocol: ApiProtocol = ApiProtocol.AUTO,
    val isKeyVisible: Boolean = false,
    val baseUrlError: String? = null,
    val modelError: String? = null,
    val isTesting: Boolean = false,
    val testSucceeded: Boolean? = null,
    val testMessage: String? = null,
    val snackbar: String? = null,
    val isLoaded: Boolean = false
) {
    val detectedProtocol: ApiProtocol
        get() = resolveProtocol(ApiProtocol.AUTO, baseUrl)

    val activeProtocol: ApiProtocol
        get() = resolveProtocol(protocol, baseUrl)
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val api: ChatApi
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = repository.state.value
            _state.update {
                it.copy(
                    baseUrl = stored.baseUrl,
                    apiKey = stored.apiKey,
                    model = stored.model,
                    protocol = stored.protocol,
                    isLoaded = true
                )
            }
            repository.state.collect { settings ->
                if (!isDirty(settings)) {
                    _state.update {
                        it.copy(
                            baseUrl = settings.baseUrl,
                            apiKey = settings.apiKey,
                            model = settings.model,
                            protocol = settings.protocol
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        repository.override = null
        super.onCleared()
    }

    private fun isDirty(settings: ApiSettings): Boolean {
        val current = _state.value
        return current.baseUrl != settings.baseUrl ||
            current.apiKey != settings.apiKey ||
            current.model != settings.model ||
            current.protocol != settings.protocol
    }

    fun onBaseUrlChange(value: String) {
        _state.update {
            it.copy(baseUrl = value, baseUrlError = null, testMessage = null, testSucceeded = null)
        }
    }

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(apiKey = value, testMessage = null, testSucceeded = null) }
    }

    fun onModelChange(value: String) {
        _state.update {
            it.copy(model = value, modelError = null, testMessage = null, testSucceeded = null)
        }
    }

    fun onProtocolChange(protocol: ApiProtocol) {
        _state.update {
            it.copy(protocol = protocol, testMessage = null, testSucceeded = null)
        }
    }

    fun toggleKeyVisibility() {
        _state.update { it.copy(isKeyVisible = !it.isKeyVisible) }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    private fun validateUrl(url: String): String? {
        if (url.isBlank()) return null
        val normalized = normalizeBaseUrl(url)
        val withoutScheme = normalized.removePrefix("https://").removePrefix("http://")
        if (withoutScheme.isBlank() || !withoutScheme.contains('.')) {
            if (!withoutScheme.startsWith("localhost") && !withoutScheme.contains(":")) {
                return "That doesn't look like a web address."
            }
        }
        if (withoutScheme.contains(' ')) return "Addresses can't contain spaces."
        return null
    }

    fun save() {
        val current = _state.value
        val urlError = validateUrl(current.baseUrl)
        if (urlError != null) {
            _state.update { it.copy(baseUrlError = urlError) }
            return
        }
        viewModelScope.launch {
            repository.save(current.baseUrl, current.apiKey, current.model, current.protocol)
            val stored = repository.state.value
            _state.update {
                it.copy(
                    baseUrl = stored.baseUrl,
                    apiKey = stored.apiKey,
                    model = stored.model,
                    protocol = stored.protocol,
                    baseUrlError = null,
                    modelError = null,
                    snackbar = "Settings saved."
                )
            }
        }
    }

    fun reset() {
        viewModelScope.launch {
            repository.override = null
            repository.reset()
            _state.update {
                it.copy(
                    baseUrl = "",
                    apiKey = "",
                    model = "",
                    protocol = ApiProtocol.AUTO,
                    baseUrlError = null,
                    modelError = null,
                    isTesting = false,
                    testSucceeded = null,
                    testMessage = null,
                    snackbar = "Restored the original defaults."
                )
            }
        }
    }

    fun testConnection() {
        val current = _state.value
        if (current.isTesting) return
        val urlError = validateUrl(current.baseUrl)
        if (urlError != null) {
            _state.update { it.copy(baseUrlError = urlError) }
            return
        }

        val stored = repository.state.value
        val url = normalizeBaseUrl(current.baseUrl).ifBlank {
            stored.baseUrl.ifBlank { ApiDefaults.BASE_URL }
        }
        val key = current.apiKey.trim().ifBlank {
            stored.apiKey.ifBlank { ApiDefaults.API_KEY }
        }
        val model = current.model.trim().ifBlank {
            stored.model.ifBlank { ApiDefaults.MODEL }
        }
        val protocol = resolveProtocol(current.protocol, url)

        _state.update {
            it.copy(
                isTesting = true,
                testMessage = null,
                testSucceeded = null,
                baseUrlError = null,
                modelError = null
            )
        }

        viewModelScope.launch {
            repository.override = ApiSettings(url, key, model, protocol)
            val result = api.testConnection(protocol, url, key, model)
            repository.override = null
            result
                .onSuccess { message ->
                    _state.update {
                        it.copy(isTesting = false, testSucceeded = true, testMessage = message)
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isTesting = false,
                            testSucceeded = false,
                            testMessage = throwable.message ?: "Connection failed."
                        )
                    }
                }
        }
    }
}
