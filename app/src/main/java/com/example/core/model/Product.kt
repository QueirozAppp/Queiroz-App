package com.example.core.model

/**
 * Modelo de domínio representando um produto no Catálogo Global da Rede (Etapa 5.1).
 * O código de barras (`barcode`) é mantido estritamente como String para preservar zeros à esquerda.
 * O campo `storeId` foi removido para centralizar o produto na rede, permitindo futuros vínculos por loja.
 */
data class Product(
    val id: String = "",
    val barcode: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val unit: String = ProductUnit.UN.name,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
