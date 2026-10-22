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
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Product::class.java)?.copy(id = doc.id)
                } ?: emptyList()
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
            val product = doc?.toObject(Product::class.java)?.copy(id = doc.id)
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
