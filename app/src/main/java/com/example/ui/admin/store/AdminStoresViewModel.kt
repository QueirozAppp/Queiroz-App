package com.example.ui.admin.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.StoreSectorRepository
import com.example.core.model.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminStoresViewModel(
    private val repository: StoreSectorRepository = StoreSectorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminStoresUiState())
    val uiState: StateFlow<AdminStoresUiState> = _uiState.asStateFlow()

    init {
        loadStores()
    }

    fun loadStores() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getStoresFlow().collect { stores ->
                _uiState.update { it.copy(stores = stores, isLoading = false) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                editingStore = null,
                storeNameInput = "",
                storeCodeInput = "",
                errorMessage = null
            )
        }
    }

    fun openEditDialog(store: Store) {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                editingStore = store,
                storeNameInput = store.name,
                storeCodeInput = store.code,
                errorMessage = null
            )
        }
    }

    fun closeDialog() {
        _uiState.update { it.copy(showCreateDialog = false, editingStore = null, storeNameInput = "", storeCodeInput = "") }
    }

    fun onStoreNameChanged(name: String) {
        _uiState.update { it.copy(storeNameInput = name, errorMessage = null) }
    }

    fun onStoreCodeChanged(code: String) {
        _uiState.update { it.copy(storeCodeInput = code, errorMessage = null) }
    }

    fun saveStore() {
        val state = _uiState.value
        val name = state.storeNameInput.trim()
        val code = state.storeCodeInput.trim()

        if (name.isBlank() || code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nome e código da loja são obrigatórios.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val editing = state.editingStore
            val store = if (editing != null) {
                editing.copy(name = name, code = code, updatedAt = System.currentTimeMillis())
            } else {
                Store(
                    id = code.uppercase(),
                    name = name,
                    code = code.uppercase(),
                    active = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            val result = repository.saveStore(store)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showCreateDialog = false,
                            editingStore = null,
                            successMessage = "Loja salva com sucesso!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao salvar loja."
                        )
                    }
                }
            )
        }
    }

    fun toggleStoreActive(store: Store) {
        viewModelScope.launch {
            val updated = store.copy(active = !store.active, updatedAt = System.currentTimeMillis())
            repository.saveStore(updated)
        }
    }
}
