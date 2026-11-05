package com.example.ui.reports

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
 * ViewModel para consolidação de Relatórios e Indicadores Operacionais (Etapa 18).
 */
class ReportsViewModel(
    private val repository: WithdrawalRequestRepository = WithdrawalRequestRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var currentUserProfile: UserProfile? = null

    fun loadReports(userProfile: UserProfile) {
        currentUserProfile = userProfile
        fetchReportsData(userProfile)
    }

    fun setPeriodFilter(period: String) {
        _uiState.update { it.copy(selectedPeriod = period) }
        currentUserProfile?.let { fetchReportsData(it) }
    }

    fun setStoreFilter(storeId: String) {
        _uiState.update { it.copy(selectedStoreId = storeId) }
        currentUserProfile?.let { fetchReportsData(it) }
    }

    fun setSectorFilter(sectorId: String) {
        _uiState.update { it.copy(selectedSectorId = sectorId) }
        currentUserProfile?.let { fetchReportsData(it) }
    }

    fun refresh() {
        currentUserProfile?.let { fetchReportsData(it) }
    }

    private fun fetchReportsData(userProfile: UserProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val periodStartTimestamp = calculatePeriodStartTimestamp(_uiState.value.selectedPeriod)
            val storeFilter = when (userProfile.role) {
                UserRole.ADMIN, UserRole.AVARISTA -> _uiState.value.selectedStoreId.takeIf { it != "ALL" }
                else -> userProfile.storeId
            }
            val sectorFilter = _uiState.value.selectedSectorId.takeIf { it != "ALL" }

            val result = repository.fetchFilteredRequests(
                userProfile = userProfile,
                limitCount = 200,
                filterStatus = null,
                filterStoreId = storeFilter,
                filterSectorId = sectorFilter,
                filterPeriodStart = periodStartTimestamp
            )

            // Obter contagens consolidadas via Server-Side Aggregation (Etapa 19)
            val serverCounts = repository.fetchServerStatusCounts(
                userProfile = userProfile,
                filterStoreId = storeFilter,
                filterSectorId = sectorFilter,
                filterPeriodStart = periodStartTimestamp
            ).getOrNull()

            result.fold(
                onSuccess = { list ->
                    val rawReportData = generateReportData(list)
                    val reportData = if (serverCounts != null && serverCounts.totalCount > 0L) {
                        val finalizedTotal = serverCounts.completedCount + serverCounts.divergenceCount
                        val divergenceRate = if (finalizedTotal > 0L) {
                            (serverCounts.divergenceCount.toDouble() / finalizedTotal.toDouble()) * 100.0
                        } else {
                            null
                        }
                        rawReportData.copy(
                            totalRequests = serverCounts.totalCount.toInt(),
                            pendingRequests = serverCounts.pendingCount.toInt(),
                            inConferenceRequests = serverCounts.inConferenceCount.toInt(),
                            completedRequests = serverCounts.completedCount.toInt(),
                            divergenceRequests = serverCounts.divergenceCount.toInt(),
                            divergenceRatePercentage = divergenceRate
                        )
                    } else {
                        rawReportData
                    }
                    val stores = list.map { it.storeId }.filter { it.isNotBlank() }.distinct().sorted()
                    val sectors = list.map { it.sectorId }.filter { it.isNotBlank() }.distinct().sorted()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            reportData = reportData,
                            availableStores = if (it.availableStores.isEmpty()) stores else it.availableStores,
                            availableSectors = if (it.availableSectors.isEmpty()) sectors else it.availableSectors
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao gerar relatórios operacionais."
                        )
                    }
                }
            )
        }
    }

    /**
     * Calcula indicadores a partir das solicitações reais do período.
     */
    fun generateReportData(requests: List<WithdrawalRequest>): OperationalReportData {
        val total = requests.size
        val pending = requests.count { it.status == "PENDING" }
        val inConference = requests.count { it.status == "IN_CONFERENCE" }
        val completed = requests.count { it.status == "COMPLETED" }
        val divergence = requests.count { it.status == "DIVERGENCE" }

        val finalizedTotal = completed + divergence
        val divergenceRate = if (finalizedTotal > 0) {
            (divergence.toDouble() / finalizedTotal.toDouble()) * 100.0
        } else {
            null // Evita divisão por zero e indica dados insuficientes
        }

        // Cálculo das quantidades físicas acumuladas
        var sumRequested = 0.0
        var sumChecked = 0.0

        requests.forEach { req ->
            req.items.forEach { item ->
                sumRequested += item.requestedQuantity
                if (item.checkedQuantity != null) {
                    sumChecked += item.checkedQuantity
                }
            }
        }

        val totalDifference = sumChecked - sumRequested

        // Agrupamento por loja
        val storeMetrics = requests.groupBy { it.storeId.ifBlank { "Sem Loja" } }
            .map { (storeId, storeReqs) ->
                StoreReportMetric(
                    storeId = storeId,
                    totalRequests = storeReqs.size,
                    completedRequests = storeReqs.count { it.status == "COMPLETED" },
                    divergenceRequests = storeReqs.count { it.status == "DIVERGENCE" }
                )
            }.sortedBy { it.storeId }

        // Agrupamento por setor
        val sectorMetrics = requests.groupBy { it.sectorId.ifBlank { "Geral" } }
            .map { (sectorId, sectorReqs) ->
                SectorReportMetric(
                    sectorId = sectorId,
                    totalRequests = sectorReqs.size,
                    completedRequests = sectorReqs.count { it.status == "COMPLETED" },
                    divergenceRequests = sectorReqs.count { it.status == "DIVERGENCE" }
                )
            }.sortedBy { it.sectorId }

        return OperationalReportData(
            totalRequests = total,
            pendingRequests = pending,
            inConferenceRequests = inConference,
            completedRequests = completed,
            divergenceRequests = divergence,
            divergenceRatePercentage = divergenceRate,
            quantities = QuantityReportMetric(
                totalRequestedQuantity = sumRequested,
                totalCheckedQuantity = sumChecked,
                totalDifference = totalDifference
            ),
            storeMetrics = storeMetrics,
            sectorMetrics = sectorMetrics
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
