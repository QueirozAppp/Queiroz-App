package com.example.ui.admin.product

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.model.Product
import com.example.core.util.ImageCompressionUtils
import com.example.ui.components.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menu/Modal de Detalhes, Edição e Exclusão do Produto Selecionado.
 * Permite visualizar todas as informações e a foto do produto,
 * e disponibiliza ações de edição e exclusão exclusivamente para Administradores e Avaristas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailBottomSheet(
    product: Product,
    uiState: AdminProductsUiState,
    canEditOrDelete: Boolean,
    onDismiss: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveEdits: () -> Unit,
    onPromptDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEditNameChanged: (String) -> Unit,
    onEditBarcodeChanged: (String) -> Unit,
    onEditPriceChanged: (String) -> Unit,
    onEditUnitChanged: (String) -> Unit,
    onEditActiveChanged: (Boolean) -> Unit,
    onEditPhotoCaptured: (String) -> Unit,
    onRemoveEditPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Launchers de Câmera e Galeria para captura/alteração de foto
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = ImageCompressionUtils.decodeScaledBitmapFromUri(context, uri)
                if (bitmap != null) {
                    val base64 = ImageCompressionUtils.resizeAndCompressBitmap(bitmap)
                    onEditPhotoCaptured(base64)
                } else {
                    Toast.makeText(context, "Erro ao decodificar imagem.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Falha ao abrir imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val base64 = resizeAndCompressBitmap(bitmap)
                onEditPhotoCaptured(base64)
            } catch (e: Exception) {
                Toast.makeText(context, "Falha ao processar foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Permissão de câmera negada.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraSafely() {
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        modifier = modifier.testTag("product_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Cabeçalho da Folha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isEditingSelectedProduct) "Editar Produto" else "Informações do Produto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_close_product_detail")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar Detalhes",
                        tint = AppColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.isEditingSelectedProduct) {
                // ==================== MODO EDIÇÃO ====================
                EditProductFormContent(
                    uiState = uiState,
                    onEditNameChanged = onEditNameChanged,
                    onEditBarcodeChanged = onEditBarcodeChanged,
                    onEditPriceChanged = onEditPriceChanged,
                    onEditUnitChanged = onEditUnitChanged,
                    onEditActiveChanged = onEditActiveChanged,
                    onRemoveEditPhoto = onRemoveEditPhoto,
                    onLaunchCamera = { launchCameraSafely() },
                    onLaunchGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCancelEditing = onCancelEditing,
                    onSaveEdits = onSaveEdits
                )
            } else {
                // ==================== MODO VISUALIZAÇÃO DE DETALHES ====================
                ViewProductDetailContent(
                    product = product,
                    canEditOrDelete = canEditOrDelete,
                    onStartEditing = onStartEditing,
                    onPromptDelete = onPromptDelete,
                    onToggleActive = onToggleActive,
                    onCopyBarcode = {
                        clipboardManager.setText(AnnotatedString(product.barcode))
                        Toast.makeText(context, "Código ${product.barcode} copiado!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Diálogo de confirmação de exclusão
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = AppColors.StatusDivergenceText,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Excluir Produto?",
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "Tem certeza de que deseja excluir permanentemente o produto '${product.name}' (Código: ${product.barcode}) do catálogo geral da rede? Esta ação não pode ser desfeita.",
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.StatusDivergence),
                    enabled = !uiState.isSavingOrDeleting,
                    modifier = Modifier.testTag("btn_confirm_delete_product")
                ) {
                    if (uiState.isSavingOrDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text("Sim, Excluir", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDelete,
                    enabled = !uiState.isSavingOrDeleting
                ) {
                    Text("Cancelar", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Surface
        )
    }
}

/**
 * Conteúdo de Visualização de Detalhes do Produto
 */
@Composable
private fun ViewProductDetailContent(
    product: Product,
    canEditOrDelete: Boolean,
    onStartEditing: () -> Unit,
    onPromptDelete: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onCopyBarcode: () -> Unit
) {
    val photoBitmap = remember(product.photoBase64) {
        ImageCompressionUtils.decodeBase64ToImageBitmap(product.photoBase64)
    }

    // Exibição da Foto do Produto
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceElevated)
            .border(BorderStroke(1.dp, AppColors.BorderSubtle), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (photoBitmap != null) {
            Image(
                bitmap = photoBitmap,
                contentDescription = "Foto do produto ${product.name}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("product_detail_photo")
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(AppColors.PrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = AppColors.Primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = "Nenhuma foto cadastrada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextTertiary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Nome do Produto e Status
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                modifier = Modifier.testTag("product_detail_name")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (product.active) AppColors.StatusCompletedBg else AppColors.StatusDivergenceBg
            ) {
                Text(
                    text = if (product.active) "Status: Ativo" else "Status: Inativo",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (product.active) AppColors.StatusCompletedText else AppColors.StatusDivergenceText
                )
            }
        }

        if (canEditOrDelete) {
            Switch(
                checked = product.active,
                onCheckedChange = { onToggleActive(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.StatusCompleted
                ),
                modifier = Modifier.testTag("switch_detail_toggle_active")
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Cartão de Informações Técnicas
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceElevated),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Código de Barras com botão de copiar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Código de Barras (EAN)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
                    )
                    Text(
                        text = product.barcode.ifBlank { "Sem código" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.testTag("product_detail_barcode")
                    )
                }

                if (product.barcode.isNotBlank()) {
                    IconButton(
                        onClick = onCopyBarcode,
                        modifier = Modifier
                            .background(AppColors.PrimaryContainer, CircleShape)
                            .size(36.dp)
                            .testTag("btn_copy_barcode")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar Código",
                            tint = AppColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Preço e Unidade de Medida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Preço Unitário",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
                    )
                    Text(
                        text = if (product.price > 0.0) String.format(Locale("pt", "BR"), "R$ %.2f", product.price) else "R$ 0,00",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Secondary,
                        modifier = Modifier.testTag("product_detail_price")
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unidade de Medida",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
                    )
                    Text(
                        text = product.unit.ifBlank { "UN" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary,
                        modifier = Modifier.testTag("product_detail_unit")
                    )
                }
            }

            // Datas de criação e atualização
            if (product.createdAt > 0L) {
                val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }
                Text(
                    text = "Cadastrado em: ${dateFormat.format(Date(product.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Ações de Administrador e Avarista
    if (canEditOrDelete) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onPromptDelete,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AppColors.StatusDivergence),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.StatusDivergenceText),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_delete_product")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Excluir", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onStartEditing,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_edit_product")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Editar", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Mensagem explicativa para Líderes de Setor ou outros usuários
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppColors.PrimaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Apenas Administradores e Avaristas possuem permissão para editar informações ou excluir produtos cadastrados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Formulário de Edição de Informações do Produto
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProductFormContent(
    uiState: AdminProductsUiState,
    onEditNameChanged: (String) -> Unit,
    onEditBarcodeChanged: (String) -> Unit,
    onEditPriceChanged: (String) -> Unit,
    onEditUnitChanged: (String) -> Unit,
    onEditActiveChanged: (Boolean) -> Unit,
    onRemoveEditPhoto: () -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveEdits: () -> Unit
) {
    var expandedUnitMenu by remember { mutableStateOf(false) }

    val photoBitmap = remember(uiState.editPhotoBase64) {
        ImageCompressionUtils.decodeBase64ToImageBitmap(uiState.editPhotoBase64)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Seção de Foto na Edição
        Text(
            text = "Foto do Produto",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.SurfaceElevated)
                .border(BorderStroke(1.dp, AppColors.BorderSubtle), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = "Foto em edição",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = AppColors.TextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Sem foto. Tire uma foto ou escolha da galeria.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextTertiary
                    )
                }
            }
        }

        // Botões de Ação de Foto
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onLaunchCamera,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("btn_edit_take_photo")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Câmera", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = onLaunchGallery,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("btn_edit_gallery_photo")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Galeria", style = MaterialTheme.typography.labelMedium)
            }

            if (uiState.editPhotoBase64.isNotBlank()) {
                IconButton(
                    onClick = onRemoveEditPhoto,
                    modifier = Modifier
                        .background(AppColors.StatusDivergenceBg, RoundedCornerShape(10.dp))
                        .size(44.dp)
                        .testTag("btn_remove_edit_photo")
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

        // Nome
        OutlinedTextField(
            value = uiState.editName,
            onValueChange = onEditNameChanged,
            label = { Text("Nome do Produto") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_edit_name")
        )

        // Código de Barras
        OutlinedTextField(
            value = uiState.editBarcode,
            onValueChange = onEditBarcodeChanged,
            label = { Text("Código de Barras (EAN)") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_edit_barcode")
        )

        // Preço
        OutlinedTextField(
            value = uiState.editPrice,
            onValueChange = onEditPriceChanged,
            label = { Text("Preço R$ (ex: 12,90)") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_edit_price")
        )

        // Unidade Dropdown
        val currentUnitLabel = uiState.availableUnits.find { it.name == uiState.editUnit }?.label ?: uiState.editUnit
        ExposedDropdownMenuBox(
            expanded = expandedUnitMenu,
            onExpandedChange = { expandedUnitMenu = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentUnitLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Unidade de Medida") },
                shape = RoundedCornerShape(10.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnitMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .testTag("dropdown_edit_unit")
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
                            onEditUnitChanged(unit.name)
                            expandedUnitMenu = false
                        }
                    )
                }
            }
        }

        // Switch Ativo / Inativo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.SurfaceElevated)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Status no Catálogo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = if (uiState.editActive) "Ativo (visível para requisições e baixas)" else "Inativo (oculto de novas baixas)",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
            }
            Switch(
                checked = uiState.editActive,
                onCheckedChange = onEditActiveChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.StatusCompleted
                ),
                modifier = Modifier.testTag("switch_edit_active")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Botões Cancelar / Salvar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancelEditing,
                enabled = !uiState.isSavingOrDeleting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_cancel_edit_product")
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = onSaveEdits,
                enabled = !uiState.isSavingOrDeleting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_save_edit_product")
            ) {
                if (uiState.isSavingOrDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar Alterações", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Utilitário para redimensionar e compactar fotos antes de salvar em Base64
 */
internal fun resizeAndCompressBitmap(bitmap: Bitmap, maxDimension: Int = 720, quality: Int = 72): String {
    return ImageCompressionUtils.resizeAndCompressBitmap(
        bitmap = bitmap,
        maxDimension = maxDimension,
        initialQuality = quality
    )
}
