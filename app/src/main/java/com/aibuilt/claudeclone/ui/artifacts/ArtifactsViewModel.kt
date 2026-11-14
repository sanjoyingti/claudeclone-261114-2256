package com.aibuilt.claudeclone.ui.artifacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibuilt.claudeclone.data.ChatMessage
import com.aibuilt.claudeclone.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Artifact(
    val id: String,
    val language: String,
    val code: String
)

data class ArtifactsUiState(
    val artifacts: List<Artifact> = emptyList(),
    val isLoaded: Boolean = false,
    val expandedId: String? = null,
    val snackbar: String? = null
)

class ArtifactsViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _state = MutableStateFlow(ArtifactsUiState())
    val state: StateFlow<ArtifactsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.load()
            repository.messages.collect { messages ->
                _state.update { current ->
                    current.copy(artifacts = extract(messages), isLoaded = true)
                }
            }
        }
    }

    fun toggleExpanded(id: String) {
        _state.update { current ->
            current.copy(expandedId = if (current.expandedId == id) null else id)
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    fun copied() {
        _state.update { it.copy(snackbar = "Copied to clipboard.") }
    }

    /** Pulls every fenced code block out of the assistant's replies, newest first. */
    private fun extract(messages: List<ChatMessage>): List<Artifact> {
        val found = mutableListOf<Artifact>()
        messages.forEach { message ->
            if (message.isUser) return@forEach
            val text = message.text
            var cursor = 0
            while (true) {
                val fenceStart = text.indexOf("```", cursor)
                if (fenceStart < 0) break
                val headerEnd = text.indexOf('\n', fenceStart)
                if (headerEnd < 0) break
                val fenceEnd = text.indexOf("```", headerEnd)
                if (fenceEnd < 0) break

                val language = text.substring(fenceStart + 3, headerEnd).trim()
                val code = text.substring(headerEnd + 1, fenceEnd).trim()
                if (code.isNotBlank()) {
                    found.add(
                        Artifact(
                            id = "${message.id}-$fenceStart",
                            language = language.ifBlank { "code" },
                            code = code
                        )
                    )
                }
                cursor = fenceEnd + 3
            }
        }
        return found.reversed()
    }
}
