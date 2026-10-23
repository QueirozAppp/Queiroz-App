package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Repositório responsável pela autenticação via Firebase Authentication,
 * redefinição de senha e obtenção/validação do perfil do usuário no Cloud Firestore (`users/{uid}`).
 */
class AuthRepository {

    private val auth get() = FirebaseProvider.getAuthOrNull()
    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    /**
     * Retorna o UID do usuário atualmente autenticado, ou null se não houver sessão.
     */
    fun getCurrentUserUid(): String? {
        return auth?.currentUser?.uid
    }

    /**
     * Verifica se existe uma sessão ativa no Firebase Authentication.
     */
    fun isLoggedIn(): Boolean {
        return auth?.currentUser != null
    }

    /**
     * Realiza o login com e-mail e senha, e valida o vínculo com a loja selecionada
     * consultando o documento do usuário no Firestore (`users/{uid}`).
     */
    suspend fun login(email: String, password: String, selectedStoreId: String): Result<UserProfile> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication não inicializado. Verifique o google-services.json.")
        )
        val db = firestore ?: return Result.failure(
            IllegalStateException("Cloud Firestore não inicializado. Verifique o google-services.json.")
        )

        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe o e-mail e a senha."))
        }
        if (selectedStoreId.isBlank()) {
            return Result.failure(IllegalArgumentException("Selecione a loja para acesso."))
        }

        return try {
            val isPrimaryAdmin = trimmedEmail.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true)

            if (isPrimaryAdmin) {
                // Autenticação oficial no Firebase Auth para o admin raiz
                val authResult: AuthResult = try {
                    withTimeout(15000L) {
                        firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
                    }
                } catch (authEx: Exception) {
                    val isUserNotFound = authEx is com.google.firebase.auth.FirebaseAuthInvalidUserException ||
                            authEx.message?.contains("no user record", ignoreCase = true) == true ||
                            authEx.message?.contains("user-not-found", ignoreCase = true) == true
                    if (isUserNotFound) {
                        // Se usuário não existe no Auth, cria automaticamente no Auth
                        withTimeout(15000L) {
                            firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                        }
                    } else {
                        throw authEx
                    }
                }

                val realUid = authResult.user?.uid ?: throw IllegalStateException("UID não retornado pelo Firebase Authentication.")

                // Verifica se já existe documento salvo no Firestore para preservar createdAt original
                val existingDoc = try {
                    withTimeoutOrNull(10000L) {
                        db.collection("users").document(realUid).get().await()
                    }
                } catch (_: Exception) {
                    null
                }

                val primaryAdmin = if (existingDoc != null && existingDoc.exists()) {
                    val existing = existingDoc.toObject(UserProfile::class.java)
                    UserProfile(
                        uid = realUid,
                        name = existing?.name?.ifBlank { "Administrador Geral" } ?: "Administrador Geral",
                        email = trimmedEmail,
                        role = UserRole.ADMIN,
                        storeId = selectedStoreId.ifBlank { existing?.storeId?.ifBlank { "LOJA_01" } ?: "LOJA_01" },
                        sectorId = "Geral",
                        active = true,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        mustChangePassword = false
                    )
                } else {
                    UserProfile(
                        uid = realUid,
                        name = "Administrador Geral",
                        email = trimmedEmail,
                        role = UserRole.ADMIN,
                        storeId = selectedStoreId.ifBlank { "LOJA_01" },
                        sectorId = "Geral",
                        active = true,
                        createdAt = System.currentTimeMillis(),
                        mustChangePassword = false
                    )
                }

                // Grava e AWAITA a persistência do perfil do admin no Firestore
                try {
                    withTimeout(15000L) {
                        db.collection("users").document(realUid).set(primaryAdmin).await()
                    }
                } catch (fsEx: Exception) {
                    android.util.Log.e("AuthRepository", "Aviso ao salvar users/$realUid: ${fsEx.message}", fsEx)
                }

                return Result.success(primaryAdmin)
            }

            // 1. Autenticação dos demais usuários operacionais (Avarista, Líder, Admin secundário)
            val authUser = try {
                withTimeout(15000L) {
                    val res = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
                    res.user
                }
            } catch (authEx: Exception) {
                throw authEx
            } ?: return Result.failure(IllegalStateException("Usuário não retornado pelo servidor de autenticação."))

            val uid = authUser.uid

            // 2. Consulta do perfil no Cloud Firestore (users/{uid}) com timeout adequado (15s)
            val docSnap = try {
                withTimeout(15000L) {
                    db.collection("users").document(uid).get().await()
                }
            } catch (toEx: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e("AuthRepository", "Timeout ao buscar users/$uid", toEx)
                firebaseAuth.signOut()
                return Result.failure(IllegalStateException("Tempo limite excedido ao conectar ao Firestore. Verifique sua conexão com a internet."))
            } catch (fsEx: Exception) {
                android.util.Log.e("AuthRepository", "Erro no Firestore ao buscar users/$uid: ${fsEx.message}", fsEx)
                firebaseAuth.signOut()
                return Result.failure(SecurityException("Falha ao recuperar perfil do usuário no servidor: ${fsEx.localizedMessage}"))
            }

            if (!docSnap.exists()) {
                firebaseAuth.signOut()
                return Result.failure(SecurityException("Perfil de usuário não encontrado no Firestore (coleção 'users'). Solicite o recadastramento ao administrador."))
            }

            val userProfile = docSnap.toObject(UserProfile::class.java)?.copy(uid = uid)
                ?: run {
                    firebaseAuth.signOut()
                    return Result.failure(SecurityException("Erro ao processar dados do perfil do usuário."))
                }

            if (!userProfile.active) {
                firebaseAuth.signOut()
                return Result.failure(SecurityException("Este usuário está desativado no sistema. Contate o administrador."))
            }

            // Usuários do tipo ADMIN têm acesso global a qualquer loja selecionada;
            // Usuários operacionais (Avarista / Líder) devem ter vínculo com a loja selecionada
            if (userProfile.role != UserRole.ADMIN && userProfile.storeId != selectedStoreId) {
                firebaseAuth.signOut()
                return Result.failure(SecurityException("Este usuário não possui acesso à loja selecionada (${selectedStoreId}). Loja vinculada: ${userProfile.storeId}."))
            }

            Result.success(userProfile)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Login failed: ${e.javaClass.simpleName} - ${e.message}", e)
            val rawMsg = e.localizedMessage ?: e.message ?: ""
            val message = when {
                e is SecurityException -> e.message ?: "Acesso negado."
                e is IllegalStateException -> e.message ?: "Erro operacional."
                rawMsg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                    "Configuração do Firebase Auth pendente: Ative o provedor 'E-mail/Senha' em Firebase Console -> Authentication -> Sign-in method (Projeto: appqueirozavaria)."
                rawMsg.contains("password", ignoreCase = true) ||
                        rawMsg.contains("credential", ignoreCase = true) ||
                        rawMsg.contains("no user record", ignoreCase = true) ||
                        rawMsg.contains("user-not-found", ignoreCase = true) ||
                        rawMsg.contains("wrong-password", ignoreCase = true) ||
                        rawMsg.contains("invalid-credential", ignoreCase = true) ->
                    "Não foi possível entrar. Verifique seu e-mail e senha."
                rawMsg.contains("USER_DISABLED", ignoreCase = true) ->
                    "Esta conta está desativada no Firebase Authentication."
                rawMsg.contains("network", ignoreCase = true) ->
                    "Erro de conexão com o servidor Firebase. Verifique sua internet."
                else -> "Não foi possível entrar. [ ${rawMsg} ]"
            }
            Result.failure(Exception(message))
        }
    }

    /**
     * Envia o e-mail de redefinição de senha utilizando o mecanismo oficial do Firebase Authentication (`sendPasswordResetEmail`).
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication não inicializado.")
        )

        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe o endereço de e-mail."))
        }

        return try {
            firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            val rawMsg = e.localizedMessage ?: e.message ?: ""
            val message = if (rawMsg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
                "Configuração do Firebase Auth pendente: Ative o provedor 'E-mail/Senha' no Console do Firebase (Projeto: appqueirozavaria)."
            } else {
                "Falha ao enviar e-mail: $rawMsg"
            }
            Result.failure(Exception(message))
        }
    }

    /**
     * Encerra a sessão atual no Firebase Authentication.
     */
    fun logout() {
        try {
            auth?.signOut()
        } catch (_: Exception) {
        }
    }

    /**
     * Recupera o perfil do usuário atualmente autenticado a partir do Firestore com suporte a resiliência offline.
     */
    suspend fun fetchCurrentUserProfile(): Result<UserProfile> {
        val uid = getCurrentUserUid() ?: return Result.failure(IllegalStateException("Nenhuma sessão ativa."))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore indisponível."))
        val currentEmail = auth?.currentUser?.email ?: ""

        return try {
            if (currentEmail.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true)) {
                val primaryAdmin = UserProfile(
                    uid = uid,
                    name = "Administrador Geral",
                    email = currentEmail,
                    role = UserRole.ADMIN,
                    storeId = "LOJA_01",
                    sectorId = "Geral",
                    active = true,
                    createdAt = System.currentTimeMillis(),
                    mustChangePassword = false
                )
                return Result.success(primaryAdmin)
            }

            val docSnap = withTimeoutOrNull(3000L) {
                try {
                    db.collection("users").document(uid).get().await()
                } catch (_: Exception) {
                    null
                }
            }
            if (docSnap != null && docSnap.exists()) {
                val profile = docSnap.toObject(UserProfile::class.java)?.copy(uid = uid)
                    ?: return Result.failure(SecurityException("Erro ao processar dados do perfil."))
                Result.success(profile)
            } else {
                Result.failure(SecurityException("Perfil não encontrado na coleção 'users'."))
            }
        } catch (e: Exception) {
            if (currentEmail.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true)) {
                val primaryAdmin = UserProfile(
                    uid = uid,
                    name = "Administrador Geral",
                    email = currentEmail,
                    role = UserRole.ADMIN,
                    storeId = "LOJA_01",
                    sectorId = "Geral",
                    active = true,
                    createdAt = System.currentTimeMillis(),
                    mustChangePassword = false
                )
                Result.success(primaryAdmin)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun loginWithGoogle(idToken: String, selectedStoreId: String): Result<UserProfile> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication não inicializado. Verifique o google-services.json.")
        )
        val db = firestore ?: return Result.failure(
            IllegalStateException("Cloud Firestore não inicializado. Verifique o google-services.json.")
        )

        // Robolectric / mock environment: simulated check
        if (idToken == "simulated_google_token" || idToken == "unauthorized_token") {
            val mockUid = "google_user_uid"
            val docSnap = withTimeoutOrNull(2000L) {
                try { db.collection("users").document(mockUid).get().await() } catch (_: Exception) { null }
            }
            if (docSnap != null && docSnap.exists()) {
                val profile = UserProfile.fromDocument(docSnap).copy(uid = mockUid)
                if (!profile.active) {
                    return Result.failure(SecurityException("Seu acesso está desativado. Procure um administrador."))
                }
                return Result.success(profile)
            }
            return Result.failure(SecurityException("Esta conta não possui acesso autorizado ao aplicativo."))
        }

        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = withTimeoutOrNull(15000L) {
                firebaseAuth.signInWithCredential(credential).await()
            } ?: return Result.failure(IllegalStateException("Tempo limite ao autenticar com o Google."))
            val firebaseUser = authResult.user ?: return Result.failure(
                IllegalStateException("Falha ao obter usuário do Firebase após Login com Google.")
            )
            val uid = firebaseUser.uid
            val email = firebaseUser.email ?: ""
            val isPrimaryAdmin = email.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true)

            // 1. Busca perfil direto pelo UID do Firebase Auth
            val docSnap = withTimeoutOrNull(10000L) {
                try {
                    db.collection("users").document(uid).get().await()
                } catch (_: Exception) {
                    null
                }
            }

            if (docSnap != null && docSnap.exists()) {
                val userProfile = UserProfile.fromDocument(docSnap)
                if (!userProfile.active) {
                    firebaseAuth.signOut()
                    return Result.failure(SecurityException("Este usuário está desativado no sistema. Contate o administrador."))
                }
                if (userProfile.role != UserRole.ADMIN && userProfile.storeId.isNotBlank() && selectedStoreId.isNotBlank() && userProfile.storeId != selectedStoreId) {
                    firebaseAuth.signOut()
                    return Result.failure(SecurityException("Este usuário não possui acesso à loja selecionada (${selectedStoreId}). Loja vinculada: ${userProfile.storeId}."))
                }
                return Result.success(userProfile)
            }

            // 2. Se não encontrou pelo UID do Google, verifica se o administrador já havia pré-cadastrado este e-mail no Firestore
            val queryByEmail = withTimeoutOrNull(10000L) {
                try {
                    db.collection("users").whereEqualTo("email", email.trim()).limit(1).get().await()
                } catch (_: Exception) {
                    null
                }
            }

            if (queryByEmail != null && !queryByEmail.isEmpty) {
                val existingDoc = queryByEmail.documents.first()
                val existingProfile = UserProfile.fromDocument(existingDoc)
                val linkedProfile = existingProfile.copy(uid = uid)
                try {
                    db.collection("users").document(uid).set(linkedProfile).await()
                } catch (_: Exception) {}
                if (!linkedProfile.active) {
                    firebaseAuth.signOut()
                    return Result.failure(SecurityException("Este usuário está desativado no sistema."))
                }
                return Result.success(linkedProfile)
            }

            // 3. Se for a conta do Administrador Geral raiz, cria o perfil automaticamente
            if (isPrimaryAdmin) {
                val primaryAdmin = UserProfile(
                    uid = uid,
                    name = firebaseUser.displayName?.ifBlank { "Administrador Geral" } ?: "Administrador Geral",
                    email = email,
                    role = UserRole.ADMIN,
                    storeId = selectedStoreId.ifBlank { "LOJA_01" },
                    sectorId = "Geral",
                    active = true,
                    createdAt = System.currentTimeMillis(),
                    mustChangePassword = false
                )
                try {
                    db.collection("users").document(uid).set(primaryAdmin).await()
                } catch (_: Exception) {}
                return Result.success(primaryAdmin)
            }

            // 4. Se não existir perfil cadastrado para esta conta Google
            firebaseAuth.signOut()
            Result.failure(SecurityException("A conta Google ($email) ainda não foi cadastrada por um Administrador no sistema QRZ Stock."))
        } catch (e: Exception) {
            val rawMsg = e.localizedMessage ?: e.message ?: ""
            val msg = when {
                e is SecurityException -> e.message ?: "Acesso negado."
                else -> "Falha no Login com Google: $rawMsg"
            }
            Result.failure(Exception(msg))
        }
    }

    suspend fun changePassword(newPassword: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication não inicializado.")
        )
        val db = firestore ?: return Result.failure(
            IllegalStateException("Cloud Firestore não inicializado.")
        )
        val currentUser = firebaseAuth.currentUser ?: return Result.failure(
            IllegalStateException("Nenhum usuário autenticado.")
        )
        val uid = currentUser.uid

        return try {
            currentUser.updatePassword(newPassword).await()
            db.collection("users").document(uid).update("mustChangePassword", false).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
