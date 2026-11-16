package com.example.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ConferenceEvidence
import com.example.core.model.WithdrawalRequest
import com.example.core.model.WithdrawalRequestItem
import com.example.ui.components.AppColors
import com.example.ui.components.AppStatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detalhes da Baixa no Histórico redesenhada (Etapa 20).
 * Apresenta dados organizados, status destacado e preservação de testTags.
 */
@Composable
fun HistoryDetailScreen(
    request: WithdrawalRequest,
    evidences: List<ConferenceEvidence>,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(request.createdAt))
    val checkedDateStr = if (request.checkedAt != null) dateFormat.format(Date(request.checkedAt!!)) else "N/A"
    val startedDateStr = if (request.conferenceStartedAt != null) dateFormat.format(Date(request.conferenceStartedAt!!)) else "N/A"

    val localStartedByName = request.conferenceStartedByName ?: ""
    val localCheckedByName = request.checkedByName ?: ""
    val localDivReason = request.divergenceReason ?: ""
    val isDivergence = request.status == "DIVERGENCE" && localDivReason.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detalhes da Solicitação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                AppStatusBadge(status = request.status)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
                    .testTag("history_detail_modal"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // IDENTIFICAÇÃO
                item {
                    DetailSectionCard(title = "Identificação Operacional") {
                        DetailRow("ID da Baixa:", "#${request.id.take(8).uppercase()}")
                        DetailRow("Criada em:", dateStr)
                        DetailRow("Loja:", request.storeId)
                        DetailRow("Setor:", request.sectorId.ifEmpty { "Geral" })
                        DetailRow("Solicitante:", request.requestedByName)
                    }
                }

                // CONFERÊNCIA
                item {
                    DetailSectionCard(title = "Dados da Conferência") {
                        DetailRow("Iniciado por:", if (localStartedByName.isBlank()) "Não iniciado" else localStartedByName)
                        DetailRow("Início em:", startedDateStr)
                        DetailRow("Conferido por:", if (localCheckedByName.isBlank()) "Pendente" else localCheckedByName)
                        DetailRow("Conclusão em:", checkedDateStr)
                    }
                }

                // DIVERGÊNCIA MOTIVO (se houver)
                if (isDivergence) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppColors.StatusDivergenceBg),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AppColors.StatusDivergence.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Divergência Registrada",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.StatusDivergenceText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = localDivReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.StatusDivergenceText
                                )
                            }
                        }
                    }
                }

                // PRODUTOS
                item {
                    Text(
                        text = "Itens Solicitados (${request.items.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                }

                items(request.items, key = { it.productId }) { item ->
                    ProductDetailItemCard(item = item)
                }

                // EVIDÊNCIAS
                if (evidences.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Evidências Fotográficas (${evidences.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    }

                    items(evidences, key = { it.id }) { ev ->
                        EvidenceDetailCard(evidence = ev)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier.testTag("btn_close_history_detail")
            ) {
                Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DetailSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.Primary
            )
            HorizontalDivider(color = AppColors.BorderSubtle)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
    }
}

@Composable
private fun ProductDetailItemCard(item: WithdrawalRequestItem) {
    val checkedQty = item.checkedQuantity ?: 0.0
    val diff = checkedQty - item.requestedQuantity
    val diffStr = if (diff > 0) "+$diff ${item.unit}" else "$diff ${item.unit}"
    val diffColor = when {
        diff < 0 -> AppColors.StatusDivergence
        diff > 0 -> AppColors.StatusPending
        else -> AppColors.StatusCompleted
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = item.productName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(text = "Cód: ${item.barcode} • Un: ${item.unit}", style = MaterialTheme.typography.labelSmall, color = AppColors.TextTertiary)
            HorizontalDivider(color = AppColors.BorderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Sol: ${item.requestedQuantity} ${item.unit}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                Text(text = "Conf: $checkedQty ${item.unit}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Text(text = "Dif: $diffStr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = diffColor)
            }
        }
    }
}

@Composable
private fun EvidenceDetailCard(evidence: ConferenceEvidence) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(evidence.createdAt))

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Enviado por: ${evidence.uploadedByName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(text = "Data: $dateStr", style = MaterialTheme.typography.labelSmall, color = AppColors.TextTertiary)
            if (evidence.description.isNotBlank()) {
                Text(text = "Obs: ${evidence.description}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
    }
}
