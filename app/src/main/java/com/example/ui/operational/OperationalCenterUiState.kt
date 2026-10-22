package com.example.ui.operational

import com.example.core.model.WithdrawalRequest

/**
 * Resumo numérico do Centro Operacional calculado a partir dos documentos reais do Firestore.
 */
data class OperationalSummary(
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val inConferenceCount: Int = 0,
    val completedCount: Int = 0,
    val divergenceCount: Int = 0
)

/**
 * Estado da interface do Centro Operacional (Etapa 17).
 */
data class OperationalCenterUiState(
    val summary: OperationalSummary = OperationalSummary(),
    val requests: List<WithdrawalRequest> = emptyList(),
    val selectedStatusFilter: String = "ALL", // ALL, PENDING, IN_CONFERENCE, COMPLETED, DIVERGENCE
    val selectedStoreFilter: String = "ALL",
    val selectedSectorFilter: String = "ALL",
    val selectedPeriodFilter: String = "ALL", // ALL, TODAY, 7_DAYS, 30_DAYS
    val availableStores: List<String> = emptyList(),
    val availableSectors: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastTimestamp: Long? = null,
    val hasMore: Boolean = false
)
