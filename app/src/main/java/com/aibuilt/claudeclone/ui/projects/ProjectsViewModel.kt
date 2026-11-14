package com.aibuilt.claudeclone.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibuilt.claudeclone.data.Project
import com.aibuilt.claudeclone.data.ProjectRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val isLoaded: Boolean = false,
    val isDialogOpen: Boolean = false,
    val name: String = "",
    val description: String = "",
    val nameError: String? = null,
    val snackbar: String? = null
)

class ProjectsViewModel(private val repository: ProjectRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.load()
            repository.projects.collect { projects ->
                _state.update { it.copy(projects = projects, isLoaded = true) }
            }
        }
    }

    fun openDialog() {
        _state.update {
            it.copy(isDialogOpen = true, name = "", description = "", nameError = null)
        }
    }

    fun closeDialog() {
        _state.update { it.copy(isDialogOpen = false, nameError = null) }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value, nameError = null) }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value) }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    fun create() {
        val current = _state.value
        val name = current.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(nameError = "Give the project a name.") }
            return
        }
        if (name.length > 60) {
            _state.update { it.copy(nameError = "Keep the name under 60 characters.") }
            return
        }
        viewModelScope.launch {
            repository.add(name, current.description)
            _state.update {
                it.copy(
                    isDialogOpen = false,
                    name = "",
                    description = "",
                    nameError = null,
                    snackbar = "Project created."
                )
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.remove(id)
            _state.update { it.copy(snackbar = "Project deleted.") }
        }
    }

    fun duplicateNameGuard(): String = UUID.randomUUID().toString()
}
