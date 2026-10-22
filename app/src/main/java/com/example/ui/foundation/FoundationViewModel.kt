package com.example.ui.foundation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.core.firebase.FirebaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel responsável por gerenciar o estado da tela de fundação técnica.
 */
class FoundationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FoundationUiState())
    val uiState: StateFlow<FoundationUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        val context = getApplication<Application>()
        val fbStatus = FirebaseProvider.getStatus(context)
        val diagnostics = FirebaseProvider.getDiagnostics(context)
        _uiState.update { current ->
            current.copy(
                firebaseStatus = fbStatus,
                diagnostics = diagnostics
            )
        }
    }
}
