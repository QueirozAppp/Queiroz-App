package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditLog
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repositório responsável pela gravação e consulta da Trilha de Auditoria Imutável (auditLogs) - Etapa 13.
 */
class AuditRepository {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    /**
     * Registra um evento imutável na coleção `auditLogs`.
     * Valida os campos obrigatórios, sanitiza metadados contra vazamento de credenciais
     * e permite chave idempotente para prevenir duplicações em requisições repetidas.
     */
    suspend fun logEvent(
        action: String,
        entityType: String,
        entityId: String,
        userId: String,
        userName: String,
        userRole: String,
        storeId: String = "",
        sectorId: String = "",
        description: String = "",
        metadata: Map<String, String> = emptyMap(),
        idempotentId: String? = null
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        if (action.isBlank() || entityType.isBlank() || entityId.isBlank() || userId.isBlank() || userRole.isBlank()) {
            return Result.failure(IllegalArgumentException("Campos obrigatórios de auditoria ausentes."))
        }

        // Sanitização estrita de metadados: remove chaves sensíveis
        val forbiddenKeys = setOf("password", "pass", "token", "refreshtoken", "apikey", "secret", "credential", "pin")
        val sanitizedMetadata = metadata.filterKeys { key ->
            !forbiddenKeys.contains(key.trim().lowercase())
        }

        return try {
            val auditId = idempotentId?.ifBlank { null }
                ?: "audit_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            val now = System.currentTimeMillis()

            val auditMap = hashMapOf<String, Any>(
                "id" to auditId,
                "action" to action,
                "entityType" to entityType,
                "entityId" to entityId,
                "userId" to userId,
                "userName" to userName,
                "userRole" to userRole,
                "storeId" to storeId,
                "sectorId" to sectorId,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "description" to description,
                "metadata" to sanitizedMetadata
            )

            kotlinx.coroutines.withTimeout(4000L) {
                db.collection("auditLogs").document(auditId).set(auditMap).await()
            }
            Result.success(auditId)
        } catch (e: Exception) {
            android.util.Log.e("AuditRepository", "FALHA CRÍTICA ao registrar log de auditoria: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Consulta registros de auditoria com filtros, ordenação decrescente por timestamp e paginação.
     */
    suspend fun fetchAuditLogs(
        limitCount: Long = 30,
        lastTimestamp: Long? = null,
        filterRole: String? = null,
        filterStoreId: String? = null,
        filterAction: String? = null,
        filterEntityType: String? = null
    ): Result<List<AuditLog>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        return try {
            var query: Query = db.collection("auditLogs")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            if (!filterRole.isNullOrBlank() && filterRole != "ALL") {
                query = query.whereEqualTo("userRole", filterRole)
            }
            if (!filterStoreId.isNullOrBlank() && filterStoreId != "ALL") {
                query = query.whereEqualTo("storeId", filterStoreId)
            }
            if (!filterAction.isNullOrBlank() && filterAction != "ALL") {
                query = query.whereEqualTo("action", filterAction)
            }
            if (!filterEntityType.isNullOrBlank() && filterEntityType != "ALL") {
                query = query.whereEqualTo("entityType", filterEntityType)
            }

            if (lastTimestamp != null && lastTimestamp > 0) {
                query = query.startAfter(lastTimestamp)
            }

            query = query.limit(limitCount)

            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { doc ->
                try {
                    val base = doc.toObject(AuditLog::class.java)
                    val ts = doc.getTimestamp("timestamp")?.toDate()?.time
                        ?: (doc.get("timestamp") as? Number)?.toLong()
                        ?: base?.timestamp
                        ?: 0L
                    base?.copy(id = doc.id, timestamp = ts)
                } catch (_: Exception) {
                    null
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            // Em caso de falta de índice composto no Firestore, tenta carregar sem múltiplos where e filtrar em memória
            try {
                var fallbackQuery = db.collection("auditLogs")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                if (lastTimestamp != null && lastTimestamp > 0) {
                    fallbackQuery = fallbackQuery.startAfter(lastTimestamp)
                }
                fallbackQuery = fallbackQuery.limit(limitCount * 2)

                val snapshot = fallbackQuery.get().await()
                var list = snapshot.documents.mapNotNull { doc ->
                    try {
                        val base = doc.toObject(AuditLog::class.java)
                        val ts = doc.getTimestamp("timestamp")?.toDate()?.time
                            ?: (doc.get("timestamp") as? Number)?.toLong()
                            ?: base?.timestamp
                            ?: 0L
                        base?.copy(id = doc.id, timestamp = ts)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (!filterRole.isNullOrBlank() && filterRole != "ALL") {
                    list = list.filter { it.userRole == filterRole }
                }
                if (!filterStoreId.isNullOrBlank() && filterStoreId != "ALL") {
                    list = list.filter { it.storeId == filterStoreId }
                }
                if (!filterAction.isNullOrBlank() && filterAction != "ALL") {
                    list = list.filter { it.action == filterAction }
                }
                if (!filterEntityType.isNullOrBlank() && filterEntityType != "ALL") {
                    list = list.filter { it.entityType == filterEntityType }
                }

                Result.success(list.take(limitCount.toInt()))
            } catch (innerErr: Exception) {
                Result.failure(e)
            }
        }
    }
}
