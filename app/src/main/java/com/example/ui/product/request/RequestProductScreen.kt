package com.example.ui.product.request

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestProductScreen(
    viewModel: RequestProductViewModel,
    userProfile: UserProfile,
    onOpenScanner: (() -> Unit)? = null,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userProfile) {
        viewModel.observeMyRequests(userProfile)
    }

    BackHandler {
        onBackToHome()
    }

    val context = LocalContext.current

    // Photo picker (Gallery) launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val base64 = resizeAndCompressBitmap(bitmap)
                        viewModel.onPhotoCaptured(base64)
                    } else {
                        viewModel.setErrorMessage("Não foi possível processar a imagem da galeria.")
                    }
                }
            } catch (e: Exception) {
                viewModel.setErrorMessage("Erro ao carregar foto da galeria: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // Camera launcher to take a photo of the product
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val base64 = resizeAndCompressBitmap(bitmap)
                viewModel.onPhotoCaptured(base64)
            } catch (e: Exception) {
                viewModel.setErrorMessage("Erro ao processar foto da câmera: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // Camera runtime permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                viewModel.setErrorMessage("Não foi possível acionar a câmera. Tente utilizar a opção Galeria.")
            }
        } else {
            viewModel.setErrorMessage("Permissão da câmera necessária para tirar fotos. Você também pode escolher pela Galeria.")
        }
    }

    fun openCameraSafely() {
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                cameraLauncher.launch(null)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            viewModel.setErrorMessage("Erro ao abrir a câmera: ${e.localizedMessage ?: "Câmera indisponível"}. Escolha pela Galeria.")
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("request_product_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Solicitar Cadastro de Produto",
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
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = uiState.activeTab,
                containerColor = AppColors.Surface,
                contentColor = AppColors.Primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.activeTab]),
                        color = AppColors.Primary
                    )
                }
            ) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.onTabChanged(0) },
                    text = {
                        Text(
                            text = "Novo Cadastro",
                            fontWeight = if (uiState.activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.activeTab == 0) AppColors.Primary else AppColors.TextSecondary
                        )
                    }
                )
                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.onTabChanged(1) },
                    text = {
                        Text(
                            text = "Minhas Solicitações (${uiState.myRequests.size})",
                            fontWeight = if (uiState.activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.activeTab == 1) AppColors.Primary else AppColors.TextSecondary
                        )
                    }
                )
            }

            // Success / Error Banners
            if (!uiState.successMessage.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.StatusCompletedBg,
                    border = BorderStroke(1.dp, AppColors.StatusCompleted.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("request_product_error")
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

            if (uiState.activeTab == 0) {
                // Tab 0: Formulário de Solicitação
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header contextual
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AppColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Setor Solicitante",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.TextTertiary
                                )
                                Text(
                                    text = "Loja: ${userProfile.storeId}  •  Setor: ${userProfile.sectorId.ifBlank { "Geral" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextPrimary
                                )
                                Text(
                                    text = "A solicitação será avaliada e aprovada pelo Avarista ou Administrador.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }
                    }

                    // Seção de Foto do Produto
                    AppSectionHeader(
                        title = "1. Foto do Produto",
                        subtitle = "Tire uma foto nítida da embalagem ou etiqueta do item"
                    )

                    val capturedBitmap = remember(uiState.photoBase64) {
                        if (uiState.photoBase64.isNotBlank()) {
                            try {
                                val bytes = Base64.decode(uiState.photoBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    }

                    if (capturedBitmap != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, AppColors.Primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = capturedBitmap,
                                    contentDescription = "Foto capturada do produto",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = { openCameraSafely() },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Tirar outra foto",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.onPhotoCaptured("") },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remover Foto",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                            border = BorderStroke(1.dp, AppColors.BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_product_photo_picker")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(AppColors.PrimaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        tint = AppColors.Primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Adicionar Foto do Produto",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        text = "Tire uma foto ou escolha da galeria para aprovação",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextTertiary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { openCameraSafely() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("btn_take_product_photo")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tirar Foto", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                galleryLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } catch (e: Exception) {
                                                viewModel.setErrorMessage("Erro ao abrir galeria: ${e.message}")
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, AppColors.Primary),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("btn_pick_gallery_photo")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Galeria", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Seção de Informações do Produto
                    AppSectionHeader(
                        title = "2. Informações do Produto",
                        subtitle = "Informe a descrição e os detalhes do produto"
                    )

                    // Descrição / Nome
                    OutlinedTextField(
                        value = uiState.nameInput,
                        onValueChange = viewModel::onNameChanged,
                        label = { Text("Descrição / Nome do Produto *") },
                        placeholder = { Text("Ex: Picanha Bovina Fatiada 1kg") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_request_product_name")
                    )

                    // Código de Barras
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.barcodeInput,
                            onValueChange = viewModel::onBarcodeChanged,
                            label = { Text("Código de Barras / EAN") },
                            placeholder = { Text("Ex: 7891234567890") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_request_barcode")
                        )

                        IconButton(
                            onClick = { onOpenScanner?.invoke() },
                            modifier = Modifier
                                .background(AppColors.PrimaryContainer, RoundedCornerShape(12.dp))
                                .size(54.dp)
                                .testTag("btn_scan_barcode_request")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Escanear Código de Barras",
                                tint = AppColors.Primary
                            )
                        }
                    }

                    // Unidade de Medida
                    var expandedUnit by remember { mutableStateOf(false) }
                    val currentUnitLabel = uiState.availableUnits.find { it.name == uiState.selectedUnit }?.label ?: uiState.selectedUnit

                    ExposedDropdownMenuBox(
                        expanded = expandedUnit,
                        onExpandedChange = { expandedUnit = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentUnitLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unidade de Medida *") },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnit) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("dropdown_request_unit")
                        )
                        ExposedDropdownMenu(
                            expanded = expandedUnit,
                            onDismissRequest = { expandedUnit = false },
                            modifier = Modifier.background(AppColors.Surface)
                        ) {
                            uiState.availableUnits.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.label, color = AppColors.TextPrimary) },
                                    colors = MenuDefaults.itemColors(textColor = AppColors.TextPrimary),
                                    onClick = {
                                        viewModel.onUnitChanged(unit.name)
                                        expandedUnit = false
                                    }
                                )
                            }
                        }
                    }

                    // Preço estimado (Opcional)
                    OutlinedTextField(
                        value = uiState.priceInput,
                        onValueChange = viewModel::onPriceChanged,
                        label = { Text("Preço Estimado R$ (Opcional)") },
                        placeholder = { Text("Ex: 34,90") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_request_price")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Botão de Envio
                    Button(
                        onClick = { viewModel.submitRequest(userProfile) },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_submit_product_request")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Solicitar Cadastro de Produto", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Tab 1: Minhas Solicitações de Cadastro
                if (uiState.myRequests.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "Nenhuma solicitação encontrada",
                            message = "Você ainda não enviou solicitações de cadastro de produtos.",
                            actionButtonText = "Solicitar Novo Produto",
                            onActionClick = { viewModel.onTabChanged(0) }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.myRequests, key = { it.id }) { request ->
                            MyRequestItemCard(request = request)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyRequestItemCard(request: ProductRegistrationRequest) {
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "Foto do produto",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(AppColors.PrimaryContainer, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = AppColors.Primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
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
                    }
                }

                // Status Chip
                when (request.status) {
                    ProductRequestStatus.APPROVED.name -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.StatusCompletedBg
                        ) {
                            Text(
                                text = "Aprovado",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.StatusPendingText
                            )
                        }
                    }
                }
            }

            // Unidade e Data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Unidade: ${request.unit}" + if (request.price > 0.0) " • R$ ${String.format("%.2f", request.price)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
            }

            // Exibir feedback de aprovação ou recusa
            if (request.status == ProductRequestStatus.APPROVED.name && request.reviewedByName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.StatusCompletedBg.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aprovado por ${request.reviewedByName} no catálogo global.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.StatusCompletedText,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (request.status == ProductRequestStatus.REJECTED.name) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.StatusDivergenceBg.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Recusado por ${request.reviewedByName.ifBlank { "Avarista/Admin" }}: ${request.rejectionReason.ifBlank { "Sem motivo informado." }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.StatusDivergenceText,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                Text(
                    text = "Aguardando análise do Avarista ou Administrador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.StatusPendingText
                )
            }
        }
    }
}

private fun resizeAndCompressBitmap(bitmap: Bitmap, maxDimension: Int = 800, quality: Int = 75): String {
    val width = bitmap.width
    val height = bitmap.height
    val scale = if (width > maxDimension || height > maxDimension) {
        val maxSide = maxOf(width, height)
        maxDimension.toFloat() / maxSide
    } else 1.0f

    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val scaledBitmap = if (scale < 1.0f) {
        Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    } else bitmap

    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}
