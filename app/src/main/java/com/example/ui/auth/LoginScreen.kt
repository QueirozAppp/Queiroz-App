package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AppColors
import com.example.ui.components.AppErrorBanner
import com.example.ui.components.AppPrimaryButton
import com.example.ui.theme.ThemeManager
import kotlinx.coroutines.launch

/**
 * Tela de Login corporativa redesenhada (Etapa 20).
 * Preserva estritamente a autenticação Firebase e todos os testTags existentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedStoreMenu by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadDiagnostics(context)
    }

    val isEmailAdmin = uiState.email.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true)
    val selectedStoreName = if (isEmailAdmin && uiState.selectedStoreId.isBlank()) {
        "Acesso Administrativo (Sem Loja)"
    } else {
        uiState.availableStores.find { it.id == uiState.selectedStoreId }?.name
            ?: if (uiState.availableStores.isEmpty()) "Nenhuma loja encontrada no Firestore" else "Selecione a Loja..."
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("login_screen"),
        containerColor = AppColors.Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Theme Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { ThemeManager.toggleTheme(AppColors.isDark, context) },
                        modifier = Modifier.testTag("btn_toggle_theme")
                    ) {
                        Icon(
                            imageVector = if (AppColors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (AppColors.isDark) "Mudar para tema claro" else "Mudar para tema escuro",
                            tint = AppColors.TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(90.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_qrz_stock),
                        contentDescription = "Logo QRZ Stock",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))



                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AppColors.Secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Gestão Operacional e Conferência de Avarias",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Card Principal de Formulário Glassmorphism
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Acesso Corporativo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )

                        // Error Banner
                        if (!uiState.errorMessage.isNullOrEmpty()) {
                            AppErrorBanner(
                                message = uiState.errorMessage ?: "",
                                modifier = Modifier.testTag("login_error_banner")
                            )
                        }

                        if (!uiState.debugErrorMessage.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "Debug: ${uiState.debugErrorMessage}",
                                    modifier = Modifier.padding(8.dp),
                                    color = Color(0xFFC62828),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Store Selector (Dropdown)
                        ExposedDropdownMenuBox(
                            expanded = expandedStoreMenu,
                            onExpandedChange = { expandedStoreMenu = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedStoreName,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                maxLines = 1,
                                label = { Text("Unidade / Loja") },
                                leadingIcon = {
                                    Icon(Icons.Default.Store, contentDescription = null, tint = AppColors.Primary)
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStoreMenu)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.Primary,
                                    unfocusedBorderColor = AppColors.BorderStrong
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("store_selector_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = expandedStoreMenu,
                                onDismissRequest = { expandedStoreMenu = false },
                                modifier = Modifier
                                    .background(AppColors.Surface)
                                    .padding(vertical = 4.dp)
                            ) {
                                if (uiState.availableStores.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Nenhuma loja ativa no Cloud Firestore. Faça login como Administrador para cadastrar lojas.",
                                                color = AppColors.TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        onClick = { expandedStoreMenu = false }
                                    )
                                } else {
                                    uiState.availableStores.forEach { store ->
                                        val isSelected = store.id == uiState.selectedStoreId
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = store.name,
                                                    color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Store,
                                                    contentDescription = null,
                                                    tint = if (isSelected) AppColors.Primary else AppColors.TextTertiary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = AppColors.TextPrimary,
                                                leadingIconColor = AppColors.Primary
                                            ),
                                            modifier = Modifier.background(
                                                if (isSelected) AppColors.PrimaryContainer else Color.Transparent
                                            ),
                                            onClick = {
                                                viewModel.onStoreSelected(store.id)
                                                expandedStoreMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Email Input
                        OutlinedTextField(
                            value = uiState.email,
                            onValueChange = viewModel::onEmailChanged,
                            label = { Text("E-mail corporativo") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = AppColors.TextTertiary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = AppColors.BorderStrong
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input")
                        )

                        // Password Input
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = viewModel::onPasswordChanged,
                            label = { Text("Senha") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AppColors.TextTertiary)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.testTag("toggle_password_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Ocultar senha" else "Exibir senha",
                                        tint = AppColors.TextTertiary
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = AppColors.BorderStrong
                            ),
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )

                        Text(
                            text = "Primeiro acesso? Utilize a senha inicial (padrão: queiroz123) para cadastrar sua nova senha definitiva.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )

                        // Forgot Password Link
                        TextButton(
                            onClick = viewModel::openForgotPasswordDialog,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("btn_forgot_password")
                        ) {
                            Text(
                                text = "Esqueci minha senha",
                                color = AppColors.PrimaryLight,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Submit Button
                        AppPrimaryButton(
                            text = "Acessar Sistema",
                            onClick = viewModel::login,
                            isLoading = uiState.isLoading,
                            modifier = Modifier.testTag("login_button")
                        )
                    }
                }
            }
        }

        // Dialog for "Esqueci minha senha" (Password Reset)
        if (uiState.showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeForgotPasswordDialog,
                containerColor = AppColors.Surface,
                title = {
                    Text(
                        text = "Redefinição de Senha",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Informe seu e-mail corporativo cadastrado para receber o link oficial de redefinição de senha.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )

                        OutlinedTextField(
                            value = uiState.resetPasswordEmail,
                            onValueChange = viewModel::onResetPasswordEmailChanged,
                            label = { Text("E-mail corporativo") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_reset_email")
                        )

                        if (!uiState.resetPasswordSuccessMessage.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.StatusCompletedBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.resetPasswordSuccessMessage ?: "",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.StatusCompletedText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::sendPasswordResetEmail,
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        modifier = Modifier.testTag("btn_send_reset_email")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Text("Enviar Link", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = viewModel::closeForgotPasswordDialog,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fechar", color = AppColors.TextPrimary)
                    }
                }
            )
        }
    }
}
