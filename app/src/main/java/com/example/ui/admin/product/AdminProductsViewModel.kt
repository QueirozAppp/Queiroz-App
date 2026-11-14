package com.example.ui.admin.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.ProductRepository
import com.example.core.firebase.FirebaseProvider
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

    private val _uiState = MutableStateFlow(AdminProductsUiState(isLoading = false))
    val uiState: StateFlow<AdminProductsUiState> = _uiState.asStateFlow()

    private var allProducts: List<Product> = emptyList()
    private var isObserving = false

    init {
        if (FirebaseProvider.getAuthOrNull()?.currentUser != null) {
            _uiState.update { it.copy(isLoading = true) }
            observeProducts()
            refreshProducts()
        }
    }

    fun refreshProducts() {
        val currentUser = FirebaseProvider.getAuthOrNull()?.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        observeProducts()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val directResult = productRepository.getAllProducts()
            directResult.fold(
                onSuccess = { directList ->
                    allProducts = directList
                    filterProducts(directList, _uiState.value.searchQuery)
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                },
                onFailure = { error ->
                    android.util.Log.w("AdminProductsViewModel", "refreshProducts aviso: ${error.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    private fun observeProducts() {
        if (isObserving || FirebaseProvider.getAuthOrNull()?.currentUser == null) return
        isObserving = true
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

    fun setUserProfile(profile: com.example.core.model.UserProfile) {
        _uiState.update { it.copy(userProfile = profile) }
    }

    // --- Seleção e Detalhes do Produto ---
    fun selectProduct(product: Product) {
        val priceStr = if (product.price > 0.0) {
            String.format(java.util.Locale.US, "%.2f", product.price).replace(".", ",")
        } else ""
        _uiState.update {
            it.copy(
                selectedProduct = product,
                isEditingSelectedProduct = false,
                editName = product.name,
                editBarcode = product.barcode,
                editPrice = priceStr,
                editUnit = product.unit,
                editPhotoBase64 = product.photoBase64,
                editActive = product.active,
                showDeleteConfirmation = false,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun dismissProductDetail() {
        _uiState.update {
            it.copy(
                selectedProduct = null,
                isEditingSelectedProduct = false,
                showDeleteConfirmation = false
            )
        }
    }

    // --- Edição de Informações do Produto (Admin ou Avarista) ---
    fun startEditingSelectedProduct() {
        val currentProfile = _uiState.value.userProfile
        val isAuthorized = currentProfile == null ||
                currentProfile.role == com.example.core.model.UserRole.ADMIN ||
                currentProfile.role == com.example.core.model.UserRole.AVARISTA
        if (!isAuthorized) {
            _uiState.update { it.copy(errorMessage = "Apenas Administradores e Avaristas podem editar informações do produto.") }
            return
        }
        val prod = _uiState.value.selectedProduct ?: return
        val priceStr = if (prod.price > 0.0) {
            String.format(java.util.Locale.US, "%.2f", prod.price).replace(".", ",")
        } else ""
        _uiState.update {
            it.copy(
                isEditingSelectedProduct = true,
                editName = prod.name,
                editBarcode = prod.barcode,
                editPrice = priceStr,
                editUnit = prod.unit,
                editPhotoBase64 = prod.photoBase64,
                editActive = prod.active,
                errorMessage = null
            )
        }
    }

    fun cancelEditingSelectedProduct() {
        val prod = _uiState.value.selectedProduct
        if (prod != null) {
            val priceStr = if (prod.price > 0.0) {
                String.format(java.util.Locale.US, "%.2f", prod.price).replace(".", ",")
            } else ""
            _uiState.update {
                it.copy(
                    isEditingSelectedProduct = false,
                    editName = prod.name,
                    editBarcode = prod.barcode,
                    editPrice = priceStr,
                    editUnit = prod.unit,
                    editPhotoBase64 = prod.photoBase64,
                    editActive = prod.active,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update { it.copy(isEditingSelectedProduct = false, errorMessage = null) }
        }
    }

    fun onEditNameChanged(name: String) {
        _uiState.update { it.copy(editName = name, errorMessage = null) }
    }

    fun onEditBarcodeChanged(barcode: String) {
        _uiState.update { it.copy(editBarcode = barcode, errorMessage = null) }
    }

    fun onEditPriceChanged(price: String) {
        _uiState.update { it.copy(editPrice = price, errorMessage = null) }
    }

    fun onEditUnitChanged(unit: String) {
        _uiState.update { it.copy(editUnit = unit, errorMessage = null) }
    }

    fun onEditActiveChanged(active: Boolean) {
        _uiState.update { it.copy(editActive = active, errorMessage = null) }
    }

    fun onEditPhotoCaptured(base64: String) {
        _uiState.update { it.copy(editPhotoBase64 = base64, errorMessage = null) }
    }

    fun onRemoveEditPhoto() {
        _uiState.update { it.copy(editPhotoBase64 = "", errorMessage = null) }
    }

    fun saveProductEdits() {
        val state = _uiState.value
        val prod = state.selectedProduct ?: return

        val trimmedName = state.editName.trim()
        val trimmedBarcode = state.editBarcode.trim()
        if (trimmedName.isBlank() || trimmedBarcode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nome e código de barras são obrigatórios.") }
            return
        }

        val trimmedPrice = state.editPrice.trim()
        val parsedPrice = if (trimmedPrice.isBlank()) {
            0.0
        } else {
            val p = trimmedPrice.replace(",", ".").toDoubleOrNull()
            if (p == null || p < 0.0) {
                _uiState.update { it.copy(errorMessage = "Informe um preço válido (ex: 12,90).") }
                return
            }
            p
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOrDeleting = true, errorMessage = null) }
            if (trimmedBarcode != prod.barcode.trim()) {
                val conflicting = productRepository.getProductByBarcode(trimmedBarcode).getOrNull()
                if (conflicting != null && conflicting.id != prod.id) {
                    _uiState.update {
                        it.copy(
                            isSavingOrDeleting = false,
                            errorMessage = "Outro produto já utiliza este código de barras: '${conflicting.name}'."
                        )
                    }
                    return@launch
                }
            }
            val result = productRepository.updateProduct(
                productId = prod.id,
                name = trimmedName,
                barcode = trimmedBarcode,
                price = parsedPrice,
                unit = state.editUnit,
                photoBase64 = state.editPhotoBase64,
                active = state.editActive,
                userProfile = state.userProfile
            )
            result.fold(
                onSuccess = {
                    val updated = prod.copy(
                        name = trimmedName,
                        barcode = trimmedBarcode,
                        price = parsedPrice,
                        unit = state.editUnit,
                        photoBase64 = state.editPhotoBase64,
                        active = state.editActive,
                        updatedAt = System.currentTimeMillis()
                    )
                    allProducts = allProducts.map { if (it.id == prod.id) updated else it }
                    filterProducts(allProducts, _uiState.value.searchQuery)
                    _uiState.update {
                        it.copy(
                            selectedProduct = updated,
                            isEditingSelectedProduct = false,
                            isSavingOrDeleting = false,
                            successMessage = "Produto '${updated.name}' atualizado com sucesso!"
                        )
                    }
                    refreshProducts()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSavingOrDeleting = false,
                            errorMessage = err.localizedMessage ?: "Erro ao atualizar produto."
                        )
                    }
                }
            )
        }
    }

    // --- Exclusão de Produto (Admin ou Avarista) ---
    fun promptDeleteProduct() {
        val currentProfile = _uiState.value.userProfile
        val isAuthorized = currentProfile == null ||
                currentProfile.role == com.example.core.model.UserRole.ADMIN ||
                currentProfile.role == com.example.core.model.UserRole.AVARISTA
        if (!isAuthorized) {
            _uiState.update { it.copy(errorMessage = "Apenas Administradores e Avaristas podem excluir produtos.") }
            return
        }
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDeleteProduct() {
        val prod = _uiState.value.selectedProduct ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOrDeleting = true, showDeleteConfirmation = false, errorMessage = null) }
            val result = productRepository.deleteProduct(prod.id, _uiState.value.userProfile)
            result.fold(
                onSuccess = {
                    allProducts = allProducts.filter { it.id != prod.id }
                    filterProducts(allProducts, _uiState.value.searchQuery)
                    _uiState.update {
                        it.copy(
                            selectedProduct = null,
                            isEditingSelectedProduct = false,
                            isSavingOrDeleting = false,
                            successMessage = "Produto '${prod.name}' excluído do catálogo com sucesso."
                        )
                    }
                    refreshProducts()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSavingOrDeleting = false,
                            errorMessage = err.localizedMessage ?: "Erro ao excluir produto."
                        )
                    }
                }
            )
        }
    }

    // --- Cadastro de Produto ---
    fun startCreatingProduct() {
        _uiState.update {
            it.copy(
                isCreatingProduct = true,
                newBarcode = "",
                newName = "",
                newPrice = "",
                newUnit = "UN",
                newPhotoBase64 = "",
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

    fun onNewPhotoCaptured(base64: String) {
        _uiState.update { it.copy(newPhotoBase64 = base64, errorMessage = null) }
    }

    fun onRemoveNewPhoto() {
        _uiState.update { it.copy(newPhotoBase64 = "", errorMessage = null) }
    }

    fun createProduct() {
        val state = _uiState.value
        val trimmedBarcode = state.newBarcode.trim()
        val trimmedName = state.newName.trim()
        if (trimmedBarcode.isBlank() || trimmedName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Preencha o código de barras e o nome do produto.") }
            return
        }

        val trimmedPrice = state.newPrice.trim()
        val parsedPrice = if (trimmedPrice.isBlank()) {
            0.0
        } else {
            val p = trimmedPrice.replace(",", ".").toDoubleOrNull()
            if (p == null || p < 0.0) {
                _uiState.update { it.copy(errorMessage = "Informe um preço válido (ex: 12,90) ou deixe em branco.") }
                return
            }
            p
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val existing = productRepository.getProductByBarcode(trimmedBarcode).getOrNull()
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Já existe um produto cadastrado com este código de barras: '${existing.name}' (${existing.unit})."
                    )
                }
                return@launch
            }
            val result = productRepository.createProduct(
                barcode = trimmedBarcode,
                name = trimmedName,
                price = parsedPrice,
                unit = state.newUnit,
                photoBase64 = state.newPhotoBase64,
                userProfile = state.userProfile
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
                        photoBase64 = state.newPhotoBase64,
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
            val result = productRepository.updateProductActiveStatus(
                productId = productId,
                active = !currentActive,
                userProfile = _uiState.value.userProfile
            )
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Erro ao atualizar status do produto: ${err.message}") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
