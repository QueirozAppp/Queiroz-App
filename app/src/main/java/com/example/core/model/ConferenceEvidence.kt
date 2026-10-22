package com.example.core.model

/**
 * Modelo de Evidência Fotográfica de Conferência (Etapa 8).
 * Armazena metadados da evidência vinculada à solicitação no Firestore.
 */
data class ConferenceEvidence(
    val id: String = "",
    val requestId: String = "",
    val itemProductId: String? = null,
    val storeId: String = "",
    val uploadedBy: String = "",
    val uploadedByName: String = "",
    val createdAt: Long = 0L,
    val storagePath: String = "",
    val downloadUrl: String = "",
    val description: String = ""
)
