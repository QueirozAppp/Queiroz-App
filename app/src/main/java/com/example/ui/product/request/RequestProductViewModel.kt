package com.example.ui.product.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.ProductRegistrationRepository
import com.example.core.model.ProductRegistrationRequest
import com.example.core.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RequestProductViewModel(
    private val repository: ProductRegistrationRepository = ProductRegistrationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestProductUiState())
    val uiState: StateFlow<RequestProductUiState> = _uiState.asStateFlow()

    fun observeMyRequests(userProfile: UserProfile) {
        viewModelScope.launch {
            repository.getRequestsFlow(userProfile).collect { list ->
                _uiState.update { it.copy(myRequests = list) }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(nameInput = name, errorMessage = null) }
    }

    fun onBarcodeChanged(barcode: String) {
        _uiState.update { it.copy(barcodeInput = barcode, errorMessage = null) }
    }

    fun onPriceChanged(price: String) {
        _uiState.update { it.copy(priceInput = price, errorMessage = null) }
    }

    fun onUnitChanged(unit: String) {
        _uiState.update { it.copy(selectedUnit = unit, errorMessage = null) }
    }

    fun onPhotoCaptured(base64: String) {
        _uiState.update { it.copy(photoBase64 = base64, errorMessage = null) }
    }

    fun onTabChanged(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex, errorMessage = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun submitRequest(userProfile: UserProfile) {
        val state = _uiState.value

        if (state.photoBase64.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Tire uma foto do produto para solicitar o cadastro.") }
            return
        }

        if (state.nameInput.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe a descrição ou nome do produto.") }
            return
        }

        val parsedPrice = state.priceInput.replace(",", ".").toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val request = ProductRegistrationRequest(
                barcode = state.barcodeInput.trim(),
                name = state.nameInput.trim(),
                price = parsedPrice,
                unit = state.selectedUnit,
                photoBase64 = state.photoBase64,
                storeId = userProfile.storeId,
                sectorId = userProfile.sectorId.ifBlank { "GERAL" },
                requestedByUid = userProfile.uid,
                requestedByName = userProfile.name.ifBlank { userProfile.email }
            )

            val result = repository.submitRequest(request)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            nameInput = "",
                            barcodeInput = "",
                            priceInput = "",
                            photoBase64 = "",
                            successMessage = "Solicitação enviada com sucesso! O Avarista ou Administrador irá analisar e aprovar o produto.",
                            activeTab = 1 // Switch to My Requests tab to see status
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao submeter solicitação de produto."
                        )
                    }
                }
            )
        }
    }
}
