package com.example.ui.reports

/**
 * Métricas operacionais por Loja (Etapa 18).
 */
data class StoreReportMetric(
    val storeId: String,
    val totalRequests: Int = 0,
    val completedRequests: Int = 0,
    val divergenceRequests: Int = 0
)

/**
 * Métricas operacionais por Setor (Etapa 18).
 */
data class SectorReportMetric(
    val sectorId: String,
    val totalRequests: Int = 0,
    val completedRequests: Int = 0,
    val divergenceRequests: Int = 0
)

/**
 * Quantidades acumuladas solicitadas, conferidas e diferença real preservando sinal (+/-).
 */
data class QuantityReportMetric(
    val totalRequestedQuantity: Double = 0.0,
    val totalCheckedQuantity: Double = 0.0,
    val totalDifference: Double = 0.0
)

/**
 * Dados consolidados do Relatório Operacional.
 */
data class OperationalReportData(
    val totalRequests: Int = 0,
    val pendingRequests: Int = 0,
    val inConferenceRequests: Int = 0,
    val completedRequests: Int = 0,
    val divergenceRequests: Int = 0,
    val divergenceRatePercentage: Double? = null, // null indica sem dados finalizados suficientes (evita divisão por zero)
    val quantities: QuantityReportMetric = QuantityReportMetric(),
    val storeMetrics: List<StoreReportMetric> = emptyList(),
    val sectorMetrics: List<SectorReportMetric> = emptyList()
)

/**
 * Estado da tela de Relatórios e Indicadores Operacionais.
 */
data class ReportsUiState(
    val reportData: OperationalReportData = OperationalReportData(),
    val selectedPeriod: String = "30_DAYS", // TODAY, 7_DAYS, 30_DAYS, ALL
    val selectedStoreId: String = "ALL",
    val selectedSectorId: String = "ALL",
    val availableStores: List<String> = emptyList(),
    val availableSectors: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
