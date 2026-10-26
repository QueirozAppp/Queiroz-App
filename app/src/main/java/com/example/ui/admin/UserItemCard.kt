package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.ui.components.AppColors

@Composable
fun UserItemCard(
    user: UserProfile,
    onToggleActive: () -> Unit,
    onTogglePasswordPermission: () -> Unit,
    onDelete: () -> Unit
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
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.testTag("btn_delete_user_${user.uid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir Usuário",
                        tint = AppColors.StatusDivergenceText
                    )
                }
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

                // Status da Senha (Definida vs Pendente vs Liberada)
                val (pwdLabel, pwdBg, pwdText) = when {
                    !user.passwordDefined -> Triple("1º Acesso Pendente", Color(0xFFFFF3E0), Color(0xFFE65100))
                    user.allowPasswordChange -> Triple("Troca Liberada", Color(0xFFE3F2FD), Color(0xFF0D47A1))
                    else -> Triple("Senha Definitiva", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = pwdBg
                ) {
                    Text(
                        text = pwdLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = pwdText
                    )
                }
            }

            // Ação administrativa: Permitir / Bloquear alteração de senha
            if (user.role != UserRole.ADMIN) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onTogglePasswordPermission,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_toggle_password_${user.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (user.allowPasswordChange) AppColors.StatusDivergenceText else AppColors.Primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (user.allowPasswordChange) "Bloquear Troca de Senha" else "Liberar Troca de Senha",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (user.allowPasswordChange) AppColors.StatusDivergenceText else AppColors.Primary
                        )
                    }
                }
            }
        }
    }
}
