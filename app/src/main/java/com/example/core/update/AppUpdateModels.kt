package com.example.core.update

/**
 * Constantes da versão instalada no aplicativo.
 * Sempre que publicar uma nova versão, aumente [APP_BUILD] (ex.: 12 -> 13)
 * e atualize o arquivo version.json publicado com o mesmo número.
 */
object AppUpdateConfig {
    const val APP_VERSION: String = "1.2.0"
    const val APP_BUILD: Int = 12

    const val VERSION_JSON_URL: String =
        "https://ais-dev-grvvjgjssysvkpoduefks4-708148426369.us-west2.run.app/version.json"

    const val FALLBACK_DOWNLOAD_URL: String =
        "https://github.com/QueirozAppp/Queiroz-App/releases/latest/download/app-debug.apk"

    const val AUTO_CHECK_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L // 6 horas
    const val REQUEST_TIMEOUT_MS: Int = 8_000 // 8 segundos
}

/**
 * Dados lidos do arquivo version.json público.
 */
data class RemoteVersionInfo(
    val version: String,
    val build: Int,
    val notes: String,
    val url: String
)

/**
 * Resultado detalhado da verificação de atualização.
 */
sealed class UpdateCheckOutcome {
    data class UpdateAvailable(val info: RemoteVersionInfo) : UpdateCheckOutcome()
    data class UpToDate(val info: RemoteVersionInfo) : UpdateCheckOutcome()
    data object Offline : UpdateCheckOutcome()
    data object Timeout : UpdateCheckOutcome()
    data class HttpNotFound(val url: String) : UpdateCheckOutcome()
    data class HttpError(val statusCode: Int) : UpdateCheckOutcome()
    data object InvalidJson : UpdateCheckOutcome()
    data object SkippedInterval : UpdateCheckOutcome()
}

/**
 * Estado da interface de atualização exibido na tela.
 */
data class AppUpdateUiState(
    val availableUpdate: RemoteVersionInfo? = null,
    val showBanner: Boolean = false,
    val isChecking: Boolean = false,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false
)
