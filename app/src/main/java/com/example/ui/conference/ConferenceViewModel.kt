package com.example.ui.conference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.WithdrawalRequestRepository
import com.example.core.model.ConferenceEvidence
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.model.WithdrawalRequest
import com.example.core.model.WithdrawalRequestItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsável pelo fluxo da Conferência Física e Evidências do Avarista (Etapas 8 e 10).
 */
class ConferenceViewModel(
    private val repository: WithdrawalRequestRepository = WithdrawalRequestRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConferenceUiState())
    val uiState: StateFlow<ConferenceUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadRequestForConference(
        requestId: String,
        userProfile: UserProfile,
        readOnlyView: Boolean = false
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    currentRequest = null,
                    items = emptyList(),
                    evidences = emptyList(),
                    isFinalized = false,
                    finalStatus = null
                )
            }

            // Escutar evidências em tempo real
            launch {
                repository.getConferenceEvidencesFlow(requestId).collect { evidencesList ->
                    _uiState.update { it.copy(evidences = evidencesList) }
                }
            }

            val initialResult = repository.getRequestById(requestId)
            initialResult.fold(
                onSuccess = { req ->
                    if (req == null) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Solicitação não encontrada.")
                        }
                        return@fold
                    }

                    val isAlreadyFinalized = req.status in listOf("COMPLETED", "DIVERGENCE", "CANCELLED", "RECTIFIED")
                    val canConductConference = !readOnlyView &&
                        (userProfile.role == UserRole.AVARISTA || userProfile.role == UserRole.ADMIN)

                    if (isAlreadyFinalized || !canConductConference) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentRequest = req,
                                items = req.items,
                                divergenceReasonInput = req.divergenceReason ?: "",
                                isFinalized = true,
                                finalStatus = req.status,
                                errorMessage = null
                            )
                        }
                    } else {
                        val startResult = repository.startConference(requestId, userProfile)
                        startResult.fold(
                            onSuccess = {
                                val updatedReq = repository.getRequestById(requestId).getOrNull() ?: req
                                val finalized = updatedReq.status in listOf("COMPLETED", "DIVERGENCE", "CANCELLED", "RECTIFIED")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        currentRequest = updatedReq,
                                        items = updatedReq.items,
                                        divergenceReasonInput = updatedReq.divergenceReason ?: "",
                                        isFinalized = finalized,
                                        finalStatus = if (finalized) updatedReq.status else null
                                    )
                                }
                            },
                            onFailure = { err ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        currentRequest = req,
                                        items = req.items,
                                        divergenceReasonInput = req.divergenceReason ?: "",
                                        isFinalized = true,
                                        finalStatus = req.status,
                                        errorMessage = err.localizedMessage ?: "Erro ao iniciar conferência."
                                    )
                                }
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.localizedMessage ?: "Erro ao carregar dados.")
                    }
                }
            )
        }
    }

    fun startEditingItem(productId: String) {
        val currentItem = _uiState.value.items.find { it.productId == productId }
        val initialInput = currentItem?.checkedQuantity?.toString()?.replace(".", ",") ?: ""
        _uiState.update {
            it.copy(
                editingItemProductId = productId,
                checkedQuantityInput = initialInput,
                errorMessage = null
            )
        }
    }

    fun onCheckedQuantityInputChanged(input: String) {
        _uiState.update { it.copy(checkedQuantityInput = input, errorMessage = null) }
    }

    fun onDivergenceReasonChanged(reason: String) {
        _uiState.update { it.copy(divergenceReasonInput = reason, errorMessage = null) }
    }

    fun cancelEditingItem() {
        _uiState.update { it.copy(editingItemProductId = null, checkedQuantityInput = "") }
    }

    fun confirmItemCheck() {
        val state = _uiState.value
        val productId = state.editingItemProductId ?: return
        val rawInput = state.checkedQuantityInput.replace(",", ".")
        val value = rawInput.toDoubleOrNull()

        if (value == null || value < 0.0) {
            _uiState.update { it.copy(errorMessage = "Informe uma quantidade válida maior ou igual a zero.") }
            return
        }

        val updatedItems = state.items.map { item ->
            if (item.productId == productId) {
                item.copy(checkedQuantity = value)
            } else {
                item
            }
        }

        _uiState.update {
            it.copy(
                items = updatedItems,
                editingItemProductId = null,
                checkedQuantityInput = "",
                errorMessage = null
            )
        }
    }

    fun uploadEvidence(
        requestId: String,
        imageBytes: ByteArray,
        description: String,
        itemProductId: String?,
        userProfile: UserProfile
    ) {
        val trimmedDesc = description.trim()
        if (trimmedDesc.length > 500) {
            _uiState.update { it.copy(errorMessage = "A descrição da evidência deve ter no máximo 500 caracteres.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = "Enviando evidência...") }
            val result = repository.uploadEvidenceFileAndCreateRecord(
                requestId = requestId,
                imageBytes = imageBytes,
                description = trimmedDesc,
                itemProductId = itemProductId,
                userProfile = userProfile
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Evidência adicionada com sucesso."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao enviar evidência."
                        )
                    }
                }
            )
        }
    }

    fun finishConference(userProfile: UserProfile, onSuccess: (String) -> Unit) {
        val state = _uiState.value
        val req = state.currentRequest ?: run {
            _uiState.update { it.copy(errorMessage = "Nenhuma solicitação selecionada para finalizar.") }
            return
        }

        // Validar que todos os itens possuem checkedQuantity
        val hasUnchecked = state.items.any { it.checkedQuantity == null }
        if (hasUnchecked) {
            _uiState.update { it.copy(errorMessage = "Todos os produtos precisam ser conferidos antes de finalizar.") }
            return
        }

        val hasDivergence = state.items.any { it.checkedQuantity != it.requestedQuantity }
        if (hasDivergence && state.divergenceReasonInput.trim().isBlank()) {
            _uiState.update { it.copy(errorMessage = "É obrigatório informar a justificativa da divergência para finalizar.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.finishConference(
                requestId = req.id,
                updatedItems = state.items,
                divergenceReason = if (hasDivergence) state.divergenceReasonInput else null,
                userProfile = userProfile
            )
            result.fold(
                onSuccess = { status ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFinalized = true,
                            finalStatus = status,
                            successMessage = if (status == "DIVERGENCE") "Conferência finalizada com DIVERGÊNCIA." else "Conferência finalizada com SUCESSO!"
                        )
                    }
                    onSuccess(status)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao finalizar conferência."
                        )
                    }
                }
            )
        }
    }

    fun cancelRequest(reason: String, userProfile: UserProfile, onSuccess: () -> Unit) {
        val req = _uiState.value.currentRequest ?: run {
            _uiState.update { it.copy(errorMessage = "Nenhuma solicitação selecionada.") }
            return
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe o motivo do cancelamento/exclusão.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.cancelOrDeleteRequest(req.id, trimmedReason, userProfile)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFinalized = true,
                            finalStatus = "CANCELLED",
                            successMessage = "Solicitação cancelada com sucesso!"
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao excluir solicitação."
                        )
                    }
                }
            )
        }
    }

    fun rectifyRequest(reason: String, userProfile: UserProfile, onSuccess: () -> Unit) {
        val req = _uiState.value.currentRequest ?: run {
            _uiState.update { it.copy(errorMessage = "Nenhuma solicitação selecionada.") }
            return
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe o motivo da retificação.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.rectifyRequest(req.id, trimmedReason, _uiState.value.items, userProfile)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFinalized = true,
                            finalStatus = "RECTIFIED",
                            successMessage = "Solicitação retificada com sucesso!"
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao retificar solicitação."
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
