package com.example.ui.admin

import com.example.core.model.Sector
import com.example.core.model.Store
import com.example.core.model.UserProfile
import com.example.core.model.UserRole

/**
 * Estado da interface para a administração de usuários (Etapa 11).
 */
data class AdminUsersUiState(
    val users: List<UserProfile> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isCreatingUser: Boolean = false,
    val newName: String = "",
    val newEmail: String = "",
    val newPassword: String = "queiroz123",
    val newRole: UserRole = UserRole.AVARISTA,
    val newStoreId: String = "",
    val newSectorId: String = "",
    val availableStores: List<Store> = emptyList(),
    val availableSectors: List<Sector> = emptyList(),
    val allowedRolesToCreate: List<UserRole> = listOf(UserRole.ADMIN, UserRole.AVARISTA, UserRole.SETOR_LEADER),
    val userToDelete: UserProfile? = null
)
