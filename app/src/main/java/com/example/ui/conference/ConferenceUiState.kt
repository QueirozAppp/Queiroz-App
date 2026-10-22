package com.example.ui.conference

import com.example.core.model.ConferenceEvidence
import com.example.core.model.WithdrawalRequest
import com.example.core.model.WithdrawalRequestItem

/**
 * Estado da interface para o fluxo de Conferência Física do Avarista (Etapa 8).
 */
data class ConferenceUiState(
    val currentRequest: WithdrawalRequest? = null,
    val items: List<WithdrawalRequestItem> = emptyList(),
    val evidences: List<ConferenceEvidence> = emptyList(),
    val editingItemProductId: String? = null,
    val checkedQuantityInput: String = "",
    val divergenceReasonInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isFinalized: Boolean = false,
    val finalStatus: String? = null
)
