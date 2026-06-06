/** Manages state for the groups create screen. */
package it.unibo.equishare.ui.screens.groups.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.domain.repository.GroupsRepository
import it.unibo.equishare.domain.usecase.CreateGroupUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewGroupViewModel(
    private val groupsRepository: GroupsRepository,
    private val createGroup: CreateGroupUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewGroupUiState())
    val uiState: StateFlow<NewGroupUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    fun onEvent(event: NewGroupEvent) {
        when (event) {
            is NewGroupEvent.GroupNameChanged ->
                _uiState.update { it.copy(groupName = event.value, groupNameError = null, createError = false) }
            is NewGroupEvent.DescriptionChanged ->
                _uiState.update { it.copy(description = event.value, createError = false) }
            is NewGroupEvent.CategorySelected ->
                _uiState.update { it.copy(selectedCategory = event.category, createError = false) }
            is NewGroupEvent.PhotoPicked ->
                _uiState.update {
                    it.copy(
                        photoUri = event.localUri,
                        photoUpload = event.upload,
                        createError = false,
                    )
                }
            NewGroupEvent.CreateGroupClicked -> submit()
            NewGroupEvent.AddPhotoClicked,
            NewGroupEvent.BackClicked -> { /* handled in Navigation.kt */ }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCategoriesLoading = true, categoriesError = null) }
            runCatching { groupsRepository.getGroupCategories() }
                .onSuccess { categories ->
                    val defaultCategory = categories.firstOrNull { it.code == "general" }
                        ?: categories.firstOrNull()
                    _uiState.update {
                        it.copy(
                            categories = categories,
                            selectedCategory = it.selectedCategory ?: defaultCategory,
                            isCategoriesLoading = false,
                            categoriesError = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            categories = emptyList(),
                            selectedCategory = null,
                            isCategoriesLoading = false,
                            categoriesError = "Unable to load categories",
                        )
                    }
                }
        }
    }

    private fun submit() {
        val s = _uiState.value
        if (!s.isFormValid || s.isLoading) return
        val category = s.selectedCategory ?: return
        _uiState.update { it.copy(isLoading = true, createError = false) }
        viewModelScope.launch {
            val result = createGroup(
                CreateGroupUseCase.Input(
                    name = s.groupName,
                    description = s.description,
                    category = category,
                    photo = s.photoUpload,
                )
            )
            result
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isCreated = true, createError = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, createError = true) }
                }
        }
    }

    fun consumeCreated() {
        _uiState.update { it.copy(isCreated = false) }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            groupsRepository.refresh()
            loadCategories()
            delay(700)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
