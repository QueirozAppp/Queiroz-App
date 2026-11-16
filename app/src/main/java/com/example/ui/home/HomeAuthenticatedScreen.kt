package com.example.ui.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.core.data.WithdrawalRequestRepository
import com.example.core.data.ProductRegistrationRepository
import com.example.core.data.RequestStatusCounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.update.AppUpdateUiState
import com.example.ui.components.AppColors
import com.example.ui.components.AppMetricCard
import com.example.ui.components.AppOutlinedButton
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSectionHeader
import com.example.ui.theme.ThemeManager
import com.example.ui.update.AppUpdateBanner
import com.example.ui.update.CheckUpdateSectionCard

/**
 * Tela inicial contextual por perfil operacional redesenhada (Etapa 20).
 * Apresenta alta hierarquia visual, foco nas ações operacionais principais e preservação total de testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAuthenticatedScreen(
    userProfile: UserProfile,
    onLogout: () -> Unit,
    onOpenNewRequest: (() -> Unit)? = null,
    onOpenOperationalCenter: (() -> Unit)? = null,
    onOpenReports: (() -> Unit)? = null,
    onOpenAdminUsers: (() -> Unit)? = null,
    onOpenStores: (() -> Unit)? = null,
    onOpenProducts: (() -> Unit)? = null,
    onOpenScanner: (() -> Unit)? = null,
    onOpenMyRequests: (() -> Unit)? = null,
    onOpenConferences: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenAuditLogs: (() -> Unit)? = null,
    onOpenRequestProduct: (() -> Unit)? = null,
    onOpenProductApproval: (() -> Unit)? = null,
    updateUiState: AppUpdateUiState = AppUpdateUiState(),
    onCheckForUpdate: (() -> Unit)? = null,
    onUpdateNow: (() -> Unit)? = null,
    onDismissUpdateBanner: (() -> Unit)? = null,
    onPublishCloudUpdate: ((version: String, build: Int, notes: String, url: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val withdrawalRepo = remember { WithdrawalRequestRepository() }
    val productRegRepo = remember { ProductRegistrationRepository() }
    val summaryFlow = remember(userProfile.uid, userProfile.role, userProfile.storeId) {
        withdrawalRepo.getSummaryFlow(userProfile)
    }
    val pendingProductRequestsFlow = remember(userProfile.uid, userProfile.role, userProfile.storeId) {
        productRegRepo.getPendingCountFlow(userProfile)
    }
    val summaryCounts by summaryFlow.collectAsState(initial = RequestStatusCounts())
    val pendingProductRequestsCount by pendingProductRequestsFlow.collectAsState(initial = 0)

    val historyButtonLabel = when (userProfile.role) {
        UserRole.ADMIN -> "Histórico Geral de Baixas"
        UserRole.AVARISTA -> "Histórico da Loja (${userProfile.storeId})"
        UserRole.SETOR_LEADER -> "Histórico de Minhas Baixas"
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_authenticated_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(AppColors.Primary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "QRZ Stock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Gestão Operacional de Avarias",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextTertiary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { ThemeManager.toggleTheme(AppColors.isDark, context) },
                        modifier = Modifier.testTag("btn_toggle_theme_home")
                    ) {
                        Icon(
                            imageVector = if (AppColors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (AppColors.isDark) "Mudar para tema claro" else "Mudar para tema escuro",
                            tint = AppColors.TextSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.StatusDivergence.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.StatusDivergence
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sair da conta",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sair", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Aviso discreto de nova versão disponível
                if (updateUiState.showBanner && updateUiState.availableUpdate != null) {
                    AppUpdateBanner(
                        updateInfo = updateUiState.availableUpdate,
                        onUpdateClick = { onUpdateNow?.invoke() },
                        onLaterClick = { onDismissUpdateBanner?.invoke() }
                    )
                }

                // Header Card com Identificação Contextual Glassmorphic (Estilo App Financeiro Premium)
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(AppColors.Primary.copy(alpha = 0.20f), CircleShape)
                                        .border(1.dp, AppColors.Primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = AppColors.Primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "Olá, ${userProfile.name.ifEmpty { "Usuário Operacional" }}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = userProfile.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            RoleBadge(role = userProfile.role)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AppColors.SurfaceVariant.copy(alpha = 0.60f),
                            border = BorderStroke(1.dp, AppColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Store,
                                    contentDescription = null,
                                    tint = AppColors.Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loja: ${userProfile.storeId}  •  Setor: ${userProfile.sectorId.ifEmpty { "Geral" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Indicadores de Resumo Operacional KPI (Estilo App Financeiro Premium)
                AppSectionHeader(
                    title = "Visão Geral de Baixas",
                    subtitle = "Acompanhamento em tempo real do fluxo de avarias"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppMetricCard(
                        title = "Pendentes",
                        value = "${summaryCounts.pendingCount}",
                        icon = Icons.Default.HourglassTop,
                        color = AppColors.StatusPending,
                        onClick = { onOpenOperationalCenter?.invoke() },
                        modifier = Modifier.weight(1f)
                    )
                    AppMetricCard(
                        title = "Em Conf.",
                        value = "${summaryCounts.inConferenceCount}",
                        icon = Icons.Default.Assignment,
                        color = AppColors.StatusInConference,
                        onClick = { onOpenOperationalCenter?.invoke() },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppMetricCard(
                        title = "Divergências",
                        value = "${summaryCounts.divergenceCount}",
                        icon = Icons.Default.Warning,
                        color = AppColors.StatusDivergence,
                        onClick = { onOpenOperationalCenter?.invoke() },
                        modifier = Modifier.weight(1f)
                    )
                    AppMetricCard(
                        title = "Concluídas",
                        value = "${summaryCounts.completedCount}",
                        icon = Icons.Default.CheckCircle,
                        color = AppColors.StatusCompleted,
                        onClick = { onOpenOperationalCenter?.invoke() },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ==========================================
                // 1. FLUXO SETOR_LEADER (Hero: NOVA BAIXA)
                // ==========================================
                if (userProfile.role == UserRole.SETOR_LEADER) {
                    AppSectionHeader(
                        title = "Ações do Setor",
                        subtitle = "Criação e acompanhamento de solicitações"
                    )

                    // Hero Button: NOVA BAIXA (Gradiente Premium)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        onClick = {
                            if (onOpenNewRequest != null) onOpenNewRequest()
                            else onOpenMyRequests?.invoke()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.GradientHeroPrimary, RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .testTag("btn_hero_new_request")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NOVA SOLICITAÇÃO DE BAIXA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Registrar avarias, vencidos ou quebras",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Ações de apoio
                    if (onOpenOperationalCenter != null) {
                        ActionCardButton(
                            title = "Centro Operacional de Baixas",
                            subtitle = "Acompanhamento em tempo real por status",
                            icon = Icons.Default.Assignment,
                            iconColor = AppColors.Primary,
                            onClick = onOpenOperationalCenter,
                            modifier = Modifier.testTag("btn_open_operational_center")
                        )
                    }

                    if (onOpenRequestProduct != null) {
                        ActionCardButton(
                            title = "Solicitar Cadastro de Produto",
                            subtitle = "Tirar foto e solicitar novo item para ${userProfile.sectorId.ifBlank { "o setor" }}",
                            icon = Icons.Default.AddAPhoto,
                            iconColor = Color(0xFF0D9488),
                            onClick = onOpenRequestProduct,
                            modifier = Modifier.testTag("btn_open_request_product")
                        )
                    }

                    if (onOpenProducts != null) {
                        ActionCardButton(
                            title = "Consultar Catálogo de Produtos",
                            subtitle = "Catálogo corporativo global com busca",
                            icon = Icons.Default.Inventory2,
                            iconColor = Color(0xFFD97706),
                            onClick = onOpenProducts,
                            modifier = Modifier.testTag("btn_open_products")
                        )
                    }

                    if (onOpenReports != null) {
                        ActionCardButton(
                            title = "Relatórios e Indicadores",
                            subtitle = "Volume, taxas e balanço físico de baixas",
                            icon = Icons.Default.QueryStats,
                            iconColor = AppColors.Primary,
                            onClick = onOpenReports,
                            modifier = Modifier.testTag("btn_open_reports_leader")
                        )
                    }
                }

                // ==========================================
                // 2. FLUXO AVARISTA (Hero: CONFERIR BAIXAS)
                // ==========================================
                if (userProfile.role == UserRole.AVARISTA) {
                    AppSectionHeader(
                        title = "Conferência de Avarias",
                        subtitle = "Conferência física, pesagem e evidências da loja"
                    )

                    // Hero Button: CONFERIR BAIXAS PENDENTES
                    if (onOpenConferences != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.Primary),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            onClick = onOpenConferences,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_open_conferences")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CONFERIR BAIXAS PENDENTES",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Fila física da loja por ordem de prioridade",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    if (onOpenOperationalCenter != null) {
                        ActionCardButton(
                            title = "Centro Operacional da Loja",
                            subtitle = "Status em tempo real das baixas da unidade",
                            icon = Icons.Default.Assignment,
                            iconColor = AppColors.Primary,
                            onClick = onOpenOperationalCenter,
                            modifier = Modifier.testTag("btn_open_operational_center_avarista")
                        )
                    }

                    if (onOpenReports != null) {
                        ActionCardButton(
                            title = "Relatórios e Indicadores da Loja",
                            subtitle = "Divergências, taxa de acerto e balanço físico",
                            icon = Icons.Default.QueryStats,
                            iconColor = Color(0xFF0284C7),
                            onClick = onOpenReports,
                            modifier = Modifier.testTag("btn_open_reports_avarista")
                        )
                    }

                    if (onOpenProductApproval != null) {
                        ActionCardButton(
                            title = "Aprovar Cadastros de Produtos",
                            subtitle = if (pendingProductRequestsCount > 0) "$pendingProductRequestsCount solicitação(ões) pendente(s) da unidade" else "Analisar fotos e descrições solicitadas pelos setores",
                            icon = Icons.Default.PlaylistAddCheck,
                            iconColor = Color(0xFF0D9488),
                            onClick = onOpenProductApproval,
                            modifier = Modifier.testTag("btn_open_product_approval")
                        )
                    }

                    if (onOpenProducts != null) {
                        ActionCardButton(
                            title = "Consultar Catálogo de Produtos",
                            subtitle = "Catálogo corporativo com unidades de medida",
                            icon = Icons.Default.Inventory2,
                            iconColor = Color(0xFFD97706),
                            onClick = onOpenProducts,
                            modifier = Modifier.testTag("btn_open_products")
                        )
                    }
                }

                // ==========================================
                // 3. FLUXO ADMIN (Painel Corporativo)
                // ==========================================
                if (userProfile.role == UserRole.ADMIN) {
                    AppSectionHeader(
                        title = "Governança e Painel Corporativo",
                        subtitle = "Acompanhamento geral de todas as unidades da rede"
                    )

                    if (onOpenOperationalCenter != null) {
                        ActionCardButton(
                            title = "Centro Operacional da Rede",
                            subtitle = "Visão holística com filtros por loja e status",
                            icon = Icons.Default.Assignment,
                            iconColor = AppColors.Primary,
                            onClick = onOpenOperationalCenter,
                            modifier = Modifier.testTag("btn_open_operational_center_admin")
                        )
                    }

                    if (onOpenReports != null) {
                        ActionCardButton(
                            title = "Relatórios e Indicadores Globais",
                            subtitle = "Taxa de divergência e distribuição por loja/setor",
                            icon = Icons.Default.QueryStats,
                            iconColor = Color(0xFF0284C7),
                            onClick = onOpenReports,
                            modifier = Modifier.testTag("btn_open_reports_admin")
                        )
                    }

                    if (onOpenAdminUsers != null) {
                        ActionCardButton(
                            title = "Gerenciar Usuários e Perfis",
                            subtitle = "Controle de acessos, lojas e atribuições",
                            icon = Icons.Default.AdminPanelSettings,
                            iconColor = AppColors.Secondary,
                            onClick = onOpenAdminUsers,
                            modifier = Modifier.testTag("btn_open_admin_users")
                        )
                    }

                    if (onOpenStores != null) {
                        ActionCardButton(
                            title = "Gerenciar Lojas e Setores",
                            subtitle = "Cadastro e edição de unidades filiais e setores",
                            icon = Icons.Default.Store,
                            iconColor = Color(0xFF0D9488),
                            onClick = onOpenStores,
                            modifier = Modifier.testTag("btn_open_stores")
                        )
                    }

                    if (onOpenProductApproval != null) {
                        ActionCardButton(
                            title = "Aprovar Cadastros de Produtos",
                            subtitle = if (pendingProductRequestsCount > 0) "$pendingProductRequestsCount solicitação(ões) pendente(s) na rede" else "Aprovar ou recusar solicitações de novos itens",
                            icon = Icons.Default.PlaylistAddCheck,
                            iconColor = Color(0xFF0D9488),
                            onClick = onOpenProductApproval,
                            modifier = Modifier.testTag("btn_open_product_approval_admin")
                        )
                    }

                    if (onOpenProducts != null) {
                        ActionCardButton(
                            title = "Catálogo Global de Produtos",
                            subtitle = "Cadastro de itens, unidades e códigos EAN",
                            icon = Icons.Default.Inventory2,
                            iconColor = Color(0xFFD97706),
                            onClick = onOpenProducts,
                            modifier = Modifier.testTag("btn_open_products")
                        )
                    }

                    if (onOpenAuditLogs != null) {
                        ActionCardButton(
                            title = "Trilha de Auditoria Imutável",
                            subtitle = "Registro de segurança inviolável de eventos",
                            icon = Icons.Default.Security,
                            iconColor = Color(0xFFDC2626),
                            onClick = onOpenAuditLogs,
                            modifier = Modifier.testTag("btn_open_audit_logs")
                        )
                    }
                }

                // Sessão e Identificação
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Detalhes da Sessão",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                        }
                        ProfileInfoRow(label = "E-mail:", value = userProfile.email)
                        ProfileInfoRow(label = "Status da Conta:", value = if (userProfile.active) "Ativo" else "Inativo")
                        ProfileInfoRow(label = "Perfil:", value = userProfile.role.label)
                    }
                }

                // Cartão de Verificação de Atualização do Aplicativo
                if (onCheckForUpdate != null) {
                    CheckUpdateSectionCard(
                        isChecking = updateUiState.isChecking,
                        statusMessage = updateUiState.statusMessage,
                        isStatusError = updateUiState.isStatusError,
                        onCheckForUpdateClick = onCheckForUpdate,
                        availableUpdate = updateUiState.availableUpdate,
                        onUpdateNowClick = onUpdateNow,
                        isAdmin = userProfile.role == UserRole.ADMIN,
                        onPublishCloudUpdate = onPublishCloudUpdate
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCardButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(role: UserRole) {
    val (bgColor, textColor) = when (role) {
        UserRole.ADMIN -> AppColors.PrimaryContainer to AppColors.Primary
        UserRole.AVARISTA -> AppColors.SecondaryContainer to AppColors.Secondary
        UserRole.SETOR_LEADER -> AppColors.StatusPendingBg to AppColors.StatusPendingText
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = if (role == UserRole.ADMIN) "Admin" else role.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
    }
}
