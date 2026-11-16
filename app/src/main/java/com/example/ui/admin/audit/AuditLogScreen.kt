package com.example.ui.admin.audit

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.AuditLog
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppLoadingView
import com.example.ui.components.AppSectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela administrativa para consulta e rastreabilidade da Trilha de Auditoria redesenhada (Etapa 20).
 * Interface corporativa com foco em governança, segurança e preservação de testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFiltersDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadInitialLogs()
    }

    BackHandler(enabled = true) {
        onBackToHome()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("audit_log_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(AppColors.PrimaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Trilha de Auditoria",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = AppColors.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showFiltersDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtros",
                            tint = AppColors.TextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.loadInitialLogs() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = AppColors.TextSecondary
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Pesquisar por usuário, ação, ID ou descrição...", color = AppColors.TextTertiary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AppColors.TextSecondary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                        }
                    }
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
                    .padding(bottom = 8.dp)
                    .testTag("search_audit_input")
            )

            // Active Filters indicator
            if (uiState.filterRole != "ALL" || uiState.filterStoreId != "ALL" || uiState.filterAction != "ALL" || uiState.filterEntityType != "ALL") {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.PrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filtros: ${listOfNotNull(
                                if (uiState.filterRole != "ALL") "Papel: ${uiState.filterRole}" else null,
                                if (uiState.filterStoreId != "ALL") "Loja: ${uiState.filterStoreId}" else null,
                                if (uiState.filterAction != "ALL") "Ação: ${uiState.filterAction}" else null,
                                if (uiState.filterEntityType != "ALL") "Entidade: ${uiState.filterEntityType}" else null
                            ).joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        TextButton(onClick = {
                            viewModel.onFilterRoleChanged("ALL")
                            viewModel.onFilterStoreChanged("ALL")
                            viewModel.onFilterActionChanged("ALL")
                            viewModel.onFilterEntityTypeChanged("ALL")
                        }) {
                            Text("Limpar", style = MaterialTheme.typography.labelSmall, color = AppColors.Primary)
                        }
                    }
                }
            }

            AppSectionHeader(
                title = "Eventos Auditados",
                subtitle = "Registro perene de operações e segurança",
                badgeText = "${uiState.filteredLogs.size} evento(s)"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Loading / Error states
            if (!uiState.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.StatusDivergenceBg,
                    border = BorderStroke(1.dp, AppColors.StatusDivergence.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = AppColors.StatusDivergenceText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (uiState.isLoading && uiState.logs.isEmpty()) {
                AppLoadingView(message = "Carregando registros imutáveis de auditoria...")
            } else if (uiState.filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Default.Security,
                        title = "Nenhum evento registrado",
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
                    items(uiState.filteredLogs, key = { it.id }) { log ->
                        AuditLogCard(
                            log = log,
                            onClick = { viewModel.onSelectLog(log) }
                        )
                    }

                    if (uiState.canLoadMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AppColors.Primary)
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.loadMoreLogs() },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, AppColors.BorderStrong),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                                    ) {
                                        Text("Carregar mais eventos", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    uiState.selectedLog?.let { log ->
        AuditLogDetailDialog(
            log = log,
            onDismiss = { viewModel.onSelectLog(null) }
        )
    }

    // Filters Dialog
    if (showFiltersDialog) {
        AuditFiltersDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showFiltersDialog = false }
        )
    }
}

@Composable
private fun AuditLogCard(
    log: AuditLog,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(log.timestamp) {
        if (log.timestamp > 0) dateFormat.format(Date(log.timestamp)) else "Sem data"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("audit_card_${log.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Date & Role
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (log.userRole) {
                        "ADMIN" -> AppColors.StatusDivergenceBg
                        "AVARISTA" -> AppColors.PrimaryContainer
                        else -> AppColors.SecondaryContainer
                    }
                ) {
                    Text(
                        text = log.userRole,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (log.userRole) {
                            "ADMIN" -> AppColors.StatusDivergenceText
                            "AVARISTA" -> AppColors.Primary
                            else -> AppColors.OnSecondaryContainer
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Badge & Entity
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.SurfaceVariant
                ) {
                    Text(
                        text = log.action,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                }
                Text(
                    text = "${log.entityType} #${log.entityId.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // User info & Description
            Text(
                text = "Por: ${log.userName.ifBlank { log.userId }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )

            if (log.description.isNotBlank()) {
                Text(
                    text = log.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    maxLines = 2
                )
            }

            if (log.storeId.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Loja: ${log.storeId}" + if (log.sectorId.isNotBlank()) " | Setor: ${log.sectorId}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
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
}

@Composable
private fun AuditLogDetailDialog(
    log: AuditLog,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(log.timestamp) {
        if (log.timestamp > 0) dateFormat.format(Date(log.timestamp)) else "Sem data"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(AppColors.PrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AppColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Detalhes do Evento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "ID do Evento", value = log.id)
                DetailRow(label = "Data e Hora", value = formattedDate)
                DetailRow(label = "Ação Executada", value = log.action)
                DetailRow(label = "Entidade", value = log.entityType)
                DetailRow(label = "ID do Objeto", value = log.entityId)
                DetailRow(label = "Usuário", value = log.userName.ifBlank { log.userId })
                DetailRow(label = "UID", value = log.userId)
                DetailRow(label = "Papel (Role)", value = log.userRole)
                DetailRow(label = "Loja", value = log.storeId.ifBlank { "N/A" })
                DetailRow(label = "Setor", value = log.sectorId.ifBlank { "N/A" })
                DetailRow(label = "Descrição", value = log.description.ifBlank { "Sem descrição adicional" })

                if (log.metadata.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Metadados da Operação:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                    HorizontalDivider(color = AppColors.BorderSubtle)
                    log.metadata.forEach { (key, value) ->
                        DetailRow(label = "  • $key", value = value)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditFiltersDialog(
    uiState: AuditLogUiState,
    viewModel: AuditLogViewModel,
    onDismiss: () -> Unit
) {
    val roles = listOf("ALL", "ADMIN", "AVARISTA", "SETOR_LEADER")
    val entityTypes = listOf("ALL", AuditEntityType.WITHDRAWAL_REQUEST, AuditEntityType.EVIDENCE, AuditEntityType.USER, AuditEntityType.STORE, AuditEntityType.SECTOR, AuditEntityType.PRODUCT, AuditEntityType.PRODUCT_REQUEST)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Text(
                text = "Filtrar Trilha de Auditoria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Role filter
                Text("Papel do Usuário:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    roles.forEach { role ->
                        val selected = uiState.filterRole == role
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) AppColors.Primary else AppColors.SurfaceVariant,
                            modifier = Modifier
                                .clickable { viewModel.onFilterRoleChanged(role) }
                                .padding(2.dp)
                        ) {
                            Text(
                                text = if (role == "ALL") "Todos" else role,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else AppColors.TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Entity Type filter
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tipo de Entidade:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entityTypes.chunked(2).forEach { rowEntities ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowEntities.forEach { entity ->
                                val selected = uiState.filterEntityType == entity
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) AppColors.Primary else AppColors.SurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.onFilterEntityTypeChanged(entity) }
                                ) {
                                    Text(
                                        text = if (entity == "ALL") "Todas as Entidades" else entity,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.White else AppColors.TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text("Aplicar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}
