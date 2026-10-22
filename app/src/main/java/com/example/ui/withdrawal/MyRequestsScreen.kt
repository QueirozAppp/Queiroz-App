package com.example.ui.withdrawal

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.data.WithdrawalRequestRepository
import com.example.core.model.UserProfile
import com.example.core.model.WithdrawalRequest
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppSectionHeader
import com.example.ui.components.AppStatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de Minhas Solicitações de Baixa do Líder de Setor redesenhada (Etapa 20).
 * Interface moderna com status claro e botão de ação rápida para novas baixas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRequestsScreen(
    userProfile: UserProfile,
    onNewRequest: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { WithdrawalRequestRepository() }
    val requests by repository.getMyRequestsFlow(userProfile.uid).collectAsState(initial = emptyList())

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    BackHandler(enabled = true) {
        onBackToHome()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_requests_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Minhas Baixas Solicitadas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "${userProfile.name} • Setor: ${userProfile.sectorId.ifEmpty { "Geral" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewRequest,
                containerColor = AppColors.Primary,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.testTag("fab_new_request")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Solicitação")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            AppSectionHeader(
                title = "Histórico de Solicitações",
                subtitle = "Acompanhamento do status de conferência de suas baixas",
                badgeText = "${requests.size} registro(s)"
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Default.History,
                        title = "Nenhuma baixa criada",
                        message = "Você ainda não solicitou nenhuma baixa de produtos para a avaria.",
                        actionButtonText = "Criar Nova Solicitação",
                        onActionClick = onNewRequest
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(requests, key = { req -> req.id }) { req ->
                        RequestCard(request = req, dateFormatter = dateFormatter)
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: WithdrawalRequest,
    dateFormatter: SimpleDateFormat
) {
    val dateStr = remember(request.createdAt) {
        if (request.createdAt > 0L) dateFormatter.format(Date(request.createdAt)) else "Data não informada"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("request_card_${request.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        color = AppColors.TextPrimary
                    )
                }

                AppStatusBadge(status = request.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Criado em: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
                Text(
                    text = "${request.items.size} produto(s)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary
                )
            }

            if (request.status == "DIVERGENCE" && !request.divergenceReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.StatusDivergenceBg
                ) {
                    Text(
                        text = "Motivo: ${request.divergenceReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.StatusDivergenceText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
