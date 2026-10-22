package com.example.core.model

/**
 * Modelo de dados para a Trilha de Auditoria e Log Imutável (Etapa 13).
 */
data class AuditLog(
    val id: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userRole: String = "",
    val storeId: String = "",
    val sectorId: String = "",
    val timestamp: Long = 0L,
    val description: String = "",
    val metadata: Map<String, String> = emptyMap()
)

object AuditAction {
    // Solicitações e Conferência
    const val WITHDRAWAL_REQUEST_CREATED = "WITHDRAWAL_REQUEST_CREATED"
    const val CONFERENCE_STARTED = "CONFERENCE_STARTED"
    const val ITEM_CHECKED = "ITEM_CHECKED"
    const val WITHDRAWAL_COMPLETED = "WITHDRAWAL_COMPLETED"
    const val WITHDRAWAL_DIVERGENCE = "WITHDRAWAL_DIVERGENCE"

    // Evidências
    const val EVIDENCE_ADDED = "EVIDENCE_ADDED"

    // Gestão de Usuários
    const val USER_CREATED = "USER_CREATED"
    const val USER_ACTIVATED = "USER_ACTIVATED"
    const val USER_DEACTIVATED = "USER_DEACTIVATED"
    const val USER_STORE_CHANGED = "USER_STORE_CHANGED"
    const val USER_SECTOR_CHANGED = "USER_SECTOR_CHANGED"
    const val USER_ROLE_CHANGED = "USER_ROLE_CHANGED"

    // Lojas
    const val STORE_CREATED = "STORE_CREATED"
    const val STORE_UPDATED = "STORE_UPDATED"
    const val STORE_ACTIVATED = "STORE_ACTIVATED"
    const val STORE_DEACTIVATED = "STORE_DEACTIVATED"

    // Setores
    const val SECTOR_CREATED = "SECTOR_CREATED"
    const val SECTOR_UPDATED = "SECTOR_UPDATED"
    const val SECTOR_ACTIVATED = "SECTOR_ACTIVATED"
    const val SECTOR_DEACTIVATED = "SECTOR_DEACTIVATED"

    // Produtos
    const val PRODUCT_CREATED = "PRODUCT_CREATED"
    const val PRODUCT_UPDATED = "PRODUCT_UPDATED"
    const val PRODUCT_ACTIVATED = "PRODUCT_ACTIVATED"
    const val PRODUCT_DEACTIVATED = "PRODUCT_DEACTIVATED"
}

object AuditEntityType {
    const val WITHDRAWAL_REQUEST = "WITHDRAWAL_REQUEST"
    const val EVIDENCE = "EVIDENCE"
    const val USER = "USER"
    const val STORE = "STORE"
    const val SECTOR = "SECTOR"
    const val PRODUCT = "PRODUCT"
}
