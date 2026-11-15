package com.example.ui.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.update.AppUpdateManager
import com.example.core.update.AppUpdateUiState
import com.example.core.update.UpdateCheckOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsável pelo estado do banner de atualização e do botão "Verificar atualização".
 */
class AppUpdateViewModel(
    application: Application,
    private val updateManager: AppUpdateManager = AppUpdateManager(application)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, AppUpdateManager(application))

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var hasTriggeredInitialCheck = false

    init {
        restoreCachedBannerIfNeeded()
    }

    private fun restoreCachedBannerIfNeeded() {
        try {
            val cached = updateManager.getCachedUpdate() ?: return
            if (updateManager.shouldDisplayBanner(cached, forceShow = false)) {
                _uiState.update {
                    it.copy(
                        availableUpdate = cached,
                        showBanner = true
                    )
                }
            }
        } catch (_: Exception) {
            // Falha ao ler cache não deve interromper o aplicativo
        }
    }

    /**
     * Chamado automaticamente ao abrir o app (respeita intervalo mínimo de 6 horas).
     * Se falhar, não exibe erro intrusivo na tela.
     */
    fun checkAutomaticallyIfNeeded() {
        if (hasTriggeredInitialCheck) return
        hasTriggeredInitialCheck = true

        viewModelScope.launch {
            try {
                when (val outcome = updateManager.checkForUpdate(force = false)) {
                    is UpdateCheckOutcome.UpdateAvailable -> {
                        val show = updateManager.shouldDisplayBanner(outcome.info, forceShow = false)
                        _uiState.update {
                            it.copy(
                                availableUpdate = outcome.info,
                                showBanner = show
                            )
                        }
                    }
                    is UpdateCheckOutcome.UpToDate -> {
                        _uiState.update {
                            it.copy(availableUpdate = null, showBanner = false)
                        }
                    }
                    else -> {
                        restoreCachedBannerIfNeeded()
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Nunca derruba o app em verificação automática em segundo plano
            }
        }
    }

    /**
     * Chamado pelo botão "Verificar atualização":
     * ignora o intervalo de 6 horas e a escolha "Depois".
     */
    fun checkManually() {
        if (_uiState.value.isChecking) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isChecking = true,
                    statusMessage = null,
                    isStatusError = false
                )
            }

            try {
                when (val outcome = updateManager.checkForUpdate(force = true)) {
                    is UpdateCheckOutcome.UpdateAvailable -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = outcome.info,
                                showBanner = true,
                                statusMessage = "Versão ${outcome.info.version} disponível.",
                                isStatusError = false
                            )
                        }
                    }
                    is UpdateCheckOutcome.UpToDate -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = null,
                                showBanner = false,
                                statusMessage = "Você já está na versão mais recente.",
                                isStatusError = false
                            )
                        }
                    }
                    is UpdateCheckOutcome.Offline -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                statusMessage = "Sem conexão com a internet. Confira o Wi-Fi.",
                                isStatusError = true
                            )
                        }
                    }
                    is UpdateCheckOutcome.Timeout -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                statusMessage = "A verificação demorou demais. Tente novamente.",
                                isStatusError = true
                            )
                        }
                    }
                    is UpdateCheckOutcome.HttpNotFound -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                statusMessage = "Arquivo de versão não encontrado no endereço: ${outcome.url}",
                                isStatusError = true
                            )
                        }
                    }
                    is UpdateCheckOutcome.HttpError -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                statusMessage = "Erro do servidor ao verificar (código ${outcome.statusCode}).",
                                isStatusError = true
                            )
                        }
                    }
                    is UpdateCheckOutcome.InvalidJson -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                statusMessage = "O endereço de atualização não devolveu os dados de versão. Confira o arquivo version.json.",
                                isStatusError = true
                            )
                        }
                    }
                    is UpdateCheckOutcome.SkippedInterval -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                statusMessage = "Você já está na versão mais recente.",
                                isStatusError = false
                            )
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        statusMessage = "Sem conexão com a internet. Confira o Wi-Fi.",
                        isStatusError = true
                    )
                }
            }
        }
    }

    fun dismissBannerForLater() {
        try {
            val current = _uiState.value.availableUpdate
            if (current != null) {
                updateManager.dismissVersion(current.build)
            }
        } catch (_: Exception) {
        }
        _uiState.update { it.copy(showBanner = false) }
    }

    fun onUpdateNow(context: Context) {
        val info = _uiState.value.availableUpdate ?: return
        val opened = updateManager.openUpdateUrl(context, info.url)
        if (!opened) {
            _uiState.update {
                it.copy(
                    statusMessage = "Não consegui abrir o link de atualização.",
                    isStatusError = true
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null, isStatusError = false) }
    }
}
