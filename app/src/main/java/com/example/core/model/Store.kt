package com.example.core.model

/**
 * Modelo de dados para uma Loja da rede de supermercados (Etapa 11).
 */
data class Store(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
