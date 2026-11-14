package com.aibuilt.claudeclone.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long
)

class ProjectRepository(private val file: File, private val json: Json) {

    private val serializer = ListSerializer(Project.serializer())
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) return@withContext
        val stored = runCatching { json.decodeFromString(serializer, text) }
            .getOrDefault(emptyList())
        _projects.value = stored
    }

    suspend fun add(name: String, description: String) {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            createdAt = System.currentTimeMillis()
        )
        persist(_projects.value + project)
    }

    suspend fun remove(id: String) {
        persist(_projects.value.filterNot { it.id == id })
    }

    private suspend fun persist(list: List<Project>) {
        _projects.value = list
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(json.encodeToString(serializer, list)) }
        }
    }
}
