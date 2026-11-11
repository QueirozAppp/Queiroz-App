package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.Product
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositório responsável pela gestão, consulta e persistência de produtos no Catálogo Global da Rede (`products`).
 */
class ProductRepository(
    private val auditRepository: AuditRepository = AuditRepository()
) {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    /**
     * Converte um DocumentSnapshot do Firestore em Product de forma ultra-segura,
     * tratando compatibilidade de tipos numéricos (Long/Double/Int) e Timestamps.
     */
    fun parseProduct(doc: com.google.firebase.firestore.DocumentSnapshot): Product? {
        return try {
            val direct = doc.toObject(Product::class.java)?.copy(id = doc.id)
            if (direct != null && direct.name.isNotBlank()) {
                return direct
            }
            val data = doc.data ?: return null
            val id = doc.id
            val barcode = (data["barcode"] as? String) ?: (data["barcode"]?.toString() ?: "")
            val name = (data["name"] as? String) ?: (data["name"]?.toString() ?: "")
            val price = when (val p = data["price"]) {
                is Number -> p.toDouble()
                is String -> p.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            val unit = (data["unit"] as? String) ?: "UN"
            val active = when (val a = data["active"]) {
                is Boolean -> a
                is String -> a.toBooleanStrictOrNull() ?: true
                else -> true
            }
            val createdAt = when (val c = data["createdAt"]) {
                is Number -> c.toLong()
                is com.google.firebase.Timestamp -> c.toDate().time
                else -> 0L
            }
            val updatedAt = when (val u = data["updatedAt"]) {
                is Number -> u.toLong()
                is com.google.firebase.Timestamp -> u.toDate().time
                else -> 0L
            }
            Product(
                id = id,
                barcode = barcode,
                name = name,
                price = price,
                unit = unit,
                active = active,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            android.util.Log.e("ProductRepository", "Erro ao deserializar produto ${doc.id}: ${e.message}")
            try {
                val data = doc.data ?: return null
                Product(
                    id = doc.id,
                    barcode = data["barcode"]?.toString() ?: "",
                    name = data["name"]?.toString() ?: "Item sem nome",
                    price = (data["price"]?.toString()?.toDoubleOrNull()) ?: 0.0,
                    unit = data["unit"]?.toString() ?: "UN",
                    active = true
                )
            } catch (ex: Exception) {
                null
            }
        }
    }

    /**
     * Retorna a lista completa de produtos do catálogo global de forma direta via consulta única.
     */
    suspend fun getAllProducts(): Result<List<Product>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            val querySnapshot = db.collection("products").get().await()
            val list = querySnapshot.documents.mapNotNull { doc -> parseProduct(doc) }
            android.util.Log.d("ProductRepository", "getAllProducts() retornou ${list.size} produtos.")
            Result.success(list)
        } catch (e: Exception) {
            android.util.Log.e("ProductRepository", "Erro ao buscar todos os produtos: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Retorna um fluxo em tempo real com todos os produtos do Catálogo Global (`products`).
     */
    val productsFlow: Flow<List<Product>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ProductRepository", "Erro ao carregar produtos em tempo real: ${error.message}")
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()
                android.util.Log.d("ProductRepository", "Produtos recebidos via snapshot: ${docs.size}")

                val list = docs.mapNotNull { doc -> parseProduct(doc) }
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Consulta um produto no Catálogo Global pelo código de barras exato (`barcode`).
     */
    suspend fun getProductByBarcode(barcode: String): Result<Product?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Código de barras inválido ou vazio."))
        }

        return try {
            val querySnapshot = db.collection("products")
                .whereEqualTo("barcode", trimmed)
                .get()
                .await()

            val doc = querySnapshot.documents.firstOrNull()
            val product = doc?.let { parseProduct(it) }
            Result.success(product)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cadastra um novo produto no Catálogo Global, validando duplicidade global de código de barras (`barcode`).
     */
    suspend fun createProduct(
        barcode: String,
        name: String,
        price: Double = 0.0,
        unit: String
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        
        val trimmedBarcode = barcode.trim()
        val trimmedName = name.trim()

        if (trimmedBarcode.isBlank()) {
            return Result.failure(IllegalArgumentException("O código de barras é obrigatório."))
        }
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("O nome do produto é obrigatório."))
        }

        return try {
            // Verificar duplicidade global de barcode no catálogo da rede
            val querySnapshot = db.collection("products")
                .whereEqualTo("barcode", trimmedBarcode)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                return Result.failure(IllegalArgumentException("Já existe um produto cadastrado com o código de barras '$trimmedBarcode' no catálogo global da rede."))
            }

            val productId = db.collection("products").document().id
            val now = System.currentTimeMillis()
            val product = Product(
                id = productId,
                barcode = trimmedBarcode,
                name = trimmedName,
                price = price,
                unit = unit,
                active = true,
                createdAt = now,
                updatedAt = now
            )

            db.collection("products").document(productId).set(product).await()

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "admin_system"
            auditRepository.logEvent(
                action = AuditAction.PRODUCT_CREATED,
                entityType = AuditEntityType.PRODUCT,
                entityId = productId,
                userId = currentUid,
                userName = "Administrador",
                userRole = "ADMIN",
                description = "Produto '${product.name}' (Código: ${product.barcode}) cadastrado no catálogo global",
                metadata = mapOf("barcode" to product.barcode, "productName" to product.name, "unit" to product.unit)
            )

            Result.success(productId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ativa ou desativa um produto no catálogo global (`active = true` ou `active = false`).
     */
    suspend fun updateProductActiveStatus(productId: String, active: Boolean): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado."))
        return try {
            db.collection("products").document(productId)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "admin_system"
            val action = if (active) AuditAction.PRODUCT_ACTIVATED else AuditAction.PRODUCT_DEACTIVATED
            auditRepository.logEvent(
                action = action,
                entityType = AuditEntityType.PRODUCT,
                entityId = productId,
                userId = currentUid,
                userName = "Administrador",
                userRole = "ADMIN",
                description = if (active) "Produto ativado no catálogo global" else "Produto desativado no catálogo global",
                metadata = mapOf("productId" to productId, "active" to "$active")
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
