package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.Product
import com.example.core.model.Store
import com.example.core.model.Withdrawal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositório central de acesso ao Cloud Firestore para o sistema Controle de Baixas.
 * Gerencia as coleções principais: stores, products, withdrawals e users.
 */
class FirestoreRepository {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    companion object {
        const val COLLECTION_STORES = "stores"
        const val COLLECTION_PRODUCTS = "products"
        const val COLLECTION_WITHDRAWALS = "withdrawals"
        const val COLLECTION_USERS = "users"
    }

    /**
     * Retorna um fluxo em tempo real com a lista de lojas cadastradas no Firestore.
     */
    val storesFlow: Flow<List<Store>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = try {
            db.collection(COLLECTION_STORES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(Store::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (_: Exception) {
            trySend(emptyList())
            null
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Retorna um fluxo em tempo real com a lista de produtos cadastrados no Firestore.
     */
    val productsFlow: Flow<List<Product>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = try {
            db.collection(COLLECTION_PRODUCTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(Product::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (_: Exception) {
            trySend(emptyList())
            null
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Registra uma nova baixa (withdrawal) no Cloud Firestore.
     */
    suspend fun addWithdrawal(withdrawal: Withdrawal): Result<String> {
        val db = firestore ?: return Result.failure(
            IllegalStateException("Firestore não inicializado. Verifique o arquivo google-services.json.")
        )
        return try {
            val docRef = db.collection(COLLECTION_WITHDRAWALS).document()
            val data = withdrawal.copy(id = docRef.id)
            docRef.set(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adiciona uma loja de exemplo (útil para inicialização/setup).
     */
    suspend fun addStore(store: Store): Result<String> {
        val db = firestore ?: return Result.failure(
            IllegalStateException("Firestore não inicializado. Verifique o arquivo google-services.json.")
        )
        return try {
            val docRef = if (store.id.isNotEmpty()) {
                db.collection(COLLECTION_STORES).document(store.id)
            } else {
                db.collection(COLLECTION_STORES).document()
            }
            val data = store.copy(id = docRef.id)
            docRef.set(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
