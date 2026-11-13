package com.example.ui.admin.product

import com.example.core.model.Product
import com.example.core.model.ProductUnit
import com.example.core.model.UserProfile

/**
 * Estado da interface para a administração de produtos no Catálogo Global.
 */
data class AdminProductsUiState(
    val products: List<Product> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    
    // Cadastro de novo produto
    val isCreatingProduct: Boolean = false,
    val newBarcode: String = "",
    val newName: String = "",
    val newPrice: String = "",
    val newUnit: String = ProductUnit.UN.name,
    val newPhotoBase64: String = "",
    val availableUnits: List<ProductUnit> = ProductUnit.values().toList(),

    // Menu de Detalhes e Edição/Exclusão do Produto Selecionado
    val selectedProduct: Product? = null,
    val isEditingSelectedProduct: Boolean = false,
    val editName: String = "",
    val editBarcode: String = "",
    val editPrice: String = "",
    val editUnit: String = ProductUnit.UN.name,
    val editPhotoBase64: String = "",
    val editActive: Boolean = true,
    val showDeleteConfirmation: Boolean = false,
    val isSavingOrDeleting: Boolean = false,

    // Perfil do usuário logado
    val userProfile: UserProfile? = null
)
