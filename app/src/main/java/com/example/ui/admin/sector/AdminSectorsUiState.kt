package com.example.ui.admin.sector

import com.example.core.model.Sector

data class AdminSectorsUiState(
    val storeId: String = "",
    val sectors: List<Sector> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val editingSector: Sector? = null,
    val sectorNameInput: String = "",
    val sectorCodeInput: String = ""
)
