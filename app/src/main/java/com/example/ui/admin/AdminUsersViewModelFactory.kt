package com.example.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.core.data.AdminUserRepository

/**
 * Factory personalizada para instanciar o AdminUsersViewModel injetando o AdminUserRepository com Context.
 */
class AdminUsersViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminUsersViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminUsersViewModel(AdminUserRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
