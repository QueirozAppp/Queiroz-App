package com.example.ui.admin.sector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.StoreSectorRepository
import com.example.core.model.Sector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminSectorsViewModel(
    private val repository: StoreSectorRepository = StoreSectorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminSectorsUiState())
    val uiState: StateFlow<AdminSectorsUiState> = _uiState.asStateFlow()

    fun loadSectorsForStore(storeId: String) {
        _uiState.update { it.copy(storeId = storeId, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getSectorsFlow(storeId).collect { sectors ->
                _uiState.update { it.copy(sectors = sectors, isLoading = false) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                editingSector = null,
                sectorNameInput = "",
                sectorCodeInput = "",
                errorMessage = null
            )
        }
    }

    fun openEditDialog(sector: Sector) {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                editingSector = sector,
                sectorNameInput = sector.name,
                sectorCodeInput = sector.code,
                errorMessage = null
            )
        }
    }

    fun closeDialog() {
        _uiState.update { it.copy(showCreateDialog = false, editingSector = null, sectorNameInput = "", sectorCodeInput = "") }
    }

    fun onSectorNameChanged(name: String) {
        _uiState.update { it.copy(sectorNameInput = name, errorMessage = null) }
    }

    fun onSectorCodeChanged(code: String) {
        _uiState.update { it.copy(sectorCodeInput = code, errorMessage = null) }
    }

    fun saveSector() {
        val state = _uiState.value
        val name = state.sectorNameInput.trim()
        val code = state.sectorCodeInput.trim()
        val storeId = state.storeId

        if (name.isBlank() || code.isBlank() || storeId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nome e código do setor são obrigatórios.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val editing = state.editingSector
            val sector = if (editing != null) {
                editing.copy(name = name, code = code, updatedAt = System.currentTimeMillis())
            } else {
                Sector(
                    id = "${storeId}_${code.uppercase()}",
                    storeId = storeId,
                    name = name,
                    code = code.uppercase(),
                    active = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            val result = repository.saveSector(sector)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showCreateDialog = false,
                            editingSector = null,
                            successMessage = "Setor salvo com sucesso!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao salvar setor."
                        )
                    }
                }
            )
        }
    }

    fun toggleSectorActive(sector: Sector) {
        viewModelScope.launch {
            val updated = sector.copy(active = !sector.active, updatedAt = System.currentTimeMillis())
            repository.saveSector(updated)
        }
    }
}
