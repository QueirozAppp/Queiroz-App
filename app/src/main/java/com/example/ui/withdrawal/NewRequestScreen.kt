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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Product
import com.example.core.model.UserProfile
import com.example.core.model.WithdrawalRequestItem
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppErrorBanner
import com.example.ui.components.AppOutlinedButton
import com.example.ui.components.AppPrimaryButton

/**
 * Tela de Nova Solicitação de Baixa redesenhada (Etapa 20).
 * Preserva estritamente todas as regras de validação, cancelamento e testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRequestScreen(
    viewModel: NewRequestViewModel,
    userProfile: UserProfile,
    onOpenScanner: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirmation by remember { mutableStateOf(false) }

    fun handleBack() {
        if (uiState.isReviewing) {
            viewModel.cancelReview()
        } else if (uiState.items.isNotEmpty()) {
            showExitConfirmation = true
        } else {
            onBackToHome()
        }
    }

    BackHandler {
        handleBack()
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Descartar solicitação?", fontWeight = FontWeight.Bold) },
            text = { Text("Você possui ${uiState.items.size} item(ns) na lista. Se sair agora, estes itens não enviados serão descartados.", color = AppColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        viewModel.resetRequest()
                        onBackToHome()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.StatusDivergence)
                ) {
                    Text("Descartar e Sair", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmation = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Continuar Editando")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("new_request_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.isReviewing) "Revisão da Solicitação" else "Nova Baixa de Avaria",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${userProfile.sectorId.ifEmpty { "Geral" }} • Loja ${userProfile.storeId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = AppColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        },
        floatingActionButton = {
            if (!uiState.isReviewing && uiState.selectedProductForQuantity == null) {
                FloatingActionButton(
                    onClick = onOpenScanner,
                    containerColor = AppColors.Primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("fab_scan_product")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Escanear Produto", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escanear", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("request_error_banner")
                )
            }

            if (uiState.isReviewing) {
                ReviewSection(
                    userProfile = userProfile,
                    items = uiState.items,
                    isLoading = uiState.isLoading,
                    onSubmit = {
                        viewModel.submitRequest(userProfile) { _ ->
                            onBackToHome()
                        }
                    },
                    onCancel = viewModel::cancelReview
                )
            } else {
                // Botão de Ação Rápida: SCANEAR PRODUTO
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    onClick = onOpenScanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(bottom = 12.dp)
                        .background(AppColors.GradientHeroPrimary, RoundedCornerShape(18.dp))
                        .testTag("btn_action_scan_product")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("SCANEAR CÓDIGO DE BARRAS", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Itens Selecionados (${uiState.items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    if (uiState.items.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppColors.PrimaryContainer
                        ) {
                            Text(
                                text = "${uiState.items.sumOf { it.requestedQuantity }} unid/kg",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AppEmptyState(
                            icon = Icons.Default.ShoppingCart,
                            title = "Nenhum produto adicionado",
                            message = "Utilize o leitor de código de barras para bipar e adicionar itens à baixa.",
                            actionButtonText = "Escanear Primeiro Produto",
                            onActionClick = onOpenScanner,
                            modifier = Modifier.testTag("btn_empty_scan")
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.items, key = { it.productId }) { item ->
                            RequestItemCard(
                                item = item,
                                onRemove = { viewModel.removeItem(item.productId) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AppPrimaryButton(
                        text = "Revisar e Enviar Solicitação (${uiState.items.size})",
                        onClick = viewModel::startReview,
                        modifier = Modifier.testTag("btn_proceed_review")
                    )
                }
            }
        }

        // Quantity Dialog
        if (uiState.selectedProductForQuantity != null) {
            QuantityDialog(
                product = uiState.selectedProductForQuantity!!,
                quantityInput = uiState.quantityInput,
                onQuantityChanged = viewModel::onQuantityChanged,
                onConfirm = viewModel::confirmAddProduct,
                onDismiss = viewModel::cancelProductSelection
            )
        }
    }
}

@Composable
private fun RequestItemCard(
    item: WithdrawalRequestItem,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("request_item_card_${item.productId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "EAN: ${item.barcode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.PrimaryContainer
                ) {
                    Text(
                        text = "Qtd solicitada: ${item.requestedQuantity} ${item.unit}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("btn_remove_item_${item.productId}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remover",
                    tint = AppColors.StatusDivergence
                )
            }
        }
    }
}

@Composable
private fun QuantityDialog(
    product: Product,
    quantityInput: String,
    onQuantityChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Informar Quantidade",
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.SurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Unidade: ${product.unit}  •  EAN: ${product.barcode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = onQuantityChanged,
                    label = { Text("Quantidade a baixar (${product.unit})") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.BorderStrong
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_requested_quantity")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier.testTag("btn_confirm_quantity")
            ) {
                Text("Adicionar à Lista", fontWeight = FontWeight.Bold)
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

@Composable
private fun ReviewSection(
    userProfile: UserProfile,
    items: List<WithdrawalRequestItem>,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppColors.BorderSubtle),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Resumo da Solicitação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                ReviewDetailRow(label = "Loja:", value = userProfile.storeId)
                ReviewDetailRow(label = "Setor:", value = userProfile.sectorId.ifEmpty { "Geral" })
                ReviewDetailRow(label = "Solicitante:", value = userProfile.name.ifEmpty { userProfile.email })
                ReviewDetailRow(label = "Total de Itens:", value = "${items.size} produto(s)")
            }
        }

        Text(
            text = "Itens a serem enviados:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.productId }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                            Text(text = "EAN: ${item.barcode}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextTertiary)
                        }
                        Text(
                            text = "${item.requestedQuantity} ${item.unit}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = AppColors.Primary
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AppColors.BorderStrong),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text("Voltar", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier
                    .weight(1.5f)
                    .height(50.dp)
                    .testTag("btn_submit_request")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("Enviar Solicitação", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReviewDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
    }
}
