package com.aibuilt.claudeclone.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibuilt.claudeclone.data.ChatApi
import com.aibuilt.claudeclone.data.ChatMessage
import com.aibuilt.claudeclone.data.ChatRepository
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isLoaded: Boolean = false,
    val snackbar: String? = null
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val api: ChatApi
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    var copied: String? = null
        private set

    init {
        viewModelScope.launch {
            repository.load()
            repository.messages.collect { messages ->
                _state.update { it.copy(messages = messages, isLoaded = true) }
            }
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    fun newChat() {
        sendJob?.cancel()
        sendJob = null
        viewModelScope.launch {
            repository.clear()
            _state.update { it.copy(input = "", isSending = false) }
        }
    }

    fun send(promptOverride: String? = null) {
        val state = _state.value
        if (state.isSending) return
        val text = (promptOverride ?: state.input).trim()
        if (text.isEmpty()) {
            _state.update { it.copy(snackbar = "Type a message first.") }
            return
        }
        _state.update { it.copy(input = "") }

        sendJob = viewModelScope.launch {
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            repository.append(userMessage)

            val placeholder = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            repository.append(placeholder)
            _state.update { it.copy(isSending = true) }

            val history = repository.messages.value
                .dropLast(1)
                .filterNot { it.isError }
            val result = api.sendMessage(history)
            result
                .onSuccess { reply ->
                    repository.replaceLast(placeholder.copy(text = reply))
                    _state.update { it.copy(isSending = false) }
                }
                .onFailure { error ->
                    repository.replaceLast(
                        placeholder.copy(
                            text = error.message ?: "Something went wrong.",
                            isError = true
                        )
                    )
                    _state.update {
                        it.copy(isSending = false, snackbar = error.message ?: "Something went wrong.")
                    }
                }
        }
    }

    fun retry(messageId: String) {
        val messages = _state.value.messages
        val index = messages.indexOfFirst { it.id == messageId }
        if (index <= 0) return
        val prompt = messages.getOrNull(index - 1)?.takeIf { it.isUser }?.text ?: return
        sendJob?.cancel()
        sendJob = null
        viewModelScope.launch {
            repository.save(messages.take(index))
            _state.update { it.copy(isSending = false, input = "") }
            send(prompt)
        }
    }

    fun copy(message: ChatMessage) {
        copied = message.text
        _state.update { it.copy(snackbar = "Copied to clipboard.") }
    }

    fun stop() {
        sendJob?.cancel()
        sendJob = null
        _state.update { it.copy(isSending = false) }
    }

    fun showSnackbar(text: String) {
        _state.update { it.copy(snackbar = text) }
    }
}
