package com.example.ui.scanner

import com.example.core.model.Product

/**
 * Estado da interface para o Scanner de Código de Barras e Consulta (Etapa 6A).
 */
data class ScannerUiState(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = false,
    val scannedBarcode: String? = null,
    val scannedProduct: Product? = null,
    val productNotFound: Boolean = false,
    val productInactive: Boolean = false,
    val errorMessage: String? = null,
    val isPaused: Boolean = false
)
