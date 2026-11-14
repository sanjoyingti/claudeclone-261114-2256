package com.aibuilt.claudeclone.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ChatRepository(private val file: File, private val json: Json) {

    private val serializer = ListSerializer(ChatMessage.serializer())
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) return@withContext
        val stored = runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
        _messages.value = stored
    }

    suspend fun save(messages: List<ChatMessage>) {
        _messages.value = messages
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(json.encodeToString(serializer, messages)) }
        }
    }

    suspend fun append(message: ChatMessage) {
        save(_messages.value + message)
    }

    suspend fun replaceLast(message: ChatMessage) {
        val current = _messages.value.toMutableList()
        if (current.isEmpty()) {
            current.add(message)
        } else {
            current[current.lastIndex] = message
        }
        save(current)
    }

    suspend fun clear() {
        _messages.value = emptyList()
        withContext(Dispatchers.IO) { runCatching { file.delete() } }
    }

    suspend fun remove(id: String) {
        save(_messages.value.filterNot { it.id == id })
    }
}
