package com.example.core.model

/**
 * Modelo de Solicitação de Baixa operacional (Etapa 8.3).
 * Armazena dados do solicitante, contexto de loja/setor, metadados da conferência,
 * justificativa de divergência (`divergenceReason`) e `productIds` para validação server-side nativa no Firestore Rules.
 */
data class WithdrawalRequest(
    val id: String = "",
    val storeId: String = "",
    val sectorId: String = "",
    val requestedBy: String = "",
    val requestedByName: String = "",
    val createdAt: Long = 0L,
    val status: String = "PENDING", // PENDING, IN_CONFERENCE, COMPLETED, DIVERGENCE
    val conferenceStartedBy: String? = null,
    val conferenceStartedByName: String? = null,
    val conferenceStartedAt: Long? = null,
    val checkedBy: String? = null,
    val checkedByName: String? = null,
    val checkedAt: Long? = null,
    val divergenceReason: String? = null,
    val productIds: List<String> = emptyList(),
    val items: List<WithdrawalRequestItem> = emptyList()
)
