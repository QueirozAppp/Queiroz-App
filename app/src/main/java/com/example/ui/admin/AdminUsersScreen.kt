package com.example.ui.admin

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.ui.components.AppColors
import com.example.ui.components.AppEmptyState
import com.example.ui.components.AppErrorBanner
import com.example.ui.components.AppOutlinedButton
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSectionHeader

/**
 * Tela de Administração de Usuários redesenhada (Etapa 20).
 * Interface corporativa com gestão de acesso, permissões e preservação total de testTags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    viewModel: AdminUsersViewModel,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshUsers()
    }

    BackHandler(enabled = true) {
        onBackToHome()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_users_screen"),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Gestão de Usuários",
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
                        onClick = viewModel::refreshUsers,
                        modifier = Modifier.testTag("btn_refresh_users")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar Lista",
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
            if (!uiState.isCreatingUser) {
                FloatingActionButton(
                    onClick = viewModel::startCreatingUser,
                    containerColor = AppColors.Primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_user")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Novo Usuário")
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
            // Success / Error Banners
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
                        .padding(bottom = 12.dp)
                        .testTag("admin_error_banner")
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        modifier = Modifier.padding(14.dp),
                        color = AppColors.StatusDivergenceText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (uiState.isCreatingUser) {
                CreateUserForm(
                    uiState = uiState,
                    viewModel = viewModel
                )
            } else {
                AppSectionHeader(
                    title = "Controle de Acessos",
                    subtitle = "Usuários cadastrados no sistema e seus privilégios",
                    badgeText = "${uiState.users.size} usuário(s)"
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (uiState.users.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AppEmptyState(
                            icon = Icons.Default.People,
                            title = "Nenhum usuário cadastrado",
                            message = "Cadastre novos usuários operacionais usando o botão abaixo.",
                            actionButtonText = "Cadastrar Usuário",
                            onActionClick = viewModel::startCreatingUser
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.users, key = { it.uid }) { user ->
                            UserItemCard(
                                user = user,
                                onToggleActive = { viewModel.toggleUserActive(user.uid, user.active) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserItemCard(
    user: UserProfile,
    onToggleActive: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user_card_${user.uid}")
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
                            .size(36.dp)
                            .background(AppColors.PrimaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = user.name.ifEmpty { "Sem Nome" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                Switch(
                    checked = user.active,
                    onCheckedChange = { onToggleActive() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppColors.StatusCompleted
                    ),
                    modifier = Modifier.testTag("switch_active_${user.uid}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.PrimaryContainer
                ) {
                    Text(
                        text = user.role.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.SecondaryContainer
                ) {
                    Text(
                        text = "Loja: ${user.storeId}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.OnSecondaryContainer
                    )
                }

                if (user.sectorId.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColors.SurfaceVariant
                    ) {
                        Text(
                            text = "Setor: ${user.sectorId}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateUserForm(
    uiState: AdminUsersUiState,
    viewModel: AdminUsersViewModel
) {
    var expandedRoleMenu by remember { mutableStateOf(false) }
    var expandedStoreMenu by remember { mutableStateOf(false) }
    var expandedSectorMenu by remember { mutableStateOf(false) }

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
                label = { Text("Nome Completo") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_name")
            )

            // Email
            OutlinedTextField(
                value = uiState.newEmail,
                onValueChange = viewModel::onNewEmailChanged,
                label = { Text("E-mail corporativo") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_email")
            )

            // Auto Password Notice
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
                Column {
                    Text(
                        text = "Senha Inicial Automática",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.OnPrimaryContainer
                    )
                    Text(
                        text = "A senha inicial será definida automaticamente para este usuário.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.OnPrimaryContainer.copy(alpha = 0.85f)
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
                    label = { Text("Função (Role)") },
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
                            label = { Text("Loja Vinculada") },
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
                                label = { Text("Nome da Loja (ex: Filial Centro)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_new_store_name")
                            )
                            OutlinedTextField(
                                value = newStoreCode,
                                onValueChange = { 
                                    newStoreCode = it 
                                    storeDialogError = null
                                },
                                label = { Text("Código/ID (ex: LOJA_04)") },
                                singleLine = true,
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
                        label = { Text("Setor Vinculado") },
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
