package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.ConferenceEvidence
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.model.WithdrawalRequest
import com.example.core.model.WithdrawalRequestItem
import com.google.firebase.firestore.AggregateSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Contadores oficiais de status obtidos via Firestore Server-Side Aggregation (Etapa 19).
 */
data class RequestStatusCounts(
    val totalCount: Long = 0L,
    val pendingCount: Long = 0L,
    val inConferenceCount: Long = 0L,
    val completedCount: Long = 0L,
    val divergenceCount: Long = 0L
)

/**
 * Repositório responsável pela persistência, consulta, conferência física e evidências de Solicitações de Baixa (`withdrawalRequests`).
 */
class WithdrawalRequestRepository(
    private val auditRepository: AuditRepository = AuditRepository()
) {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()
    private val storage get() = FirebaseProvider.getStorageOrNull()

    /**
     * Retorna um fluxo em tempo real com as solicitações criadas pelo usuário autenticado (`requestedBy == uid`),
     * ordenadas por data de criação decrescente.
     */
    fun getMyRequestsFlow(uid: String): Flow<List<WithdrawalRequest>> = callbackFlow {
        val db = firestore
        if (db == null || uid.isBlank() || FirebaseProvider.getAuthOrNull()?.currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = try {
            db.collection("withdrawalRequests")
                .whereEqualTo("requestedBy", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(WithdrawalRequest::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }?.sortedByDescending { it.createdAt } ?: emptyList()
                    trySend(list)
                }
        } catch (_: Exception) {
            trySend(emptyList())
            null
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Retorna um fluxo em tempo real com as solicitações de baixa pendentes de conferência na loja do Avarista (`storeId`),
     * ordenadas pela mais antiga primeiro (prioridade de atendimento).
     */
    fun getPendingConferencesFlow(storeId: String): Flow<List<WithdrawalRequest>> = callbackFlow {
        val db = firestore
        if (db == null || storeId.isBlank() || FirebaseProvider.getAuthOrNull()?.currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = try {
            db.collection("withdrawalRequests")
                .whereEqualTo("storeId", storeId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(WithdrawalRequest::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }?.filter { it.status == "PENDING" || it.status == "IN_CONFERENCE" }
                        ?.sortedBy { it.createdAt } ?: emptyList()
                    trySend(list)
                }
        } catch (_: Exception) {
            trySend(emptyList())
            null
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Retorna um fluxo em tempo real com o resumo consolidado de contagens para o Dashboard da Home.
     */
    fun getSummaryFlow(userProfile: UserProfile): Flow<RequestStatusCounts> = callbackFlow {
        val db = firestore
        if (db == null || FirebaseProvider.getAuthOrNull()?.currentUser == null) {
            trySend(RequestStatusCounts())
            close()
            return@callbackFlow
        }

        var query: com.google.firebase.firestore.Query = db.collection("withdrawalRequests")
        when (userProfile.role) {
            UserRole.AVARISTA -> query = query.whereEqualTo("storeId", userProfile.storeId)
            UserRole.SETOR_LEADER -> query = query.whereEqualTo("requestedBy", userProfile.uid)
            UserRole.ADMIN -> { /* Acompanhamento global para Admin */ }
        }

        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestStatusCounts())
                    return@addSnapshotListener
                }
                try {
                    val docs = snapshot?.documents ?: emptyList()
                    var pending = 0L
                    var inConf = 0L
                    var completed = 0L
                    var divergence = 0L

                    for (doc in docs) {
                        when (doc.getString("status")) {
                            "PENDING" -> pending++
                            "IN_CONFERENCE" -> inConf++
                            "COMPLETED" -> completed++
                            "DIVERGENCE" -> divergence++
                        }
                    }

                    trySend(
                        RequestStatusCounts(
                            totalCount = docs.size.toLong(),
                            pendingCount = pending,
                            inConferenceCount = inConf,
                            completedCount = completed,
                            divergenceCount = divergence
                        )
                    )
                } catch (_: Exception) {
                    trySend(RequestStatusCounts())
                }
            }
        } catch (_: Exception) {
            trySend(RequestStatusCounts())
            null
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Obter uma solicitação específica pelo ID.
     */
    suspend fun getRequestById(requestId: String): Result<WithdrawalRequest?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            val doc = db.collection("withdrawalRequests").document(requestId).get().await()
            val request = doc.toObject(WithdrawalRequest::class.java)?.copy(id = doc.id)
            Result.success(request)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicia a conferência de uma solicitação (transição de "PENDING" para "IN_CONFERENCE").
     * Garante concorrência para impedir que dois Avaristas assumam a mesma solicitação simultaneamente.
     */
    suspend fun startConference(requestId: String, userProfile: UserProfile): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        return try {
            val docRef = db.collection("withdrawalRequests").document(requestId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentStatus = snapshot.getString("status") ?: "PENDING"
                val startedBy = snapshot.getString("conferenceStartedBy")

                if (currentStatus == "IN_CONFERENCE" && startedBy != userProfile.uid) {
                    val startedByName = snapshot.getString("conferenceStartedByName") ?: "outro usuário"
                    throw IllegalStateException("Esta solicitação já está em conferência por $startedByName.")
                }

                if (currentStatus in listOf("COMPLETED", "DIVERGENCE")) {
                    throw IllegalStateException("Esta solicitação já foi finalizada e não pode ser alterada.")
                }

                if (currentStatus == "PENDING") {
                    transaction.update(
                        docRef,
                        mapOf(
                            "status" to "IN_CONFERENCE",
                            "conferenceStartedBy" to userProfile.uid,
                            "conferenceStartedByName" to userProfile.name.ifEmpty { userProfile.email },
                            "conferenceStartedAt" to System.currentTimeMillis()
                        )
                    )
                }
            }.await()

            // Registrar evento de auditoria
            auditRepository.logEvent(
                action = AuditAction.CONFERENCE_STARTED,
                entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                entityId = requestId,
                userId = userProfile.uid,
                userName = userProfile.name.ifEmpty { userProfile.email },
                userRole = userProfile.role.name,
                storeId = userProfile.storeId,
                sectorId = userProfile.sectorId,
                description = "Iniciada conferência física pelo Avarista"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Finaliza a conferência física, calculando o resultado ("COMPLETED" se sem divergências ou "DIVERGENCE" se com diferenças).
     * Exige obrigatoriamente a justificativa (`divergenceReason`) quando houver divergência.
     */
    suspend fun finishConference(
        requestId: String,
        updatedItems: List<WithdrawalRequestItem>,
        divergenceReason: String?,
        userProfile: UserProfile
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        // Validar que todos os itens possuem checkedQuantity != null
        val uncheckedItem = updatedItems.find { it.checkedQuantity == null }
        if (uncheckedItem != null) {
            return Result.failure(IllegalArgumentException("Todos os produtos devem ter a quantidade conferida antes de finalizar."))
        }

        val hasDivergence = updatedItems.any { it.checkedQuantity != it.requestedQuantity }
        val trimmedReason = divergenceReason?.trim()

        if (hasDivergence && trimmedReason.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("É obrigatório informar a justificativa da divergência para finalizar."))
        }

        return try {
            val docRef = db.collection("withdrawalRequests").document(requestId)
            val now = System.currentTimeMillis()

            var finalStatus = "COMPLETED"

            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentStatus = snapshot.getString("status") ?: "PENDING"
                val startedBy = snapshot.getString("conferenceStartedBy")
                val originalRequest = snapshot.toObject(WithdrawalRequest::class.java)
                    ?: throw IllegalStateException("Solicitação não encontrada no banco de dados.")

                if (currentStatus in listOf("COMPLETED", "DIVERGENCE")) {
                    throw IllegalStateException("Esta solicitação já foi finalizada anteriormente.")
                }

                if (startedBy != null && startedBy != userProfile.uid) {
                    throw IllegalStateException("Apenas o Avarista responsável que iniciou a conferência pode finalizá-la.")
                }

                // Reconstruir a lista de itens preservando integralmente os dados históricos do snapshot do Firestore
                val itemsData = originalRequest.items.map { originalItem ->
                    val inputItem = updatedItems.find { it.productId == originalItem.productId }
                    val checkedQty = inputItem?.checkedQuantity ?: originalItem.checkedQuantity
                    
                    if (checkedQty == null) {
                        throw IllegalArgumentException("O produto ${originalItem.productName} não teve a quantidade conferida.")
                    }

                    mapOf(
                        "productId" to originalItem.productId,
                        "barcode" to originalItem.barcode,
                        "productName" to originalItem.productName,
                        "unit" to originalItem.unit,
                        "requestedQuantity" to originalItem.requestedQuantity,
                        "checkedQuantity" to checkedQty
                    )
                }

                val productIds = originalRequest.productIds.ifEmpty { originalRequest.items.map { it.productId } }

                // Recalcular divergência estritamente com base nos dados históricos do documento original
                val divergenceFound = originalRequest.items.any { originalItem ->
                    val inputItem = updatedItems.find { it.productId == originalItem.productId }
                    val checkedQty = inputItem?.checkedQuantity
                    checkedQty != originalItem.requestedQuantity
                }

                finalStatus = if (divergenceFound) "DIVERGENCE" else "COMPLETED"

                val updateMap = mutableMapOf<String, Any?>(
                    "status" to finalStatus,
                    "checkedBy" to userProfile.uid,
                    "checkedByName" to userProfile.name.ifEmpty { userProfile.email },
                    "checkedAt" to now,
                    "productIds" to productIds,
                    "items" to itemsData
                )

                if (divergenceFound) {
                    updateMap["divergenceReason"] = trimmedReason
                }

                transaction.update(docRef, updateMap)
            }.await()

            // Registrar evento de auditoria de finalização
            val auditAction = if (finalStatus == "DIVERGENCE") AuditAction.WITHDRAWAL_DIVERGENCE else AuditAction.WITHDRAWAL_COMPLETED
            auditRepository.logEvent(
                action = auditAction,
                entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                entityId = requestId,
                userId = userProfile.uid,
                userName = userProfile.name.ifEmpty { userProfile.email },
                userRole = userProfile.role.name,
                storeId = userProfile.storeId,
                sectorId = userProfile.sectorId,
                description = if (finalStatus == "DIVERGENCE") "Conferência finalizada com divergência: $trimmedReason" else "Conferência finalizada com sucesso",
                metadata = if (finalStatus == "DIVERGENCE") mapOf("divergenceReason" to (trimmedReason ?: "")) else emptyMap()
            )

            Result.success(finalStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retorna um fluxo em tempo real das evidências fotográficas vinculadas a uma solicitação.
     */
    fun getConferenceEvidencesFlow(requestId: String): Flow<List<ConferenceEvidence>> = callbackFlow {
        val db = firestore
        if (db == null || requestId.isBlank() || FirebaseProvider.getAuthOrNull()?.currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = try {
            db.collection("withdrawalRequests")
                .document(requestId)
                .collection("evidence")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(ConferenceEvidence::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }?.sortedByDescending { it.createdAt } ?: emptyList()
                    trySend(list)
                }
        } catch (_: Exception) {
            trySend(emptyList())
            null
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Realiza o upload binário para o Firebase Storage e registra os metadados no Firestore em subcoleção.
     * Trata o estado inconsistente em caso de falha de gravação no Firestore para remover o arquivo enviado ao Storage.
     */
    suspend fun uploadEvidenceFileAndCreateRecord(
        requestId: String,
        imageBytes: ByteArray,
        description: String,
        itemProductId: String?,
        userProfile: UserProfile
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        val st = storage ?: return Result.failure(IllegalStateException("Firebase Storage não inicializado. Verifique a configuração do projeto."))

        val evidenceId = UUID.randomUUID().toString()
        val storagePath = "withdrawalRequests/$requestId/evidence/$evidenceId/photo.jpg"
        val storageRef = st.reference.child(storagePath)

        return try {
            // 1. Upload para Firebase Storage
            storageRef.putBytes(imageBytes).await()
            val downloadUrl = try {
                storageRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                ""
            }

            // 2. Gravar metadados no Firestore
            val now = System.currentTimeMillis()
            val evidence = ConferenceEvidence(
                id = evidenceId,
                requestId = requestId,
                itemProductId = itemProductId,
                storeId = userProfile.storeId,
                uploadedBy = userProfile.uid,
                uploadedByName = userProfile.name.ifEmpty { userProfile.email },
                createdAt = now,
                storagePath = storagePath,
                downloadUrl = downloadUrl,
                description = description
            )

            try {
                db.collection("withdrawalRequests")
                    .document(requestId)
                    .collection("evidence")
                    .document(evidenceId)
                    .set(evidence)
                    .await()

                // Registrar evento de auditoria
                auditRepository.logEvent(
                    action = AuditAction.EVIDENCE_ADDED,
                    entityType = AuditEntityType.EVIDENCE,
                    entityId = evidenceId,
                    userId = userProfile.uid,
                    userName = userProfile.name.ifEmpty { userProfile.email },
                    userRole = userProfile.role.name,
                    storeId = userProfile.storeId,
                    sectorId = userProfile.sectorId,
                    description = "Evidência fotográfica anexada",
                    metadata = mapOf(
                        "requestId" to requestId,
                        "storagePath" to storagePath,
                        "itemProductId" to (itemProductId ?: "")
                    )
                )

                Result.success(evidenceId)
            } catch (firestoreErr: Exception) {
                // Tratamento de órfão no Storage: se falhar o Firestore, excluir arquivo do Storage
                try {
                    storageRef.delete().await()
                } catch (_: Exception) {
                    // Ignorar erro na exclusão do órfão
                }
                Result.failure(firestoreErr)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registra os metadados de uma evidência na subcoleção `withdrawalRequests/{requestId}/evidence/{evidenceId}`.
     */
    suspend fun addConferenceEvidence(
        requestId: String,
        evidence: ConferenceEvidence
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        if (requestId.isBlank()) return Result.failure(IllegalArgumentException("ID da solicitação é obrigatório."))

        return try {
            val evidenceRef = db.collection("withdrawalRequests")
                .document(requestId)
                .collection("evidence")
                .document()
            
            val evidenceId = evidenceRef.id
            val now = System.currentTimeMillis()
            val finalEvidence = evidence.copy(
                id = evidenceId,
                requestId = requestId,
                createdAt = now
            )

            evidenceRef.set(finalEvidence).await()
            Result.success(evidenceId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cria e salva uma nova Solicitação de Baixa no Firestore com status "PENDING".
     */
    suspend fun createRequest(request: WithdrawalRequest): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        if (request.items.isEmpty()) {
            return Result.failure(IllegalArgumentException("A solicitação deve conter ao menos um produto."))
        }
        if (request.requestedBy.isBlank() || request.storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("Dados de autenticação ou loja inválidos."))
        }

        return try {
            val requestId = db.collection("withdrawalRequests").document().id
            val now = System.currentTimeMillis()
            val pIds = request.items.map { it.productId }
            val finalRequest = request.copy(
                id = requestId,
                createdAt = now,
                status = "PENDING",
                productIds = pIds
            )

            db.collection("withdrawalRequests").document(requestId).set(finalRequest).await()

            // Registrar evento de auditoria
            auditRepository.logEvent(
                action = AuditAction.WITHDRAWAL_REQUEST_CREATED,
                entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                entityId = requestId,
                userId = request.requestedBy,
                userName = request.requestedByName,
                userRole = "SETOR_LEADER",
                storeId = request.storeId,
                sectorId = request.sectorId,
                description = "Solicitação de baixa criada com ${request.items.size} produto(s)",
                metadata = mapOf(
                    "itemsCount" to "${request.items.size}",
                    "storeId" to request.storeId,
                    "sectorId" to request.sectorId
                )
            )

            Result.success(requestId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancela ou exclui uma solicitação informando obrigatoriamente o motivo (Avarista ou Administrador).
     */
    suspend fun cancelOrDeleteRequest(
        requestId: String,
        cancellationReason: String,
        userProfile: UserProfile
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        val trimmedReason = cancellationReason.trim()
        if (trimmedReason.isBlank()) {
            return Result.failure(IllegalArgumentException("É obrigatório informar o motivo para cancelar/excluir a solicitação."))
        }
        return try {
            val now = System.currentTimeMillis()
            db.collection("withdrawalRequests").document(requestId).update(
                mapOf(
                    "status" to "CANCELLED",
                    "cancellationReason" to trimmedReason,
                    "cancelledByUid" to userProfile.uid,
                    "cancelledByName" to userProfile.name,
                    "cancelledAt" to now,
                    "updatedAt" to now
                )
            ).await()

            auditRepository.logEvent(
                action = AuditAction.CONFERENCE_CANCELLED,
                entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                entityId = requestId,
                userId = userProfile.uid,
                userName = userProfile.name,
                userRole = userProfile.role.name,
                storeId = userProfile.storeId,
                description = "Solicitação excluída/cancelada na conferência. Motivo: $trimmedReason",
                metadata = mapOf("cancellationReason" to trimmedReason, "requestId" to requestId)
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta o histórico de solicitações com base no perfil do usuário, respeitando as regras de segurança do Firestore.
     */
    suspend fun fetchHistoryRequests(userProfile: UserProfile, limitCount: Long = 50L): Result<List<WithdrawalRequest>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            var query: com.google.firebase.firestore.Query = db.collection("withdrawalRequests")

            when (userProfile.role) {
                UserRole.ADMIN -> {
                    // ADMIN pode consultar de todas as lojas
                }
                UserRole.AVARISTA -> {
                    if (userProfile.storeId.isNotBlank()) {
                        query = query.whereEqualTo("storeId", userProfile.storeId.trim())
                    }
                }
                UserRole.SETOR_LEADER -> {
                    query = query.whereEqualTo("requestedBy", userProfile.uid)
                }
            }

            val snapshot = try {
                query.orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(limitCount)
                    .get()
                    .await()
            } catch (queryEx: Exception) {
                android.util.Log.w("WithdrawalRequestRepo", "Fallback sem orderBy no Firestore para evitar erro de índice: ${queryEx.message}")
                query.limit(limitCount).get().await()
            }

            val list = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(WithdrawalRequest::class.java)?.copy(id = doc.id)
                } catch (_: Exception) {
                    null
                }
            }.sortedByDescending { it.createdAt }

            Result.success(list)
        } catch (e: Exception) {
            android.util.Log.e("WithdrawalRequestRepo", "Erro ao carregar histórico: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Consulta solicitações com filtros de status, loja, setor e período para o Centro Operacional (Etapa 17).
     */
    suspend fun fetchFilteredRequests(
        userProfile: UserProfile,
        limitCount: Long = 50L,
        filterStatus: String? = null,
        filterStoreId: String? = null,
        filterSectorId: String? = null,
        filterPeriodStart: Long? = null
    ): Result<List<WithdrawalRequest>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            var query: com.google.firebase.firestore.Query = db.collection("withdrawalRequests")

            when (userProfile.role) {
                UserRole.ADMIN, UserRole.AVARISTA -> {
                    if (!filterStoreId.isNullOrBlank() && filterStoreId != "ALL") {
                        query = query.whereEqualTo("storeId", filterStoreId)
                    }
                }
                UserRole.SETOR_LEADER -> {
                    query = query.whereEqualTo("requestedBy", userProfile.uid)
                }
            }

            if (!filterStatus.isNullOrBlank() && filterStatus != "ALL") {
                query = query.whereEqualTo("status", filterStatus)
            }

            val snapshot = query.orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limitCount)
                .get()
                .await()

            var list = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(WithdrawalRequest::class.java)?.copy(id = doc.id)
                } catch (_: Exception) {
                    null
                }
            }

            if (!filterSectorId.isNullOrBlank() && filterSectorId != "ALL") {
                list = list.filter { it.sectorId == filterSectorId }
            }
            if (filterPeriodStart != null && filterPeriodStart > 0L) {
                list = list.filter { it.createdAt >= filterPeriodStart }
            }

            Result.success(list)
        } catch (e: Exception) {
            // Em caso de erro de índice composto, efetua leitura e filtragem segura em memória
            try {
                var fallbackQuery: com.google.firebase.firestore.Query = db.collection("withdrawalRequests")
                when (userProfile.role) {
                    UserRole.AVARISTA -> fallbackQuery = fallbackQuery.whereEqualTo("storeId", userProfile.storeId)
                    UserRole.SETOR_LEADER -> fallbackQuery = fallbackQuery.whereEqualTo("requestedBy", userProfile.uid)
                    UserRole.ADMIN -> {
                        if (!filterStoreId.isNullOrBlank() && filterStoreId != "ALL") {
                            fallbackQuery = fallbackQuery.whereEqualTo("storeId", filterStoreId)
                        }
                    }
                }

                val fallbackSnapshot = fallbackQuery.limit(limitCount * 2).get().await()
                var list = fallbackSnapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(WithdrawalRequest::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (!filterStatus.isNullOrBlank() && filterStatus != "ALL") {
                    list = list.filter { it.status == filterStatus }
                }
                if (!filterSectorId.isNullOrBlank() && filterSectorId != "ALL") {
                    list = list.filter { it.sectorId == filterSectorId }
                }
                if (filterPeriodStart != null && filterPeriodStart > 0L) {
                    list = list.filter { it.createdAt >= filterPeriodStart }
                }

                Result.success(list.sortedByDescending { it.createdAt }.take(limitCount.toInt()))
            } catch (innerErr: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Executa agregação server-side COUNT no Cloud Firestore (Etapa 19).
     * Não baixa documentos nem fica preso a limit(200), escalando para milhões de registros.
     */
    suspend fun countRequests(
        userProfile: UserProfile,
        filterStatus: String? = null,
        filterStoreId: String? = null,
        filterSectorId: String? = null,
        filterPeriodStart: Long? = null
    ): Result<Long> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            var query: com.google.firebase.firestore.Query = db.collection("withdrawalRequests")

            when (userProfile.role) {
                UserRole.ADMIN, UserRole.AVARISTA -> {
                    if (!filterStoreId.isNullOrBlank() && filterStoreId != "ALL") {
                        query = query.whereEqualTo("storeId", filterStoreId)
                    }
                }
                UserRole.SETOR_LEADER -> {
                    query = query.whereEqualTo("requestedBy", userProfile.uid)
                }
            }

            if (!filterStatus.isNullOrBlank() && filterStatus != "ALL") {
                query = query.whereEqualTo("status", filterStatus)
            }
            if (!filterSectorId.isNullOrBlank() && filterSectorId != "ALL") {
                query = query.whereEqualTo("sectorId", filterSectorId)
            }
            if (filterPeriodStart != null && filterPeriodStart > 0L) {
                query = query.whereGreaterThanOrEqualTo("createdAt", filterPeriodStart)
            }

            val aggregateSnapshot = query.count().get(AggregateSource.SERVER).await()
            Result.success(aggregateSnapshot.count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtém os contadores consolidados de status (Total, Pendentes, Em Conferência, Concluídas, Divergências)
     * utilizando agregações server-side sem limites parciais.
     */
    suspend fun fetchServerStatusCounts(
        userProfile: UserProfile,
        filterStoreId: String? = null,
        filterSectorId: String? = null,
        filterPeriodStart: Long? = null
    ): Result<RequestStatusCounts> {
        return try {
            val total = countRequests(userProfile, null, filterStoreId, filterSectorId, filterPeriodStart).getOrDefault(0L)
            val pending = countRequests(userProfile, "PENDING", filterStoreId, filterSectorId, filterPeriodStart).getOrDefault(0L)
            val inConf = countRequests(userProfile, "IN_CONFERENCE", filterStoreId, filterSectorId, filterPeriodStart).getOrDefault(0L)
            val completed = countRequests(userProfile, "COMPLETED", filterStoreId, filterSectorId, filterPeriodStart).getOrDefault(0L)
            val divergence = countRequests(userProfile, "DIVERGENCE", filterStoreId, filterSectorId, filterPeriodStart).getOrDefault(0L)

            Result.success(
                RequestStatusCounts(
                    totalCount = total,
                    pendingCount = pending,
                    inConferenceCount = inConf,
                    completedCount = completed,
                    divergenceCount = divergence
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retifica uma solicitação de baixa já finalizada (COMPLETED ou DIVERGENCE).
     */
    suspend fun rectifyRequest(
        requestId: String,
        rectificationReason: String,
        correctedItems: List<WithdrawalRequestItem>,
        userProfile: UserProfile
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        
        if (userProfile.role != UserRole.AVARISTA && userProfile.role != UserRole.ADMIN) {
             return Result.failure(IllegalStateException("Apenas Avaristas e Administradores podem retificar baixas."))
        }

        val trimmedReason = rectificationReason.trim()
        if (trimmedReason.isBlank()) {
            return Result.failure(IllegalArgumentException("O motivo da retificação é obrigatório."))
        }

        return try {
            val docRef = db.collection("withdrawalRequests").document(requestId)
            val now = System.currentTimeMillis()

            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.getString("status") !in listOf("COMPLETED", "DIVERGENCE")) {
                    throw IllegalStateException("Apenas solicitações finalizadas podem ser retificadas.")
                }

                val itemsData = correctedItems.map { item ->
                     mapOf(
                        "productId" to item.productId,
                        "barcode" to item.barcode,
                        "productName" to item.productName,
                        "unit" to item.unit,
                        "requestedQuantity" to item.requestedQuantity,
                        "checkedQuantity" to (item.checkedQuantity ?: item.requestedQuantity)
                    )
                }

                transaction.update(
                    docRef,
                    mapOf(
                        "status" to "RECTIFIED",
                        "divergenceReason" to trimmedReason,
                        "items" to itemsData,
                        "updatedAt" to now,
                        "rectifiedBy" to userProfile.uid,
                        "rectifiedByName" to userProfile.name.ifEmpty { userProfile.email }
                    )
                )
            }.await()
            
            auditRepository.logEvent(
                action = AuditAction.WITHDRAWAL_RECTIFIED,
                entityType = AuditEntityType.WITHDRAWAL_REQUEST,
                entityId = requestId,
                userId = userProfile.uid,
                userName = userProfile.name.ifEmpty { userProfile.email },
                userRole = userProfile.role.name,
                storeId = userProfile.storeId,
                sectorId = userProfile.sectorId,
                description = "Solicitação retificada. Motivo: $trimmedReason",
                metadata = mapOf("rectificationReason" to trimmedReason)
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
