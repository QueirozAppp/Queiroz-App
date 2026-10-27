package com.example.ui.product.approval

import com.example.core.model.ProductRegistrationRequest

data class ProductApprovalUiState(
    val requests: List<ProductRegistrationRequest> = emptyList(),
    val filterStatus: String = "PENDING",
    val rejectingRequest: ProductRegistrationRequest? = null,
    val rejectionReasonInput: String = "",
    val approvingRequest: ProductRegistrationRequest? = null,
    val adjustedBarcode: String = "",
    val adjustedName: String = "",
    val adjustedUnit: String = "UN",
    val adjustedPrice: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val pendingCount: Int = 0
)
