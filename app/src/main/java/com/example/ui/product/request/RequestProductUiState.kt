package com.example.ui.product.request

import com.example.core.model.ProductRegistrationRequest
import com.example.core.model.ProductUnit

data class RequestProductUiState(
    val nameInput: String = "",
    val barcodeInput: String = "",
    val priceInput: String = "",
    val selectedUnit: String = ProductUnit.UN.name,
    val photoBase64: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val myRequests: List<ProductRegistrationRequest> = emptyList(),
    val availableUnits: List<ProductUnit> = ProductUnit.values().toList(),
    val activeTab: Int = 0 // 0: Form, 1: Minhas Solicitações
)
