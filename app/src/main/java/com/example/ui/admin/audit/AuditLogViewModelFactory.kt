package com.example.ui.admin.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.core.data.AuditRepository
import com.example.core.data.StoreSectorRepository

/**
 * Factory para instanciar o AuditLogViewModel.
 */
class AuditLogViewModelFactory(
    private val auditRepository: AuditRepository = AuditRepository(),
    private val storeSectorRepository: StoreSectorRepository = StoreSectorRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuditLogViewModel::class.java)) {
            return AuditLogViewModel(auditRepository, storeSectorRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
