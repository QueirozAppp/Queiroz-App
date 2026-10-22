package com.example.ui.admin.audit

import com.example.core.model.AuditLog
import com.example.core.model.Store

/**
 * Estado da tela de Auditoria e Logs do Sistema (Etapa 13).
 */
data class AuditLogUiState(
    val logs: List<AuditLog> = emptyList(),
    val filteredLogs: List<AuditLog> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val errorMessage: String? = null,
    val selectedLog: AuditLog? = null,
    val searchQuery: String = "",
    val filterRole: String = "ALL",
    val filterStoreId: String = "ALL",
    val filterAction: String = "ALL",
    val filterEntityType: String = "ALL",
    val availableStores: List<Store> = emptyList()
)
