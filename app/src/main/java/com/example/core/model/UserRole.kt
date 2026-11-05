package com.example.core.model

/**
 * Níveis de acesso previstos para a rede de supermercados:
 * 1. Administrador da Rede — Diretoria
 * 2. Avarista — Responsável operacional de uma loja
 * 3. Líder de Setor
 *
 * Definidos na fundação para padronizar o vocabulário de domínio e regras futuras.
 */
enum class UserRole(val label: String, val description: String) {
    ADMIN(
        label = "Admin",
        description = "Diretoria e gestão global da rede de supermercados"
    ),
    AVARISTA(
        label = "Avarista",
        description = "Responsável operacional pelo controle de avarias e baixas da loja"
    ),
    SETOR_LEADER(
        label = "Líder de Setor",
        description = "Responsável pelas requisições e baixas dos setores internos"
    )
}
