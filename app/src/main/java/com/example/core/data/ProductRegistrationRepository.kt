package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.Product
import com.example.core.model.ProductRegistrationRequest
import com.example.core.model.ProductRequestStatus
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositório responsável pela gestão das solicitações de cadastro de produtos.
 * Permite que Líderes de Setor enviem solicitações com foto e descrição, e que
 * Avaristas e Administradores aprovem ou recusem.
 */
class ProductRegistrationRepository(
    private val productRepository: ProductRepository = ProductRepository(),
    private val auditRepository: AuditRepository = AuditRepository()
) {
    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    /**
     * Submete uma nova solicitação de cadastro de produto feita por um líder de setor.
     */
    suspend fun submitRequest(request: ProductRegistrationRequest): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))

        val trimmedName = request.name.trim()
        val trimmedBarcode = request.barcode.trim()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("O nome / descrição do produto é obrigatório."))
        }
        if (request.photoBase64.isBlank()) {
            return Result.failure(IllegalArgumentException("É obrigatório tirar a foto do produto."))
        }

        return try {
            val docRef = db.collection("productRegistrationRequests").document()
            val id = docRef.id
            val now = System.currentTimeMillis()

            val finalRequest = request.copy(
                id = id,
                name = trimmedName,
                barcode = trimmedBarcode,
                status = ProductRequestStatus.PENDING.name,
                requestedAt = now
            )

            docRef.set(finalRequest).await()

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
     * Retorna um fluxo em tempo real de solicitações baseado no perfil do usuário.
     * - SETOR_LEADER: vê as solicitações feitas por ele (ou do seu setor)
     * - AVARISTA: vê as solicitações da sua loja
     * - ADMIN: vê todas as solicitações da rede
     */
    fun getRequestsFlow(
        userProfile: UserProfile,
        filterStatus: String? = null
    ): Flow<List<ProductRegistrationRequest>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("productRegistrationRequests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                var list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ProductRegistrationRequest::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Filtrar por perfil
                when (userProfile.role) {
                    UserRole.SETOR_LEADER -> {
                        list = list.filter { it.requestedByUid == userProfile.uid || (it.storeId == userProfile.storeId && it.sectorId == userProfile.sectorId) }
                    }
                    UserRole.AVARISTA -> {
                        list = list.filter { it.storeId == userProfile.storeId }
                    }
                    UserRole.ADMIN -> {
                        // Admin visualiza todos
                    }
                }

                if (!filterStatus.isNullOrBlank() && filterStatus != "ALL") {
                    list = list.filter { it.status == filterStatus }
                }

                // Mais recentes primeiro
                trySend(list.sortedByDescending { it.requestedAt })
            }

        awaitClose { listener.remove() }
    }

    /**
     * Retorna a contagem de solicitações pendentes para exibição de badge de notificações.
     */
    fun getPendingCountFlow(userProfile: UserProfile): Flow<Int> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(0)
            close()
            return@callbackFlow
        }

        val listener = db.collection("productRegistrationRequests")
            .whereEqualTo("status", ProductRequestStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }

                var list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ProductRegistrationRequest::class.java)
                } ?: emptyList()

                when (userProfile.role) {
                    UserRole.SETOR_LEADER -> {
                        list = list.filter { it.requestedByUid == userProfile.uid }
                    }
                    UserRole.AVARISTA -> {
                        list = list.filter { it.storeId == userProfile.storeId }
                    }
                    UserRole.ADMIN -> {
                        // Todos
                    }
                }

                trySend(list.size)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Aprova a solicitação de produto (Avarista ou Administrador).
     * Cadastra o produto no catálogo global `products` e atualiza o status para APPROVED.
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

            // 1. Cadastrar ou reativar produto no Catálogo Global
            var productId = ""
            if (finalBarcode.isNotBlank()) {
                val existingQuery = db.collection("products")
                    .whereEqualTo("barcode", finalBarcode)
                    .get()
                    .await()

                if (!existingQuery.isEmpty) {
                    val existingDoc = existingQuery.documents.first()
                    productId = existingDoc.id
                    // Reativar se inativo
                    db.collection("products").document(productId).update(
                        mapOf(
                            "name" to finalName,
                            "price" to finalPrice,
                            "unit" to finalUnit,
                            "active" to true,
                            "updatedAt" to now
                        )
                    ).await()
                }
            }

            if (productId.isBlank()) {
                val newProdDoc = db.collection("products").document()
                productId = newProdDoc.id
                val product = Product(
                    id = productId,
                    barcode = finalBarcode,
                    name = finalName,
                    price = finalPrice,
                    unit = finalUnit,
                    active = true,
                    createdAt = now,
                    updatedAt = now
                )
                newProdDoc.set(product).await()
            }

            // 2. Atualizar a solicitação como Aprovada
            db.collection("productRegistrationRequests").document(request.id).update(
                mapOf(
                    "status" to ProductRequestStatus.APPROVED.name,
                    "barcode" to finalBarcode,
                    "name" to finalName,
                    "unit" to finalUnit,
                    "price" to finalPrice,
                    "reviewedByUid" to reviewerProfile.uid,
                    "reviewedByName" to reviewerProfile.name.ifEmpty { reviewerProfile.email },
                    "reviewedAt" to now
                )
            ).await()

            // 3. Trilha de Auditoria
            auditRepository.logEvent(
                action = AuditAction.PRODUCT_REQUEST_APPROVED,
                entityType = AuditEntityType.PRODUCT_REQUEST,
                entityId = request.id,
                userId = reviewerProfile.uid,
                userName = reviewerProfile.name.ifEmpty { reviewerProfile.email },
                userRole = reviewerProfile.role.name,
                storeId = reviewerProfile.storeId,
                description = "Cadastro de produto '${finalName}' aprovado por ${reviewerProfile.role.label}",
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

            db.collection("productRegistrationRequests").document(requestId).update(
                mapOf(
                    "status" to ProductRequestStatus.REJECTED.name,
                    "rejectionReason" to trimmedReason,
                    "reviewedByUid" to reviewerProfile.uid,
                    "reviewedByName" to reviewerProfile.name.ifEmpty { reviewerProfile.email },
                    "reviewedAt" to now
                )
            ).await()

            auditRepository.logEvent(
                action = AuditAction.PRODUCT_REQUEST_REJECTED,
                entityType = AuditEntityType.PRODUCT_REQUEST,
                entityId = requestId,
                userId = reviewerProfile.uid,
                userName = reviewerProfile.name.ifEmpty { reviewerProfile.email },
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
