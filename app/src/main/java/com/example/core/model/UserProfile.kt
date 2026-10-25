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
    val mustChangePassword: Boolean = false,
    val allowPasswordChange: Boolean = false,
    val passwordDefined: Boolean = false
) {
    companion object {
        /**
         * Converte com segurança um documento do Firestore em um UserProfile,
         * tolerando variações de maiúsculas/minúsculas de cargos e formatos de data (Timestamp ou Long).
         */
        fun fromDocument(doc: com.google.firebase.firestore.DocumentSnapshot): UserProfile {
            val data = doc.data ?: return UserProfile(uid = doc.id)
            val rawRole = data["role"]?.toString() ?: "AVARISTA"
            val role = when (rawRole.trim().uppercase()) {
                "ADMIN", "ADMINISTRADOR", "ADMINISTRATOR" -> UserRole.ADMIN
                "SETOR_LEADER", "LIDER", "LÍDER", "LIDER_SETOR", "LEADER" -> UserRole.SETOR_LEADER
                else -> UserRole.AVARISTA
            }
            val rawCreatedAt = data["createdAt"]
            val createdAt = when (rawCreatedAt) {
                is Number -> rawCreatedAt.toLong()
                is com.google.firebase.Timestamp -> rawCreatedAt.toDate().time
                else -> System.currentTimeMillis()
            }
            val active = when (val rawActive = data["active"]) {
                is Boolean -> rawActive
                is String -> rawActive.toBoolean()
                else -> true
            }
            val mustChange = when (val rawMust = data["mustChangePassword"]) {
                is Boolean -> rawMust
                is String -> rawMust.toBoolean()
                else -> false
            }
            val allowPasswordChange = when (val rawAllow = data["allowPasswordChange"]) {
                is Boolean -> rawAllow
                is String -> rawAllow.toBoolean()
                else -> false
            }
            val passwordDefined = when (val rawDefined = data["passwordDefined"]) {
                is Boolean -> rawDefined
                is String -> rawDefined.toBoolean()
                else -> false
            }
            return UserProfile(
                uid = doc.id,
                name = data["name"]?.toString() ?: "",
                email = data["email"]?.toString() ?: "",
                role = role,
                storeId = data["storeId"]?.toString() ?: "",
                sectorId = data["sectorId"]?.toString() ?: "",
                active = active,
                createdAt = createdAt,
                mustChangePassword = mustChange,
                allowPasswordChange = allowPasswordChange,
                passwordDefined = passwordDefined
            )
        }
    }
}
