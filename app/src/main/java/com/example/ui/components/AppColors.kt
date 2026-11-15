package com.example.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Paleta corporativa e tokens semânticos do Design System Premium (Etapa 20).
 * Baseada na identidade de Azul-Marinho Profundo, Glassmorphism e gradientes tecnológicos.
 */
object AppColors {
    var isDark by mutableStateOf(true)

    // Gradientes Principais
    val GradientNavyBackground: Brush
        get() = if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0B132B),
                    Color(0xFF0F172A),
                    Color(0xFF1C2541)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF1F5F9),
                    Color(0xFFE2E8F0),
                    Color(0xFFCBD5E1)
                )
            )
        }

    val GradientGlassCard: Brush
        get() = if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0x38334155),
                    Color(0x1F1E293B)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xF0FFFFFF),
                    Color(0xE0F1F5F9)
                )
            )
        }

    val GradientHeroPrimary = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF0284C7),
            Color(0xFF2563EB)
        )
    )

    val GradientHeroAccent = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF38BDF8),
            Color(0xFF6366F1)
        )
    )

    // Cores Institucionais
    val Primary: Color
        get() = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    val PrimaryHover: Color
        get() = if (isDark) Color(0xFF0284C7) else Color(0xFF0369A1)

    val PrimaryLight: Color
        get() = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0284C7)

    val PrimaryContainer: Color
        get() = if (isDark) Color(0xFF0284C7).copy(alpha = 0.30f) else Color(0xFFE0F2FE)

    val OnPrimaryContainer: Color
        get() = if (isDark) Color(0xFFE0F2FE) else Color(0xFF0369A1)

    val Secondary: Color
        get() = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)

    val SecondaryContainer: Color
        get() = if (isDark) Color(0xFF0F766E).copy(alpha = 0.35f) else Color(0xFFCCFBF1)

    val OnSecondaryContainer: Color
        get() = if (isDark) Color(0xFFCCFBF1) else Color(0xFF115E59)

    // Superfícies Neutras
    val Background: Color
        get() = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)

    val Surface: Color
        get() = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)

    val SurfaceGlass: Color
        get() = if (isDark) Color(0x331E293B) else Color(0xEEFFFFFF)

    val SurfaceElevated: Color
        get() = if (isDark) Color(0xFF243248) else Color(0xFFF8FAFC)

    val SurfaceVariant: Color
        get() = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    val BorderSubtle: Color
        get() = if (isDark) Color(0x33FFFFFF) else Color(0xFFCBD5E1)

    val BorderStrong: Color
        get() = if (isDark) Color(0x59FFFFFF) else Color(0xFF94A3B8)

    // Tipografia Neutra
    val TextPrimary: Color
        get() = if (isDark) Color(0xFFF8FAFC) else Color(0xFF000000)

    val TextSecondary: Color
        get() = if (isDark) Color(0xFF94A3B8) else Color(0xFF111827)

    val TextTertiary: Color
        get() = if (isDark) Color(0xFF64748B) else Color(0xFF1F2937)

    val TextDisabled: Color
        get() = if (isDark) Color(0xFF475569) else Color(0xFF4B5563)

    // Estados Semânticos de Baixa
    val StatusPending: Color
        get() = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)

    val StatusPendingBg: Color
        get() = if (isDark) Color(0xFF78350F).copy(alpha = 0.45f) else Color(0xFFFEF3C7)

    val StatusPendingText: Color
        get() = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)

    val StatusInConference: Color
        get() = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    val StatusInConferenceBg: Color
        get() = if (isDark) Color(0xFF1E40AF).copy(alpha = 0.45f) else Color(0xFFDBEAFE)

    val StatusInConferenceText: Color
        get() = if (isDark) Color(0xFFDBEAFE) else Color(0xFF1E40AF)

    val StatusCompleted: Color
        get() = if (isDark) Color(0xFF34D399) else Color(0xFF059669)

    val StatusCompletedBg: Color
        get() = if (isDark) Color(0xFF065F46).copy(alpha = 0.45f) else Color(0xFFD1FAE5)

    val StatusCompletedText: Color
        get() = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)

    val StatusDivergence: Color
        get() = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)

    val StatusDivergenceBg: Color
        get() = if (isDark) Color(0xFF991B1B).copy(alpha = 0.45f) else Color(0xFFFEE2E2)

    val StatusDivergenceText: Color
        get() = if (isDark) Color(0xFFFECACA) else Color(0xFF991B1B)

    val StatusNeutral: Color
        get() = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val StatusNeutralBg: Color
        get() = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    val StatusNeutralText: Color
        get() = if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
}
