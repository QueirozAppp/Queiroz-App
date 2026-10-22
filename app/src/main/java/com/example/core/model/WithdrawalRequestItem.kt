package com.example.core.model

/**
 * Item individual de uma Solicitação de Baixa (Etapa 7).
 * Preserva a quantidade solicitada histórica (`requestedQuantity`) e a quantidade conferida (`checkedQuantity`).
 * `checkedQuantity == null` indica que o item ainda não foi conferido fisicamente pelo avarista.
 */
data class WithdrawalRequestItem(
    val productId: String = "",
    val barcode: String = "",
    val productName: String = "",
    val unit: String = "",
    val requestedQuantity: Double = 0.0,
    val checkedQuantity: Double? = null
)
