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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                placeholder = { Text("Pesquisar por pessoa, ação, loja ou detalhe...", color = AppColors.TextTertiary) },
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filtros: ${listOfNotNull(
                                if (uiState.filterRole != "ALL") "Perfil: ${AuditLogFormatter.formatRole(uiState.filterRole)}" else null,
                                if (uiState.filterStoreId != "ALL") "Loja: ${uiState.filterStoreId}" else null,
                                if (uiState.filterAction != "ALL") "Ação: ${AuditLogFormatter.formatActionTitle(uiState.filterAction)}" else null,
                                if (uiState.filterEntityType != "ALL") "Tipo: ${AuditLogFormatter.formatEntityType(uiState.filterEntityType)}" else null
                            ).joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
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
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }
    val formattedDate = remember(log.timestamp) {
        if (log.timestamp > 0) dateFormat.format(Date(log.timestamp)) else "Sem data"
    }
    val actionTitle = remember(log.action) { AuditLogFormatter.formatActionTitle(log.action) }
    val entityLabel = remember(log.entityType, log.entityId) {
        "${AuditLogFormatter.formatEntityType(log.entityType)} ${AuditLogFormatter.formatReferenceCode(log.entityId)}"
    }
    val roleLabel = remember(log.userRole) { AuditLogFormatter.formatRole(log.userRole) }
    val friendlyDescription = remember(log.id, log.description, log.action) {
        AuditLogFormatter.formatDescription(log)
    }
    val locationText = remember(log.storeId, log.sectorId, log.entityType) {
        AuditLogFormatter.formatLocation(log)
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
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Data e Perfil do Usuário
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
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
                        text = roleLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = when (log.userRole) {
                            "ADMIN" -> AppColors.StatusDivergenceText
                            "AVARISTA" -> AppColors.Primary
                            else -> AppColors.OnSecondaryContainer
                        }
                    )
                }
            }

            // Ação em linguagem simples
            Text(
                text = actionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            // Tipo e código curto do item
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppColors.SurfaceVariant
            ) {
                Text(
                    text = entityLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quem fez e o que aconteceu
            Text(
                text = "Feito por: ${log.userName.ifBlank { "Sistema" }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )

            Text(
                text = friendlyDescription,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Local e seta de detalhes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = locationText,
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

@Composable
private fun AuditLogDetailDialog(
    log: AuditLog,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(log.timestamp) {
        if (log.timestamp > 0) dateFormat.format(Date(log.timestamp)) else "Sem data registrada"
    }
    val actionTitle = remember(log.action) { AuditLogFormatter.formatActionTitle(log.action) }
    val entityLabel = remember(log.entityType, log.entityId) {
        "${AuditLogFormatter.formatEntityType(log.entityType)} (${AuditLogFormatter.formatReferenceCode(log.entityId)})"
    }
    val roleLabel = remember(log.userRole) { AuditLogFormatter.formatRole(log.userRole) }
    val friendlyDescription = remember(log.id, log.description, log.action) {
        AuditLogFormatter.formatDescription(log)
    }
    val locationText = remember(log.storeId, log.sectorId, log.entityType) {
        AuditLogFormatter.formatLocation(log)
    }
    val friendlyMetadata = remember(log.metadata) {
        AuditLogFormatter.formatFriendlyMetadata(log.metadata)
    }
    var showTechnicalIds by remember(log.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
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
                    text = "Resumo do Registro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bloco de destaque explicando de forma direta o que aconteceu
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.PrimaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = actionTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary
                        )
                        Text(
                            text = friendlyDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextPrimary
                        )
                    }
                }

                DetailRow(
                    label = "Quem fez",
                    value = "${log.userName.ifBlank { "Sistema" }} ($roleLabel)"
                )
                DetailRow(label = "Quando", value = formattedDate)
                DetailRow(label = "Onde", value = locationText)
                DetailRow(label = "Tipo de item", value = entityLabel)

                if (friendlyMetadata.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Detalhes da Operação:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                    HorizontalDivider(color = AppColors.BorderSubtle)
                    friendlyMetadata.forEach { item ->
                        DetailRow(label = item.label, value = item.value)
                    }
                }

                TextButton(
                    onClick = { showTechnicalIds = !showTechnicalIds },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (showTechnicalIds) "Ocultar códigos técnicos" else "Ver códigos técnicos",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary
                    )
                }

                if (showTechnicalIds) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.SurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DetailRow(label = "Código do Evento", value = log.id)
                            DetailRow(label = "Código do Item", value = log.entityId.ifBlank { "—" })
                            DetailRow(label = "ID do Usuário", value = log.userId.ifBlank { "—" })
                            DetailRow(label = "Código da Ação", value = log.action)
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
                Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextSecondary,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
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
    val entityTypes = listOf(
        "ALL",
        AuditEntityType.WITHDRAWAL_REQUEST,
        AuditEntityType.EVIDENCE,
        AuditEntityType.USER,
        AuditEntityType.STORE,
        AuditEntityType.SECTOR,
        AuditEntityType.PRODUCT,
        AuditEntityType.PRODUCT_REQUEST
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Text(
                text = "Filtrar Registros",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Perfil de quem fez:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.chunked(2).forEach { rowRoles ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowRoles.forEach { role ->
                                val selected = uiState.filterRole == role
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) AppColors.Primary else AppColors.SurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.onFilterRoleChanged(role) }
                                ) {
                                    Text(
                                        text = if (role == "ALL") "Todos" else AuditLogFormatter.formatRole(role),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.White else AppColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tipo de atividade:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        text = if (entity == "ALL") "Todas" else AuditLogFormatter.formatEntityType(entity),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.White else AppColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
