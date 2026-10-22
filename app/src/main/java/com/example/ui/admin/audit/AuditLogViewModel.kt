package com.example.ui.admin.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.AuditRepository
import com.example.core.data.StoreSectorRepository
import com.example.core.model.AuditLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar a consulta, filtragem, busca e paginação dos logs de auditoria (Etapa 13).
 */
class AuditLogViewModel(
    private val auditRepository: AuditRepository = AuditRepository(),
    private val storeSectorRepository: StoreSectorRepository = StoreSectorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    init {
        loadStores()
        loadInitialLogs()
    }

    private fun loadStores() {
        viewModelScope.launch {
            storeSectorRepository.getStoresFlow().collect { stores ->
                _uiState.update { it.copy(availableStores = stores) }
            }
        }
    }

    fun loadInitialLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = auditRepository.fetchAuditLogs(
                limitCount = 30,
                lastTimestamp = null,
                filterRole = _uiState.value.filterRole,
                filterStoreId = _uiState.value.filterStoreId,
                filterAction = _uiState.value.filterAction,
                filterEntityType = _uiState.value.filterEntityType
            )

            result.fold(
                onSuccess = { logs ->
                    _uiState.update { state ->
                        state.copy(
                            logs = logs,
                            isLoading = false,
                            canLoadMore = logs.size >= 30
                        )
                    }
                    applyLocalFilters()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao carregar registros de auditoria."
                        )
                    }
                }
            )
        }
    }

    fun loadMoreLogs() {
        val currentLogs = _uiState.value.logs
        if (_uiState.value.isLoadingMore || currentLogs.isEmpty() || !_uiState.value.canLoadMore) return

        val lastTimestamp = currentLogs.lastOrNull()?.timestamp ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = auditRepository.fetchAuditLogs(
                limitCount = 30,
                lastTimestamp = lastTimestamp,
                filterRole = _uiState.value.filterRole,
                filterStoreId = _uiState.value.filterStoreId,
                filterAction = _uiState.value.filterAction,
                filterEntityType = _uiState.value.filterEntityType
            )

            result.fold(
                onSuccess = { moreLogs ->
                    _uiState.update { state ->
                        val combined = (state.logs + moreLogs).distinctBy { it.id }
                        state.copy(
                            logs = combined,
                            isLoadingMore = false,
                            canLoadMore = moreLogs.size >= 30
                        )
                    }
                    applyLocalFilters()
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyLocalFilters()
    }

    fun onFilterRoleChanged(role: String) {
        _uiState.update { it.copy(filterRole = role) }
        loadInitialLogs()
    }

    fun onFilterStoreChanged(storeId: String) {
        _uiState.update { it.copy(filterStoreId = storeId) }
        loadInitialLogs()
    }

    fun onFilterActionChanged(action: String) {
        _uiState.update { it.copy(filterAction = action) }
        loadInitialLogs()
    }

    fun onFilterEntityTypeChanged(entityType: String) {
        _uiState.update { it.copy(filterEntityType = entityType) }
        loadInitialLogs()
    }

    fun onSelectLog(log: AuditLog?) {
        _uiState.update { it.copy(selectedLog = log) }
    }

    private fun applyLocalFilters() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val logs = _uiState.value.logs

        val filtered = if (query.isBlank()) {
            logs
        } else {
            logs.filter { log ->
                log.userName.lowercase().contains(query) ||
                log.userId.lowercase().contains(query) ||
                log.entityId.lowercase().contains(query) ||
                log.description.lowercase().contains(query) ||
                log.action.lowercase().contains(query)
            }
        }

        _uiState.update { it.copy(filteredLogs = filtered) }
    }
}
