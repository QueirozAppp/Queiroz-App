package com.example.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.WithdrawalRequestRepository
import com.example.core.model.ConferenceEvidence
import com.example.core.model.UserProfile
import com.example.core.model.WithdrawalRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsável pela consulta, filtragem, resumo e detalhamento do Histórico de Baixas.
 */
class HistoryViewModel(
    private val repository: WithdrawalRequestRepository = WithdrawalRequestRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun loadHistory(userProfile: UserProfile) {
        _uiState.update { it.copy(isLoading = true, userProfile = userProfile, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.fetchHistoryRequests(userProfile)
            result.fold(
                onSuccess = { requests ->
                    _uiState.update { state ->
                        val filtered = applyFilters(requests, state.filterStatus, state.filterSector, state.filterSearchQuery, state.filterDivergenceOnly)
                        state.copy(
                            requests = requests,
                            filteredRequests = filtered,
                            isLoading = false,
                            totalCount = filtered.size,
                            completedCount = filtered.count { it.status == "COMPLETED" },
                            divergenceCount = filtered.count { it.status == "DIVERGENCE" },
                            inConferenceCount = filtered.count { it.status == "IN_CONFERENCE" },
                            pendingCount = filtered.count { it.status == "PENDING" }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao carregar histórico."
                        )
                    }
                }
            )
        }
    }

    fun onStatusFilterChanged(status: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.requests, status, state.filterSector, state.filterSearchQuery, state.filterDivergenceOnly)
            state.copy(
                filterStatus = status,
                filteredRequests = filtered,
                totalCount = filtered.size,
                completedCount = filtered.count { it.status == "COMPLETED" },
                divergenceCount = filtered.count { it.status == "DIVERGENCE" },
                inConferenceCount = filtered.count { it.status == "IN_CONFERENCE" },
                pendingCount = filtered.count { it.status == "PENDING" }
            )
        }
    }

    fun onSectorFilterChanged(sector: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.requests, state.filterStatus, sector, state.filterSearchQuery, state.filterDivergenceOnly)
            state.copy(
                filterSector = sector,
                filteredRequests = filtered,
                totalCount = filtered.size,
                completedCount = filtered.count { it.status == "COMPLETED" },
                divergenceCount = filtered.count { it.status == "DIVERGENCE" },
                inConferenceCount = filtered.count { it.status == "IN_CONFERENCE" },
                pendingCount = filtered.count { it.status == "PENDING" }
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.requests, state.filterStatus, state.filterSector, query, state.filterDivergenceOnly)
            state.copy(
                filterSearchQuery = query,
                filteredRequests = filtered,
                totalCount = filtered.size,
                completedCount = filtered.count { it.status == "COMPLETED" },
                divergenceCount = filtered.count { it.status == "DIVERGENCE" },
                inConferenceCount = filtered.count { it.status == "IN_CONFERENCE" },
                pendingCount = filtered.count { it.status == "PENDING" }
            )
        }
    }

    fun onDivergenceOnlyChanged(divergenceOnly: Boolean) {
        _uiState.update { state ->
            val filtered = applyFilters(state.requests, state.filterStatus, state.filterSector, state.filterSearchQuery, divergenceOnly)
            state.copy(
                filterDivergenceOnly = divergenceOnly,
                filteredRequests = filtered,
                totalCount = filtered.size,
                completedCount = filtered.count { it.status == "COMPLETED" },
                divergenceCount = filtered.count { it.status == "DIVERGENCE" },
                inConferenceCount = filtered.count { it.status == "IN_CONFERENCE" },
                pendingCount = filtered.count { it.status == "PENDING" }
            )
        }
    }

    fun selectRequest(request: WithdrawalRequest) {
        _uiState.update { it.copy(selectedRequest = request, showDetailModal = true) }
        viewModelScope.launch {
            repository.getConferenceEvidencesFlow(request.id).collect { evidences ->
                _uiState.update { it.copy(selectedRequestEvidences = evidences) }
            }
        }
    }

    fun closeDetailModal() {
        _uiState.update { it.copy(selectedRequest = null, selectedRequestEvidences = emptyList(), showDetailModal = false) }
    }

    private fun applyFilters(
        all: List<WithdrawalRequest>,
        status: String,
        sector: String,
        searchQuery: String,
        divergenceOnly: Boolean
    ): List<WithdrawalRequest> {
        return all.filter { req ->
            val matchStatus = if (status == "ALL") true else req.status == status
            val matchSector = if (sector.isBlank()) true else req.sectorId.equals(sector, ignoreCase = true)
            val matchDivergence = if (!divergenceOnly) true else req.status == "DIVERGENCE"
            val matchSearch = if (searchQuery.isBlank()) true else {
                req.id.contains(searchQuery, ignoreCase = true) ||
                        req.requestedByName.contains(searchQuery, ignoreCase = true) ||
                        req.storeId.contains(searchQuery, ignoreCase = true)
            }
            matchStatus && matchSector && matchDivergence && matchSearch
        }
    }
}
