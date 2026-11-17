package com.example.ui.admin.audit

import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.AuditLog

/**
 * Converte códigos internos da Trilha de Auditoria em textos didáticos em português
 * para facilitar a leitura gerencial sem perder os dados técnicos originais.
 */
object AuditLogFormatter {

    fun formatActionTitle(action: String): String = when (action) {
        AuditAction.WITHDRAWAL_REQUEST_CREATED -> "Solicitação de baixa criada"
        AuditAction.CONFERENCE_STARTED -> "Conferência iniciada"
        AuditAction.ITEM_CHECKED -> "Produto conferido"
        AuditAction.WITHDRAWAL_COMPLETED -> "Conferência concluída"
        AuditAction.WITHDRAWAL_DIVERGENCE -> "Divergência registrada"
        AuditAction.CONFERENCE_CANCELLED -> "Solicitação cancelada"
        AuditAction.WITHDRAWAL_RECTIFIED -> "Baixa retificada"
        AuditAction.EVIDENCE_ADDED -> "Foto / evidência anexada"

        AuditAction.USER_CREATED -> "Usuário cadastrado"
        AuditAction.USER_ACTIVATED -> "Usuário ativado"
        AuditAction.USER_DEACTIVATED -> "Usuário desativado"
        AuditAction.USER_STORE_CHANGED -> "Perfil/loja do usuário alterado"
        AuditAction.USER_SECTOR_CHANGED -> "Setor do usuário alterado"
        AuditAction.USER_ROLE_CHANGED -> "Cargo do usuário alterado"
        AuditAction.USER_DELETED -> "Usuário removido"

        AuditAction.STORE_CREATED -> "Loja cadastrada"
        AuditAction.STORE_UPDATED -> "Loja atualizada"
        AuditAction.STORE_ACTIVATED -> "Loja ativada"
        AuditAction.STORE_DEACTIVATED -> "Loja desativada"
        AuditAction.STORE_DELETED -> "Loja removida"

        AuditAction.SECTOR_CREATED -> "Setor cadastrado"
        AuditAction.SECTOR_UPDATED -> "Setor atualizado"
        AuditAction.SECTOR_ACTIVATED -> "Setor ativado"
        AuditAction.SECTOR_DEACTIVATED -> "Setor desativado"

        AuditAction.PRODUCT_CREATED -> "Produto cadastrado"
        AuditAction.PRODUCT_UPDATED -> "Produto atualizado"
        AuditAction.PRODUCT_ACTIVATED -> "Produto ativado"
        AuditAction.PRODUCT_DEACTIVATED -> "Produto desativado"
        AuditAction.PRODUCT_DELETED -> "Produto excluído"

        AuditAction.PRODUCT_REQUEST_CREATED -> "Cadastro de produto solicitado"
        AuditAction.PRODUCT_REQUEST_APPROVED -> "Cadastro de produto aprovado"
        AuditAction.PRODUCT_REQUEST_REJECTED -> "Cadastro de produto recusado"

        else -> action.replace('_', ' ').lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    fun formatEntityType(entityType: String): String = when (entityType) {
        AuditEntityType.WITHDRAWAL_REQUEST -> "Solicitação de Baixa"
        AuditEntityType.EVIDENCE -> "Foto / Evidência"
        AuditEntityType.USER -> "Usuário"
        AuditEntityType.STORE -> "Loja"
        AuditEntityType.SECTOR -> "Setor"
        AuditEntityType.PRODUCT -> "Produto"
        AuditEntityType.PRODUCT_REQUEST -> "Solicitação de Produto"
        else -> entityType
    }

    fun formatRole(role: String): String = when (role.trim().uppercase()) {
        "ADMIN", "ADMINISTRADOR" -> "Administrador"
        "AVARISTA" -> "Avarista"
        "SETOR_LEADER", "LIDER_SETOR" -> "Líder de Setor"
        else -> role.ifBlank { "Sistema" }
    }

    fun formatDescription(log: AuditLog): String {
        val raw = log.description.trim()
        if (raw.isBlank()) return formatActionTitle(log.action)
        return raw
            .replace("(ADMIN)", "(Administrador)")
            .replace("(AVARISTA)", "(Avarista)")
            .replace("(SETOR_LEADER)", "(Líder de Setor)")
    }

    fun formatReferenceCode(entityId: String): String {
        val clean = entityId.trim()
        return if (clean.isBlank()) "—" else "#${clean.take(6).uppercase()}"
    }

    fun formatLocation(log: AuditLog): String {
        val parts = buildList {
            if (log.storeId.isNotBlank()) add("Loja: ${log.storeId}")
            if (log.sectorId.isNotBlank()) add("Setor: ${log.sectorId}")
        }
        return if (parts.isEmpty()) {
            when (log.entityType) {
                AuditEntityType.PRODUCT, AuditEntityType.PRODUCT_REQUEST -> "Catálogo Global (todas as lojas)"
                else -> "Geral"
            }
        } else {
            parts.joinToString(" • ")
        }
    }

    data class FriendlyMetadataItem(
        val label: String,
        val value: String
    )

    private val hiddenTechnicalMetadataKeys = setOf(
        "productId",
        "requestId",
        "createdUid",
        "targetUid",
        "storagePath",
        "itemProductId",
        "wasRecovered"
    )

    fun formatFriendlyMetadata(metadata: Map<String, String>): List<FriendlyMetadataItem> {
        if (metadata.isEmpty()) return emptyList()
        return metadata.entries.mapNotNull { (rawKey, rawValue) ->
            if (rawKey in hiddenTechnicalMetadataKeys) return@mapNotNull null
            val cleanVal = rawValue.trim()
            if (cleanVal.isBlank()) return@mapNotNull null

            val label = when (rawKey) {
                "productName" -> "Produto"
                "barcode" -> "Código de Barras"
                "unit" -> "Unidade"
                "price" -> "Preço"
                "itemsCount" -> "Qtd. de Produtos"
                "storeId", "store" -> "Loja"
                "sectorId" -> "Setor"
                "storeName" -> "Nome da Loja"
                "sectorName" -> "Nome do Setor"
                "oldStoreId" -> "Loja Anterior"
                "newStoreId" -> "Nova Loja"
                "oldSectorId" -> "Setor Anterior"
                "newSectorId" -> "Novo Setor"
                "divergenceReason" -> "Motivo da Divergência"
                "cancellationReason", "reason" -> "Motivo"
                "rectificationReason" -> "Motivo da Retificação"
                "role" -> "Perfil"
                "active" -> "Situação"
                "allowPasswordChange" -> "Troca de Senha"
                "name" -> "Nome"
                else -> rawKey
            }

            val value = when (rawKey) {
                "role" -> formatRole(cleanVal)
                "active" -> if (cleanVal.equals("true", ignoreCase = true)) "Ativo" else "Inativo"
                "allowPasswordChange" -> if (cleanVal.equals("true", ignoreCase = true)) "Liberada" else "Bloqueada"
                "price" -> {
                    val num = cleanVal.replace(",", ".").toDoubleOrNull()
                    if (num != null && num > 0.0) {
                        String.format(java.util.Locale.forLanguageTag("pt-BR"), "R$ %.2f", num)
                    } else {
                        "Não informado"
                    }
                }
                else -> cleanVal
            }

            FriendlyMetadataItem(label = label, value = value)
        }
    }
}
