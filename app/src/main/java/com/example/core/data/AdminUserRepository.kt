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

    /**
     * Retorna um fluxo em tempo real com todos os usuários cadastrados na coleção `users`.
     */
    val usersFlow: Flow<List<UserProfile>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                } ?: emptyList()
                trySend(list)
            }

        awaitClose { listener.remove() }
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
        val db = firestore ?: return Result.failure(
            IllegalStateException("Firestore não inicializado.")
        )
        if (email.isBlank() || pass.isBlank() || name.isBlank() || storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("Preencha todos os campos obrigatórios."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return Result.failure(IllegalArgumentException("Formato de e-mail inválido."))
        }

        return try {
            // Inicializar app secundário para autenticação sem deslogar o Admin
            val secondaryAppName = "admin_secondary_auth_app"
            val secondaryApp = try {
                FirebaseApp.getInstance(secondaryAppName)
            } catch (e: Exception) {
                val primaryOptions = try {
                    FirebaseApp.getInstance().options
                } catch (_: Exception) {
                    FirebaseProvider.getStatus(context)
                    try { FirebaseApp.getInstance().options } catch (_: Exception) { null }
                }
                if (primaryOptions != null) {
                    FirebaseApp.initializeApp(context, primaryOptions, secondaryAppName)
                } else {
                    null
                }
            }
            if (secondaryApp == null) {
                return Result.failure(IllegalStateException("Firebase App não inicializado."))
            }
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

            // Criar usuário no Auth
            val authResult = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                secondaryAuth.createUserWithEmailAndPassword(email.trim(), pass).await()
            } ?: throw IllegalStateException("Tempo limite de comunicação com o serviço de autenticação.")
            val newUid = authResult.user?.uid ?: throw IllegalStateException("UID não retornado pelo Auth.")

            // Encerrar sessão secundária
            secondaryAuth.signOut()

            // Criar perfil no Firestore
            val newProfile = UserProfile(
                uid = newUid,
                name = name.trim(),
                email = email.trim(),
                role = role,
                storeId = storeId,
                sectorId = sectorId,
                active = true,
                createdAt = System.currentTimeMillis(),
                mustChangePassword = true
            )

            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                db.collection("users").document(newUid).set(newProfile).await()
            } ?: throw IllegalStateException("Tempo limite ao gravar perfil no Firestore.")

            // Registrar evento de auditoria
            val currentAdminUid = try { FirebaseProvider.getAuthOrNull()?.currentUser?.uid } catch (_: Exception) { null } ?: "admin_system"
            auditRepository.logEvent(
                action = AuditAction.USER_CREATED,
                entityType = AuditEntityType.USER,
                entityId = newUid,
                userId = currentAdminUid,
                userName = "Administrador",
                userRole = "ADMIN",
                storeId = storeId,
                sectorId = sectorId,
                description = "Usuário ${newProfile.name} (${newProfile.email}) criado com papel ${newProfile.role.name}",
                metadata = mapOf(
                    "createdUid" to newUid,
                    "role" to role.name,
                    "storeId" to storeId,
                    "sectorId" to sectorId
                )
            )

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
}
