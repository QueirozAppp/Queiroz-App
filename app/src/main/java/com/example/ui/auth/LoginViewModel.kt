package com.example.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.AuthRepository
import com.example.core.firebase.FirebaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.core.data.StoreSectorRepository

/**
 * ViewModel responsável pela lógica de autenticação, diagnósticos do Firebase e redefinição de senha.
 */
class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val storeSectorRepository: StoreSectorRepository = StoreSectorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkExistingSession()
        observeStores()
    }

    private fun observeStores() {
        viewModelScope.launch {
            try {
                storeSectorRepository.getStoresFlow().collect { stores ->
                    val activeStores = stores.filter { it.active }
                    _uiState.update { current ->
                        val currentSelected = current.selectedStoreId
                        val finalSelected = if (currentSelected.isNotBlank() && activeStores.any { it.id == currentSelected }) {
                            currentSelected
                        } else {
                            activeStores.firstOrNull()?.id ?: ""
                        }
                        current.copy(
                            availableStores = activeStores,
                            selectedStoreId = finalSelected
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("LoginViewModel", "Erro ao carregar lojas em tempo real: ${e.message}")
            }
        }
    }

    private var applicationContext: Context? = null

    fun loadDiagnostics(context: Context) {
        applicationContext = context.applicationContext
        val info = FirebaseProvider.getDiagnosticInfo(context)
        val prefs = context.getSharedPreferences("qrz_stock_auth_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs.getString("last_email", "") ?: ""
        _uiState.update { current ->
            current.copy(
                diagnosticInfo = info,
                email = if (current.email.isBlank() && savedEmail.isNotBlank()) savedEmail else current.email
            )
        }
    }

    fun toggleDiagnosticsPanel() {
        _uiState.update { it.copy(showDiagnosticsPanel = !it.showDiagnosticsPanel) }
    }

    fun checkExistingSession() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                _uiState.update { it.copy(authState = AuthState.CheckingSession) }
                val result = authRepository.fetchCurrentUserProfile()
                result.fold(
                    onSuccess = { profile ->
                        _uiState.update { it.copy(authState = AuthState.Authenticated(profile)) }
                    },
                    onFailure = {
                        authRepository.logout()
                        _uiState.update { it.copy(authState = AuthState.Unauthenticated) }
                    }
                )
            } else {
                _uiState.update { it.copy(authState = AuthState.Unauthenticated) }
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onStoreSelected(storeId: String) {
        _uiState.update { it.copy(selectedStoreId = storeId, errorMessage = null) }
    }

    fun openForgotPasswordDialog() {
        val currentEmail = _uiState.value.email
        _uiState.update {
            it.copy(
                showForgotPasswordDialog = true,
                resetPasswordEmail = currentEmail,
                resetPasswordSuccessMessage = null,
                errorMessage = null
            )
        }
    }

    fun closeForgotPasswordDialog() {
        _uiState.update {
            it.copy(
                showForgotPasswordDialog = false,
                resetPasswordEmail = "",
                resetPasswordSuccessMessage = null
            )
        }
    }

    fun onResetPasswordEmailChanged(email: String) {
        _uiState.update { it.copy(resetPasswordEmail = email, errorMessage = null) }
    }

    fun sendPasswordResetEmail() {
        val email = _uiState.value.resetPasswordEmail.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe o e-mail para a redefinição de senha.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetPasswordSuccessMessage = "Se este e-mail estiver cadastrado, enviaremos as instruções para redefinição da senha."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao solicitar redefinição de senha."
                        )
                    }
                }
            )
        }
    }

    fun setErrorMessage(msg: String) {
        _uiState.update { it.copy(errorMessage = msg, isLoading = false) }
    }

    fun setDebugErrorMessage(msg: String?) {
        _uiState.update { it.copy(debugErrorMessage = msg) }
    }

    fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    fun onGoogleLoginResult(idToken: String, selectedStoreId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, authState = AuthState.Authenticating) }
            val result = authRepository.loginWithGoogle(idToken, selectedStoreId)
            result.fold(
                onSuccess = { profile ->
                    applicationContext?.let { ctx ->
                        try {
                            val prefs = ctx.getSharedPreferences("qrz_stock_auth_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("last_email", profile.email).apply()
                        } catch (_: Exception) {}
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authState = AuthState.Authenticated(profile),
                            errorMessage = null
                        )
                    }
                },
                onFailure = { exception ->
                    val msg = exception.localizedMessage ?: "Falha no login com Google."
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authState = AuthState.Unauthenticated,
                            errorMessage = msg
                        )
                    }
                }
            )
        }
    }

    fun changePassword(newPassword: String, confirmPassword: String, onPasswordChanged: () -> Unit) {
        if (newPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "A nova senha não pode ser vazia.") }
            return
        }
        if (newPassword.length < 6) {
            _uiState.update { it.copy(errorMessage = "A senha deve ter no mínimo 6 caracteres.") }
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "A confirmação de senha deve ser igual à nova senha.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.changePassword(newPassword)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                    onPasswordChanged()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao atualizar a senha."
                        )
                    }
                }
            )
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe o e-mail e a senha.") }
            return
        }
        if (state.selectedStoreId.isBlank() && !state.email.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true)) {
            _uiState.update { it.copy(errorMessage = "Selecione a loja para acesso.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, authState = AuthState.Authenticating) }
            val result = authRepository.login(state.email, state.password, state.selectedStoreId)
            result.fold(
                onSuccess = { profile ->
                    applicationContext?.let { ctx ->
                        try {
                            val prefs = ctx.getSharedPreferences("qrz_stock_auth_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("last_email", profile.email).apply()
                        } catch (_: Exception) {}
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authState = AuthState.Authenticated(profile),
                            errorMessage = null
                        )
                    }
                },
                onFailure = { exception ->
                    val msg = exception.localizedMessage ?: "Falha na autenticação."
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authState = AuthState.Unauthenticated,
                            errorMessage = msg
                        )
                    }
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update {
                it.copy(
                    email = "",
                    password = "",
                    selectedStoreId = "",
                    authState = AuthState.Unauthenticated,
                    errorMessage = null
                )
            }
        }
    }
}
