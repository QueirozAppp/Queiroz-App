package com.example.core.model

enum class ProductRequestStatus(val label: String) {
    PENDING("Pendente"),
    APPROVED("Aprovado"),
    REJECTED("Recusado")
}

/**
 * Solicitação de cadastro de novo produto no catálogo global da rede.
 * Criado pelo Líder de Setor (Açougue, Padaria, Geral, etc.) com foto e descrição.
 * Aprovado ou recusado pelo Avarista ou Administrador da Rede.
 */
data class ProductRegistrationRequest(
    val id: String = "",
    val barcode: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val unit: String = ProductUnit.UN.name,
    val photoBase64: String = "",
    val storeId: String = "",
    val sectorId: String = "",
    val requestedByUid: String = "",
    val requestedByName: String = "",
    val requestedAt: Long = 0L,
    val status: String = ProductRequestStatus.PENDING.name,
    val reviewedByUid: String = "",
    val reviewedByName: String = "",
    val reviewedAt: Long = 0L,
    val rejectionReason: String = ""
)
