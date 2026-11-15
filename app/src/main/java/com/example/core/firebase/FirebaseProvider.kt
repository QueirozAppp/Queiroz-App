package com.example.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import com.example.BuildConfig

/**
 * Ponto de acesso seguro aos serviços do Firebase (Auth, Firestore, Storage).
 *
 * Garante que a presença de `google-services.json` seja reconhecida e inicializada
 * tanto em runtime quanto em testes unitários (Robolectric).
 */
object FirebaseProvider {

    private fun ensureInitialized(context: Context): Boolean {
        return try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            android.util.Log.e("FirebaseProvider", "FirebaseApp.getInstance() failed: ${e.message}")
            try {
                val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
                    FirebaseApp.getApps(context).firstOrNull()
                } else {
                    try {
                        FirebaseApp.initializeApp(context)
                    } catch (_: Exception) {
                        val options = com.google.firebase.FirebaseOptions.Builder()
                            .setProjectId("appqueirozavaria")
                            .setApplicationId("1:196784493285:android:8d0481125195bbb98bba0c")
                            .setApiKey("AIzaSyCUMuqPKbD7MC1bzMOnPYRuOeJ9JwdTjCU")
                            .setStorageBucket("appqueirozavaria.firebasestorage.app")
                            .setGcmSenderId("196784493285")
                            .build()
                        com.google.firebase.FirebaseApp.initializeApp(context, options)
                    }
                }
                if (app != null && BuildConfig.DEBUG) {
                    try {
                        com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                        )
                    } catch (_: Exception) {
                    }
                }
                app != null
            } catch (ex: Exception) {
                android.util.Log.e("FirebaseProvider", "Firebase initialization failed: ${ex.message}")
                false
            }
        }
    }

    /**
     * Retorna informações de diagnóstico seguras (sem expor credenciais completas) para exibição na UI.
     */
    fun getDiagnosticInfo(context: Context): FirebaseDiagnosticInfo {
        val isInit = ensureInitialized(context)
        var projId = "appqueirozavaria"
        var appId = "com.aistudio.controledebaixas.ktfpxz"
        var bucket = "appqueirozavaria.firebasestorage.app"
        var maskedKey = "N/A"

        if (isInit) {
            try {
                val app = FirebaseApp.getInstance()
                projId = app.options.projectId ?: projId
                appId = app.options.applicationId ?: appId
                bucket = app.options.storageBucket ?: bucket
                val rawKey = app.options.apiKey ?: ""
                if (rawKey.length > 10) {
                    maskedKey = "${rawKey.take(8)}...${rawKey.takeLast(3)}"
                }
            } catch (_: Exception) {}
        }

        val authReady = try {
            getAuthOrNull() != null
        } catch (_: Exception) {
            false
        }

        return FirebaseDiagnosticInfo(
            appInitialized = isInit,
            projectId = projId,
            applicationId = appId,
            storageBucket = bucket,
            authInstanceCreated = authReady,
            maskedApiKey = maskedKey
        )
    }

    /**
     * Retorna diagnóstico detalhado para telas técnicas e testes.
     */
    fun getDiagnostics(context: Context): FirebaseServicesDiagnostic {
        val initialized = ensureInitialized(context)

        val initResult = if (initialized) {
            ServiceCheckResult.Ready(
                "FirebaseApp inicializado com sucesso (Projeto: appqueirozavaria, AppId: com.aistudio.controledebaixas.ktfpxz)."
            )
        } else {
            ServiceCheckResult.Pending(
                "Aguardando arquivo app/google-services.json com credenciais do console Firebase."
            )
        }

        val authResult = if (initialized) {
            try {
                FirebaseAuth.getInstance()
                ServiceCheckResult.Ready("FirebaseAuth inicializado com sucesso.")
            } catch (e: Exception) {
                ServiceCheckResult.Error("Falha ao instanciar FirebaseAuth: ${e.message}")
            }
        } else {
            ServiceCheckResult.Configured(
                "SDK incluído no Gradle (firebase-auth). Inicialização bloqueada pela ausência do google-services.json."
            )
        }

        val firestoreResult = if (initialized) {
            try {
                FirebaseFirestore.getInstance()
                ServiceCheckResult.Ready("FirebaseFirestore (Database) inicializado com sucesso.")
            } catch (e: Exception) {
                ServiceCheckResult.Error("Falha ao instanciar FirebaseFirestore: ${e.message}")
            }
        } else {
            ServiceCheckResult.Configured(
                "SDK incluído no Gradle (firebase-firestore). Inicialização bloqueada pela ausência do google-services.json."
            )
        }

        val storageResult = if (initialized) {
            try {
                FirebaseStorage.getInstance()
                ServiceCheckResult.Ready("FirebaseStorage inicializado com sucesso.")
            } catch (e: Exception) {
                ServiceCheckResult.Error("Falha ao instanciar FirebaseStorage: ${e.message}")
            }
        } else {
            ServiceCheckResult.Configured(
                "SDK incluído no Gradle (firebase-storage). Inicialização bloqueada pela ausência do google-services.json."
            )
        }

        return FirebaseServicesDiagnostic(
            initialization = initResult,
            authentication = authResult,
            firestore = firestoreResult,
            storage = storageResult
        )
    }

    /**
     * Retorna um status sumarizado de inicialização.
     */
    fun getStatus(context: Context): FirebaseStatus {
        val info = getDiagnosticInfo(context)
        return if (info.appInitialized) {
            FirebaseStatus.Ready(
                applicationId = info.applicationId,
                projectId = info.projectId
            )
        } else {
            FirebaseStatus.PendingConfiguration(
                message = "Configuração do Firebase pendente: google-services.json não encontrado."
            )
        }
    }

    /**
     * Retorna a instância do FirebaseAuth caso o FirebaseApp esteja inicializado.
     */
    fun getAuthOrNull(): FirebaseAuth? {
        return try {
            FirebaseApp.getInstance()
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retorna a instância do FirebaseFirestore caso o FirebaseApp esteja inicializado.
     */
    fun getFirestoreOrNull(): FirebaseFirestore? {
        return try {
            val app = FirebaseApp.getInstance()
            android.util.Log.d("FirebaseProvider", "Firestore requested for project: ${app.options.projectId}")
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retorna a instância do FirebaseStorage caso o FirebaseApp esteja inicializado.
     */
    fun getStorageOrNull(): FirebaseStorage? {
        return try {
            FirebaseApp.getInstance()
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Modelo seguro de diagnósticos do Firebase para exibição transparente na UI.
 */
data class FirebaseDiagnosticInfo(
    val appInitialized: Boolean,
    val projectId: String,
    val applicationId: String,
    val storageBucket: String,
    val authInstanceCreated: Boolean,
    val maskedApiKey: String
)
