package com.example.core.model

/**
 * Modelo de domínio representando o perfil do usuário autenticado no sistema Controle de Baixas.
 * Obtido exclusivamente do backend (Cloud Firestore: users/{uid}).
 */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.AVARISTA,
    val storeId: String = "",
    val sectorId: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val mustChangePassword: Boolean = false
)
