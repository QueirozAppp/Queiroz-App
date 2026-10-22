package com.example.ui.withdrawal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.ProductRepository
import com.example.core.data.WithdrawalRequestRepository
import com.example.core.model.Product
import com.example.core.model.UserProfile
import com.example.core.model.WithdrawalRequest
import com.example.core.model.WithdrawalRequestItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar a criação, adição de itens, revisão e envio de Solicitações de Baixa (Etapa 6B).
 */
class NewRequestViewModel(
    private val withdrawalRepository: WithdrawalRequestRepository = WithdrawalRequestRepository(),
    private val productRepository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewRequestUiState())
    val uiState: StateFlow<NewRequestUiState> = _uiState.asStateFlow()

    fun onProductScannedOrSelected(product: Product) {
        if (!product.active) {
            _uiState.update { it.copy(errorMessage = "Este produto está inativo e não pode ser adicionado.") }
            return
        }
        _uiState.update {
            it.copy(
                selectedProductForQuantity = product,
                quantityInput = "1",
                errorMessage = null
            )
        }
    }

    fun onQuantityChanged(qty: String) {
        _uiState.update { it.copy(quantityInput = qty, errorMessage = null) }
    }

    fun cancelProductSelection() {
        _uiState.update { it.copy(selectedProductForQuantity = null, quantityInput = "") }
    }

    fun confirmAddProduct() {
        val state = _uiState.value
        val product = state.selectedProductForQuantity ?: return
        val qtyValue = state.quantityInput.replace(",", ".").toDoubleOrNull()

        if (qtyValue == null || qtyValue <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Informe uma quantidade válida maior que zero.") }
            return
        }

        val existingIndex = state.items.indexOfFirst { it.productId == product.id }
        val updatedItems = if (existingIndex >= 0) {
            // Atualizar quantidade se já existir
            state.items.mapIndexed { index, item ->
                if (index == existingIndex) {
                    item.copy(requestedQuantity = qtyValue)
                } else {
                    item
                }
            }
        } else {
            // Adicionar novo item
            state.items + WithdrawalRequestItem(
                productId = product.id,
                barcode = product.barcode,
                productName = product.name,
                unit = product.unit,
                requestedQuantity = qtyValue
            )
        }

        _uiState.update {
            it.copy(
                items = updatedItems,
                selectedProductForQuantity = null,
                quantityInput = "",
                errorMessage = null
            )
        }
    }

    fun removeItem(productId: String) {
        _uiState.update { state ->
            state.copy(items = state.items.filter { it.productId != productId })
        }
    }

    fun startReview() {
        if (_uiState.value.items.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Adicione ao menos um produto à solicitação.") }
            return
        }
        _uiState.update { it.copy(isReviewing = true, errorMessage = null) }
    }

    fun cancelReview() {
        _uiState.update { it.copy(isReviewing = false) }
    }

    fun submitRequest(userProfile: UserProfile, onSuccess: (String) -> Unit) {
        val state = _uiState.value
        if (state.items.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "A solicitação não possui produtos.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val request = WithdrawalRequest(
                storeId = userProfile.storeId,
                sectorId = userProfile.sectorId.ifEmpty { "Geral" },
                requestedBy = userProfile.uid,
                requestedByName = userProfile.name.ifEmpty { userProfile.email },
                status = "PENDING",
                items = state.items
            )

            val result = withdrawalRepository.createRequest(request)
            result.fold(
                onSuccess = { requestId ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Solicitação enviada com sucesso!",
                            createdRequestId = requestId
                        )
                    }
                    onSuccess(requestId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao enviar solicitação."
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun resetRequest() {
        _uiState.value = NewRequestUiState()
    }
}
