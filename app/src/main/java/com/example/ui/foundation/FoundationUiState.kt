package com.example.ui.foundation

import com.example.core.firebase.FirebaseServicesDiagnostic
import com.example.core.firebase.FirebaseStatus
import com.example.core.model.UserRole

/**
 * Estado da tela de fundação técnica.
 */
data class FoundationUiState(
    val appTitle: String = "Queiroz Avaria",
    val appSubtitle: String = "Base do aplicativo e Banco de Dados configurados.",
    val architectureStatus: String = "Arquitetura Modular / Clean Architecture com Firestore Repository",
    val buildStatus: String = "Compilado com sucesso (Gradle Kotlin DSL)",
    val firebaseStatus: FirebaseStatus = FirebaseStatus.PendingConfiguration(
        message = "SDKs preparados (Auth, Firestore, Storage). Aguardando google-services.json."
    ),
    val diagnostics: FirebaseServicesDiagnostic? = null,
    val plannedRoles: List<UserRole> = UserRole.entries,
    val databaseCollections: List<String> = listOf("stores", "products", "withdrawals", "users")
)
