package com.example.ui.history

import com.example.core.model.ConferenceEvidence
import com.example.core.model.WithdrawalRequest
import com.example.core.model.UserProfile

/**
 * Estado da tela de Histórico de Baixas.
 */
data class HistoryUiState(
    val requests: List<WithdrawalRequest> = emptyList(),
    val filteredRequests: List<WithdrawalRequest> = emptyList(),
    val selectedRequest: WithdrawalRequest? = null,
    val selectedRequestEvidences: List<ConferenceEvidence> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userProfile: UserProfile? = null,

    // Filtros
    val filterStatus: String = "ALL", // "ALL", "PENDING", "IN_CONFERENCE", "COMPLETED", "DIVERGENCE"
    val filterSector: String = "",
    val filterSearchQuery: String = "",
    val filterDivergenceOnly: Boolean = false,

    // Resumo (baseado nos registros filtrados)
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val divergenceCount: Int = 0,
    val inConferenceCount: Int = 0,
    val pendingCount: Int = 0,

    // Tela de Detalhes
    val showDetailModal: Boolean = false
)
