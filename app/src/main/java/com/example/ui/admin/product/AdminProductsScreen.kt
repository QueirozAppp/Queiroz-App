package com.example.ui.admin.product

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Product
import com.example.core.model.ProductUnit
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.util.ImageCompressionUtils
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppSectionHeader

/**
 * Tela de Gestão e Catálogo Geral de Produtos.
 * Oferece busca instantânea, cadastro rápido com foto e menu de detalhes do produto ao clicar.
 * Edição e exclusão são restritas para Administradores e Avaristas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductsScreen(
    viewModel: AdminProductsViewModel,
    onBackToHome: () -> Unit,
    userProfile: UserProfile? = null,
    isAdmin: Boolean = true,
    onOpenScanner: (() -> Unit)? = null,
    onOpenApprovals: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userProfile) {
        if (userProfile != null) {
            viewModel.setUserProfile(userProfile)
        }
    }

    val effectiveProfile = userProfile ?: uiState.userProfile
    val canEditOrDelete = isAdmin || (effectiveProfile?.role == UserRole.AVARISTA)

    BackHandler(enabled = true) {
        if (uiState.selectedProduct != null) {
            viewModel.dismissProductDetail()
        } else if (uiState.isCreatingProduct) {
            viewModel.cancelCreatingProduct()
        } else {
            onBackToHome()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_products_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (canEditOrDelete) "Catálogo Geral de Produtos" else "Consulta de Produtos",
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
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshProducts() },
                        modifier = Modifier.testTag("btn_refresh_products")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar Catálogo",
                            tint = AppColors.Primary
                        )
                    }
                    if (onOpenApprovals != null && canEditOrDelete) {
                        IconButton(
                            onClick = onOpenApprovals,
                            modifier = Modifier.testTag("btn_top_product_approvals")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAddCheck,
                                contentDescription = "Solicitações de Cadastro",
                                tint = AppColors.Primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface
                )
            )
        },
        floatingActionButton = {
            if (canEditOrDelete && !uiState.isCreatingProduct) {
                FloatingActionButton(
                    onClick = viewModel::startCreatingProduct,
                    containerColor = AppColors.Primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_product")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Novo Produto")
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
            // Banners de Sucesso ou Erro
            if (!uiState.successMessage.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.StatusCompletedBg,
                    border = BorderStroke(1.dp, AppColors.StatusCompleted.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
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
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearMessages) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar aviso",
                                tint = AppColors.StatusCompletedText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                        .padding(bottom = 12.dp)
                        .testTag("product_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            modifier = Modifier.weight(1f),
                            color = AppColors.StatusDivergenceText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = viewModel::clearMessages) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar erro",
                                tint = AppColors.StatusDivergenceText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.isCreatingProduct && canEditOrDelete) {
                CreateProductForm(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenScanner = onOpenScanner
                )
            } else {
                // Barra de Pesquisa Instantânea
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = { Text("Pesquisar por nome ou código de barras...", color = AppColors.TextTertiary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AppColors.TextSecondary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("input_search_product")
                )

                AppSectionHeader(
                    title = "Catálogo de Itens",
                    subtitle = "Toque em um item para abrir suas informações completas e foto",
                    badgeText = "${uiState.products.size} produto(s)"
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (uiState.isLoading && uiState.products.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = AppColors.Primary)
                            Text(
                                text = "Carregando catálogo de produtos...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                } else if (uiState.products.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AppEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "Nenhum produto encontrado",
                            message = if (uiState.searchQuery.isNotBlank()) "Nenhum item corresponde à busca." else "Nenhum produto cadastrado no catálogo.",
                            actionButtonText = if (canEditOrDelete) "Cadastrar Produto" else null,
                            onActionClick = if (canEditOrDelete) viewModel::startCreatingProduct else null
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.products, key = { it.id }) { product ->
                            ProductItemCard(
                                product = product,
                                canEditOrDelete = canEditOrDelete,
                                onClick = { viewModel.selectProduct(product) },
                                onToggleActive = { viewModel.toggleProductActive(product.id, product.active) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de Detalhes, Edição e Foto do Produto Selecionado
    if (uiState.selectedProduct != null) {
        ProductDetailBottomSheet(
            product = uiState.selectedProduct!!,
            uiState = uiState,
            canEditOrDelete = canEditOrDelete,
            onDismiss = viewModel::dismissProductDetail,
            onStartEditing = viewModel::startEditingSelectedProduct,
            onCancelEditing = viewModel::cancelEditingSelectedProduct,
            onSaveEdits = viewModel::saveProductEdits,
            onPromptDelete = viewModel::promptDeleteProduct,
            onDismissDelete = viewModel::dismissDeleteConfirmation,
            onConfirmDelete = viewModel::confirmDeleteProduct,
            onToggleActive = { viewModel.toggleProductActive(uiState.selectedProduct!!.id, uiState.selectedProduct!!.active) },
            onEditNameChanged = viewModel::onEditNameChanged,
            onEditBarcodeChanged = viewModel::onEditBarcodeChanged,
            onEditPriceChanged = viewModel::onEditPriceChanged,
            onEditUnitChanged = viewModel::onEditUnitChanged,
            onEditActiveChanged = viewModel::onEditActiveChanged,
            onEditPhotoCaptured = viewModel::onEditPhotoCaptured,
            onRemoveEditPhoto = viewModel::onRemoveEditPhoto
        )
    }
}

@Composable
private fun ProductItemCard(
    product: Product,
    canEditOrDelete: Boolean,
    onClick: () -> Unit,
    onToggleActive: () -> Unit
) {
    val photoBitmap = remember(product.photoBase64) {
        ImageCompressionUtils.decodeBase64ToImageBitmap(product.photoBase64)
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            contentDescription = "Foto do produto ${product.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(AppColors.PrimaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = if (product.barcode.isNotBlank()) "EAN: ${product.barcode}" else "Sem código EAN",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextTertiary
                        )
                    }
                }

                if (canEditOrDelete) {
                    Switch(
                        checked = product.active,
                        onCheckedChange = { onToggleActive() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.StatusCompleted
                        ),
                        modifier = Modifier.testTag("switch_product_active_${product.id}")
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Abrir detalhes",
                        tint = AppColors.TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.PrimaryContainer
                ) {
                    Text(
                        text = "Unidade: ${product.unit}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                }

                if (product.price > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColors.SecondaryContainer
                    ) {
                        Text(
                            text = String.format(java.util.Locale.getDefault(), "R$ %.2f", product.price),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Secondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (product.active) AppColors.StatusCompletedBg else AppColors.StatusDivergenceBg
                ) {
                    Text(
                        text = if (product.active) "Ativo" else "Inativo",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (product.active) AppColors.StatusCompletedText else AppColors.StatusDivergenceText
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Toque para ver detalhes",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProductForm(
    uiState: AdminProductsUiState,
    viewModel: AdminProductsViewModel,
    onOpenScanner: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var expandedUnitMenu by remember { mutableStateOf(false) }

    val selectedUnitLabel = uiState.availableUnits.find { it.name == uiState.newUnit }?.label ?: uiState.newUnit

    val photoBitmap = remember(uiState.newPhotoBase64) {
        if (uiState.newPhotoBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(uiState.newPhotoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    // Launchers de Câmera e Galeria para novo produto
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = ImageCompressionUtils.decodeScaledBitmapFromUri(context, uri)
                if (bitmap != null) {
                    val base64 = ImageCompressionUtils.resizeAndCompressBitmap(bitmap)
                    viewModel.onNewPhotoCaptured(base64)
                } else {
                    Toast.makeText(context, "Erro ao decodificar imagem.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao carregar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val base64 = resizeAndCompressBitmap(bitmap)
                viewModel.onNewPhotoCaptured(base64)
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao processar foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Permissão da câmera necessária.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppColors.BorderSubtle),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_product_form")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Text(
                text = "Cadastrar Produto na Rede",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            // Seção de Foto
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (photoBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "Foto capturada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(AppColors.SurfaceElevated, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = AppColors.TextTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { launchCamera() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_new_product_camera")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Câmera", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_new_product_gallery")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galeria", style = MaterialTheme.typography.labelSmall)
                    }

                    if (uiState.newPhotoBase64.isNotBlank()) {
                        IconButton(
                            onClick = viewModel::onRemoveNewPhoto,
                            modifier = Modifier
                                .background(AppColors.StatusDivergenceBg, RoundedCornerShape(10.dp))
                                .size(48.dp)
                                .testTag("btn_new_product_remove_photo")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remover Foto",
                                tint = AppColors.StatusDivergenceText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Código de Barras com botão de Scanner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.newBarcode,
                    onValueChange = viewModel::onNewBarcodeChanged,
                    label = { Text("Código de Barras (ex: 789...)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_new_barcode")
                )

                IconButton(
                    onClick = { onOpenScanner?.invoke() },
                    modifier = Modifier
                        .background(AppColors.PrimaryContainer, RoundedCornerShape(10.dp))
                        .size(52.dp)
                        .testTag("btn_scan_barcode_product")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Escanear Código de Barras",
                        tint = AppColors.Primary
                    )
                }
            }

            // Nome
            OutlinedTextField(
                value = uiState.newName,
                onValueChange = viewModel::onNewNameChanged,
                label = { Text("Nome do Produto") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_product_name")
            )

            // Preço
            OutlinedTextField(
                value = uiState.newPrice,
                onValueChange = viewModel::onNewPriceChanged,
                label = { Text("Preço R$ (ex: 12,90)") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_product_price")
            )

            // Unidade Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedUnitMenu,
                onExpandedChange = { expandedUnitMenu = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedUnitLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unidade de Medida") },
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnitMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .testTag("dropdown_new_unit")
                )
                ExposedDropdownMenu(
                    expanded = expandedUnitMenu,
                    onDismissRequest = { expandedUnitMenu = false },
                    modifier = Modifier.background(AppColors.Surface)
                ) {
                    uiState.availableUnits.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit.label, color = AppColors.TextPrimary) },
                            colors = MenuDefaults.itemColors(textColor = AppColors.TextPrimary),
                            onClick = {
                                viewModel.onNewUnitChanged(unit.name)
                                expandedUnitMenu = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::cancelCreatingProduct,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_cancel_create_product")
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = viewModel::createProduct,
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_save_product")
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                    } else {
                        Text("Salvar no Catálogo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    }
}
