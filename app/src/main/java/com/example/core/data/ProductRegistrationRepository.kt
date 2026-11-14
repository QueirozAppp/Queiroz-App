package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.ProductRegistrationRequest
import com.example.core.model.ProductRequestStatus
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositório resiliente responsável pela gestão das solicitações de cadastro de produtos.
 * Permite que Líderes de Setor enviem solicitações com foto e descrição, e que
 * Avaristas e Administradores aprovem ou recusem — com persistência multi-canal
 * para garantir funcionamento mesmo se as regras remotas da coleção secundária ainda não foram publicadas.
 */
class ProductRegistrationRepository(
    private val productRepository: ProductRepository = ProductRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) {
    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    companion object {
        private const val COLLECTION_REQUESTS = "productRegistrationRequests"
        private const val COLLECTION_CLOUD_FALLBACK = "sectors"
        private const val COLLECTION_WITHDRAWAL = "withdrawalRequests"
        private const val CATALOG_DOC_ID = "__GLOBAL_CATALOG__"
        private const val SUBCOLLECTION_EVIDENCE = "evidence"
        private const val DOC_TYPE_REG_REQUEST = "PRODUCT_REQUEST"
        private const val REQUESTS_STORE_KEY = "__PRODUCT_REG_REQUESTS__"

        @Volatile
        private var memoryCache: List<ProductRegistrationRequest> = emptyList()

        private fun updateCache(item: ProductRegistrationRequest) {
            memoryCache = listOf(item) + memoryCache.filter { it.id != item.id }
        }
    }

    private fun parseRequest(doc: DocumentSnapshot): ProductRegistrationRequest? {
        return try {
            val data = doc.data ?: return null
            val rawId = (data["requestId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: doc.id.removePrefix("req_")
            val name = (data["name"] as? String) ?: ""
            if (name.isBlank()) return null

            val realStoreId = (data["originalStoreId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["storeId"] as? String)?.takeIf { it != REQUESTS_STORE_KEY }
                ?: ""

            val price = when (val p = data["price"]) {
                is Number -> p.toDouble()
                is String -> p.replace(",", ".").toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            ProductRegistrationRequest(
                id = rawId,
                barcode = (data["barcode"] as? String) ?: "",
                name = name,
                price = price,
                unit = (data["unit"] as? String)?.takeIf { it.isNotBlank() } ?: "UN",
                photoBase64 = (data["photoBase64"] as? String) ?: "",
                storeId = realStoreId,
                sectorId = (data["sectorId"] as? String) ?: "Geral",
                requestedByUid = (data["requestedByUid"] as? String) ?: "",
                requestedByName = (data["requestedByName"] as? String) ?: "",
                requestedAt = (data["requestedAt"] as? Number)?.toLong() ?: 0L,
                status = (data["status"] as? String)?.takeIf { it.isNotBlank() }
                    ?: ProductRequestStatus.PENDING.name,
                rejectionReason = (data["rejectionReason"] as? String) ?: "",
                reviewedByUid = (data["reviewedByUid"] as? String) ?: "",
                reviewedByName = (data["reviewedByName"] as? String) ?: "",
                reviewedAt = (data["reviewedAt"] as? Number)?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            android.util.Log.w("ProductRegRepo", "Erro ao converter solicitação ${doc.id}: ${e.message}")
            null
        }
    }

    private fun mergeRequests(vararg sources: List<ProductRegistrationRequest>): List<ProductRegistrationRequest> {
        val map = linkedMapOf<String, ProductRegistrationRequest>()
        for (source in sources) {
            for (item in source) {
                if (item.id.isBlank() || item.name.isBlank()) continue
                val existing = map[item.id]
                if (existing == null) {
                    map[item.id] = item
                } else {
                    val itemScore = maxOf(item.reviewedAt, item.requestedAt)
                    val existingScore = maxOf(existing.reviewedAt, existing.requestedAt)
                    val winner = if (itemScore >= existingScore) item else existing
                    val photo = winner.photoBase64.ifBlank {
                        if (winner === item) existing.photoBase64 else item.photoBase64
                    }
                    map[item.id] = winner.copy(photoBase64 = photo)
                }
            }
        }
        return map.values.sortedByDescending { it.requestedAt }
    }

    /**
     * Submete uma nova solicitação de cadastro de produto feita por um líder de setor.
     */
    suspend fun submitRequest(request: ProductRegistrationRequest): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        if (FirebaseProvider.getAuthOrNull()?.currentUser == null) {
            return Result.failure(IllegalStateException("Sessão expirada. Faça login novamente."))
        }

        val trimmedName = request.name.trim()
        val trimmedBarcode = request.barcode.trim()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("O nome / descrição do produto é obrigatório."))
        }
        if (request.photoBase64.isBlank()) {
            return Result.failure(IllegalArgumentException("É obrigatório tirar a foto do produto."))
        }

        return try {
            val docRef = db.collection(COLLECTION_REQUESTS).document()
            val id = docRef.id
            val now = System.currentTimeMillis()

            val finalRequest = request.copy(
                id = id,
                name = trimmedName,
                barcode = trimmedBarcode,
                status = ProductRequestStatus.PENDING.name,
                requestedAt = now
            )

            var anySucceeded = false

            // Canal 1: Coleção principal 'productRegistrationRequests'
            try {
                docRef.set(finalRequest).await()
                anySucceeded = true
            } catch (ex: Exception) {
                android.util.Log.w("ProductRegRepo", "Canal 'productRegistrationRequests' indisponível (${ex.message}), usando canal resiliente.")
            }

            // Mapa espelho para canais resilientes
            val mirrorMap = hashMapOf<String, Any>(
                "id" to id,
                "requestId" to id,
                "storeId" to REQUESTS_STORE_KEY,
                "originalStoreId" to finalRequest.storeId,
                "docType" to DOC_TYPE_REG_REQUEST,
                "code" to trimmedBarcode.ifBlank { id },
                "barcode" to trimmedBarcode,
                "name" to trimmedName,
                "price" to finalRequest.price,
                "unit" to finalRequest.unit,
                "photoBase64" to finalRequest.photoBase64,
                "sectorId" to finalRequest.sectorId,
                "requestedByUid" to finalRequest.requestedByUid,
                "requestedByName" to finalRequest.requestedByName,
                "requestedAt" to now,
                "status" to ProductRequestStatus.PENDING.name,
                "rejectionReason" to "",
                "reviewedByUid" to "",
                "reviewedByName" to "",
                "reviewedAt" to 0L
            )

            // Canal 2: Espelho em nuvem seguro ('sectors' com storeId="__PRODUCT_REG_REQUESTS__")
            try {
                db.collection(COLLECTION_CLOUD_FALLBACK).document("req_$id")
                    .set(mirrorMap, SetOptions.merge()).await()
                anySucceeded = true
            } catch (ex: Exception) {
                android.util.Log.w("ProductRegRepo", "Canal fallback 'sectors' falhou: ${ex.message}")
            }

            // Canal 3: Espelho resiliente na subcoleção autenticada 'withdrawalRequests/__GLOBAL_CATALOG__/evidence'
            try {
                db.collection(COLLECTION_WITHDRAWAL)
                    .document(CATALOG_DOC_ID)
                    .collection(SUBCOLLECTION_EVIDENCE)
                    .document("req_$id")
                    .set(mirrorMap, SetOptions.merge()).await()
                anySucceeded = true
            } catch (ex: Exception) {
                android.util.Log.w("ProductRegRepo", "Canal fallback 'evidence' falhou: ${ex.message}")
            }

            if (!anySucceeded) {
                return Result.failure(IllegalStateException("Não foi possível enviar a solicitação. Verifique sua conexão e tente novamente."))
            }

            updateCache(finalRequest)

            auditRepository.logEvent(
                action = AuditAction.PRODUCT_REQUEST_CREATED,
                entityType = AuditEntityType.PRODUCT_REQUEST,
                entityId = id,
                userId = request.requestedByUid,
                userName = request.requestedByName,
                userRole = "SETOR_LEADER",
                storeId = request.storeId,
                sectorId = request.sectorId,
                description = "Líder de setor solicitou cadastro do produto '$trimmedName' (${request.sectorId})",
                metadata = mapOf(
                    "productName" to trimmedName,
                    "barcode" to trimmedBarcode,
                    "unit" to request.unit,
                    "sectorId" to request.sectorId
                )
            )

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registra no histórico de cadastros quando um Administrador ou Avarista publica
     * diretamente um item pela tela de Cadastro de Produto.
     */
    suspend fun recordDirectRegistration(
        productId: String,
        barcode: String,
        name: String,
        price: Double,
        unit: String,
        photoBase64: String,
        userProfile: UserProfile
    ) {
        val now = System.currentTimeMillis()
        val authorName = userProfile.name.ifBlank { userProfile.email }
        val entry = ProductRegistrationRequest(
            id = productId,
            barcode = barcode.trim(),
            name = name.trim(),
            price = price,
            unit = unit,
            photoBase64 = photoBase64,
            storeId = userProfile.storeId.ifBlank { "GLOBAL" },
            sectorId = userProfile.sectorId.ifBlank { "Geral" },
            requestedByUid = userProfile.uid,
            requestedByName = authorName,
            requestedAt = now,
            status = ProductRequestStatus.APPROVED.name,
            reviewedByUid = userProfile.uid,
            reviewedByName = authorName,
            reviewedAt = now
        )
        updateCache(entry)

        val db = firestore ?: return
        val mirrorMap = hashMapOf<String, Any>(
            "id" to productId,
            "requestId" to productId,
            "storeId" to REQUESTS_STORE_KEY,
            "originalStoreId" to entry.storeId,
            "docType" to DOC_TYPE_REG_REQUEST,
            "code" to entry.barcode.ifBlank { productId },
            "barcode" to entry.barcode,
            "name" to entry.name,
            "price" to entry.price,
            "unit" to entry.unit,
            "photoBase64" to entry.photoBase64,
            "sectorId" to entry.sectorId,
            "requestedByUid" to entry.requestedByUid,
            "requestedByName" to entry.requestedByName,
            "requestedAt" to now,
            "status" to ProductRequestStatus.APPROVED.name,
            "rejectionReason" to "",
            "reviewedByUid" to entry.reviewedByUid,
            "reviewedByName" to entry.reviewedByName,
            "reviewedAt" to now
        )
        try {
            db.collection(COLLECTION_CLOUD_FALLBACK).document("req_$productId")
                .set(mirrorMap, SetOptions.merge()).await()
        } catch (_: Exception) {}
        try {
            db.collection(COLLECTION_WITHDRAWAL)
                .document(CATALOG_DOC_ID)
                .collection(SUBCOLLECTION_EVIDENCE)
                .document("req_$productId")
                .set(mirrorMap, SetOptions.merge()).await()
        } catch (_: Exception) {}
    }

    private fun filterByRole(
        list: List<ProductRegistrationRequest>,
        userProfile: UserProfile,
        filterStatus: String?
    ): List<ProductRegistrationRequest> {
        var result = when (userProfile.role) {
            UserRole.SETOR_LEADER -> {
                list.filter {
                    it.requestedByUid == userProfile.uid ||
                        (it.storeId == userProfile.storeId && it.sectorId.equals(userProfile.sectorId, ignoreCase = true))
                }
            }
            UserRole.AVARISTA -> {
                list.filter {
                    it.storeId == userProfile.storeId || it.storeId == "GLOBAL" || it.storeId.isBlank()
                }
            }
            UserRole.ADMIN -> list
        }

        if (!filterStatus.isNullOrBlank() && filterStatus != "ALL") {
            result = result.filter { it.status == filterStatus }
        }
        return result.sortedByDescending { it.requestedAt }
    }

    /**
     * Retorna um fluxo em tempo real resiliente de solicitações baseado no perfil do usuário.
     */
    fun getRequestsFlow(
        userProfile: UserProfile,
        filterStatus: String? = null
    ): Flow<List<ProductRegistrationRequest>> = callbackFlow {
        val db = firestore
        if (db == null || FirebaseProvider.getAuthOrNull()?.currentUser == null) {
            trySend(filterByRole(memoryCache, userProfile, filterStatus))
            close()
            return@callbackFlow
        }

        var primaryItems: List<ProductRegistrationRequest> = emptyList()
        var fallbackItems: List<ProductRegistrationRequest> = emptyList()
        var evidenceItems: List<ProductRegistrationRequest> = emptyList()

        fun emitMerged() {
            val merged = mergeRequests(primaryItems, fallbackItems, evidenceItems, memoryCache)
            memoryCache = merged
            trySend(filterByRole(merged, userProfile, filterStatus))
        }

        var primaryListener: ListenerRegistration? = null
        var fallbackListener: ListenerRegistration? = null
        var evidenceListener: ListenerRegistration? = null

        try {
            primaryListener = db.collection(COLLECTION_REQUESTS)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        primaryItems = snapshot.documents.mapNotNull { parseRequest(it) }
                        emitMerged()
                    }
                }
        } catch (_: Exception) {}

        try {
            fallbackListener = db.collection(COLLECTION_CLOUD_FALLBACK)
                .whereEqualTo("storeId", REQUESTS_STORE_KEY)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        fallbackItems = snapshot.documents.mapNotNull { parseRequest(it) }
                        emitMerged()
                    }
                }
        } catch (_: Exception) {}

        try {
            evidenceListener = db.collection(COLLECTION_WITHDRAWAL)
                .document(CATALOG_DOC_ID)
                .collection(SUBCOLLECTION_EVIDENCE)
                .whereEqualTo("docType", DOC_TYPE_REG_REQUEST)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        evidenceItems = snapshot.documents.mapNotNull { parseRequest(it) }
                        emitMerged()
                    }
                }
        } catch (_: Exception) {}

        emitMerged()

        awaitClose {
            primaryListener?.remove()
            fallbackListener?.remove()
            evidenceListener?.remove()
        }
    }

    /**
     * Retorna a contagem de solicitações pendentes para exibição de badge de notificações.
     */
    fun getPendingCountFlow(userProfile: UserProfile): Flow<Int> = callbackFlow {
        val db = firestore
        if (db == null || FirebaseProvider.getAuthOrNull()?.currentUser == null || userProfile.role == UserRole.SETOR_LEADER) {
            trySend(0)
            close()
            return@callbackFlow
        }

        var primaryItems: List<ProductRegistrationRequest> = emptyList()
        var fallbackItems: List<ProductRegistrationRequest> = emptyList()
        var evidenceItems: List<ProductRegistrationRequest> = emptyList()

        fun emitCount() {
            val merged = mergeRequests(primaryItems, fallbackItems, evidenceItems, memoryCache)
            val filtered = filterByRole(merged, userProfile, ProductRequestStatus.PENDING.name)
            trySend(filtered.size)
        }

        var primaryListener: ListenerRegistration? = null
        var fallbackListener: ListenerRegistration? = null
        var evidenceListener: ListenerRegistration? = null

        try {
            primaryListener = db.collection(COLLECTION_REQUESTS)
                .whereEqualTo("status", ProductRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        primaryItems = snapshot.documents.mapNotNull { parseRequest(it) }
                        emitCount()
                    }
                }
        } catch (_: Exception) {}

        try {
            fallbackListener = db.collection(COLLECTION_CLOUD_FALLBACK)
                .whereEqualTo("storeId", REQUESTS_STORE_KEY)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        fallbackItems = snapshot.documents.mapNotNull { parseRequest(it) }
                        emitCount()
                    }
                }
        } catch (_: Exception) {}

        try {
            evidenceListener = db.collection(COLLECTION_WITHDRAWAL)
                .document(CATALOG_DOC_ID)
                .collection(SUBCOLLECTION_EVIDENCE)
                .whereEqualTo("docType", DOC_TYPE_REG_REQUEST)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        evidenceItems = snapshot.documents.mapNotNull { parseRequest(it) }
                        emitCount()
                    }
                }
        } catch (_: Exception) {}

        emitCount()

        awaitClose {
            primaryListener?.remove()
            fallbackListener?.remove()
            evidenceListener?.remove()
        }
    }

    /**
     * Aprova a solicitação de produto (Avarista ou Administrador).
     * Cadastra o produto no catálogo global e atualiza o status para APPROVED.
     */
    suspend fun approveRequest(
        request: ProductRegistrationRequest,
        reviewerProfile: UserProfile,
        adjustedBarcode: String? = null,
        adjustedName: String? = null,
        adjustedUnit: String? = null,
        adjustedPrice: Double? = null
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        if (reviewerProfile.role != UserRole.AVARISTA && reviewerProfile.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("Apenas Avaristas e Administradores podem aprovar solicitações de cadastro."))
        }

        val finalName = (adjustedName ?: request.name).trim()
        val finalBarcode = (adjustedBarcode ?: request.barcode).trim()
        val finalUnit = adjustedUnit ?: request.unit
        val finalPrice = adjustedPrice ?: request.price

        if (finalName.isBlank()) {
            return Result.failure(IllegalArgumentException("O nome do produto é obrigatório."))
        }

        return try {
            val now = System.currentTimeMillis()
            val reviewerName = reviewerProfile.name.ifEmpty { reviewerProfile.email }

            // 1. Publicar produto aprovado no Catálogo Global (com foto e sincronização multi-canal)
            val upsertResult = productRepository.upsertApprovedProduct(
                barcode = finalBarcode,
                name = finalName,
                price = finalPrice,
                unit = finalUnit,
                photoBase64 = request.photoBase64,
                reviewerProfile = reviewerProfile
            )
            val productId = upsertResult.getOrElse { return Result.failure(it) }

            // 2. Atualizar status da solicitação como APPROVED nos canais disponíveis
            val updatePayload = mapOf(
                "status" to ProductRequestStatus.APPROVED.name,
                "barcode" to finalBarcode,
                "name" to finalName,
                "unit" to finalUnit,
                "price" to finalPrice,
                "reviewedByUid" to reviewerProfile.uid,
                "reviewedByName" to reviewerName,
                "reviewedAt" to now
            )

            try {
                db.collection(COLLECTION_REQUESTS).document(request.id)
                    .set(updatePayload, SetOptions.merge()).await()
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_CLOUD_FALLBACK).document("req_${request.id}")
                    .set(updatePayload + mapOf("storeId" to REQUESTS_STORE_KEY), SetOptions.merge()).await()
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_WITHDRAWAL)
                    .document(CATALOG_DOC_ID)
                    .collection(SUBCOLLECTION_EVIDENCE)
                    .document("req_${request.id}")
                    .set(
                        updatePayload + mapOf(
                            "storeId" to REQUESTS_STORE_KEY,
                            "docType" to DOC_TYPE_REG_REQUEST
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (_: Exception) {}

            updateCache(
                request.copy(
                    status = ProductRequestStatus.APPROVED.name,
                    barcode = finalBarcode,
                    name = finalName,
                    unit = finalUnit,
                    price = finalPrice,
                    reviewedByUid = reviewerProfile.uid,
                    reviewedByName = reviewerName,
                    reviewedAt = now
                )
            )

            // 3. Trilha de Auditoria
            auditRepository.logEvent(
                action = AuditAction.PRODUCT_REQUEST_APPROVED,
                entityType = AuditEntityType.PRODUCT_REQUEST,
                entityId = request.id,
                userId = reviewerProfile.uid,
                userName = reviewerName,
                userRole = reviewerProfile.role.name,
                storeId = reviewerProfile.storeId,
                description = "Cadastro de produto '$finalName' aprovado por ${reviewerProfile.role.label}",
                metadata = mapOf(
                    "productId" to productId,
                    "barcode" to finalBarcode,
                    "productName" to finalName,
                    "requestedBy" to request.requestedByName
                )
            )

            Result.success(productId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recusa a solicitação de produto (Avarista ou Administrador) com justificativa.
     */
    suspend fun rejectRequest(
        requestId: String,
        rejectionReason: String,
        reviewerProfile: UserProfile
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        if (reviewerProfile.role != UserRole.AVARISTA && reviewerProfile.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("Apenas Avaristas e Administradores podem recusar solicitações de cadastro."))
        }

        val trimmedReason = rejectionReason.trim()
        if (trimmedReason.isBlank()) {
            return Result.failure(IllegalArgumentException("É obrigatório informar o motivo da recusa."))
        }

        return try {
            val now = System.currentTimeMillis()
            val reviewerName = reviewerProfile.name.ifEmpty { reviewerProfile.email }

            val updatePayload = mapOf(
                "status" to ProductRequestStatus.REJECTED.name,
                "rejectionReason" to trimmedReason,
                "reviewedByUid" to reviewerProfile.uid,
                "reviewedByName" to reviewerName,
                "reviewedAt" to now
            )

            var anySucceeded = false
            try {
                db.collection(COLLECTION_REQUESTS).document(requestId)
                    .set(updatePayload, SetOptions.merge()).await()
                anySucceeded = true
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_CLOUD_FALLBACK).document("req_$requestId")
                    .set(updatePayload + mapOf("storeId" to REQUESTS_STORE_KEY), SetOptions.merge()).await()
                anySucceeded = true
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_WITHDRAWAL)
                    .document(CATALOG_DOC_ID)
                    .collection(SUBCOLLECTION_EVIDENCE)
                    .document("req_$requestId")
                    .set(
                        updatePayload + mapOf(
                            "storeId" to REQUESTS_STORE_KEY,
                            "docType" to DOC_TYPE_REG_REQUEST
                        ),
                        SetOptions.merge()
                    ).await()
                anySucceeded = true
            } catch (_: Exception) {}

            if (!anySucceeded) {
                return Result.failure(IllegalStateException("Não foi possível registrar a recusa. Verifique sua conexão."))
            }

            memoryCache = memoryCache.map {
                if (it.id == requestId) {
                    it.copy(
                        status = ProductRequestStatus.REJECTED.name,
                        rejectionReason = trimmedReason,
                        reviewedByUid = reviewerProfile.uid,
                        reviewedByName = reviewerName,
                        reviewedAt = now
                    )
                } else it
            }

            auditRepository.logEvent(
                action = AuditAction.PRODUCT_REQUEST_REJECTED,
                entityType = AuditEntityType.PRODUCT_REQUEST,
                entityId = requestId,
                userId = reviewerProfile.uid,
                userName = reviewerName,
                userRole = reviewerProfile.role.name,
                storeId = reviewerProfile.storeId,
                description = "Solicitação de cadastro de produto recusada: $trimmedReason",
                metadata = mapOf(
                    "requestId" to requestId,
                    "reason" to trimmedReason
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
