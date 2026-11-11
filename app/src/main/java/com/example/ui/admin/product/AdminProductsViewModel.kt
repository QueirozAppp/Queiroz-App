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

    private val _uiState = MutableStateFlow(AdminProductsUiState(isLoading = true))
    val uiState: StateFlow<AdminProductsUiState> = _uiState.asStateFlow()

    private var allProducts: List<Product> = emptyList()

    init {
        observeProducts()
        refreshProducts()
    }

    fun refreshProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val directResult = productRepository.getAllProducts()
            directResult.fold(
                onSuccess = { directList ->
                    allProducts = directList
                    filterProducts(directList, _uiState.value.searchQuery)
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    val msg = if (error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                        "Permissão negada no Firestore: As regras do banco precisam de leitura liberada para 'products'. Veja as instruções no painel de suporte."
                    } else {
                        "Erro ao carregar produtos: ${error.localizedMessage}"
                    }
                    _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
                }
            )
        }
    }

    private fun observeProducts() {
        viewModelScope.launch {
            productRepository.productsFlow.collect { list ->
                android.util.Log.d("AdminProductsViewModel", "Recebidos ${list.size} produtos via flow")
                allProducts = list
                filterProducts(list, _uiState.value.searchQuery)
                _uiState.update { it.copy(isLoading = false) }
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
                onSuccess = { createdId ->
                    val now = System.currentTimeMillis()
                    val immediateProduct = Product(
                        id = createdId,
                        barcode = state.newBarcode.trim(),
                        name = state.newName.trim(),
                        price = parsedPrice,
                        unit = state.newUnit,
                        active = true,
                        createdAt = now,
                        updatedAt = now
                    )
                    allProducts = (listOf(immediateProduct) + allProducts).distinctBy { it.id }
                    filterProducts(allProducts, _uiState.value.searchQuery)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCreatingProduct = false,
                            successMessage = "Produto cadastrado no Catálogo Global com sucesso!"
                        )
                    }
                    refreshProducts()
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
