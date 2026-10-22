package com.example.ui.operational

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.WithdrawalRequestRepository
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.model.WithdrawalRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel responsável pela agregação e filtros do Centro Operacional (Etapa 17).
 */
class OperationalCenterViewModel(
    private val repository: WithdrawalRequestRepository = WithdrawalRequestRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperationalCenterUiState())
    val uiState: StateFlow<OperationalCenterUiState> = _uiState.asStateFlow()

    private var currentUserProfile: UserProfile? = null

    fun loadData(userProfile: UserProfile) {
        currentUserProfile = userProfile
        fetchSummaryAndRequests(userProfile, isRefresh = true)
    }

    fun setStatusFilter(status: String) {
        _uiState.update { it.copy(selectedStatusFilter = status) }
        currentUserProfile?.let { fetchSummaryAndRequests(it, isRefresh = true) }
    }

    fun setStoreFilter(storeId: String) {
        _uiState.update { it.copy(selectedStoreFilter = storeId) }
        currentUserProfile?.let { fetchSummaryAndRequests(it, isRefresh = true) }
    }

    fun setSectorFilter(sectorId: String) {
        _uiState.update { it.copy(selectedSectorFilter = sectorId) }
        currentUserProfile?.let { fetchSummaryAndRequests(it, isRefresh = true) }
    }

    fun setPeriodFilter(period: String) {
        _uiState.update { it.copy(selectedPeriodFilter = period) }
        currentUserProfile?.let { fetchSummaryAndRequests(it, isRefresh = true) }
    }

    fun refresh() {
        currentUserProfile?.let { fetchSummaryAndRequests(it, isRefresh = true) }
    }

    private fun fetchSummaryAndRequests(userProfile: UserProfile, isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Calcular timestamp de início para filtro de período
            val periodStartTimestamp = calculatePeriodStartTimestamp(_uiState.value.selectedPeriodFilter)

            val filterStore = when (userProfile.role) {
                UserRole.ADMIN -> _uiState.value.selectedStoreFilter.takeIf { it != "ALL" }
                else -> userProfile.storeId
            }

            val filterSector = _uiState.value.selectedSectorFilter.takeIf { it != "ALL" }
            val filterStatus = _uiState.value.selectedStatusFilter.takeIf { it != "ALL" }
            val filterUser = when (userProfile.role) {
                UserRole.SETOR_LEADER -> userProfile.uid
                else -> null
            }

            val result = repository.fetchFilteredRequests(
                userProfile = userProfile,
                limitCount = 50,
                filterStatus = filterStatus,
                filterStoreId = filterStore,
                filterSectorId = filterSector,
                filterPeriodStart = periodStartTimestamp
            )

            // Obter contagens reais via Server-Side Aggregation
            val serverCounts = repository.fetchServerStatusCounts(
                userProfile = userProfile,
                filterStoreId = filterStore,
                filterSectorId = filterSector,
                filterPeriodStart = periodStartTimestamp
            ).getOrNull()

            result.fold(
                onSuccess = { list ->
                    // Utilizar os contadores do servidor, ou fallback em memória
                    val summary = if (serverCounts != null && serverCounts.totalCount > 0L) {
                        OperationalSummary(
                            totalCount = serverCounts.totalCount.toInt(),
                            pendingCount = serverCounts.pendingCount.toInt(),
                            inConferenceCount = serverCounts.inConferenceCount.toInt(),
                            completedCount = serverCounts.completedCount.toInt(),
                            divergenceCount = serverCounts.divergenceCount.toInt()
                        )
                    } else {
                        calculateSummary(list)
                    }
                    val stores = list.map { it.storeId }.filter { it.isNotBlank() }.distinct().sorted()
                    val sectors = list.map { it.sectorId }.filter { it.isNotBlank() }.distinct().sorted()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            requests = list,
                            summary = summary,
                            availableStores = if (it.availableStores.isEmpty()) stores else it.availableStores,
                            availableSectors = if (it.availableSectors.isEmpty()) sectors else it.availableSectors,
                            lastTimestamp = list.lastOrNull()?.createdAt,
                            hasMore = list.size >= 50
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Não foi possível carregar os dados operacionais."
                        )
                    }
                }
            )
        }
    }

    private fun calculateSummary(list: List<WithdrawalRequest>): OperationalSummary {
        val total = list.size
        val pending = list.count { it.status == "PENDING" }
        val inConf = list.count { it.status == "IN_CONFERENCE" }
        val comp = list.count { it.status == "COMPLETED" }
        val diverg = list.count { it.status == "DIVERGENCE" }

        return OperationalSummary(
            totalCount = total,
            pendingCount = pending,
            inConferenceCount = inConf,
            completedCount = comp,
            divergenceCount = diverg
        )
    }

    private fun calculatePeriodStartTimestamp(period: String): Long? {
        val cal = Calendar.getInstance()
        return when (period) {
            "TODAY" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "7_DAYS" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            "30_DAYS" -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.timeInMillis
            }
            else -> null
        }
    }
}
