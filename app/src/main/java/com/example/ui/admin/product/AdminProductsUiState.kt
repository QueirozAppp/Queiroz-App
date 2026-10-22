package com.example.ui.admin.product

import com.example.core.model.Product
import com.example.core.model.ProductUnit

/**
 * Estado da interface para a administração de produtos no Catálogo Global (Etapa 5.1).
 */
data class AdminProductsUiState(
    val products: List<Product> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isCreatingProduct: Boolean = false,
    val newBarcode: String = "",
    val newName: String = "",
    val newPrice: String = "",
    val newUnit: String = ProductUnit.UN.name,
    val availableUnits: List<ProductUnit> = ProductUnit.values().toList()
)
