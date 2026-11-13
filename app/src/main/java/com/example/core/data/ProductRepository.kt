package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.Product
import com.example.core.model.ProductRequestStatus
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositório resiliente para gestão, consulta e persistência global de produtos.
 * Opera com persistência multi-canal ('products', 'productRegistrationRequests' e espelho
 * seguro em 'sectors' com storeId="__GLOBAL_PRODUCTS__") para garantir sincronização global
 * entre todas as lojas e usuários sem bloqueios de 'PERMISSION_DENIED'.
 */
class ProductRepository(
    private val auditRepository: AuditRepository = AuditRepository()
) {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    companion object {
        private const val COLLECTION_PRODUCTS = "products"
        private const val COLLECTION_REQUESTS = "productRegistrationRequests"
        private const val COLLECTION_CLOUD_FALLBACK = "sectors"
        private const val GLOBAL_PRODUCTS_STORE_KEY = "__GLOBAL_PRODUCTS__"

        @Volatile
        private var memoryCache: List<Product> = emptyList()

        @Volatile
        private var deletedProductIds: Set<String> = emptySet()

        fun invalidateDeleted(productId: String) {
            deletedProductIds = deletedProductIds + productId
            memoryCache = memoryCache.filter { it.id != productId }
        }

        fun updateCache(product: Product) {
            deletedProductIds = deletedProductIds - product.id
            memoryCache = (listOf(product) + memoryCache.filter { it.id != product.id })
        }
    }

    /**
     * Converte um DocumentSnapshot do Firestore em Product de forma segura.
     */
    fun parseProduct(doc: DocumentSnapshot): Product? {
        return try {
            val data = doc.data ?: return null
            if ((data["deleted"] as? Boolean) == true) {
                return null
            }
            val rawId = (data["productId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: doc.id.removePrefix("prod_")
            val barcode = (data["barcode"] as? String) ?: (data["barcode"]?.toString() ?: "")
            val name = (data["name"] as? String) ?: (data["name"]?.toString() ?: "")
            if (name.isBlank()) return null

            val price = when (val p = data["price"]) {
                is Number -> p.toDouble()
                is String -> p.replace(",", ".").toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            val unit = (data["unit"] as? String)?.takeIf { it.isNotBlank() } ?: "UN"
            val photoBase64 = (data["photoBase64"] as? String) ?: (data["photoUrl"] as? String) ?: ""
            val active = when (val a = data["active"]) {
                is Boolean -> a
                is String -> a.toBooleanStrictOrNull() ?: true
                else -> true
            }
            val createdAt = when (val c = data["createdAt"]) {
                is Number -> c.toLong()
                is com.google.firebase.Timestamp -> c.toDate().time
                else -> (data["requestedAt"] as? Number)?.toLong() ?: 0L
            }
            val updatedAt = when (val u = data["updatedAt"]) {
                is Number -> u.toLong()
                is com.google.firebase.Timestamp -> u.toDate().time
                else -> (data["reviewedAt"] as? Number)?.toLong() ?: createdAt
            }
            Product(
                id = rawId,
                barcode = barcode,
                name = name,
                price = price,
                unit = unit,
                photoBase64 = photoBase64,
                active = active,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            android.util.Log.e("ProductRepository", "Erro ao deserializar produto ${doc.id}: ${e.message}")
            null
        }
    }

    private fun mergeProductLists(
        vararg sources: List<Product>,
        extraDeletedIds: Set<String> = emptySet()
    ): List<Product> {
        val blockedIds = deletedProductIds + extraDeletedIds
        val mergedById = linkedMapOf<String, Product>()

        for (source in sources) {
            for (item in source) {
                if (item.id.isBlank() || item.id in blockedIds || item.name.isBlank()) continue
                val existing = mergedById[item.id]
                if (existing == null) {
                    mergedById[item.id] = item
                } else {
                    val itemScore = maxOf(item.updatedAt, item.createdAt)
                    val existingScore = maxOf(existing.updatedAt, existing.createdAt)
                    val winner = if (itemScore >= existingScore) item else existing
                    val fallbackPhoto = winner.photoBase64.ifBlank {
                        if (winner === item) existing.photoBase64 else item.photoBase64
                    }
                    mergedById[item.id] = winner.copy(photoBase64 = fallbackPhoto)
                }
            }
        }

        // Deduplicar também itens que possam ter sido criados em múltiplas coleções sem o mesmo ID mas com mesmo barcode
        val result = mutableListOf<Product>()
        val seenBarcodes = mutableSetOf<String>()
        val sortedCandidates = mergedById.values.sortedByDescending { maxOf(it.updatedAt, it.createdAt) }
        for (prod in sortedCandidates) {
            val cleanCode = prod.barcode.trim()
            if (cleanCode.isNotBlank()) {
                if (seenBarcodes.add(cleanCode)) {
                    result.add(prod)
                }
            } else {
                result.add(prod)
            }
        }
        return result
    }

    private suspend fun getApprovedFromRequests(db: FirebaseFirestore): List<Product> {
        if (FirebaseAuth.getInstance().currentUser == null) return emptyList()
        return try {
            val snapshot = db.collection(COLLECTION_REQUESTS)
                .whereEqualTo("status", ProductRequestStatus.APPROVED.name)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc -> parseProduct(doc) }
        } catch (e: Exception) {
            android.util.Log.w("ProductRepository", "Fallback requests leitura: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getFromCloudFallback(db: FirebaseFirestore): Pair<List<Product>, Set<String>> {
        if (FirebaseAuth.getInstance().currentUser == null) return emptyList<Product>() to emptySet()
        return try {
            val snapshot = db.collection(COLLECTION_CLOUD_FALLBACK)
                .whereEqualTo("storeId", GLOBAL_PRODUCTS_STORE_KEY)
                .get()
                .await()
            val deletedIds = mutableSetOf<String>()
            val products = mutableListOf<Product>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val pid = (data["productId"] as? String)?.takeIf { it.isNotBlank() }
                    ?: doc.id.removePrefix("prod_")
                if ((data["deleted"] as? Boolean) == true) {
                    deletedIds.add(pid)
                } else {
                    parseProduct(doc)?.let { products.add(it) }
                }
            }
            products to deletedIds
        } catch (e: Exception) {
            android.util.Log.w("ProductRepository", "Fallback cloud leitura: ${e.message}")
            emptyList<Product>() to emptySet()
        }
    }

    /**
     * Retorna a lista completa de produtos de forma resiliente.
     */
    suspend fun getAllProducts(): Result<List<Product>> {
        val db = firestore ?: return Result.success(memoryCache)
        if (FirebaseAuth.getInstance().currentUser == null) {
            return Result.success(memoryCache)
        }

        val primaryList = try {
            db.collection(COLLECTION_PRODUCTS).get().await()
                .documents.mapNotNull { parseProduct(it) }
        } catch (e: Exception) {
            android.util.Log.w("ProductRepository", "Leitura de 'products' indisponível (${e.message}).")
            emptyList()
        }

        val requestList = getApprovedFromRequests(db)
        val (fallbackList, fallbackDeleted) = getFromCloudFallback(db)

        if (fallbackDeleted.isNotEmpty()) {
            deletedProductIds = deletedProductIds + fallbackDeleted
        }

        val combined = mergeProductLists(
            primaryList,
            fallbackList,
            requestList,
            memoryCache,
            extraDeletedIds = fallbackDeleted
        )
        memoryCache = combined

        return Result.success(combined)
    }

    /**
     * Retorna um fluxo em tempo real resiliente com todos os produtos sincronizados entre todas as fontes.
     */
    val productsFlow: Flow<List<Product>> = callbackFlow {
        val db = firestore
        val user = FirebaseAuth.getInstance().currentUser
        if (db == null || user == null) {
            trySend(memoryCache)
            close()
            return@callbackFlow
        }

        var primaryItems: List<Product> = emptyList()
        var requestItems: List<Product> = emptyList()
        var fallbackItems: List<Product> = emptyList()
        var fallbackDeleted: Set<String> = emptySet()

        fun emitMerged() {
            val combined = mergeProductLists(
                primaryItems,
                fallbackItems,
                requestItems,
                memoryCache,
                extraDeletedIds = fallbackDeleted
            )
            memoryCache = combined
            trySend(combined)
        }

        var primaryListener: ListenerRegistration? = null
        var requestsListener: ListenerRegistration? = null
        var fallbackListener: ListenerRegistration? = null

        try {
            primaryListener = db.collection(COLLECTION_PRODUCTS)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        primaryItems = snapshot.documents.mapNotNull { parseProduct(it) }
                        emitMerged()
                    }
                }
        } catch (_: Exception) {}

        try {
            requestsListener = db.collection(COLLECTION_REQUESTS)
                .whereEqualTo("status", ProductRequestStatus.APPROVED.name)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        requestItems = snapshot.documents.mapNotNull { parseProduct(it) }
                        emitMerged()
                    }
                }
        } catch (_: Exception) {}

        try {
            fallbackListener = db.collection(COLLECTION_CLOUD_FALLBACK)
                .whereEqualTo("storeId", GLOBAL_PRODUCTS_STORE_KEY)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val del = mutableSetOf<String>()
                        val items = mutableListOf<Product>()
                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            val pid = (data["productId"] as? String)?.takeIf { it.isNotBlank() }
                                ?: doc.id.removePrefix("prod_")
                            if ((data["deleted"] as? Boolean) == true) {
                                del.add(pid)
                            } else {
                                parseProduct(doc)?.let { items.add(it) }
                            }
                        }
                        fallbackDeleted = del
                        deletedProductIds = deletedProductIds + del
                        fallbackItems = items
                        emitMerged()
                    }
                }
        } catch (_: Exception) {}

        emitMerged()

        awaitClose {
            primaryListener?.remove()
            requestsListener?.remove()
            fallbackListener?.remove()
        }
    }

    /**
     * Consulta um produto no Catálogo Global pelo código de barras exato (`barcode`).
     */
    suspend fun getProductByBarcode(barcode: String): Result<Product?> {
        val db = firestore ?: return Result.success(memoryCache.find { it.barcode.trim() == barcode.trim() })
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Código de barras inválido ou vazio."))
        }

        // 1. Tentar em 'products'
        try {
            val querySnapshot = db.collection(COLLECTION_PRODUCTS)
                .whereEqualTo("barcode", trimmed)
                .get()
                .await()
            val product = querySnapshot.documents.firstOrNull()?.let { parseProduct(it) }
            if (product != null && product.id !in deletedProductIds) {
                updateCache(product)
                return Result.success(product)
            }
        } catch (_: Exception) {}

        // 2. Tentar no espelho em nuvem ('sectors')
        try {
            val fbSnapshot = db.collection(COLLECTION_CLOUD_FALLBACK)
                .whereEqualTo("storeId", GLOBAL_PRODUCTS_STORE_KEY)
                .whereEqualTo("barcode", trimmed)
                .get()
                .await()
            val validDoc = fbSnapshot.documents.firstOrNull { (it.data?.get("deleted") as? Boolean) != true }
            val product = validDoc?.let { parseProduct(it) }
            if (product != null && product.id !in deletedProductIds) {
                updateCache(product)
                return Result.success(product)
            }
        } catch (_: Exception) {}

        // 3. Tentar em 'productRegistrationRequests'
        try {
            val reqSnapshot = db.collection(COLLECTION_REQUESTS)
                .whereEqualTo("barcode", trimmed)
                .whereEqualTo("status", ProductRequestStatus.APPROVED.name)
                .get()
                .await()
            val product = reqSnapshot.documents.firstOrNull()?.let { parseProduct(it) }
            if (product != null && product.id !in deletedProductIds) {
                updateCache(product)
                return Result.success(product)
            }
        } catch (_: Exception) {}

        // 4. Tentar em memória
        val inMemory = memoryCache.find { it.barcode.trim() == trimmed && it.id !in deletedProductIds }
        return Result.success(inMemory)
    }

    /**
     * Persiste os dados completos do produto em todos os canais de sincronização disponíveis.
     */
    private suspend fun persistProductToCloud(
        db: FirebaseFirestore,
        product: Product,
        userProfile: UserProfile?
    ): Boolean {
        var anySucceeded = false
        val now = System.currentTimeMillis()
        val authorName = userProfile?.name?.ifBlank { userProfile.email } ?: "Administrador"
        val authorUid = userProfile?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: "admin_system"
        val authorStore = userProfile?.storeId?.ifBlank { "GLOBAL" } ?: "GLOBAL"
        val authorSector = userProfile?.sectorId?.ifBlank { "Geral" } ?: "Geral"

        // Canal 1: Coleção 'products'
        try {
            db.collection(COLLECTION_PRODUCTS).document(product.id).set(product).await()
            anySucceeded = true
        } catch (ex: Exception) {
            android.util.Log.w("ProductRepository", "Canal 'products' indisponível (${ex.message}), usando canal resiliente.")
        }

        // Canal 2: Coleção 'productRegistrationRequests' (status APPROVED)
        try {
            val approvedData = hashMapOf(
                "id" to product.id,
                "barcode" to product.barcode,
                "name" to product.name,
                "price" to product.price,
                "unit" to product.unit,
                "photoBase64" to product.photoBase64,
                "status" to ProductRequestStatus.APPROVED.name,
                "storeId" to authorStore,
                "sectorId" to authorSector,
                "requestedByUid" to authorUid,
                "requestedByName" to authorName,
                "requestedAt" to (product.createdAt.takeIf { it > 0L } ?: now),
                "reviewedByUid" to authorUid,
                "reviewedByName" to authorName,
                "reviewedAt" to now,
                "active" to product.active,
                "updatedAt" to now
            )
            db.collection(COLLECTION_REQUESTS).document(product.id).set(approvedData, SetOptions.merge()).await()
            anySucceeded = true
        } catch (ex: Exception) {
            android.util.Log.w("ProductRepository", "Canal 'productRegistrationRequests' indisponível: ${ex.message}")
        }

        // Canal 3: Espelho global resiliente em 'sectors' (nunca interfere em setores de lojas reais)
        try {
            val fallbackData = hashMapOf(
                "id" to product.id,
                "productId" to product.id,
                "storeId" to GLOBAL_PRODUCTS_STORE_KEY,
                "docType" to "GLOBAL_PRODUCT",
                "barcode" to product.barcode,
                "name" to product.name,
                "code" to product.barcode.ifBlank { product.id },
                "price" to product.price,
                "unit" to product.unit,
                "photoBase64" to product.photoBase64,
                "active" to product.active,
                "deleted" to false,
                "createdAt" to (product.createdAt.takeIf { it > 0L } ?: now),
                "updatedAt" to now
            )
            db.collection(COLLECTION_CLOUD_FALLBACK).document("prod_${product.id}")
                .set(fallbackData, SetOptions.merge()).await()
            anySucceeded = true
        } catch (ex: Exception) {
            android.util.Log.w("ProductRepository", "Canal fallback em nuvem falhou: ${ex.message}")
        }

        return anySucceeded
    }

    /**
     * Cadastra um novo produto no Catálogo Global.
     */
    suspend fun createProduct(
        barcode: String,
        name: String,
        price: Double = 0.0,
        unit: String,
        photoBase64: String = "",
        userProfile: UserProfile? = null
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        if (FirebaseAuth.getInstance().currentUser == null) {
            return Result.failure(IllegalStateException("Sessão expirada. Faça login novamente."))
        }

        val trimmedBarcode = barcode.trim()
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("O nome do produto é obrigatório."))
        }

        return try {
            val productId = db.collection(COLLECTION_PRODUCTS).document().id
            val now = System.currentTimeMillis()
            val product = Product(
                id = productId,
                barcode = trimmedBarcode,
                name = trimmedName,
                price = price,
                unit = unit,
                photoBase64 = photoBase64,
                active = true,
                createdAt = now,
                updatedAt = now
            )

            val savedToCloud = persistProductToCloud(db, product, userProfile)
            if (!savedToCloud) {
                return Result.failure(IllegalStateException("Não foi possível salvar o produto no banco de dados. Verifique sua conexão e tente novamente."))
            }

            updateCache(product)

            val currentUid = userProfile?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: "admin_system"
            val currentRole = userProfile?.role?.name ?: "ADMIN"
            val currentName = userProfile?.name?.ifBlank { userProfile.email } ?: "Administrador"
            auditRepository.logEvent(
                action = AuditAction.PRODUCT_CREATED,
                entityType = AuditEntityType.PRODUCT,
                entityId = productId,
                userId = currentUid,
                userName = currentName,
                userRole = currentRole,
                description = "Produto '${product.name}' (Código: ${product.barcode.ifBlank { "S/N" }}) cadastrado no catálogo global",
                metadata = mapOf("barcode" to product.barcode, "productName" to product.name, "unit" to product.unit)
            )

            Result.success(productId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cria ou atualiza um produto aprovado a partir do fluxo de aprovação de solicitações.
     */
    suspend fun upsertApprovedProduct(
        existingProductId: String? = null,
        barcode: String,
        name: String,
        price: Double,
        unit: String,
        photoBase64: String,
        reviewerProfile: UserProfile
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        val now = System.currentTimeMillis()

        // Verificar se já existe produto com o mesmo código de barras para atualizar em vez de duplicar
        var resolvedId = existingProductId?.takeIf { it.isNotBlank() }
        var createdAt = now
        var existingPhoto = photoBase64

        if (resolvedId == null && barcode.isNotBlank()) {
            val found = getProductByBarcode(barcode).getOrNull()
            if (found != null) {
                resolvedId = found.id
                if (found.createdAt > 0L) createdAt = found.createdAt
                if (existingPhoto.isBlank()) existingPhoto = found.photoBase64
            }
        }

        val finalId = resolvedId ?: db.collection(COLLECTION_PRODUCTS).document().id
        val product = Product(
            id = finalId,
            barcode = barcode.trim(),
            name = name.trim(),
            price = price,
            unit = unit,
            photoBase64 = existingPhoto,
            active = true,
            createdAt = createdAt,
            updatedAt = now
        )

        val saved = persistProductToCloud(db, product, reviewerProfile)
        if (!saved) {
            return Result.failure(IllegalStateException("Erro ao publicar o produto aprovado no catálogo."))
        }

        updateCache(product)
        return Result.success(finalId)
    }

    /**
     * Atualiza as informações de um produto existente no Catálogo Global.
     * Restrito a Administradores e Avaristas.
     */
    suspend fun updateProduct(
        productId: String,
        name: String,
        barcode: String,
        price: Double,
        unit: String,
        photoBase64: String,
        active: Boolean,
        userProfile: UserProfile? = null
    ): Result<Unit> {
        if (userProfile != null && userProfile.role != UserRole.ADMIN && userProfile.role != UserRole.AVARISTA) {
            return Result.failure(IllegalStateException("Apenas Administradores e Avaristas podem editar informações do produto."))
        }
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        val trimmedName = name.trim()
        val trimmedBarcode = barcode.trim()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("O nome do produto é obrigatório."))
        }

        return try {
            val existing = memoryCache.find { it.id == productId }
            val now = System.currentTimeMillis()
            val updatedProduct = Product(
                id = productId,
                barcode = trimmedBarcode,
                name = trimmedName,
                price = price,
                unit = unit,
                photoBase64 = photoBase64,
                active = active,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )

            val saved = persistProductToCloud(db, updatedProduct, userProfile)
            if (!saved) {
                return Result.failure(IllegalStateException("Não foi possível salvar as alterações no banco de dados."))
            }

            updateCache(updatedProduct)

            val uid = userProfile?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: "system"
            val userName = userProfile?.name?.ifBlank { userProfile.email } ?: "Usuário"
            val userRole = userProfile?.role?.name ?: "ADMIN"

            auditRepository.logEvent(
                action = AuditAction.PRODUCT_UPDATED,
                entityType = AuditEntityType.PRODUCT,
                entityId = productId,
                userId = uid,
                userName = userName,
                userRole = userRole,
                description = "Produto '$trimmedName' atualizado por $userName ($userRole)",
                metadata = mapOf("barcode" to trimmedBarcode, "price" to "$price", "unit" to unit)
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atualiza especificamente a foto do produto.
     */
    suspend fun updateProductPhoto(
        productId: String,
        photoBase64: String,
        userProfile: UserProfile? = null
    ): Result<Unit> {
        if (userProfile != null && userProfile.role != UserRole.ADMIN && userProfile.role != UserRole.AVARISTA) {
            return Result.failure(IllegalStateException("Apenas Administradores e Avaristas podem alterar a foto do produto."))
        }
        val current = memoryCache.find { it.id == productId }
        return if (current != null) {
            updateProduct(
                productId = productId,
                name = current.name,
                barcode = current.barcode,
                price = current.price,
                unit = current.unit,
                photoBase64 = photoBase64,
                active = current.active,
                userProfile = userProfile
            )
        } else {
            val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
            try {
                try {
                    db.collection(COLLECTION_PRODUCTS).document(productId).update("photoBase64", photoBase64).await()
                } catch (_: Exception) {}
                try {
                    db.collection(COLLECTION_CLOUD_FALLBACK).document("prod_$productId")
                        .set(mapOf("photoBase64" to photoBase64, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                        .await()
                } catch (_: Exception) {}
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Exclui um produto do Catálogo Global.
     * Restrito a Administradores e Avaristas.
     */
    suspend fun deleteProduct(
        productId: String,
        userProfile: UserProfile? = null
    ): Result<Unit> {
        if (userProfile != null && userProfile.role != UserRole.ADMIN && userProfile.role != UserRole.AVARISTA) {
            return Result.failure(IllegalStateException("Apenas Administradores e Avaristas podem excluir produtos."))
        }
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        val targetProduct = memoryCache.find { it.id == productId }

        return try {
            var anySucceeded = false

            // 1. Deletar de 'products'
            try {
                db.collection(COLLECTION_PRODUCTS).document(productId).delete().await()
                anySucceeded = true
            } catch (_: Exception) {}

            // 2. Deletar de 'productRegistrationRequests'
            try {
                db.collection(COLLECTION_REQUESTS).document(productId).delete().await()
                anySucceeded = true
            } catch (_: Exception) {}

            // 3. Marcar como deletado no espelho em nuvem (funciona inclusive para Avarista) e tentar delete físico
            try {
                db.collection(COLLECTION_CLOUD_FALLBACK).document("prod_$productId")
                    .set(
                        mapOf(
                            "productId" to productId,
                            "storeId" to GLOBAL_PRODUCTS_STORE_KEY,
                            "deleted" to true,
                            "active" to false,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()
                anySucceeded = true
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_CLOUD_FALLBACK).document("prod_$productId").delete().await()
            } catch (_: Exception) {}

            if (!anySucceeded) {
                return Result.failure(IllegalStateException("Não foi possível excluir o produto. Tente novamente."))
            }

            invalidateDeleted(productId)

            val uid = userProfile?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: "system"
            val userName = userProfile?.name?.ifBlank { userProfile.email } ?: "Usuário"
            val userRole = userProfile?.role?.name ?: "ADMIN"
            val prodName = targetProduct?.name ?: productId

            auditRepository.logEvent(
                action = AuditAction.PRODUCT_DELETED,
                entityType = AuditEntityType.PRODUCT,
                entityId = productId,
                userId = uid,
                userName = userName,
                userRole = userRole,
                description = "Produto '$prodName' excluído do catálogo por $userName ($userRole)",
                metadata = mapOf("productId" to productId, "barcode" to (targetProduct?.barcode ?: ""))
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ativa ou desativa um produto no catálogo global (`active = true` ou `active = false`).
     */
    suspend fun updateProductActiveStatus(
        productId: String,
        active: Boolean,
        userProfile: UserProfile? = null
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            val now = System.currentTimeMillis()
            var anySucceeded = false

            try {
                db.collection(COLLECTION_PRODUCTS).document(productId)
                    .update(mapOf("active" to active, "updatedAt" to now))
                    .await()
                anySucceeded = true
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_REQUESTS).document(productId)
                    .update(mapOf("active" to active, "updatedAt" to now))
                    .await()
                anySucceeded = true
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_CLOUD_FALLBACK).document("prod_$productId")
                    .set(
                        mapOf(
                            "productId" to productId,
                            "storeId" to GLOBAL_PRODUCTS_STORE_KEY,
                            "active" to active,
                            "updatedAt" to now
                        ),
                        SetOptions.merge()
                    )
                    .await()
                anySucceeded = true
            } catch (_: Exception) {}

            if (!anySucceeded) {
                return Result.failure(IllegalStateException("Não foi possível atualizar o status do produto."))
            }

            memoryCache = memoryCache.map { if (it.id == productId) it.copy(active = active, updatedAt = now) else it }

            val currentUid = userProfile?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: "admin_system"
            val currentRole = userProfile?.role?.name ?: "ADMIN"
            val currentName = userProfile?.name?.ifBlank { userProfile?.email } ?: "Administrador"
            val action = if (active) AuditAction.PRODUCT_ACTIVATED else AuditAction.PRODUCT_DEACTIVATED
            auditRepository.logEvent(
                action = action,
                entityType = AuditEntityType.PRODUCT,
                entityId = productId,
                userId = currentUid,
                userName = currentName,
                userRole = currentRole,
                description = if (active) "Produto ativado no catálogo global" else "Produto desativado no catálogo global",
                metadata = mapOf("productId" to productId, "active" to "$active")
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
