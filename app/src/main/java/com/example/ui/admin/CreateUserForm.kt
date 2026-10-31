package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.UserRole
import com.example.ui.components.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserForm(
    uiState: AdminUsersUiState,
    viewModel: AdminUsersViewModel
) {
    var expandedRoleMenu by remember { mutableStateOf(false) }
    var expandedStoreMenu by remember { mutableStateOf(false) }
    var expandedSectorMenu by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var showAddStoreDialog by remember { mutableStateOf(false) }
    var newStoreCode by remember { mutableStateOf("") }
    var newStoreName by remember { mutableStateOf("") }
    var isSavingStore by remember { mutableStateOf(false) }
    var storeDialogError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val storeRepo = remember { com.example.core.data.StoreSectorRepository() }

    val selectedStoreName = uiState.availableStores.find { it.id == uiState.newStoreId }?.name ?: "Selecione a Loja..."
    val selectedSectorName = uiState.availableSectors.find { it.id == uiState.newSectorId }?.name ?: "Selecione o Setor..."

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("create_user_form")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Cadastrar Novo Usuário",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Text(
                text = "Nota: Administradores, Avaristas e Líderes de Setor podem ser criados pela interface.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )

            // Name
            OutlinedTextField(
                value = uiState.newName,
                onValueChange = viewModel::onNewNameChanged,
                label = { Text("Nome Completo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_name")
            )

            // Email
            OutlinedTextField(
                value = uiState.newEmail,
                onValueChange = viewModel::onNewEmailChanged,
                label = { Text("E-mail corporativo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_email")
            )

            // Initial Password Input
            OutlinedTextField(
                value = uiState.newPassword,
                onValueChange = viewModel::onNewPasswordChanged,
                label = { Text("Senha inicial", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text("queiroz123", maxLines = 1) },
                supportingText = {
                    Text(
                        text = "Senha padrão: queiroz123 (mínimo 6 caracteres)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(10.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AppColors.TextSecondary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Ocultar senha" else "Exibir senha",
                            tint = AppColors.TextTertiary
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_password")
            )

            // First Access Password Notice
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.PrimaryContainer, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AppColors.OnPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Troca Obrigatória no Primeiro Acesso",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.OnPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "O novo usuário entrará com a senha inicial (padrão: queiroz123) e definirá a definitiva no primeiro acesso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.OnPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }

            // Role Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedRoleMenu,
                onExpandedChange = { expandedRoleMenu = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.newRole.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Função", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    maxLines = 1,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRoleMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .testTag("dropdown_new_role")
                )
                ExposedDropdownMenu(
                    expanded = expandedRoleMenu,
                    onDismissRequest = { expandedRoleMenu = false },
                    modifier = Modifier.background(AppColors.Surface)
                ) {
                    uiState.allowedRolesToCreate.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.label, color = AppColors.TextPrimary) },
                            colors = MenuDefaults.itemColors(textColor = AppColors.TextPrimary),
                            onClick = {
                                viewModel.onNewRoleChanged(role)
                                expandedRoleMenu = false
                            }
                        )
                    }
                }
            }

            // Store Dropdown (Only for non-ADMIN users)
            if (uiState.newRole != UserRole.ADMIN) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Loja Vinculada",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextSecondary
                        )
                        TextButton(
                            onClick = { showAddStoreDialog = true },
                            modifier = Modifier.testTag("btn_add_new_store")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Nova Loja", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = expandedStoreMenu,
                        onExpandedChange = { expandedStoreMenu = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedStoreName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Loja Vinculada", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            maxLines = 1,
                            shape = RoundedCornerShape(10.dp),
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStoreMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("dropdown_new_store")
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStoreMenu,
                            onDismissRequest = { expandedStoreMenu = false },
                            modifier = Modifier.background(AppColors.Surface)
                        ) {
                            uiState.availableStores.forEach { store ->
                                DropdownMenuItem(
                                    text = { Text(store.name, color = AppColors.TextPrimary) },
                                    colors = MenuDefaults.itemColors(textColor = AppColors.TextPrimary),
                                    onClick = {
                                        viewModel.onNewStoreChanged(store.id)
                                        expandedStoreMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showAddStoreDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        if (!isSavingStore) {
                            showAddStoreDialog = false 
                            storeDialogError = null
                        }
                    },
                    title = { Text("Cadastrar Nova Loja", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Informe o nome e o código de identificação para adicionar uma nova loja à rede.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                            if (storeDialogError != null) {
                                Surface(
                                    color = AppColors.StatusDivergenceBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AppColors.StatusDivergence.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = storeDialogError!!,
                                        color = AppColors.StatusDivergenceText,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = newStoreName,
                                onValueChange = { 
                                    newStoreName = it 
                                    storeDialogError = null
                                },
                                label = { Text("Nome da Loja", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("Ex: Filial Centro") },
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_new_store_name")
                            )
                            OutlinedTextField(
                                value = newStoreCode,
                                onValueChange = { 
                                    newStoreCode = it 
                                    storeDialogError = null
                                },
                                label = { Text("Código/ID", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("Ex: LOJA_04") },
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_new_store_code")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val trimmedCode = newStoreCode.trim().uppercase().ifEmpty { "LOJA_${System.currentTimeMillis().toString().takeLast(4)}" }
                                val trimmedName = newStoreName.trim().ifEmpty { "Nova Loja $trimmedCode" }
                                isSavingStore = true
                                storeDialogError = null
                                coroutineScope.launch {
                                    try {
                                        val saveResult = storeRepo.saveStore(
                                            com.example.core.model.Store(
                                                id = trimmedCode,
                                                code = trimmedCode,
                                                name = trimmedName,
                                                active = true
                                            )
                                        )
                                        saveResult.fold(
                                            onSuccess = {
                                                showAddStoreDialog = false
                                                viewModel.onNewStoreChanged(trimmedCode)
                                                newStoreCode = ""
                                                newStoreName = ""
                                                storeDialogError = null
                                            },
                                            onFailure = { err ->
                                                storeDialogError = err.localizedMessage ?: "Erro ao salvar a loja."
                                            }
                                        )
                                    } catch (e: Exception) {
                                        storeDialogError = e.localizedMessage ?: "Falha ao gravar os dados da loja."
                                    } finally {
                                        isSavingStore = false
                                    }
                                }
                            },
                            enabled = !isSavingStore,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                            modifier = Modifier.testTag("btn_save_store_dialog")
                        ) {
                            if (isSavingStore) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Text("Salvar Loja", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { 
                                showAddStoreDialog = false
                                isSavingStore = false
                                storeDialogError = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_cancel_store_dialog")
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Sector Dropdown (Only for SETOR_LEADER users)
            if (uiState.newRole == UserRole.SETOR_LEADER) {
                ExposedDropdownMenuBox(
                    expanded = expandedSectorMenu,
                    onExpandedChange = { expandedSectorMenu = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSectorName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Setor Vinculado", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSectorMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("dropdown_new_sector")
                    )
                    ExposedDropdownMenu(
                        expanded = expandedSectorMenu,
                        onDismissRequest = { expandedSectorMenu = false },
                        modifier = Modifier.background(AppColors.Surface)
                    ) {
                        uiState.availableSectors.forEach { sector ->
                            DropdownMenuItem(
                                text = { Text(sector.name, color = AppColors.TextPrimary) },
                                colors = MenuDefaults.itemColors(textColor = AppColors.TextPrimary),
                                onClick = {
                                    viewModel.onNewSectorChanged(sector.id)
                                    expandedSectorMenu = false
                                }
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
                    onClick = viewModel::cancelCreatingUser,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = viewModel::createUser,
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_save_user")
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                    } else {
                        Text("Salvar Usuário", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
