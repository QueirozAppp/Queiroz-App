package com.example.core.model

/**
 * Modelo de dados para o registro de Baixa (Write-off) de produtos.
 */
data class Withdrawal(
    val id: String = "",
    val storeId: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val unit: String = "UN",
    val reason: String = "", // ex: Avaria, Validade Vencida, Consumo Interno
    val sector: String = "", // ex: Açougue, Padaria, Hortifrúti
    val userId: String = "",
    val userName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
