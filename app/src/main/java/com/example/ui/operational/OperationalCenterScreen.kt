package com.example.ui.operational

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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.model.WithdrawalRequest
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppErrorBanner
import com.example.ui.components.AppMetricCard
import com.example.ui.components.AppStatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela do Centro Operacional de Baixas redesenhada (Etapa 20).
 * Preserva estritamente os filtros, lógica e testTags existentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalCenterScreen(
    viewModel: OperationalCenterViewModel,
    userProfile: UserProfile,
    onOpenRequestDetail: (String) -> Unit,
    onOpenConferenceDetail: ((String) -> Unit)? = null,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(userProfile) {
        viewModel.loadData(userProfile)
    }

    BackHandler(enabled = true) {
        onBackToHome()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("operational_center_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Centro Operacional",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Acompanhamento e fila de baixas",
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
                        modifier = Modifier.testTag("btn_refresh_operational")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar dados", tint = AppColors.Primary)
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

            // Grid de Métricas Operacionais com Cards Padronizados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppMetricCard(
                    title = "Pendentes",
                    value = uiState.summary.pendingCount.toString(),
                    icon = Icons.Default.HourglassTop,
                    color = AppColors.StatusPending,
                    isSelected = uiState.selectedStatusFilter == "PENDING",
                    onClick = {
                        viewModel.setStatusFilter(if (uiState.selectedStatusFilter == "PENDING") "ALL" else "PENDING")
                    },
                    modifier = Modifier.weight(1f)
                )

                AppMetricCard(
                    title = "Em Conferência",
                    value = uiState.summary.inConferenceCount.toString(),
                    icon = Icons.Default.Assignment,
                    color = AppColors.StatusInConference,
                    isSelected = uiState.selectedStatusFilter == "IN_CONFERENCE",
                    onClick = {
                        viewModel.setStatusFilter(if (uiState.selectedStatusFilter == "IN_CONFERENCE") "ALL" else "IN_CONFERENCE")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppMetricCard(
                    title = "Concluídas",
                    value = uiState.summary.completedCount.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = AppColors.StatusCompleted,
                    isSelected = uiState.selectedStatusFilter == "COMPLETED",
                    onClick = {
                        viewModel.setStatusFilter(if (uiState.selectedStatusFilter == "COMPLETED") "ALL" else "COMPLETED")
                    },
                    modifier = Modifier.weight(1f)
                )

                AppMetricCard(
                    title = "Divergências",
                    value = uiState.summary.divergenceCount.toString(),
                    icon = Icons.Default.Warning,
                    color = AppColors.StatusDivergence,
                    isSelected = uiState.selectedStatusFilter == "DIVERGENCE",
                    onClick = {
                        viewModel.setStatusFilter(if (uiState.selectedStatusFilter == "DIVERGENCE") "ALL" else "DIVERGENCE")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filtros de Período
            Text(
                text = "Período da Consulta",
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
                    FilterChip(
                        selected = uiState.selectedPeriodFilter == "ALL",
                        onClick = { viewModel.setPeriodFilter("ALL") },
                        label = { Text("Tudo") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedPeriodFilter == "TODAY",
                        onClick = { viewModel.setPeriodFilter("TODAY") },
                        label = { Text("Hoje") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedPeriodFilter == "7_DAYS",
                        onClick = { viewModel.setPeriodFilter("7_DAYS") },
                        label = { Text("Últimos 7 dias") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedPeriodFilter == "30_DAYS",
                        onClick = { viewModel.setPeriodFilter("30_DAYS") },
                        label = { Text("Últimos 30 dias") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary
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
                        FilterChip(
                            selected = uiState.selectedStoreFilter == "ALL",
                            onClick = { viewModel.setStoreFilter("ALL") },
                            label = { Text("Todas as Lojas") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.PrimaryContainer,
                                selectedLabelColor = AppColors.Primary
                            )
                        )
                    }
                    items(uiState.availableStores) { storeId ->
                        FilterChip(
                            selected = uiState.selectedStoreFilter == storeId,
                            onClick = { viewModel.setStoreFilter(storeId) },
                            label = { Text(storeId) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.PrimaryContainer,
                                selectedLabelColor = AppColors.Primary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cabeçalho da Lista
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fila de Solicitações (${uiState.requests.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                if (uiState.selectedStatusFilter != "ALL") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColors.PrimaryContainer
                    ) {
                        Text(
                            text = "Filtro: ${getStatusLabel(uiState.selectedStatusFilter)}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading && uiState.requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            } else if (uiState.requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Default.Inbox,
                        title = "Nenhuma solicitação encontrada",
                        message = "Não há baixas correspondentes aos filtros aplicados."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.requests, key = { it.id }) { req ->
                        OperationalRequestCard(
                            request = req,
                            dateFormatter = dateFormatter,
                            userProfile = userProfile,
                            onClick = {
                                if (userProfile.role == UserRole.AVARISTA && req.status in listOf("PENDING", "IN_CONFERENCE") && onOpenConferenceDetail != null) {
                                    onOpenConferenceDetail(req.id)
                                } else {
                                    onOpenRequestDetail(req.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationalRequestCard(
    request: WithdrawalRequest,
    dateFormatter: SimpleDateFormat,
    userProfile: UserProfile,
    onClick: () -> Unit
) {
    val dateStr = remember(request.createdAt) {
        if (request.createdAt > 0L) dateFormatter.format(Date(request.createdAt)) else "Data não informada"
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("operational_card_${request.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Baixa #${request.id.take(6).uppercase()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                AppStatusBadge(status = request.status)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Loja: ${request.storeId}  •  Setor: ${request.sectorId}  •  ${request.items.size} produto(s)",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
            Text(
                text = "Solicitante: ${request.requestedByName}  •  $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextTertiary
            )

            // Detalhe para IN_CONFERENCE
            if (request.status == "IN_CONFERENCE" && !request.conferenceStartedByName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.StatusInConferenceBg
                ) {
                    Text(
                        text = "Conferente: ${request.conferenceStartedByName}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.StatusInConferenceText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Destaque para DIVERGENCE
            if (request.status == "DIVERGENCE") {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.StatusDivergenceBg,
                    border = BorderStroke(1.dp, AppColors.StatusDivergence.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.StatusDivergenceText, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Divergência Registrada",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.StatusDivergenceText
                            )
                        }
                        if (!request.divergenceReason.isNullOrBlank()) {
                            Text(
                                text = "Motivo: ${request.divergenceReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.StatusDivergenceText
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getStatusLabel(status: String): String {
    return when (status) {
        "PENDING" -> "Pendentes"
        "IN_CONFERENCE" -> "Em Conferência"
        "COMPLETED" -> "Concluídas"
        "DIVERGENCE" -> "Divergências"
        else -> "Todas"
    }
}
