package com.example.ui.scanner

import androidx.activity.compose.BackHandler

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Product
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppPrimaryButton

/**
 * Tela do Scanner de Código de Barras redesenhada (Etapa 20).
 * Interface corporativa com viewfinder nítido, feedback visual claro e preservação de testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onBackToHome: () -> Unit,
    onProductSelected: ((Product) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = true) {
        onBackToHome()
    }

    var manualBarcode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(hasPermission)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("scanner_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Leitor de Código de Barras",
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!uiState.permissionGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(AppColors.PrimaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = AppColors.Primary
                            )
                        }
                        Text(
                            text = "Acesso à Câmera Necessário",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Precisamos da permissão de câmera para realizar a leitura rápida dos códigos de barras dos produtos avariados.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = AppColors.TextSecondary
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_request_camera")
                        ) {
                            Text("Conceder Permissão", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                if (uiState.isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = AppColors.Primary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Buscando produto no catálogo central...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                } else if (uiState.scannedProduct != null && !uiState.productInactive) {
                    ResultCard(
                        title = "Produto Localizado",
                        titleColor = AppColors.StatusCompletedText,
                        containerBg = AppColors.StatusCompletedBg,
                        icon = Icons.Default.CheckCircle,
                        product = uiState.scannedProduct,
                        actionButtonText = if (onProductSelected != null) "Adicionar à Baixa" else "Escanear Outro",
                        onAction = {
                            if (onProductSelected != null && uiState.scannedProduct != null) {
                                onProductSelected(uiState.scannedProduct!!)
                            } else {
                                viewModel.tryAgain()
                            }
                        }
                    )
                } else if (uiState.productInactive) {
                    ResultCard(
                        title = "Produto Inativo",
                        titleColor = AppColors.StatusDivergenceText,
                        containerBg = AppColors.StatusDivergenceBg,
                        icon = Icons.Default.Warning,
                        product = uiState.scannedProduct,
                        subtitle = "Este produto está inativo no catálogo e não pode sofrer baixa.",
                        actionButtonText = "Tentar Novamente",
                        onAction = viewModel::tryAgain
                    )
                } else if (uiState.productNotFound) {
                    ResultCard(
                        title = "Produto não cadastrado",
                        titleColor = AppColors.StatusDivergenceText,
                        containerBg = AppColors.StatusDivergenceBg,
                        icon = Icons.Default.Error,
                        subtitle = "O código informado não corresponde a nenhum item cadastrado.",
                        actionButtonText = "Tentar Novamente",
                        onAction = viewModel::tryAgain,
                        secondaryActionButtonText = if (onProductSelected != null) "Cadastrar novo produto" else null,
                        onSecondaryAction = {
                            // Passar o barcode para o caller, simulando um produto vazio
                            onProductSelected?.invoke(Product(barcode = uiState.scannedBarcode ?: ""))
                        }
                    )
                } else if (!uiState.errorMessage.isNullOrEmpty()) {
                    ResultCard(
                        title = "Erro na Leitura",
                        titleColor = AppColors.StatusDivergenceText,
                        containerBg = AppColors.StatusDivergenceBg,
                        icon = Icons.Default.Error,
                        subtitle = uiState.errorMessage,
                        actionButtonText = "Tentar Novamente",
                        onAction = viewModel::tryAgain
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Viewfinder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(Color.Black, RoundedCornerShape(16.dp))
                                .testTag("camera_viewfinder")
                        ) {
                            CameraScanner(onBarcodeScanned = { barcode ->
                                viewModel.onBarcodeScanned(barcode)
                            })
                        }

                        // Manual fallback entry
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, AppColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Entrada Manual (Código de Barras):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextPrimary
                                )
                                OutlinedTextField(
                                    value = manualBarcode,
                                    onValueChange = { manualBarcode = it },
                                    placeholder = { Text("Ex: 7891234567890", color = AppColors.TextTertiary) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AppColors.Primary,
                                        unfocusedBorderColor = AppColors.BorderStrong
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (manualBarcode.isNotBlank()) {
                                            viewModel.onBarcodeScanned(manualBarcode)
                                            manualBarcode = ""
                                        }
                                    }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_manual_barcode")
                                )

                                Button(
                                    onClick = {
                                        if (manualBarcode.isNotBlank()) {
                                            viewModel.onBarcodeScanned(manualBarcode)
                                            manualBarcode = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_submit_manual_barcode")
                                ) {
                                    Text("Consultar Código", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    titleColor: Color,
    containerBg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    product: Product? = null,
    subtitle: String? = null,
    actionButtonText: String = "Tentar Novamente",
    onAction: () -> Unit,
    secondaryActionButtonText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scanner_result_card")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(containerBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )

            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = AppColors.TextSecondary
                )
            }

            if (product != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.SurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResultInfoRow(label = "Nome", value = product.name)
                        ResultInfoRow(label = "Código de Barras", value = product.barcode)
                        ResultInfoRow(label = "Unidade de Medida", value = product.unit)
                        ResultInfoRow(label = "Status no Catálogo", value = if (product.active) "Ativo" else "Inativo")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_scanner_action")
            ) {
                Text(actionButtonText, fontWeight = FontWeight.Bold)
            }
            
            if (secondaryActionButtonText != null && onSecondaryAction != null) {
                OutlinedButton(
                    onClick = onSecondaryAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_scanner_secondary_action")
                ) {
                    Text(secondaryActionButtonText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ResultInfoRow(label: String, value: String) {
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )
    }
}
