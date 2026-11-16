package com.example.ui.history

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.model.WithdrawalRequest
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppLoadingView
import com.example.ui.components.AppStatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de Histórico de Baixas redesenhada (Etapa 20).
 * Interface corporativa com métricas consolidadas, filtros avançados e preservação de testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    userProfile: UserProfile,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = true) {
        onBackToHome()
    }

    val title = when (userProfile.role) {
        UserRole.ADMIN -> "Histórico Geral de Baixas"
        UserRole.AVARISTA -> "Histórico da Loja ${userProfile.storeId}"
        UserRole.SETOR_LEADER -> "Histórico de Minhas Baixas"
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier.testTag("btn_back_to_home")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = AppColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Resumo Superior Corporativo
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_summary_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Resumo dos Registros",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryItem(label = "Total", count = uiState.totalCount, color = AppColors.Primary)
                        SummaryItem(label = "Concluídas", count = uiState.completedCount, color = AppColors.StatusCompleted)
                        SummaryItem(label = "Divergência", count = uiState.divergenceCount, color = AppColors.StatusDivergence)
                        SummaryItem(label = "Em Conf.", count = uiState.inConferenceCount, color = AppColors.StatusInConference)
                        SummaryItem(label = "Pendentes", count = uiState.pendingCount, color = AppColors.StatusPending)
                    }
                }
            }

            // Barra de Pesquisa
            OutlinedTextField(
                value = uiState.filterSearchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Buscar por ID, solicitante, loja...", color = AppColors.TextTertiary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AppColors.TextSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = AppColors.BorderStrong,
                    focusedContainerColor = AppColors.Surface,
                    unfocusedContainerColor = AppColors.Surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            // Filtros de status e divergência
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.filterDivergenceOnly,
                            onCheckedChange = viewModel::onDivergenceOnlyChanged,
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppColors.StatusDivergence,
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.testTag("checkbox_divergence_only")
                        )
                        Text(
                            text = "Somente Divergências",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.filterDivergenceOnly) AppColors.StatusDivergenceText else AppColors.TextPrimary
                        )
                    }
                }

                StatusFilterChips(
                    selectedStatus = uiState.filterStatus,
                    onStatusSelected = viewModel::onStatusFilterChanged
                )
            }

            // Listagem
            if (uiState.isLoading) {
                AppLoadingView(message = "Carregando histórico de baixas...")
            } else if (uiState.filteredRequests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Default.History,
                        title = "Nenhuma baixa encontrada",
                        message = "Não foram encontrados registros para os filtros selecionados."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredRequests, key = { it.id }) { request ->
                        HistoryItemCard(request = request, onClick = { viewModel.selectRequest(request) })
                    }
                }
            }
        }

        // Modal de Detalhes
        if (uiState.showDetailModal && uiState.selectedRequest != null) {
            HistoryDetailScreen(
                request = uiState.selectedRequest!!,
                evidences = uiState.selectedRequestEvidences,
                onDismiss = viewModel::closeDetailModal
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusFilterChips(selectedStatus: String, onStatusSelected: (String) -> Unit) {
    val statuses = listOf(
        "ALL" to "Todos",
        "PENDING" to "Pendente",
        "IN_CONFERENCE" to "Em conf.",
        "COMPLETED" to "Concluída",
        "DIVERGENCE" to "Divergência"
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(statuses) { (key, label) ->
            val isSelected = selectedStatus == key
            FilterChip(
                selected = isSelected,
                onClick = { onStatusSelected(key) },
                label = {
                    Text(
                        text = label,
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

@Composable
private fun HistoryItemCard(request: WithdrawalRequest, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(request.createdAt))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_item_card_${request.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(AppColors.Primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Solicitação #${request.id.take(6).uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AppStatusBadge(status = request.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Loja: ${request.storeId} • Setor: ${request.sectorId.ifEmpty { "Geral" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${request.items.size} produto(s)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Por: ${request.requestedByName} • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Ver Detalhes",
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
