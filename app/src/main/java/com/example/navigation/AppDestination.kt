package com.example.navigation

/**
 * Destinos de navegação do aplicativo.
 *
 * Nesta etapa inicial, apenas a tela de fundação técnica está ativa.
 * Destinos futuros (Autenticação, Painel do Avarista, Baixas, etc.) serão adicionados
 * conforme forem implementados nas etapas seguintes.
 */
sealed class AppDestination(val route: String) {
    data object Foundation : AppDestination("foundation")
}
