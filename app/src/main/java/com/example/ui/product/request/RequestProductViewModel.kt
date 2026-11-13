package com.example.ui.product.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.ProductRegistrationRepository
import com.example.core.data.ProductRepository
import com.example.core.model.ProductRegistrationRequest
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RequestProductViewModel(
    private val repository: ProductRegistrationRepository = ProductRegistrationRepository(),
    private val productRepository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestProductUiState())
    val uiState: StateFlow<RequestProductUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun observeMyRequests(userProfile: UserProfile) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.getRequestsFlow(userProfile).collect { list ->
                _uiState.update { it.copy(myRequests = list) }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(nameInput = name, errorMessage = null, successMessage = null) }
    }

    fun onBarcodeChanged(barcode: String) {
        _uiState.update { it.copy(barcodeInput = barcode, errorMessage = null, successMessage = null) }
    }

    fun onPriceChanged(price: String) {
        _uiState.update { it.copy(priceInput = price, errorMessage = null, successMessage = null) }
    }

    fun onUnitChanged(unit: String) {
        _uiState.update { it.copy(selectedUnit = unit, errorMessage = null, successMessage = null) }
    }

    fun onPhotoCaptured(base64: String) {
        _uiState.update { it.copy(photoBase64 = base64, errorMessage = null, successMessage = null) }
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
            _uiState.update { it.copy(errorMessage = "Tire ou selecione uma foto do produto para continuar.") }
            return
        }

        val trimmedName = state.nameInput.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe a descrição ou nome do produto.") }
            return
        }

        val trimmedBarcode = state.barcodeInput.trim()
        val trimmedPrice = state.priceInput.trim()
        val parsedPrice = if (trimmedPrice.isBlank()) {
            0.0
        } else {
            val parsed = trimmedPrice.replace(",", ".").toDoubleOrNull()
            if (parsed == null || parsed < 0.0) {
                _uiState.update { it.copy(errorMessage = "Informe um preço válido (ex: 12,90) ou deixe em branco.") }
                return
            }
            parsed
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            // Verificar se já existe um produto ativo com o mesmo código de barras no catálogo global
            if (trimmedBarcode.isNotBlank()) {
                val existing = productRepository.getProductByBarcode(trimmedBarcode).getOrNull()
                if (existing != null && existing.active) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Este código de barras já está cadastrado: '${existing.name}' (${existing.unit})."
                        )
                    }
                    return@launch
                }
            }

            val isAuthorizedForDirectCreation =
                userProfile.role == UserRole.ADMIN || userProfile.role == UserRole.AVARISTA

            if (isAuthorizedForDirectCreation) {
                val result = productRepository.createProduct(
                    barcode = trimmedBarcode,
                    name = trimmedName,
                    price = parsedPrice,
                    unit = state.selectedUnit,
                    photoBase64 = state.photoBase64,
                    userProfile = userProfile
                )
                result.fold(
                    onSuccess = { createdId ->
                        repository.recordDirectRegistration(
                            productId = createdId,
                            barcode = trimmedBarcode,
                            name = trimmedName,
                            price = parsedPrice,
                            unit = state.selectedUnit,
                            photoBase64 = state.photoBase64,
                            userProfile = userProfile
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                nameInput = "",
                                barcodeInput = "",
                                priceInput = "",
                                photoBase64 = "",
                                successMessage = "Produto '$trimmedName' cadastrado no catálogo global com sucesso!",
                                activeTab = 1
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.localizedMessage ?: "Erro ao cadastrar produto diretamente."
                            )
                        }
                    }
                )
            } else {
                val request = ProductRegistrationRequest(
                    barcode = trimmedBarcode,
                    name = trimmedName,
                    price = parsedPrice,
                    unit = state.selectedUnit,
                    photoBase64 = state.photoBase64,
                    storeId = userProfile.storeId,
                    sectorId = userProfile.sectorId.ifBlank { "Geral" },
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
                                successMessage = "Solicitação de '$trimmedName' enviada! O Avarista ou Administrador irá aprovar para o catálogo geral.",
                                activeTab = 1
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.localizedMessage ?: "Erro ao enviar solicitação de produto."
                            )
                        }
                    }
                )
            }
        }
    }
}
