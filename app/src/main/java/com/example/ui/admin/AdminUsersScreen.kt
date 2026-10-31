package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
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
        var showDeleteConfirmation by remember { mutableStateOf<UserProfile?>(null) }
        var userToEdit by remember { mutableStateOf<UserProfile?>(null) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Edit User Dialog
            userToEdit?.let { targetUser ->
                var editName by remember { mutableStateOf(targetUser.name) }
                var editRole by remember { mutableStateOf(targetUser.role) }
                var editStoreId by remember { mutableStateOf(targetUser.storeId) }
                var editSectorId by remember { mutableStateOf(targetUser.sectorId) }
                var expandedRoleMenu by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { userToEdit = null },
                    title = {
                        Text(
                            text = "Editar Dados do Usuário",
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
                                text = "E-mail: ${targetUser.email}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )

                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Nome Completo") },
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_edit_user_name")
                            )

                            // Cargo
                            ExposedDropdownMenuBox(
                                expanded = expandedRoleMenu,
                                onExpandedChange = { expandedRoleMenu = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = editRole.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Cargo / Função") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRoleMenu) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedRoleMenu,
                                    onDismissRequest = { expandedRoleMenu = false },
                                    modifier = Modifier.background(AppColors.Surface)
                                ) {
                                    UserRole.values().forEach { r ->
                                        DropdownMenuItem(
                                            text = { Text(r.label, color = AppColors.TextPrimary) },
                                            onClick = {
                                                editRole = r
                                                expandedRoleMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Loja
                            OutlinedTextField(
                                value = editStoreId,
                                onValueChange = { editStoreId = it },
                                label = { Text("Loja (ex: LOJA_01, LOJA_05)") },
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_edit_user_store")
                            )

                            // Setor
                            OutlinedTextField(
                                value = editSectorId,
                                onValueChange = { editSectorId = it },
                                label = { Text("Setor (ex: ACOUGUE, PADARIA, GERAL)") },
                                singleLine = true,
                                maxLines = 1,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_edit_user_sector")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.updateUserProfile(
                                    user = targetUser,
                                    newName = editName,
                                    newRole = editRole,
                                    newStoreId = editStoreId,
                                    newSectorId = editSectorId
                                )
                                userToEdit = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                            modifier = Modifier.testTag("btn_confirm_edit_user")
                        ) {
                            Text("Salvar Alterações", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { userToEdit = null },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Confirmation Dialog for User Deletion
            showDeleteConfirmation?.let { userToDelete ->
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = null },
                    title = { Text("Excluir Usuário") },
                    text = { Text("Tem certeza que deseja excluir permanentemente o usuário ${userToDelete.name}? Esta ação não pode ser desfeita.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteUser(userToDelete)
                                showDeleteConfirmation = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = AppColors.StatusDivergence)
                        ) {
                            Text("Excluir")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = null }) {
                            Text("Cancelar")
                        }
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
                                onToggleActive = { viewModel.toggleUserActive(user.uid, user.active) },
                                onTogglePasswordPermission = { viewModel.toggleAllowPasswordChange(user) },
                                onEdit = { userToEdit = user },
                                onDelete = { showDeleteConfirmation = user }
                            )
                        }
                    }
                }
            }
        }
    }
}
