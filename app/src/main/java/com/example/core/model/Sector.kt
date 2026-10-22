package com.example.core.model

/**
 * Modelo de dados para um Setor de uma Loja da rede (Etapa 11).
 */
data class Sector(
    val id: String = "",
    val storeId: String = "",
    val name: String = "",
    val code: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
