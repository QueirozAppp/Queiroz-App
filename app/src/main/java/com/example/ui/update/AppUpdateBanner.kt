package com.example.ui.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.core.update.AppUpdateConfig
import com.example.core.update.RemoteVersionInfo
import com.example.ui.components.AppColors

/**
 * Aviso discreto exibido na tela quando há uma nova versão disponível.
 */
@Composable
fun AppUpdateBanner(
    updateInfo: RemoteVersionInfo,
    onUpdateClick: () -> Unit,
    onLaterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.PrimaryContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_update_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Atualização disponível",
                    tint = AppColors.Primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Nova versão ${updateInfo.version} disponível",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.testTag("text_update_banner_title")
                )
            }

            if (updateInfo.notes.isNotBlank()) {
                Text(
                    text = updateInfo.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onLaterClick,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_update_later")
                ) {
                    Text(
                        text = "Depois",
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onUpdateClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_update_now")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Atualizar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * Cartão de Configurações / Versão com o botão "Verificar atualização".
 */
@Composable
fun CheckUpdateSectionCard(
    isChecking: Boolean,
    statusMessage: String?,
    isStatusError: Boolean,
    onCheckForUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    onPublishCloudUpdate: ((version: String, build: Int, notes: String, url: String) -> Unit)? = null
) {
    var showPublishDialog by remember { mutableStateOf(false) }
    var versionInput by remember { mutableStateOf(AppUpdateConfig.APP_VERSION) }
    var buildInput by remember { mutableStateOf((AppUpdateConfig.APP_BUILD + 1).toString()) }
    var notesInput by remember { mutableStateOf("Nova versão disponível para download.") }
    var urlInput by remember { mutableStateOf(AppUpdateConfig.FALLBACK_DOWNLOAD_URL) }

    if (showPublishDialog && onPublishCloudUpdate != null) {
        AlertDialog(
            onDismissRequest = { showPublishDialog = false },
            containerColor = AppColors.Surface,
            title = {
                Text(
                    text = "Publicar Atualização na Nuvem",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Notifica todos os usuários direto pelo Firestore (mesmo com GitHub privado).",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    OutlinedTextField(
                        value = versionInput,
                        onValueChange = { versionInput = it },
                        label = { Text("Versão (ex: 1.3.0)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = buildInput,
                        onValueChange = { buildInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Número do Build (atual: ${AppUpdateConfig.APP_BUILD})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notas da Versão") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Link do APK / Release") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedBuild = buildInput.toIntOrNull() ?: 0
                        onPublishCloudUpdate(versionInput, parsedBuild, notesInput, urlInput)
                        showPublishDialog = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Publicar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPublishDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppColors.BorderStrong)
                ) {
                    Text("Cancelar", color = AppColors.TextPrimary)
                }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("check_update_section_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atualizações do Aplicativo",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Versão instalada: ${AppUpdateConfig.APP_VERSION} (Build ${AppUpdateConfig.APP_BUILD})",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                val bgColor = if (isStatusError) AppColors.StatusDivergenceBg else AppColors.PrimaryContainer.copy(alpha = 0.65f)
                val fgColor = if (isStatusError) AppColors.StatusDivergenceText else AppColors.Primary
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_status_message_box")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isStatusError) Icons.Default.WifiOff else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fgColor,
                            modifier = Modifier.testTag("text_update_status_message")
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onCheckForUpdateClick,
                enabled = !isChecking,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_check_update")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificando...", fontWeight = FontWeight.Bold, color = AppColors.Primary)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AppColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificar atualização", fontWeight = FontWeight.Bold, color = AppColors.Primary)
                }
            }

            if (isAdmin && onPublishCloudUpdate != null) {
                OutlinedButton(
                    onClick = { showPublishDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.BorderStrong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_admin_publish_update")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = AppColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Publicar atualização na nuvem",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
