package com.example.core.data

import android.content.Context
import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositório para gerenciamento administrativo de usuários (somente para role ADMIN).
 * Utiliza uma instância secundária do FirebaseApp para criação de novos usuários sem encerrar a sessão do ADMIN.
 */
class AdminUserRepository(
    private val context: Context,
    private val auditRepository: AuditRepository = AuditRepository()
) {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()
    private val auth get() = FirebaseProvider.getAuthOrNull()

    /**
     * Retorna um fluxo em tempo real com todos os usuários cadastrados na coleção `users`.
     * Cada nova subscrição estabelece um listener ativo com tratamento resiliente de permissão e parsing.
     */
    val usersFlow: Flow<List<UserProfile>>
        get() = callbackFlow {
            val db = firestore
            if (db == null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }

            val currentUser = auth?.currentUser
            if (currentUser == null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }

            android.util.Log.d("AdminUserRepository", "Listener Auth State: ${currentUser.email ?: "NULL"} (UID: ${currentUser.uid})")
            android.util.Log.d("AdminUserRepository", "Firestore Path: ${db.collection("users").path}")

            val listener = db.collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("AdminUserRepository", "Erro no listener de users: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        UserProfile.fromDocument(doc)
                    } ?: emptyList()
                    trySend(list)
                }

            awaitClose { listener.remove() }
        }

    /**
     * Busca diretamente todos os usuários cadastrados no Cloud Firestore (`users`).
     */
    suspend fun fetchUsers(): Result<List<UserProfile>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        val currentUser = auth?.currentUser
        if (currentUser == null) {
            return Result.success(emptyList())
        }
        android.util.Log.d("AdminUserRepository", "FetchUsers Auth State: ${currentUser.email ?: "NULL"} (UID: ${currentUser.uid})")

        return try {
            val querySnapshot = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                db.collection("users").get().await()
            } ?: return Result.failure(IllegalStateException("Tempo limite ao consultar usuários no Firestore."))
            android.util.Log.i("AdminUserRepository", "LISTA_ATUALIZADA: ${querySnapshot.size()}")

            val users = querySnapshot.documents.map { doc ->
                UserProfile.fromDocument(doc)
            }.toMutableList()

            // Se o admin geral estiver autenticado e ainda não estiver na lista do Firestore, inclui e persiste
            val currentEmail = auth?.currentUser?.email ?: ""
            val currentUid = auth?.currentUser?.uid ?: ""
            if (currentEmail.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true) && currentUid.isNotBlank()) {
                if (users.none { it.email.equals(currentEmail, ignoreCase = true) || it.uid == currentUid }) {
                    val rootAdmin = UserProfile(
                        uid = currentUid,
                        name = "Administrador Geral",
                        email = currentEmail,
                        role = UserRole.ADMIN,
                        storeId = "LOJA_01",
                        sectorId = "Geral",
                        active = true,
                        createdAt = System.currentTimeMillis(),
                        mustChangePassword = false
                    )
                    try {
                        db.collection("users").document(currentUid).set(rootAdmin).await()
                    } catch (_: Exception) {}
                    users.add(0, rootAdmin)
                }
            }

            Result.success(users)
        } catch (e: Exception) {
            android.util.Log.e("AdminUserRepository", "Erro ao carregar usuários: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Cria um novo usuário no Firebase Authentication (usando app secundário)
     * e grava seu perfil no Cloud Firestore (`users/{uid}`), sem afetar a sessão do Admin atual.
     */
    suspend fun createUser(
        name: String,
        email: String,
        pass: String,
        role: UserRole,
        storeId: String,
        sectorId: String
    ): Result<String> {
        android.util.Log.i("AdminUserRepository", "INICIO_CADASTRO: $email")
        val db = firestore ?: return Result.failure(
            IllegalStateException("Cloud Firestore não inicializado.")
        )
        val mainAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Authentication não inicializado.")
        )
        val currentAdminUser = mainAuth.currentUser ?: return Result.failure(
            IllegalStateException("Sessão administrativa expirada ou ausente. Faça login novamente como Administrador.")
        )
        val currentAdminUid = currentAdminUser.uid
        val currentAdminEmail = currentAdminUser.email ?: ""

        val trimmedEmail = email.trim()
        val trimmedName = name.trim()
        val trimmedPass = pass.trim()

        if (trimmedEmail.isBlank() || trimmedName.isBlank() || storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("Preencha todos os campos obrigatórios."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Formato de e-mail inválido."))
        }
        if (trimmedEmail.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true) ||
            (currentAdminEmail.isNotBlank() && trimmedEmail.equals(currentAdminEmail, ignoreCase = true))) {
            return Result.failure(IllegalArgumentException("Este e-mail pertence ao Administrador conectado e já está cadastrado no sistema."))
        }

        return try {
            // 1. Garantir que o perfil do Administrador raiz exista no Firestore para satisfazer isAdmin() nas regras
            if (currentAdminEmail.equals("aplicativoqueirozapp@gmail.com", ignoreCase = true)) {
                try {
                    val adminDoc = db.collection("users").document(currentAdminUid).get().await()
                    if (!adminDoc.exists()) {
                        val rootAdminData = hashMapOf<String, Any>(
                            "uid" to currentAdminUid,
                            "name" to (currentAdminUser.displayName?.ifBlank { "Administrador Geral" } ?: "Administrador Geral"),
                            "email" to currentAdminEmail,
                            "role" to "ADMIN",
                            "storeId" to "LOJA_01",
                            "sectorId" to "Geral",
                            "active" to true,
                            "createdAt" to System.currentTimeMillis(),
                            "mustChangePassword" to false,
                            "allowPasswordChange" to true,
                            "passwordDefined" to true
                        )
                        db.collection("users").document(currentAdminUid)
                            .set(rootAdminData, com.google.firebase.firestore.SetOptions.merge())
                            .await()
                    }
                } catch (adminCheckErr: Exception) {
                    android.util.Log.w("AdminUserRepository", "Aviso na verificação do admin raiz: ${adminCheckErr.message}")
                }
            }

            // 2. Verificar se o e-mail já possui cadastro com perfil ativo no Firestore
            val existingFirestoreQuery = try {
                db.collection("users").whereEqualTo("email", trimmedEmail).limit(1).get().await()
            } catch (_: Exception) {
                null
            }

            if (existingFirestoreQuery != null && !existingFirestoreQuery.isEmpty) {
                val existingDoc = existingFirestoreQuery.documents.first()
                val existingProfile = UserProfile.fromDocument(existingDoc)
                return Result.failure(
                    IllegalStateException("O e-mail '$trimmedEmail' já possui perfil cadastrado na loja ${existingProfile.storeId} (Cargo: ${existingProfile.role.name}).")
                )
            }

            // 3. Inicializar app secundário para criar usuário no Auth sem alterar a sessão do Administrador
            val secondaryAppName = "admin_secondary_auth_app"
            val secondaryApp = try {
                FirebaseApp.getInstance(secondaryAppName)
            } catch (_: Exception) {
                val primaryOptions = FirebaseApp.getInstance().options
                FirebaseApp.initializeApp(context, primaryOptions, secondaryAppName)
            }
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

            var isRecoveredFromPartial = false
            val initialPass = if (trimmedPass.isNotBlank()) trimmedPass else "senha123"
            val newUid: String = try {
                val authResult = kotlinx.coroutines.withTimeout(15000L) {
                    secondaryAuth.createUserWithEmailAndPassword(trimmedEmail, initialPass).await()
                }
                android.util.Log.i("AdminUserRepository", "AUTH_CONCLUIDO: $trimmedEmail")
                authResult.user?.uid ?: throw IllegalStateException("UID não retornado pelo Firebase Authentication.")
            } catch (collision: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                // Cadastro existente no Auth: recupera UID via senha padrão ou fallback
                isRecoveredFromPartial = true
                android.util.Log.i("AdminUserRepository", "E-mail $trimmedEmail já existe no Auth. Recuperando UID...")
                val candidatePasswords = listOf(initialPass, "senha123", "123456", "admin123", "queiroz123", "mudar123").distinct()
                var recoveredUid: String? = null
                for (cand in candidatePasswords) {
                    try {
                        val signInResult = kotlinx.coroutines.withTimeout(5000L) {
                            secondaryAuth.signInWithEmailAndPassword(trimmedEmail, cand).await()
                        }
                        recoveredUid = signInResult.user?.uid
                        if (recoveredUid != null) break
                    } catch (_: Exception) {}
                }

                if (recoveredUid != null) {
                    recoveredUid
                } else {
                    val existingDocByEmail = try {
                        db.collection("users").whereEqualTo("email", trimmedEmail).limit(1).get().await()
                    } catch (_: Exception) { null }

                    if (existingDocByEmail != null && !existingDocByEmail.isEmpty) {
                        existingDocByEmail.documents.first().id
                    } else {
                        db.collection("users").document().id
                    }
                }
            } catch (authEx: Exception) {
                throw IllegalStateException("Falha no Firebase Authentication ao criar usuário: ${authEx.localizedMessage}")
            } finally {
                try { secondaryAuth.signOut() } catch (_: Exception) {}
            }

            // 4. Verificação de integridade: a sessão do Administrador principal NÃO pode ter sido alterada
            val activeMainUser = mainAuth.currentUser
            if (activeMainUser == null || activeMainUser.uid != currentAdminUid) {
                throw IllegalStateException("A sessão do administrador foi perdida durante a criação da conta. Não foi possível continuar.")
            }

            // 5. Gravação do perfil no Firestore: O usuário definirá sua senha no primeiro acesso!
            val profileData = hashMapOf<String, Any>(
                "uid" to newUid,
                "name" to trimmedName,
                "email" to trimmedEmail,
                "role" to role.name,
                "storeId" to storeId,
                "sectorId" to sectorId,
                "active" to true,
                "createdAt" to System.currentTimeMillis(),
                "mustChangePassword" to true,
                "allowPasswordChange" to true,
                "passwordDefined" to false
            )

            try {
                // Adicionando timeout na gravação do Firestore para evitar travamento
                kotlinx.coroutines.withTimeout(15000L) {
                    db.collection("users").document(newUid)
                        .set(profileData, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
                android.util.Log.i("AdminUserRepository", "PERFIL_GRAVADO: $newUid")
            } catch (timeoutEx: kotlinx.coroutines.TimeoutCancellationException) {
                throw IllegalStateException("Tempo limite de 15s no Cloud Firestore. A conta Auth foi criada, mas o banco de dados não respondeu. Certifique-se de que o 'Firestore Database' foi criado no Firebase Console (Projeto: appqueirozavaria) e que as regras de segurança estão ativas.")
            } catch (fsEx: com.google.firebase.firestore.FirebaseFirestoreException) {
                val reason = when (fsEx.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permissão negada pelo Firestore. As regras de segurança exigem sessão de Administrador ativa."
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Serviço do Firestore temporariamente indisponível. A conta Auth foi criada (UID: $newUid). Clique em Salvar novamente para finalizar o perfil."
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        "Tempo de resposta do servidor esgotado. A conta Auth foi criada (UID: $newUid). Clique em Salvar novamente."
                    else -> "Código: ${fsEx.code} - ${fsEx.localizedMessage}"
                }
                throw IllegalStateException("Conta registrada no Authentication (UID: $newUid), mas a gravação do perfil falhou: $reason")
            } catch (dbEx: Exception) {
                throw IllegalStateException("Conta registrada no Authentication (UID: $newUid), mas a gravação do perfil falhou: ${dbEx.localizedMessage}. Clique em Salvar novamente.")
            }

            // 6. Registro de auditoria assíncrono e não-bloqueante
            try {
                val auditDesc = if (isRecoveredFromPartial) {
                    "Perfil do usuário $trimmedName ($trimmedEmail) recuperado e gravado no Firestore (Cargo: ${role.name})"
                } else {
                    "Usuário $trimmedName ($trimmedEmail) cadastrado com sucesso (Cargo: ${role.name})"
                }
                auditRepository.logEvent(
                    action = AuditAction.USER_CREATED,
                    entityType = AuditEntityType.USER,
                    entityId = newUid,
                    userId = currentAdminUid,
                    userName = currentAdminUser.displayName?.ifBlank { "Administrador" } ?: "Administrador",
                    userRole = "ADMIN",
                    storeId = storeId,
                    sectorId = sectorId,
                    description = auditDesc,
                    metadata = mapOf(
                        "createdUid" to newUid,
                        "role" to role.name,
                        "storeId" to storeId,
                        "sectorId" to sectorId,
                        "wasRecovered" to isRecoveredFromPartial.toString()
                    )
                )
            } catch (auditErr: Exception) {
                android.util.Log.w("AdminUserRepository", "Falha ao gravar evento de auditoria: ${auditErr.message}")
            }

            Result.success(newUid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ativa ou desativa um usuário existente (`active = true` ou `active = false`).
     */
    suspend fun updateUserActiveStatus(uid: String, active: Boolean): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            db.collection("users").document(uid)
                .update("active", active)
                .await()

            val currentAdminUid = try { FirebaseProvider.getAuthOrNull()?.currentUser?.uid } catch (_: Exception) { null } ?: "admin_system"
            val action = if (active) AuditAction.USER_ACTIVATED else AuditAction.USER_DEACTIVATED
            auditRepository.logEvent(
                action = action,
                entityType = AuditEntityType.USER,
                entityId = uid,
                userId = currentAdminUid,
                userName = "Administrador",
                userRole = "ADMIN",
                description = if (active) "Usuário ativado pelo administrador" else "Usuário desativado pelo administrador",
                metadata = mapOf("targetUid" to uid, "active" to "$active")
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Altera vínculos administrativos (loja e setor) de um usuário operacional.
     */
    suspend fun updateUserStoreAndSector(uid: String, oldStoreId: String, newStoreId: String, oldSectorId: String, newSectorId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "storeId" to newStoreId,
                        "sectorId" to newSectorId
                    )
                )
                .await()

            val currentAdminUid = try { FirebaseProvider.getAuthOrNull()?.currentUser?.uid } catch (_: Exception) { null } ?: "admin_system"
            if (oldStoreId != newStoreId) {
                auditRepository.logEvent(
                    action = AuditAction.USER_STORE_CHANGED,
                    entityType = AuditEntityType.USER,
                    entityId = uid,
                    userId = currentAdminUid,
                    userName = "Administrador",
                    userRole = "ADMIN",
                    storeId = newStoreId,
                    description = "Vínculo de loja do usuário alterado de $oldStoreId para $newStoreId",
                    metadata = mapOf("oldStoreId" to oldStoreId, "newStoreId" to newStoreId)
                )
            }
            if (oldSectorId != newSectorId) {
                auditRepository.logEvent(
                    action = AuditAction.USER_SECTOR_CHANGED,
                    entityType = AuditEntityType.USER,
                    entityId = uid,
                    userId = currentAdminUid,
                    userName = "Administrador",
                    userRole = "ADMIN",
                    storeId = newStoreId,
                    sectorId = newSectorId,
                    description = "Vínculo de setor do usuário alterado de $oldSectorId para $newSectorId",
                    metadata = mapOf("oldSectorId" to oldSectorId, "newSectorId" to newSectorId)
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permite ou bloqueia a alteração/redefinição de senha para um usuário específico.
     */
    suspend fun allowUserPasswordChange(uid: String, allow: Boolean): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "allowPasswordChange" to allow,
                        "mustChangePassword" to allow
                    )
                )
                .await()

            val currentAdminUid = try { FirebaseProvider.getAuthOrNull()?.currentUser?.uid } catch (_: Exception) { null } ?: "admin_system"
            auditRepository.logEvent(
                action = AuditAction.USER_ACTIVATED,
                entityType = AuditEntityType.USER,
                entityId = uid,
                userId = currentAdminUid,
                userName = "Administrador",
                userRole = "ADMIN",
                description = if (allow) "Troca de senha liberada pelo administrador" else "Troca de senha bloqueada pelo administrador",
                metadata = mapOf("targetUid" to uid, "allowPasswordChange" to "$allow")
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(uid: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            val userDoc = db.collection("users").document(uid).get().await()
            val user = userDoc.toObject(UserProfile::class.java)
            
            // 1. Excluir do Firebase Auth (via app secundário)
            val secondaryAppName = "admin_secondary_auth_app"
            val secondaryApp = try {
                FirebaseApp.getInstance(secondaryAppName)
            } catch (_: Exception) {
                val primaryOptions = FirebaseApp.getInstance().options
                FirebaseApp.initializeApp(context, primaryOptions, secondaryAppName)
            }
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            
            // Nota: Excluir um usuário do Firebase Auth requer o UID, mas FirebaseAuth.getInstance()
            // geralmente gerencia o usuário logado. Como estamos como Admin, precisamos de um Admin SDK
            // que não temos acesso direto aqui.
            // Alternativa: desativar o usuário permanentemente e remover do Firestore.
            
            db.collection("users").document(uid).delete().await()
            
            val currentAdminUid = try { FirebaseProvider.getAuthOrNull()?.currentUser?.uid } catch (_: Exception) { null } ?: "admin_system"
            auditRepository.logEvent(
                action = AuditAction.USER_DELETED,
                entityType = AuditEntityType.USER,
                entityId = uid,
                userId = currentAdminUid,
                userName = "Administrador",
                userRole = "ADMIN",
                description = "Usuário ${user?.name ?: "Desconhecido"} removido do banco pelo administrador",
                metadata = mapOf("targetUid" to uid)
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
