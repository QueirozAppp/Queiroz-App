package com.example.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.ProductRepository
import com.example.core.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar o fluxo do Scanner de Código de Barras e consulta ao Catálogo Global (Etapa 6A).
 */
class ScannerViewModel(
    private val productRepository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
    }

    /**
     * Processa a leitura do código de barras de forma única, pausando o scanner,
     * consultando o Firestore e atualizando o estado de acordo com o resultado.
     */
    fun onBarcodeScanned(barcode: String) {
        val currentState = _uiState.value
        if (currentState.isPaused || currentState.isLoading || barcode.isBlank()) return

        val trimmedBarcode = barcode.trim()
        _uiState.update {
            it.copy(
                isPaused = true,
                isLoading = true,
                scannedBarcode = trimmedBarcode,
                scannedProduct = null,
                productNotFound = false,
                productInactive = false,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = productRepository.getProductByBarcode(trimmedBarcode)
            result.fold(
                onSuccess = { product ->
                    if (product == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                productNotFound = true
                            )
                        }
                    } else if (!product.active) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                productInactive = true,
                                scannedProduct = product
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                scannedProduct = product
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Erro ao consultar o produto no catálogo."
                        )
                    }
                }
            )
        }
    }

    /**
     * Reseta o estado do scanner para permitir uma nova leitura.
     */
    fun tryAgain() {
        _uiState.update {
            ScannerUiState(
                permissionGranted = it.permissionGranted,
                isLoading = false,
                scannedBarcode = null,
                scannedProduct = null,
                productNotFound = false,
                productInactive = false,
                errorMessage = null,
                isPaused = false
            )
        }
    }
}
