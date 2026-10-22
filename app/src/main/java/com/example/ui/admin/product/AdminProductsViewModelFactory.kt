package com.example.ui.admin.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.core.data.ProductRepository

/**
 * Factory personalizada para instanciar o AdminProductsViewModel.
 */
class AdminProductsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminProductsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminProductsViewModel(ProductRepository()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
