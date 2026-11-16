package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.AuditLog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Repositório responsável pela gravação e consulta da Trilha de Auditoria Imutável (auditLogs) - Etapa 13.
 * Possui deserialização segura de Timestamps do Firestore e persistência resiliente multi-canal.
 */
class AuditRepository {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    companion object {
        private const val COLLECTION_AUDIT = "auditLogs"
        private const val COLLECTION_CLOUD_FALLBACK = "sectors"
        private const val COLLECTION_WITHDRAWAL = "withdrawalRequests"
        private const val CATALOG_DOC_ID = "__GLOBAL_CATALOG__"
        private const val SUBCOLLECTION_EVIDENCE = "evidence"
        private const val AUDIT_STORE_KEY = "__AUDIT_LOGS__"
        private const val DOC_TYPE_AUDIT = "AUDIT_LOG"

        @Volatile
        private var memoryCache: List<AuditLog> = emptyList()

        private fun updateCache(log: AuditLog) {
            memoryCache = (listOf(log) + memoryCache.filter { it.id != log.id })
                .sortedByDescending { it.timestamp }
                .take(200)
        }
    }

    private fun parseAuditLog(doc: DocumentSnapshot): AuditLog? {
        return try {
            val data = doc.data ?: return null
            val id = (data["auditId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: doc.id.removePrefix("audit_")
            val action = (data["action"] as? String) ?: ""
            val entityType = (data["entityType"] as? String) ?: ""
            if (action.isBlank() && entityType.isBlank()) return null

            val timestampMs = try {
                doc.getTimestamp("timestamp")?.toDate()?.time
            } catch (_: Exception) {
                null
            } ?: (data["timestamp"] as? Number)?.toLong()
                ?: (data["createdAtMs"] as? Number)?.toLong()
                ?: 0L

            val realStoreId = (data["originalStoreId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["storeId"] as? String)?.takeIf { it != AUDIT_STORE_KEY }
                ?: ""

            val rawMetadata = data["metadata"] as? Map<*, *>
            val parsedMetadata = rawMetadata?.entries
                ?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = v?.toString() ?: ""
                    key to value
                }
                ?.toMap()
                ?: emptyMap()

            AuditLog(
                id = id,
                action = action,
                entityType = entityType,
                entityId = (data["entityId"] as? String) ?: "",
                userId = (data["userId"] as? String) ?: "",
                userName = (data["userName"] as? String) ?: "",
                userRole = (data["userRole"] as? String) ?: "",
                storeId = realStoreId,
                sectorId = (data["sectorId"] as? String) ?: "",
                timestamp = timestampMs,
                description = (data["description"] as? String) ?: "",
                metadata = parsedMetadata
            )
        } catch (e: Exception) {
            android.util.Log.w("AuditRepository", "Erro ao deserializar log ${doc.id}: ${e.message}")
            null
        }
    }

    /**
     * Registra um evento imutável na coleção `auditLogs` e canais resilientes.
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

            val localEntry = AuditLog(
                id = auditId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                userId = userId,
                userName = userName,
                userRole = userRole,
                storeId = storeId,
                sectorId = sectorId,
                timestamp = now,
                description = description,
                metadata = sanitizedMetadata
            )
            updateCache(localEntry)

            val auditMap = hashMapOf<String, Any>(
                "id" to auditId,
                "auditId" to auditId,
                "action" to action,
                "entityType" to entityType,
                "entityId" to entityId,
                "userId" to userId,
                "userName" to userName,
                "userRole" to userRole,
                "storeId" to storeId,
                "sectorId" to sectorId,
                "timestamp" to FieldValue.serverTimestamp(),
                "createdAtMs" to now,
                "description" to description,
                "metadata" to sanitizedMetadata
            )

            var anySucceeded = false

            // Canal 1: Coleção principal 'auditLogs'
            try {
                kotlinx.coroutines.withTimeout(4000L) {
                    db.collection(COLLECTION_AUDIT).document(auditId).set(auditMap).await()
                }
                anySucceeded = true
            } catch (ex: Exception) {
                android.util.Log.w("AuditRepository", "Canal 'auditLogs' indisponível (${ex.message}), usando canal resiliente.")
            }

            // Mapa espelho para canais resilientes
            val mirrorMap = hashMapOf<String, Any>(
                "id" to auditId,
                "auditId" to auditId,
                "storeId" to AUDIT_STORE_KEY,
                "originalStoreId" to storeId,
                "docType" to DOC_TYPE_AUDIT,
                "action" to action,
                "entityType" to entityType,
                "entityId" to entityId,
                "userId" to userId,
                "userName" to userName,
                "userRole" to userRole,
                "sectorId" to sectorId,
                "timestamp" to now,
                "createdAtMs" to now,
                "description" to description,
                "metadata" to sanitizedMetadata
            )

            // Canal 2: Espelho em 'sectors'
            try {
                kotlinx.coroutines.withTimeout(3000L) {
                    db.collection(COLLECTION_CLOUD_FALLBACK).document("audit_$auditId")
                        .set(mirrorMap, SetOptions.merge()).await()
                }
                anySucceeded = true
            } catch (_: Exception) {}

            // Canal 3: Espelho em 'withdrawalRequests/__GLOBAL_CATALOG__/evidence'
            try {
                kotlinx.coroutines.withTimeout(3000L) {
                    db.collection(COLLECTION_WITHDRAWAL)
                        .document(CATALOG_DOC_ID)
                        .collection(SUBCOLLECTION_EVIDENCE)
                        .document("audit_$auditId")
                        .set(mirrorMap, SetOptions.merge()).await()
                }
                anySucceeded = true
            } catch (_: Exception) {}

            if (anySucceeded) {
                Result.success(auditId)
            } else {
                Result.failure(IllegalStateException("Não foi possível gravar o log de auditoria."))
            }
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
            val fetchLimit = (limitCount * 4).coerceAtLeast(120L)

            val primaryLogs = try {
                val snapshot = db.collection(COLLECTION_AUDIT)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(fetchLimit)
                    .get()
                    .await()
                snapshot.documents.mapNotNull { parseAuditLog(it) }
            } catch (_: Exception) {
                try {
                    db.collection(COLLECTION_AUDIT)
                        .limit(fetchLimit)
                        .get()
                        .await()
                        .documents.mapNotNull { parseAuditLog(it) }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val fallbackLogs = try {
                db.collection(COLLECTION_CLOUD_FALLBACK)
                    .whereEqualTo("storeId", AUDIT_STORE_KEY)
                    .get()
                    .await()
                    .documents.mapNotNull { parseAuditLog(it) }
            } catch (_: Exception) {
                emptyList()
            }

            val evidenceLogs = try {
                db.collection(COLLECTION_WITHDRAWAL)
                    .document(CATALOG_DOC_ID)
                    .collection(SUBCOLLECTION_EVIDENCE)
                    .whereEqualTo("docType", DOC_TYPE_AUDIT)
                    .get()
                    .await()
                    .documents.mapNotNull { parseAuditLog(it) }
            } catch (_: Exception) {
                emptyList()
            }

            val mergedById = linkedMapOf<String, AuditLog>()
            for (source in listOf(primaryLogs, fallbackLogs, evidenceLogs, memoryCache)) {
                for (item in source) {
                    if (item.id.isBlank()) continue
                    val existing = mergedById[item.id]
                    if (existing == null || item.timestamp > existing.timestamp) {
                        mergedById[item.id] = item
                    }
                }
            }

            // Reconstruir eventos reais a partir das coleções operacionais do Firestore caso não exista
            // registro explícito correspondente em auditLogs (ex.: registros anteriores ou bloqueio prévio de regras)
            val existingActionKeys = mergedById.values.map { "${it.entityId}::${it.action}" }.toMutableSet()
            val derivedLogs = reconstructHistoricalLogs(db, existingActionKeys)
            for (derived in derivedLogs) {
                if (!mergedById.containsKey(derived.id)) {
                    mergedById[derived.id] = derived
                }
            }

            var filtered = mergedById.values.sortedByDescending { it.timestamp }

            if (!filterRole.isNullOrBlank() && filterRole != "ALL") {
                filtered = filtered.filter { it.userRole == filterRole }
            }
            if (!filterStoreId.isNullOrBlank() && filterStoreId != "ALL") {
                filtered = filtered.filter { it.storeId == filterStoreId }
            }
            if (!filterAction.isNullOrBlank() && filterAction != "ALL") {
                filtered = filtered.filter { it.action == filterAction }
            }
            if (!filterEntityType.isNullOrBlank() && filterEntityType != "ALL") {
                filtered = filtered.filter { it.entityType == filterEntityType }
            }
            if (lastTimestamp != null && lastTimestamp > 0L) {
                filtered = filtered.filter { it.timestamp < lastTimestamp }
            }

            val explicitOnly = mergedById.values
                .filter { !it.id.startsWith("hist_") }
                .sortedByDescending { it.timestamp }
                .take(200)
            if (explicitOnly.isNotEmpty()) {
                memoryCache = explicitOnly
            }
            Result.success(filtered.take(limitCount.toInt()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractMillis(doc: DocumentSnapshot, vararg keys: String): Long {
        for (key in keys) {
            try {
                val ts = doc.getTimestamp(key)?.toDate()?.time
                if (ts != null && ts > 0L) return ts
            } catch (_: Exception) {}
            val raw = doc.get(key)
            if (raw is Number && raw.toLong() > 0L) return raw.toLong()
        }
        return 0L
    }

    private suspend fun reconstructHistoricalLogs(
        db: FirebaseFirestore,
        existingKeys: MutableSet<String>
    ): List<AuditLog> = coroutineScope {
        val result = mutableListOf<AuditLog>()

        fun addIfMissing(log: AuditLog) {
            if (log.entityId.isBlank() || log.action.isBlank()) return
            val key = "${log.entityId}::${log.action}"
            if (existingKeys.add(key)) {
                result.add(log)
            }
        }

        val withdrawalsDeferred = async {
            try {
                withTimeout(8000L) { db.collection(COLLECTION_WITHDRAWAL).get().await().documents }
            } catch (_: Exception) { emptyList() }
        }
        val prodReqPrimaryDeferred = async {
            try {
                withTimeout(8000L) { db.collection("productRegistrationRequests").get().await().documents }
            } catch (_: Exception) { emptyList() }
        }
        val prodPrimaryDeferred = async {
            try {
                withTimeout(8000L) { db.collection("products").get().await().documents }
            } catch (_: Exception) { emptyList() }
        }
        val sectorsAllDeferred = async {
            try {
                withTimeout(8000L) { db.collection(COLLECTION_CLOUD_FALLBACK).get().await().documents }
            } catch (_: Exception) { emptyList() }
        }
        val evidenceCatalogDeferred = async {
            try {
                withTimeout(8000L) {
                    db.collection(COLLECTION_WITHDRAWAL)
                        .document(CATALOG_DOC_ID)
                        .collection(SUBCOLLECTION_EVIDENCE)
                        .get().await().documents
                }
            } catch (_: Exception) { emptyList() }
        }
        val usersDeferred = async {
            try {
                withTimeout(8000L) { db.collection("users").get().await().documents }
            } catch (_: Exception) { emptyList() }
        }
        val storesDeferred = async {
            try {
                withTimeout(8000L) { db.collection("stores").get().await().documents }
            } catch (_: Exception) { emptyList() }
        }

        val withdrawalDocs = withdrawalsDeferred.await()
        val prodReqPrimaryDocs = prodReqPrimaryDeferred.await()
        val prodPrimaryDocs = prodPrimaryDeferred.await()
        val allSectorDocs = sectorsAllDeferred.await()
        val allEvidenceCatalogDocs = evidenceCatalogDeferred.await()
        val userDocs = usersDeferred.await()
        val storeDocs = storesDeferred.await()

        // 1. Solicitações de Baixa e Conferências (withdrawalRequests)
        for (doc in withdrawalDocs) {
            if (doc.id == CATALOG_DOC_ID) continue
            val data = doc.data ?: continue
            val reqId = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: doc.id
            val storeId = (data["storeId"] as? String) ?: ""
            val sectorId = (data["sectorId"] as? String) ?: ""
            val status = (data["status"] as? String) ?: "PENDING"
            val requestedBy = (data["requestedBy"] as? String) ?: ""
            val requestedByName = (data["requestedByName"] as? String) ?: "Líder de Setor"
            val createdAt = extractMillis(doc, "createdAt", "updatedAt")
            val itemsCount = (data["items"] as? List<*>)?.size ?: 0

            if (requestedBy.isNotBlank() || createdAt > 0L) {
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.WITHDRAWAL_REQUEST_CREATED}_$reqId",
                        action = AuditAction.WITHDRAWAL_REQUEST_CREATED,
                        entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                        entityId = reqId,
                        userId = requestedBy,
                        userName = requestedByName,
                        userRole = "SETOR_LEADER",
                        storeId = storeId,
                        sectorId = sectorId,
                        timestamp = createdAt,
                        description = "Solicitação de baixa criada com $itemsCount produto(s)",
                        metadata = mapOf("itemsCount" to "$itemsCount", "storeId" to storeId, "sectorId" to sectorId)
                    )
                )
            }

            val startedBy = (data["conferenceStartedBy"] as? String) ?: ""
            val startedByName = (data["conferenceStartedByName"] as? String) ?: "Avarista"
            val startedAt = extractMillis(doc, "conferenceStartedAt")
            if (startedBy.isNotBlank() || startedAt > 0L || status == "IN_CONFERENCE") {
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.CONFERENCE_STARTED}_$reqId",
                        action = AuditAction.CONFERENCE_STARTED,
                        entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                        entityId = reqId,
                        userId = startedBy,
                        userName = startedByName,
                        userRole = "AVARISTA",
                        storeId = storeId,
                        sectorId = sectorId,
                        timestamp = if (startedAt > 0L) startedAt else createdAt,
                        description = "Iniciada conferência física pelo Avarista"
                    )
                )
            }

            val checkedBy = (data["checkedBy"] as? String) ?: ""
            val checkedByName = (data["checkedByName"] as? String) ?: "Avarista"
            val checkedAt = extractMillis(doc, "checkedAt", "updatedAt")
            val divReason = (data["divergenceReason"] as? String) ?: ""
            if (status == "COMPLETED" || status == "DIVERGENCE" || status == "RECTIFIED") {
                val action = if (status == "DIVERGENCE") AuditAction.WITHDRAWAL_DIVERGENCE else AuditAction.WITHDRAWAL_COMPLETED
                addIfMissing(
                    AuditLog(
                        id = "hist_${action}_$reqId",
                        action = action,
                        entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                        entityId = reqId,
                        userId = checkedBy,
                        userName = checkedByName,
                        userRole = "AVARISTA",
                        storeId = storeId,
                        sectorId = sectorId,
                        timestamp = if (checkedAt > 0L) checkedAt else createdAt,
                        description = if (status == "DIVERGENCE") "Conferência finalizada com divergência: $divReason" else "Conferência finalizada com sucesso",
                        metadata = if (divReason.isNotBlank()) mapOf("divergenceReason" to divReason) else emptyMap()
                    )
                )
            } else if (status == "CANCELLED") {
                val cancelReason = (data["cancellationReason"] as? String) ?: ""
                val cancelledByUid = (data["cancelledByUid"] as? String) ?: ""
                val cancelledByName = (data["cancelledByName"] as? String) ?: "Avarista"
                val cancelledAt = extractMillis(doc, "cancelledAt", "updatedAt")
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.CONFERENCE_CANCELLED}_$reqId",
                        action = AuditAction.CONFERENCE_CANCELLED,
                        entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                        entityId = reqId,
                        userId = cancelledByUid,
                        userName = cancelledByName,
                        userRole = "AVARISTA",
                        storeId = storeId,
                        sectorId = sectorId,
                        timestamp = if (cancelledAt > 0L) cancelledAt else createdAt,
                        description = "Solicitação excluída/cancelada na conferência. Motivo: $cancelReason",
                        metadata = mapOf("cancellationReason" to cancelReason, "requestId" to reqId)
                    )
                )
            }
        }

        // 2. Solicitações e Aprovações de Cadastro de Produto (principal + canais resilientes)
        val prodReqDocs = prodReqPrimaryDocs +
            allSectorDocs.filter { (it.getString("storeId") == "__PRODUCT_REG_REQUESTS__") } +
            allEvidenceCatalogDocs.filter { (it.getString("docType") == "PRODUCT_REQUEST") }

        for (doc in prodReqDocs) {
            val data = doc.data ?: continue
            val reqId = (data["requestId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: doc.id.removePrefix("req_")
            val name = (data["name"] as? String) ?: ""
            if (name.isBlank()) continue
            val barcode = (data["barcode"] as? String) ?: ""
            val status = (data["status"] as? String) ?: "PENDING"
            val storeId = (data["originalStoreId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["storeId"] as? String)?.takeIf { it != "__PRODUCT_REG_REQUESTS__" }
                ?: "GLOBAL"
            val sectorId = (data["sectorId"] as? String) ?: "Geral"
            val requestedByUid = (data["requestedByUid"] as? String) ?: ""
            val requestedByName = (data["requestedByName"] as? String) ?: "Usuário"
            val requestedAt = extractMillis(doc, "requestedAt", "createdAt")

            addIfMissing(
                AuditLog(
                    id = "hist_${AuditAction.PRODUCT_REQUEST_CREATED}_$reqId",
                    action = AuditAction.PRODUCT_REQUEST_CREATED,
                    entityType = AuditEntityType.PRODUCT_REQUEST,
                    entityId = reqId,
                    userId = requestedByUid,
                    userName = requestedByName,
                    userRole = "SETOR_LEADER",
                    storeId = storeId,
                    sectorId = sectorId,
                    timestamp = requestedAt,
                    description = "Solicitação de cadastro do produto '$name' ($sectorId)",
                    metadata = mapOf("productName" to name, "barcode" to barcode)
                )
            )

            val reviewedAt = extractMillis(doc, "reviewedAt", "updatedAt")
            val reviewedByUid = (data["reviewedByUid"] as? String) ?: ""
            val reviewedByName = (data["reviewedByName"] as? String) ?: "Administrador"
            if (status == "APPROVED") {
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.PRODUCT_REQUEST_APPROVED}_$reqId",
                        action = AuditAction.PRODUCT_REQUEST_APPROVED,
                        entityType = AuditEntityType.PRODUCT_REQUEST,
                        entityId = reqId,
                        userId = reviewedByUid,
                        userName = reviewedByName,
                        userRole = "ADMIN",
                        storeId = storeId,
                        description = "Cadastro de produto '$name' aprovado",
                        timestamp = if (reviewedAt > 0L) reviewedAt else requestedAt,
                        metadata = mapOf("productName" to name, "barcode" to barcode)
                    )
                )
            } else if (status == "REJECTED") {
                val reason = (data["rejectionReason"] as? String) ?: ""
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.PRODUCT_REQUEST_REJECTED}_$reqId",
                        action = AuditAction.PRODUCT_REQUEST_REJECTED,
                        entityType = AuditEntityType.PRODUCT_REQUEST,
                        entityId = reqId,
                        userId = reviewedByUid,
                        userName = reviewedByName,
                        userRole = "ADMIN",
                        storeId = storeId,
                        description = "Solicitação de cadastro de produto recusada: $reason",
                        timestamp = if (reviewedAt > 0L) reviewedAt else requestedAt,
                        metadata = mapOf("productName" to name, "reason" to reason)
                    )
                )
            }
        }

        // 3. Produtos no Catálogo Global
        val productDocs = prodPrimaryDocs +
            allSectorDocs.filter { (it.getString("storeId") == "__GLOBAL_PRODUCTS__") } +
            allEvidenceCatalogDocs.filter { (it.getString("docType") == "GLOBAL_PRODUCT") }

        for (doc in productDocs) {
            val data = doc.data ?: continue
            if ((data["deleted"] as? Boolean) == true) continue
            val prodId = (data["productId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: doc.id.removePrefix("prod_")
            val name = (data["name"] as? String) ?: ""
            if (name.isBlank()) continue
            val barcode = (data["barcode"] as? String) ?: ""
            val unit = (data["unit"] as? String) ?: "UN"
            val ts = extractMillis(doc, "createdAt", "updatedAt")
            addIfMissing(
                AuditLog(
                    id = "hist_${AuditAction.PRODUCT_CREATED}_$prodId",
                    action = AuditAction.PRODUCT_CREATED,
                    entityType = AuditEntityType.PRODUCT,
                    entityId = prodId,
                    userId = (data["createdByUid"] as? String) ?: "system",
                    userName = (data["createdByName"] as? String) ?: "Administrador",
                    userRole = "ADMIN",
                    timestamp = ts,
                    description = "Produto '$name' (Código: ${barcode.ifBlank { "S/N" }}) cadastrado no catálogo global",
                    metadata = mapOf("barcode" to barcode, "productName" to name, "unit" to unit)
                )
            )
        }

        // 4. Usuários, Lojas e Setores
        for (doc in userDocs) {
            val data = doc.data ?: continue
            val uid = doc.id
            val name = (data["name"] as? String)?.ifBlank { data["email"] as? String } ?: continue
            val role = (data["role"] as? String) ?: "AVARISTA"
            val storeId = (data["storeId"] as? String) ?: ""
            val sectorId = (data["sectorId"] as? String) ?: ""
            val createdAt = extractMillis(doc, "createdAt")
            if (createdAt > 0L) {
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.USER_CREATED}_$uid",
                        action = AuditAction.USER_CREATED,
                        entityType = AuditEntityType.USER,
                        entityId = uid,
                        userId = "admin_system",
                        userName = "Administrador",
                        userRole = "ADMIN",
                        storeId = storeId,
                        sectorId = sectorId,
                        timestamp = createdAt,
                        description = "Usuário $name cadastrado (Cargo: $role)",
                        metadata = mapOf("createdUid" to uid, "role" to role, "storeId" to storeId, "sectorId" to sectorId)
                    )
                )
            }
        }

        for (doc in storeDocs) {
            val data = doc.data ?: continue
            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: doc.id
            val name = (data["name"] as? String) ?: continue
            val code = (data["code"] as? String) ?: id
            val ts = extractMillis(doc, "createdAt", "updatedAt")
            if (ts > 0L) {
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.STORE_CREATED}_$id",
                        action = AuditAction.STORE_CREATED,
                        entityType = AuditEntityType.STORE,
                        entityId = id,
                        userId = "admin_system",
                        userName = "Administrador",
                        userRole = "ADMIN",
                        storeId = id,
                        timestamp = ts,
                        description = "Loja $name ($code) cadastrada",
                        metadata = mapOf("storeName" to name)
                    )
                )
            }
        }

        for (doc in allSectorDocs) {
            val data = doc.data ?: continue
            val storeId = (data["storeId"] as? String) ?: ""
            if (storeId.isBlank() || storeId.startsWith("__")) continue
            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: doc.id
            val name = (data["name"] as? String) ?: continue
            val code = (data["code"] as? String) ?: id
            val ts = extractMillis(doc, "createdAt", "updatedAt")
            if (ts > 0L) {
                addIfMissing(
                    AuditLog(
                        id = "hist_${AuditAction.SECTOR_CREATED}_$id",
                        action = AuditAction.SECTOR_CREATED,
                        entityType = AuditEntityType.SECTOR,
                        entityId = id,
                        userId = "admin_system",
                        userName = "Administrador",
                        userRole = "ADMIN",
                        storeId = storeId,
                        sectorId = id,
                        timestamp = ts,
                        description = "Setor $name ($code) cadastrado para a loja $storeId",
                        metadata = mapOf("sectorName" to name, "storeId" to storeId)
                    )
                )
            }
        }

        result
    }
}
