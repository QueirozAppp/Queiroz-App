package com.example.ui.admin.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.ProductRepository
import com.example.core.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar a listagem, pesquisa, cadastro e ativação/desativação de produtos no Catálogo Global (Etapa 5.1).
 */
class AdminProductsViewModel(
    private val productRepository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminProductsUiState())
    val uiState: StateFlow<AdminProductsUiState> = _uiState.asStateFlow()

    private var allProducts: List<Product> = emptyList()

    init {
        observeProducts()
    }

    private fun observeProducts() {
        viewModelScope.launch {
            productRepository.productsFlow.collect { list ->
                allProducts = list
                filterProducts(list, _uiState.value.searchQuery)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterProducts(allProducts, query)
    }

    private fun filterProducts(list: List<Product>, query: String) {
        val filtered = if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter {
                it.name.lowercase().contains(q) || it.barcode.lowercase().contains(q)
            }
        }
        _uiState.update { it.copy(products = filtered) }
    }

    fun startCreatingProduct() {
        _uiState.update {
            it.copy(
                isCreatingProduct = true,
                newBarcode = "",
                newName = "",
                newPrice = "",
                newUnit = "UN",
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun cancelCreatingProduct() {
        _uiState.update { it.copy(isCreatingProduct = false, errorMessage = null) }
    }

    fun onNewBarcodeChanged(barcode: String) {
        _uiState.update { it.copy(newBarcode = barcode, errorMessage = null) }
    }

    fun onNewNameChanged(name: String) {
        _uiState.update { it.copy(newName = name, errorMessage = null) }
    }

    fun onNewPriceChanged(price: String) {
        _uiState.update { it.copy(newPrice = price, errorMessage = null) }
    }

    fun onNewUnitChanged(unit: String) {
        _uiState.update { it.copy(newUnit = unit, errorMessage = null) }
    }

    fun createProduct() {
        val state = _uiState.value
        if (state.newBarcode.isBlank() || state.newName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Preencha o código de barras e o nome do produto.") }
            return
        }

        val parsedPrice = state.newPrice.replace(",", ".").toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = productRepository.createProduct(
                barcode = state.newBarcode,
                name = state.newName,
                price = parsedPrice,
                unit = state.newUnit
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCreatingProduct = false,
                            successMessage = "Produto cadastrado no Catálogo Global com sucesso!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao cadastrar produto."
                        )
                    }
                }
            )
        }
    }

    fun toggleProductActive(productId: String, currentActive: Boolean) {
        viewModelScope.launch {
            val result = productRepository.updateProductActiveStatus(productId, !currentActive)
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Erro ao atualizar status do produto: ${err.message}") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
