package com.example.ui.auth

import com.example.core.firebase.FirebaseDiagnosticInfo
import com.example.core.model.Store
import com.example.core.model.UserProfile

/**
 * Estados do fluxo de autenticação.
 */
sealed interface AuthState {
    data object CheckingSession : AuthState
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(val userProfile: UserProfile) : AuthState
    data class Error(val message: String) : AuthState
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val selectedStoreId: String = "",
    val availableStores: List<Store> = listOf(
        Store(id = "LOJA_01", name = "Supermercado Queiroz - Matriz", code = "01"),
        Store(id = "LOJA_02", name = "Supermercado Queiroz - Filial Centro", code = "02"),
        Store(id = "LOJA_03", name = "Supermercado Queiroz - Filial Norte", code = "03")
    ),
    val authState: AuthState = AuthState.Unauthenticated,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showForgotPasswordDialog: Boolean = false,
    val resetPasswordEmail: String = "",
    val resetPasswordSuccessMessage: String? = null,
    val diagnosticInfo: FirebaseDiagnosticInfo? = null,
    val showDiagnosticsPanel: Boolean = false,
    val debugErrorMessage: String? = null
)
