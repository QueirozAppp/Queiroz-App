package com.example.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.AdminUserRepository
import com.example.core.data.StoreSectorRepository
import com.example.core.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar a listagem, criação e ativação/desativação de usuários (Admin - Etapa 11).
 */
class AdminUsersViewModel(
    private val adminRepository: AdminUserRepository,
    private val storeSectorRepository: StoreSectorRepository = StoreSectorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init {
        observeUsers()
        refreshUsers()
        loadStores()
    }

    private fun observeUsers() {
        viewModelScope.launch {
            try {
                adminRepository.usersFlow.collect { list ->
                    if (list.isNotEmpty() || _uiState.value.users.isEmpty()) {
                        _uiState.update { it.copy(users = list) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AdminUsersViewModel", "Aviso no fluxo de usuários: ${e.message}")
            }
        }
    }

    fun refreshUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = adminRepository.fetchUsers()
            result.fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(users = list, isLoading = false) }
                },
                onFailure = { err ->
                    android.util.Log.e("AdminUsersViewModel", "Erro ao carregar usuários: ${err.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    private fun loadStores() {
        viewModelScope.launch {
            storeSectorRepository.getStoresFlow().collect { stores ->
                _uiState.update { state ->
                    val defaultStore = stores.firstOrNull()?.id ?: ""
                    val currentStore = if (state.newStoreId.isBlank()) defaultStore else state.newStoreId
                    state.copy(availableStores = stores, newStoreId = currentStore)
                }
                if (_uiState.value.newStoreId.isNotBlank()) {
                    loadSectors(_uiState.value.newStoreId)
                }
            }
        }
    }

    private fun loadSectors(storeId: String) {
        viewModelScope.launch {
            storeSectorRepository.getSectorsFlow(storeId).collect { sectors ->
                _uiState.update { state ->
                    val defaultSector = sectors.firstOrNull()?.id ?: ""
                    state.copy(availableSectors = sectors, newSectorId = defaultSector)
                }
            }
        }
    }

    fun startCreatingUser() {
        val firstStore = _uiState.value.availableStores.firstOrNull()?.id ?: ""
        _uiState.update {
            it.copy(
                isCreatingUser = true,
                newName = "",
                newEmail = "",
                newPassword = "",
                newRole = UserRole.AVARISTA,
                newStoreId = firstStore,
                newSectorId = "",
                errorMessage = null,
                successMessage = null
            )
        }
        if (firstStore.isNotBlank()) {
            loadSectors(firstStore)
        }
    }

    fun cancelCreatingUser() {
        _uiState.update { it.copy(isCreatingUser = false, errorMessage = null) }
    }

    fun onNewNameChanged(name: String) {
        _uiState.update { it.copy(newName = name, errorMessage = null) }
    }

    fun onNewEmailChanged(email: String) {
        _uiState.update { it.copy(newEmail = email, errorMessage = null) }
    }

    fun onNewPasswordChanged(password: String) {
        _uiState.update { it.copy(newPassword = password, errorMessage = null) }
    }

    fun onNewRoleChanged(role: UserRole) {
        _uiState.update { it.copy(newRole = role, errorMessage = null) }
    }

    fun onNewStoreChanged(storeId: String) {
        _uiState.update { it.copy(newStoreId = storeId, newSectorId = "", errorMessage = null) }
        loadSectors(storeId)
    }

    fun onNewSectorChanged(sectorId: String) {
        _uiState.update { it.copy(newSectorId = sectorId, errorMessage = null) }
    }

    fun createUser() {
        val state = _uiState.value
        if (state.newName.isBlank() || state.newEmail.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Preencha todos os campos obrigatórios.") }
            return
        }
        val isSectorRequired = state.newRole == UserRole.SETOR_LEADER
        if (isSectorRequired && state.newSectorId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "O setor é obrigatório para Líder de Setor.") }
            return
        }
        val isStoreRequired = state.newRole != UserRole.ADMIN
        if (isStoreRequired && state.newStoreId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "A loja é obrigatória.") }
            return
        }

        viewModelScope.launch {
            android.util.Log.i("AdminUsersViewModel", "FIM_CADASTRO_INICIO")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val finalStoreId = if (state.newRole == UserRole.ADMIN) "LOJA_01" else state.newStoreId
            val finalSectorId = if (state.newRole == UserRole.ADMIN) "Geral" else state.newSectorId
            val result = adminRepository.createUser(
                name = state.newName,
                email = state.newEmail,
                pass = "senha123", // Automatic initial password!
                role = state.newRole,
                storeId = finalStoreId,
                sectorId = finalSectorId
            )
            result.fold(
                onSuccess = {
                    android.util.Log.i("AdminUsersViewModel", "FIM_CADASTRO_SUCESSO")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCreatingUser = false,
                            successMessage = "Usuário ${state.newName} cadastrado! A senha definitiva será criada por ele no primeiro acesso."
                        )
                    }
                    refreshUsers()
                },
                onFailure = { err ->
                    android.util.Log.e("AdminUsersViewModel", "FIM_CADASTRO_ERRO: ${err.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao cadastrar usuário."
                        )
                    }
                }
            )
        }
    }

    fun toggleUserActive(uid: String, currentActive: Boolean) {
        viewModelScope.launch {
            val result = adminRepository.updateUserActiveStatus(uid, !currentActive)
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Erro ao atualizar status: ${err.message}") }
            }
        }
    }

    fun toggleAllowPasswordChange(user: com.example.core.model.UserProfile) {
        viewModelScope.launch {
            val newAllowed = !user.allowPasswordChange
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = adminRepository.allowUserPasswordChange(user.uid, newAllowed)
            result.fold(
                onSuccess = {
                    val statusMsg = if (newAllowed) {
                        "Troca de senha liberada para ${user.name}! O usuário poderá definir uma nova senha no próximo login."
                    } else {
                        "Troca de senha bloqueada para ${user.name}."
                    }
                    _uiState.update { it.copy(isLoading = false, successMessage = statusMsg) }
                    refreshUsers()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Erro ao alterar permissão de senha: ${err.localizedMessage ?: err.message}"
                        )
                    }
                }
            )
        }
    }

    fun deleteUser(user: com.example.core.model.UserProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = adminRepository.deleteUser(user.uid)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Usuário ${user.name} removido com sucesso!") }
                    refreshUsers()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Erro ao excluir usuário: ${err.localizedMessage ?: err.message}"
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
