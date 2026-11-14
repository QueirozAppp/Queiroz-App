package com.example.ui.product.approval

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.ProductRegistrationRequest
import com.example.core.model.ProductRequestStatus
import com.example.core.model.UserProfile
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppSectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductApprovalScreen(
    viewModel: ProductApprovalViewModel,
    userProfile: UserProfile,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var zoomedPhotoBase64 by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userProfile) {
        viewModel.loadRequests(userProfile)
    }

    BackHandler {
        onBackToHome()
    }

    // Photo Zoom Dialog
    zoomedPhotoBase64?.let { base64 ->
        val bitmap = remember(base64) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }

        AlertDialog(
            onDismissRequest = { zoomedPhotoBase64 = null },
            confirmButton = {
                TextButton(onClick = { zoomedPhotoBase64 = null }) {
                    Text("Fechar")
                }
            },
            text = {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Foto ampliada do produto",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Foto indisponível")
                }
            }
        )
    }

    // Approve Dialog (Review & Confirm)
    uiState.approvingRequest?.let { req ->
        AlertDialog(
            onDismissRequest = viewModel::closeApproveDialog,
            title = {
                Text(
                    text = "Aprovar Cadastro de Produto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Revise as informações antes de cadastrar no Catálogo Global da rede:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )

                    OutlinedTextField(
                        value = uiState.adjustedName,
                        onValueChange = viewModel::onAdjustedNameChanged,
                        label = { Text("Nome / Descrição do Produto") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uiState.adjustedBarcode,
                        onValueChange = viewModel::onAdjustedBarcodeChanged,
                        label = { Text("Código de Barras / EAN") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.adjustedUnit,
                            onValueChange = viewModel::onAdjustedUnitChanged,
                            label = { Text("Unidade (UN, KG)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = uiState.adjustedPrice,
                            onValueChange = viewModel::onAdjustedPriceChanged,
                            label = { Text("Preço R$") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmApprove(userProfile) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.StatusCompleted),
                    enabled = !uiState.isLoading,
                    modifier = Modifier.testTag("btn_confirm_approve_product")
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Confirmar Aprovação", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = viewModel::closeApproveDialog,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Reject Dialog
    uiState.rejectingRequest?.let { req ->
        AlertDialog(
            onDismissRequest = viewModel::closeRejectDialog,
            title = {
                Text(
                    text = "Recusar Cadastro de Produto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.StatusDivergenceText
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Informe ao líder de setor o motivo pelo qual o cadastro de '${req.name}' não foi aceito:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )

                    OutlinedTextField(
                        value = uiState.rejectionReasonInput,
                        onValueChange = viewModel::onRejectionReasonChanged,
                        label = { Text("Motivo da Recusa *") },
                        placeholder = { Text("Ex: Código de barras incorreto, produto já cadastrado...") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_rejection_reason")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmReject(userProfile) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.StatusDivergence),
                    enabled = !uiState.isLoading,
                    modifier = Modifier.testTag("btn_confirm_reject_product")
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Recusar Cadastro", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = viewModel::closeRejectDialog,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("product_approval_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Aprovação de Produtos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Success / Error Banners
            if (!uiState.successMessage.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.StatusCompletedBg,
                    border = BorderStroke(1.dp, AppColors.StatusCompleted.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.StatusCompletedText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.successMessage ?: "",
                            color = AppColors.StatusCompletedText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!uiState.errorMessage.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.StatusDivergenceBg,
                    border = BorderStroke(1.dp, AppColors.StatusDivergence.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = AppColors.StatusDivergenceText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = AppColors.StatusDivergenceText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Status Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.filterStatus == "PENDING",
                        onClick = { viewModel.onFilterStatusChanged("PENDING") },
                        label = { Text("Pendentes (${uiState.pendingCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.PrimaryContainer,
                            selectedLabelColor = AppColors.Primary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterStatus == "APPROVED",
                        onClick = { viewModel.onFilterStatusChanged("APPROVED") },
                        label = { Text("Aprovados") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.StatusCompletedBg,
                            selectedLabelColor = AppColors.StatusCompletedText
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterStatus == "REJECTED",
                        onClick = { viewModel.onFilterStatusChanged("REJECTED") },
                        label = { Text("Recusados") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.StatusDivergenceBg,
                            selectedLabelColor = AppColors.StatusDivergenceText
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterStatus == "ALL",
                        onClick = { viewModel.onFilterStatusChanged("ALL") },
                        label = { Text("Todos") }
                    )
                }
            }

            AppSectionHeader(
                title = "Solicitações de Cadastro",
                subtitle = "Itens enviados por líderes de açougue, padaria e outros setores",
                badgeText = "${uiState.requests.size} item(ns)"
            )

            if (uiState.requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "Nenhuma solicitação",
                        message = if (uiState.filterStatus == "PENDING") "Não há solicitações pendentes de aprovação no momento." else "Nenhum registro encontrado para este filtro."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.requests, key = { it.id }) { request ->
                        ApprovalItemCard(
                            request = request,
                            onZoomPhoto = { zoomedPhotoBase64 = request.photoBase64 },
                            onApprove = { viewModel.openApproveDialog(request) },
                            onReject = { viewModel.openRejectDialog(request) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalItemCard(
    request: ProductRegistrationRequest,
    onZoomPhoto: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = if (request.requestedAt > 0) dateFormat.format(Date(request.requestedAt)) else ""

    val photoBitmap = remember(request.photoBase64) {
        if (request.photoBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(request.photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("approval_card_${request.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Foto com clique para ampliar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onZoomPhoto() }
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "Foto do produto",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Ampliar foto",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppColors.PrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = AppColors.Primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = if (request.barcode.isNotBlank()) "EAN: ${request.barcode}" else "Sem código EAN",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Unidade: ${request.unit}" + if (request.price > 0.0) " • R$ ${String.format(java.util.Locale.getDefault(), "%.2f", request.price)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Primary
                    )
                }

                // Status Badge
                when (request.status) {
                    ProductRequestStatus.APPROVED.name -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.StatusCompletedBg
                        ) {
                            Text(
                                text = "Aprovado",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.StatusCompletedText
                            )
                        }
                    }
                    ProductRequestStatus.REJECTED.name -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.StatusDivergenceBg
                        ) {
                            Text(
                                text = "Recusado",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.StatusDivergenceText
                            )
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.StatusPendingBg
                        ) {
                            Text(
                                text = "Pendente",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.StatusPendingText
                            )
                        }
                    }
                }
            }

            // Metadados do Solicitante
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppColors.SurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Solicitante: ${request.requestedByName} (${request.sectorId})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary
                    )
                }
            }

            // Se aprovado ou recusado, mostrar quem e o motivo
            if (request.status == ProductRequestStatus.APPROVED.name && request.reviewedByName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.StatusCompletedBg.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aprovado por ${request.reviewedByName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.StatusCompletedText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (request.status == ProductRequestStatus.REJECTED.name) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.StatusDivergenceBg.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Recusado por ${request.reviewedByName}: ${request.rejectionReason.ifBlank { "Sem justificativa informada." }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.StatusDivergenceText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Ações para Pendentes
            if (request.status == ProductRequestStatus.PENDING.name) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.StatusDivergence),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.StatusDivergence),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_reject_${request.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recusar", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.StatusCompleted),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_approve_${request.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aprovar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
