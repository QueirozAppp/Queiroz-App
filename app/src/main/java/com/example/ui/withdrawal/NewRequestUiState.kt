package com.example.ui.withdrawal

import com.example.core.model.Product
import com.example.core.model.WithdrawalRequestItem

/**
 * Estado da interface para a criação de uma Nova Solicitação de Baixa (Etapa 6B).
 */
data class NewRequestUiState(
    val items: List<WithdrawalRequestItem> = emptyList(),
    val selectedProductForQuantity: Product? = null,
    val quantityInput: String = "",
    val isReviewing: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val createdRequestId: String? = null
)
