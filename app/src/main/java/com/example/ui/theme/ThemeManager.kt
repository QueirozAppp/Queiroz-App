package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.components.AppColors

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

/**
 * Gerenciador de tema da aplicação Queiroz Avaria.
 * Permite alternar entre Tema Claro, Escuro e seguir o padrão do Sistema.
 */
object ThemeManager {
    var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
        private set

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("queiroz_avaria_theme_prefs", Context.MODE_PRIVATE)
        val modeStr = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val loadedMode = try {
            AppThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
        themeMode = loadedMode
        when (loadedMode) {
            AppThemeMode.LIGHT -> AppColors.isDark = false
            AppThemeMode.DARK -> AppColors.isDark = true
            AppThemeMode.SYSTEM -> {
                val isNight = (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                AppColors.isDark = isNight
            }
        }
    }

    @Composable
    fun isDark(): Boolean {
        return when (themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    }

    fun setTheme(mode: AppThemeMode, context: Context? = null) {
        themeMode = mode
        when (mode) {
            AppThemeMode.LIGHT -> AppColors.isDark = false
            AppThemeMode.DARK -> AppColors.isDark = true
            AppThemeMode.SYSTEM -> {
                context?.let { ctx ->
                    val isNight = (ctx.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    AppColors.isDark = isNight
                }
            }
        }
        context?.let {
            it.getSharedPreferences("queiroz_avaria_theme_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("theme_mode", mode.name)
                .apply()
        }
    }

    fun toggleTheme(isCurrentlyDark: Boolean, context: Context? = null) {
        val newMode = if (isCurrentlyDark) AppThemeMode.LIGHT else AppThemeMode.DARK
        setTheme(newMode, context)
    }
}
