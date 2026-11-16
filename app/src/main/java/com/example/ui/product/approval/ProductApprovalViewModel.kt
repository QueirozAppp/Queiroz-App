package com.example.ui.product.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.ProductRegistrationRepository
import com.example.core.model.ProductRegistrationRequest
import com.example.core.model.ProductRequestStatus
import com.example.core.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductApprovalViewModel(
    private val repository: ProductRegistrationRepository = ProductRegistrationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductApprovalUiState())
    val uiState: StateFlow<ProductApprovalUiState> = _uiState.asStateFlow()

    private var allRequests: List<ProductRegistrationRequest> = emptyList()
    private var loadJob: Job? = null

    fun loadRequests(userProfile: UserProfile) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            launch {
                val fresh = repository.refreshRequests(userProfile)
                updateRequestsSnapshot(fresh)
            }
            repository.getRequestsFlow(userProfile).collect { list ->
                updateRequestsSnapshot(list)
            }
        }
    }

    fun refreshRequests(userProfile: UserProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val fresh = repository.refreshRequests(userProfile)
            updateRequestsSnapshot(fresh)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun updateRequestsSnapshot(list: List<ProductRegistrationRequest>) {
        allRequests = list
        val pending = list.count { it.status == ProductRequestStatus.PENDING.name }
        _uiState.update { it.copy(pendingCount = pending) }
        applyFilter(_uiState.value.filterStatus)
    }

    fun onFilterStatusChanged(status: String) {
        _uiState.update { it.copy(filterStatus = status) }
        applyFilter(status)
    }

    private fun applyFilter(status: String) {
        val filtered = if (status == "ALL") {
            allRequests
        } else {
            allRequests.filter { it.status == status }
        }
        _uiState.update { it.copy(requests = filtered) }
    }

    fun openApproveDialog(request: ProductRegistrationRequest) {
        _uiState.update {
            it.copy(
                approvingRequest = request,
                adjustedBarcode = request.barcode,
                adjustedName = request.name,
                adjustedUnit = request.unit,
                adjustedPrice = if (request.price > 0.0) String.format(java.util.Locale.getDefault(), "%.2f", request.price) else "",
                errorMessage = null
            )
        }
    }

    fun closeApproveDialog() {
        _uiState.update { it.copy(approvingRequest = null) }
    }

    fun onAdjustedBarcodeChanged(barcode: String) {
        _uiState.update { it.copy(adjustedBarcode = barcode) }
    }

    fun onAdjustedNameChanged(name: String) {
        _uiState.update { it.copy(adjustedName = name) }
    }

    fun onAdjustedUnitChanged(unit: String) {
        _uiState.update { it.copy(adjustedUnit = unit) }
    }

    fun onAdjustedPriceChanged(price: String) {
        _uiState.update { it.copy(adjustedPrice = price) }
    }

    fun confirmApprove(reviewerProfile: UserProfile) {
        val request = _uiState.value.approvingRequest ?: return
        val barcode = _uiState.value.adjustedBarcode.trim()
        val name = _uiState.value.adjustedName.trim()
        val unit = _uiState.value.adjustedUnit.trim()
        val price = _uiState.value.adjustedPrice.replace(",", ".").toDoubleOrNull() ?: 0.0

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "O nome do produto não pode ficar em branco.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.approveRequest(
                request = request,
                reviewerProfile = reviewerProfile,
                adjustedBarcode = barcode,
                adjustedName = name,
                adjustedUnit = unit,
                adjustedPrice = price
            )

            result.fold(
                onSuccess = {
                    val now = System.currentTimeMillis()
                    val reviewerName = reviewerProfile.name.ifEmpty { reviewerProfile.email }
                    val updatedList = allRequests.map { item ->
                        if (item.id == request.id) {
                            item.copy(
                                status = ProductRequestStatus.APPROVED.name,
                                barcode = barcode,
                                name = name,
                                unit = unit,
                                price = price,
                                reviewedByUid = reviewerProfile.uid,
                                reviewedByName = reviewerName,
                                reviewedAt = now
                            )
                        } else item
                    }
                    updateRequestsSnapshot(updatedList)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            approvingRequest = null,
                            successMessage = "Produto '${name}' aprovado e adicionado ao catálogo global com sucesso!"
                        )
                    }
                    refreshRequests(reviewerProfile)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao aprovar produto."
                        )
                    }
                }
            )
        }
    }

    fun openRejectDialog(request: ProductRegistrationRequest) {
        _uiState.update {
            it.copy(
                rejectingRequest = request,
                rejectionReasonInput = "",
                errorMessage = null
            )
        }
    }

    fun closeRejectDialog() {
        _uiState.update { it.copy(rejectingRequest = null, rejectionReasonInput = "") }
    }

    fun onRejectionReasonChanged(reason: String) {
        _uiState.update { it.copy(rejectionReasonInput = reason) }
    }

    fun confirmReject(reviewerProfile: UserProfile) {
        val request = _uiState.value.rejectingRequest ?: return
        val reason = _uiState.value.rejectionReasonInput.trim()

        if (reason.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe o motivo da recusa do produto.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.rejectRequest(
                requestId = request.id,
                rejectionReason = reason,
                reviewerProfile = reviewerProfile
            )

            result.fold(
                onSuccess = {
                    val now = System.currentTimeMillis()
                    val reviewerName = reviewerProfile.name.ifEmpty { reviewerProfile.email }
                    val updatedList = allRequests.map { item ->
                        if (item.id == request.id) {
                            item.copy(
                                status = ProductRequestStatus.REJECTED.name,
                                rejectionReason = reason,
                                reviewedByUid = reviewerProfile.uid,
                                reviewedByName = reviewerName,
                                reviewedAt = now
                            )
                        } else item
                    }
                    updateRequestsSnapshot(updatedList)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rejectingRequest = null,
                            rejectionReasonInput = "",
                            successMessage = "Solicitação de cadastro recusada."
                        )
                    }
                    refreshRequests(reviewerProfile)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao recusar solicitação."
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
