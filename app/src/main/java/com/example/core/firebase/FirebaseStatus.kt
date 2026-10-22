package com.example.core.firebase

/**
 * Representa o estado global de inicialização do Firebase no ambiente do aplicativo.
 *
 * Utilizado para garantir que o aplicativo informe honestamente o status
 * dos serviços sem simulações falsas de conectividade.
 */
sealed interface FirebaseStatus {
    /**
     * FirebaseApp foi inicializado no processo com configurações válidas do google-services.json.
     */
    data class Ready(
        val applicationId: String,
        val projectId: String
    ) : FirebaseStatus

    /**
     * Firebase ainda não foi inicializado em tempo de execução porque o arquivo `google-services.json`
     * ainda não foi adicionado ao módulo `app/` pelo responsável técnico.
     */
    data class PendingConfiguration(
        val message: String
    ) : FirebaseStatus

    /**
     * Ocorreu uma exceção ou erro inesperado durante a tentativa de inicialização.
     */
    data class Error(
        val message: String
    ) : FirebaseStatus
}

/**
 * Representa o status individual de cada serviço do Firebase avaliado na Etapa 2A.
 */
sealed interface ServiceCheckResult {
    data class Ready(val details: String) : ServiceCheckResult
    data class Configured(val details: String) : ServiceCheckResult
    data class Pending(val reason: String) : ServiceCheckResult
    data class Error(val error: String) : ServiceCheckResult
}

/**
 * Diagnóstico completo dos 4 serviços principais avaliados na Etapa 2A.
 */
data class FirebaseServicesDiagnostic(
    val initialization: ServiceCheckResult,
    val authentication: ServiceCheckResult,
    val firestore: ServiceCheckResult,
    val storage: ServiceCheckResult
)
