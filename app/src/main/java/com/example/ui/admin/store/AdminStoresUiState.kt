package com.example.ui.admin.store

import com.example.core.model.Store

data class AdminStoresUiState(
    val stores: List<Store> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val editingStore: Store? = null,
    val storeNameInput: String = "",
    val storeCodeInput: String = ""
)
