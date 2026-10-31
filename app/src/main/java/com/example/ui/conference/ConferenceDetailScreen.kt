package com.example.ui.conference

import androidx.activity.compose.BackHandler

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.ConferenceEvidence
import com.example.core.model.UserProfile
import com.example.core.model.WithdrawalRequestItem
import com.example.ui.components.AppColors
import com.example.ui.components.AppErrorBanner
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSectionHeader
import com.example.ui.components.AppStatusBadge
import java.io.ByteArrayOutputStream

/**
 * Tela de Conferência Física e Evidências do Avarista redesenhada (Etapa 20).
 * Interface limpa com destaque para divergências, fotos de evidência e preservação estrita de testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceDetailScreen(
    requestId: String,
    viewModel: ConferenceViewModel,
    userProfile: UserProfile,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReasonInput by remember { mutableStateOf("") }
    var showAddEvidenceDialog by remember { mutableStateOf(false) }
    var pendingImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var evidenceDescriptionInput by remember { mutableStateOf("") }
    var selectedItemProductIdForEvidence by remember { mutableStateOf<String?>(null) }
    var previewEvidenceUrl by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            pendingImageBytes = stream.toByteArray()
            showAddEvidenceDialog = true
        }
    }

    LaunchedEffect(requestId) {
        viewModel.loadRequestForConference(requestId, userProfile)
    }

    BackHandler(enabled = true) {
        onBackToList()
    }

    val allItemsChecked = remember(uiState.items) {
        uiState.items.isNotEmpty() && uiState.items.all { it.checkedQuantity != null }
    }

    val hasDivergence = remember(uiState.items) {
        uiState.items.any { it.checkedQuantity != null && it.checkedQuantity != it.requestedQuantity }
    }

    val itemsWithoutDivergenceCount = remember(uiState.items) {
        uiState.items.count { it.checkedQuantity != null && it.checkedQuantity == it.requestedQuantity }
    }

    val itemsWithDivergenceCount = remember(uiState.items) {
        uiState.items.count { it.checkedQuantity != null && it.checkedQuantity != it.requestedQuantity }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("conference_detail_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Conferência Física",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Solicitação #${requestId.take(6).uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextTertiary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToList) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
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
                    onRetry = { viewModel.loadRequestForConference(requestId, userProfile) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (uiState.isLoading && uiState.currentRequest == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            } else if (uiState.currentRequest != null) {
                val req = uiState.currentRequest!!

                // Card com Metadados da Baixa
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dados da Solicitação",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                            AppStatusBadge(status = req.status)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loja: ${req.storeId}  •  Setor: ${req.sectorId}  •  Solicitante: ${req.requestedByName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                        if (!req.conferenceStartedByName.isNullOrBlank()) {
                            Text(
                                text = "Conferente Responsável: ${req.conferenceStartedByName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.StatusInConferenceText
                            )
                        }
                    }
                }

                // Botão de Adicionar Evidência (Disponível somente em IN_CONFERENCE)
                if (!uiState.isFinalized) {
                    Button(
                        onClick = {
                            evidenceDescriptionInput = ""
                            selectedItemProductIdForEvidence = null
                            cameraLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(bottom = 4.dp)
                            .testTag("btn_add_evidence")
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adicionar Foto de Evidência", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }

                // Lista de Evidências Anexadas
                if (uiState.evidences.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Evidências Anexadas (${uiState.evidences.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.evidences, key = { it.id }) { ev ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { previewEvidenceUrl = ev.downloadUrl }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(AppColors.PrimaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (ev.description.isNotBlank()) ev.description else "Foto de Evidência",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.TextPrimary
                                        )
                                        Text(
                                            text = "Enviado por: ${ev.uploadedByName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppColors.TextTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Resumo de Conferência Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Total Itens: ${uiState.items.size}", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                        Text(text = "Conferem: $itemsWithoutDivergenceCount", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.StatusCompleted)
                        Text(
                            text = "Com divergência: $itemsWithDivergenceCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (itemsWithDivergenceCount > 0) AppColors.StatusDivergence else AppColors.TextSecondary
                        )
                    }
                }

                Text(
                    text = "Produtos a Conferir (${uiState.items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.items, key = { it.productId }) { item ->
                        ConferenceItemCard(
                            item = item,
                            isReadOnly = uiState.isFinalized,
                            onEdit = { viewModel.startEditingItem(item.productId) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!uiState.isFinalized) {
                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = allItemsChecked && !uiState.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasDivergence) AppColors.StatusDivergence else AppColors.Primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_finish_conference")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasDivergence) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasDivergence) "Finalizar com Divergência" else "Finalizar Conferência",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.StatusDivergenceText),
                            border = BorderStroke(1.dp, AppColors.StatusDivergence),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_cancel_conference_request")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = AppColors.StatusDivergenceText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Excluir / Cancelar Solicitação", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onBackToList,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Voltar para Fila", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Dialog for entering checked quantity
        if (uiState.editingItemProductId != null) {
            val editingItem = uiState.items.find { it.productId == uiState.editingItemProductId }
            if (editingItem != null) {
                CheckQuantityDialog(
                    item = editingItem,
                    quantityInput = uiState.checkedQuantityInput,
                    onQuantityChanged = viewModel::onCheckedQuantityInputChanged,
                    onConfirm = viewModel::confirmItemCheck,
                    onDismiss = viewModel::cancelEditingItem
                )
            }
        }

        // Dialog for adding evidence description
        if (showAddEvidenceDialog) {
            AlertDialog(
                onDismissRequest = { showAddEvidenceDialog = false },
                title = { Text("Evidência Fotográfica", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Informe uma descrição opcional para esta imagem (máx. 500 caracteres):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                        OutlinedTextField(
                            value = evidenceDescriptionInput,
                            onValueChange = { if (it.length <= 500) evidenceDescriptionInput = it },
                            label = { Text("Descrição da Evidência") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_evidence_description"),
                            minLines = 2
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val bytes = pendingImageBytes
                            if (bytes != null && uiState.currentRequest != null) {
                                showAddEvidenceDialog = false
                                viewModel.uploadEvidence(
                                    requestId = uiState.currentRequest!!.id,
                                    imageBytes = bytes,
                                    description = evidenceDescriptionInput,
                                    itemProductId = selectedItemProductIdForEvidence,
                                    userProfile = userProfile
                                )
                                pendingImageBytes = null
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        modifier = Modifier.testTag("btn_confirm_upload_evidence")
                    ) {
                        Text("Confirmar Envio", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showAddEvidenceDialog = false; pendingImageBytes = null },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Confirmation Dialog before finishing conference with mandatory justification for divergence
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                icon = {
                    Icon(
                        imageVector = if (hasDivergence) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (hasDivergence) AppColors.StatusDivergence else AppColors.StatusCompleted
                    )
                },
                title = {
                    Text(
                        text = if (hasDivergence) "Confirmar com Divergência?" else "Finalizar Conferência?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (hasDivergence)
                                "Existem diferenças entre as quantidades solicitadas e conferidas. A solicitação será gravada com o status DIVERGÊNCIA."
                            else
                                "Todos os itens conferem exatamente com as quantidades solicitadas. A baixa será finalizada como CONCLUÍDA.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )

                        if (hasDivergence) {
                            OutlinedTextField(
                                value = uiState.divergenceReasonInput,
                                onValueChange = viewModel::onDivergenceReasonChanged,
                                label = { Text("Justificativa Obrigatória da Divergência") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_divergence_reason"),
                                minLines = 2
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!hasDivergence || uiState.divergenceReasonInput.trim().isNotBlank()) {
                                showConfirmDialog = false
                                viewModel.finishConference(userProfile) {}
                            }
                        },
                        enabled = !hasDivergence || uiState.divergenceReasonInput.trim().isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasDivergence) AppColors.StatusDivergence else AppColors.Primary
                        ),
                        modifier = Modifier.testTag("btn_confirm_finish")
                    ) {
                        Text(if (hasDivergence) "Finalizar com Divergência" else "Finalizar Conferência", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showConfirmDialog = false },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Dialog for Cancelling/Deleting Conference Request with Mandatory Reason
        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AppColors.StatusDivergence
                    )
                },
                title = {
                    Text(
                        text = "Excluir / Cancelar Solicitação",
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Informe obrigatoriamente o motivo pelo qual esta solicitação está sendo excluída/cancelada da conferência:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                        OutlinedTextField(
                            value = cancelReasonInput,
                            onValueChange = { cancelReasonInput = it },
                            label = { Text("Motivo do Cancelamento *") },
                            placeholder = { Text("Ex: Produtos lançados indevidamente pelo setor...") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = false,
                            minLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_cancellation_reason")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (cancelReasonInput.trim().isNotBlank()) {
                                showCancelDialog = false
                                viewModel.cancelRequest(cancelReasonInput, userProfile) {
                                    onBackToList()
                                }
                            }
                        },
                        enabled = cancelReasonInput.trim().isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.StatusDivergence),
                        modifier = Modifier.testTag("btn_confirm_cancel_request")
                    ) {
                        Text("Confirmar Exclusão", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showCancelDialog = false },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Voltar")
                    }
                }
            )
        }
    }
}

@Composable
private fun ConferenceItemCard(
    item: WithdrawalRequestItem,
    isReadOnly: Boolean,
    onEdit: () -> Unit
) {
    val isChecked = item.checkedQuantity != null
    val diff = if (isChecked) (item.checkedQuantity!! - item.requestedQuantity) else 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isChecked && diff != 0.0) AppColors.StatusDivergence.copy(alpha = 0.5f) else AppColors.BorderSubtle
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conference_item_card_${item.productId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "EAN: ${item.barcode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
                    )
                }

                if (!isReadOnly) {
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isChecked) AppColors.PrimaryContainer else AppColors.Primary,
                            contentColor = if (isChecked) AppColors.Primary else Color.White
                        ),
                        modifier = Modifier.testTag("btn_check_item_${item.productId}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isChecked) "Alterar" else "Conferir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.SurfaceVariant
                ) {
                    Text(
                        text = "Solicitado: ${item.requestedQuantity} ${item.unit}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isChecked) AppColors.PrimaryContainer else AppColors.StatusPendingBg
                ) {
                    Text(
                        text = if (isChecked) "Conferido: ${item.checkedQuantity} ${item.unit}" else "Pendente conferência",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isChecked) AppColors.Primary else AppColors.StatusPendingText
                    )
                }
            }

            if (isChecked) {
                Spacer(modifier = Modifier.height(8.dp))
                val diffColor = when {
                    diff == 0.0 -> AppColors.StatusCompleted
                    diff < 0.0 -> AppColors.StatusDivergence
                    else -> AppColors.StatusPending
                }
                val diffFormatted = if (diff > 0) "+$diff" else "$diff"
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = diffColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Diferença Física: $diffFormatted ${item.unit}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = diffColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckQuantityDialog(
    item: WithdrawalRequestItem,
    quantityInput: String,
    onQuantityChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conferência Física", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.SurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = item.productName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Solicitado pelo líder: ${item.requestedQuantity} ${item.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = onQuantityChanged,
                    label = { Text("Qtd. Realmente Apurada (${item.unit})") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.BorderStrong
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_checked_quantity")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier.testTag("btn_confirm_item_check")
            ) {
                Text("Confirmar Item", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}
