package com.example.ui.reports

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppErrorBanner
import com.example.ui.components.AppMetricCard
import com.example.ui.components.AppSectionHeader
import java.util.Locale

/**
 * Tela de Relatórios e Indicadores Operacionais redesenhada (Etapa 20).
 * Preserva estritamente todos os cálculos, filtros e testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    userProfile: UserProfile,
    onOpenOperationalStatus: (String) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userProfile) {
        viewModel.loadReports(userProfile)
    }

    BackHandler(enabled = true) {
        onBackToHome()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Relatórios e Indicadores",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Consolidação analítica de baixas",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextTertiary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("btn_refresh_reports")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar Relatórios", tint = AppColors.Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Error Banner
            if (!uiState.errorMessage.isNullOrEmpty()) {
                AppErrorBanner(
                    message = uiState.errorMessage ?: "",
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Filtros de Período
            Text(
                text = "Período Analisado",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextSecondary
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    val isSelected = uiState.selectedPeriod == "TODAY"
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setPeriodFilter("TODAY") },
                        label = {
                            Text(
                                text = "Hoje",
                                color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary,
                            containerColor = AppColors.Surface,
                            labelColor = AppColors.TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AppColors.Primary else AppColors.BorderSubtle
                        )
                    )
                }
                item {
                    val isSelected = uiState.selectedPeriod == "7_DAYS"
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setPeriodFilter("7_DAYS") },
                        label = {
                            Text(
                                text = "Últimos 7 dias",
                                color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary,
                            containerColor = AppColors.Surface,
                            labelColor = AppColors.TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AppColors.Primary else AppColors.BorderSubtle
                        )
                    )
                }
                item {
                    val isSelected = uiState.selectedPeriod == "30_DAYS"
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setPeriodFilter("30_DAYS") },
                        label = {
                            Text(
                                text = "Últimos 30 dias",
                                color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary,
                            containerColor = AppColors.Surface,
                            labelColor = AppColors.TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AppColors.Primary else AppColors.BorderSubtle
                        )
                    )
                }
                item {
                    val isSelected = uiState.selectedPeriod == "ALL"
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setPeriodFilter("ALL") },
                        label = {
                            Text(
                                text = "Todo o histórico",
                                color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary,
                            containerColor = AppColors.Surface,
                            labelColor = AppColors.TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AppColors.Primary else AppColors.BorderSubtle
                        )
                    )
                }
            }

            // Filtro de Loja para ADMIN
            if (userProfile.role == UserRole.ADMIN && uiState.availableStores.isNotEmpty()) {
                Text(
                    text = "Filtrar por Unidade",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        val isSelected = uiState.selectedStoreId == "ALL"
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setStoreFilter("ALL") },
                            label = {
                                Text(
                                    text = "Todas as Lojas",
                                    color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.PrimaryContainer,
                                selectedLabelColor = AppColors.Primary,
                                containerColor = AppColors.Surface,
                                labelColor = AppColors.TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) AppColors.Primary else AppColors.BorderSubtle
                            )
                        )
                    }
                    items(uiState.availableStores) { storeId ->
                        val isSelected = uiState.selectedStoreId == storeId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setStoreFilter(storeId) },
                            label = {
                                Text(
                                    text = storeId,
                                    color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.PrimaryContainer,
                                selectedLabelColor = AppColors.Primary,
                                containerColor = AppColors.Surface,
                                labelColor = AppColors.TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) AppColors.Primary else AppColors.BorderSubtle
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            } else if (uiState.reportData.totalRequests == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppEmptyState(
                        icon = Icons.Default.QueryStats,
                        title = "Nenhum dado encontrado",
                        message = "Não há registros de baixas no período selecionado."
                    )
                }
            } else {
                val report = uiState.reportData
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Resumo Quantitativo de Solicitações
                    item {
                        AppSectionHeader(
                            title = "Volume Operacional de Baixas",
                            subtitle = "Totais consolidados por status no período"
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppMetricCard(
                                title = "Total Baixas",
                                value = report.totalRequests.toString(),
                                icon = Icons.Default.Assignment,
                                color = AppColors.Primary,
                                onClick = { onOpenOperationalStatus("ALL") },
                                modifier = Modifier.weight(1f)
                            )
                            AppMetricCard(
                                title = "Pendentes",
                                value = report.pendingRequests.toString(),
                                icon = Icons.Default.HourglassTop,
                                color = AppColors.StatusPending,
                                onClick = { onOpenOperationalStatus("PENDING") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppMetricCard(
                                title = "Em Conferência",
                                value = report.inConferenceRequests.toString(),
                                icon = Icons.Default.Assignment,
                                color = AppColors.StatusInConference,
                                onClick = { onOpenOperationalStatus("IN_CONFERENCE") },
                                modifier = Modifier.weight(1f)
                            )
                            AppMetricCard(
                                title = "Concluídas",
                                value = report.completedRequests.toString(),
                                icon = Icons.Default.CheckCircle,
                                color = AppColors.StatusCompleted,
                                onClick = { onOpenOperationalStatus("COMPLETED") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 2. Taxa de Divergência & Divergências
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppMetricCard(
                                title = "Divergências",
                                value = report.divergenceRequests.toString(),
                                icon = Icons.Default.Warning,
                                color = AppColors.StatusDivergence,
                                onClick = { onOpenOperationalStatus("DIVERGENCE") },
                                modifier = Modifier.weight(1f)
                            )

                            val rateText = report.divergenceRatePercentage?.let {
                                String.format(Locale.getDefault(), "%.1f%%", it)
                            } ?: "Sem dados"

                            AppMetricCard(
                                title = "Taxa Divergência",
                                value = rateText,
                                icon = Icons.Default.Percent,
                                color = if (report.divergenceRatePercentage != null && report.divergenceRatePercentage > 0) AppColors.StatusDivergence else AppColors.StatusCompleted,
                                onClick = { onOpenOperationalStatus("DIVERGENCE") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Balanço Físico de Quantidades
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, AppColors.BorderSubtle),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Scale, contentDescription = null, tint = AppColors.Primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Balanço de Itens Físicos",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Qtd. Solicitada:", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.1f", report.quantities.totalRequestedQuantity),
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Qtd. Conferida:", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.1f", report.quantities.totalCheckedQuantity),
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                }
                                Divider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = AppColors.BorderSubtle
                                )

                                val diff = report.quantities.totalDifference
                                val diffPrefix = if (diff > 0) "+" else ""
                                val diffColor = when {
                                    diff < 0 -> AppColors.StatusDivergence
                                    diff > 0 -> AppColors.StatusPending
                                    else -> AppColors.StatusCompleted
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Diferença Acumulada:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        text = "$diffPrefix${String.format(Locale.getDefault(), "%.1f", diff)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = diffColor
                                    )
                                }
                            }
                        }
                    }

                    // 4. Indicadores por Loja (Visão Global ADMIN)
                    if (userProfile.role == UserRole.ADMIN && report.storeMetrics.isNotEmpty()) {
                        item {
                            AppSectionHeader(
                                title = "Acompanhamento por Loja",
                                subtitle = "Distribuição entre as unidades da rede"
                            )
                        }

                        items(report.storeMetrics, key = { it.storeId }) { st ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Store, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = st.storeId,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.TextPrimary
                                        )
                                    }
                                    Text(
                                        text = "Total: ${st.totalRequests}  |  Conc: ${st.completedRequests}  |  Div: ${st.divergenceRequests}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 5. Indicadores por Setor
                    if (report.sectorMetrics.isNotEmpty()) {
                        item {
                            AppSectionHeader(
                                title = "Acompanhamento por Setor",
                                subtitle = "Distribuição operacional interna"
                            )
                        }

                        items(report.sectorMetrics, key = { it.sectorId }) { sec ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sec.sectorId,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        text = "Total: ${sec.totalRequests}  |  Conc: ${sec.completedRequests}  |  Div: ${sec.divergenceRequests}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
