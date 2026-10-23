package com.example.core.data

import com.example.core.firebase.FirebaseProvider
import com.example.core.model.AuditAction
import com.example.core.model.AuditEntityType
import com.example.core.model.Sector
import com.example.core.model.Store
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.*

/**
 * Repositório responsável pela gestão de Lojas (stores) e Setores (sectors) no Firestore (Etapa 11).
 */
class StoreSectorRepository(
    private val auditRepository: AuditRepository = AuditRepository()
) {

    private val firestore get() = FirebaseProvider.getFirestoreOrNull()

    /**
     * Retorna um fluxo com todas as lojas cadastradas. Se a coleção estiver vazia, semeia as lojas padrão.
     */
    fun getStoresFlow(): Flow<List<Store>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(defaultStores())
            close()
            return@callbackFlow
        }

        val listener = db.collection("stores")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(defaultStores())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Store::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                if (list.isEmpty()) {
                    kotlinx.coroutines.GlobalScope.launch {
                        seedDefaultStores()
                    }
                    trySend(defaultStores())
                } else {
                    trySend(list.sortedBy { it.name })
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Retorna um fluxo com os setores vinculados a uma loja específica.
     */
    fun getSectorsFlow(storeId: String): Flow<List<Sector>> = callbackFlow {
        val db = firestore
        if (db == null || storeId.isBlank()) {
            trySend(defaultSectorsForStore(storeId))
            close()
            return@callbackFlow
        }

        val listener = db.collection("sectors")
            .whereEqualTo("storeId", storeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(defaultSectorsForStore(storeId))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Sector::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                if (list.isEmpty()) {
                    kotlinx.coroutines.GlobalScope.launch {
                        seedDefaultSectorsForStore(storeId)
                    }
                    trySend(defaultSectorsForStore(storeId))
                } else {
                    trySend(list.sortedBy { it.name })
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun saveStore(store: Store): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore indisponível."))
        return try {
            val id = store.id.ifBlank { db.collection("stores").document().id }
            val now = System.currentTimeMillis()
            val finalStore = store.copy(
                id = id,
                code = store.code.ifBlank { id },
                createdAt = if (store.createdAt == 0L) now else store.createdAt,
                updatedAt = now
            )
            
            try {
                withTimeout(4000L) {
                    db.collection("stores").document(id).set(finalStore).await()
                }
            } catch (toEx: TimeoutCancellationException) {
                // Em redes móveis, o Firestore já gravou no cache local SQLite e continuará a sincronizar no background
                android.util.Log.i("StoreSectorRepo", "Loja $id gravada localmente, sincronizando com a nuvem em segundo plano.")
            }

            // Semeia automaticamente os setores padrão para a nova loja criada
            try {
                for (sector in defaultSectorsForStore(id)) {
                    db.collection("sectors").document(sector.id).set(sector)
                }
            } catch (e: Exception) {
                android.util.Log.w("StoreSectorRepo", "Aviso ao registrar setores padrão para $id: ${e.message}")
            }

            try {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "admin_system"
                val isNew = store.id.isBlank() || store.createdAt == 0L
                val action = if (isNew) AuditAction.STORE_CREATED else if (!finalStore.active) AuditAction.STORE_DEACTIVATED else AuditAction.STORE_UPDATED
                auditRepository.logEvent(
                    action = action,
                    entityType = AuditEntityType.STORE,
                    entityId = id,
                    userId = currentUid,
                    userName = "Administrador",
                    userRole = "ADMIN",
                    storeId = id,
                    description = "Loja ${finalStore.name} (${finalStore.code}) salva",
                    metadata = mapOf("storeName" to finalStore.name, "active" to "${finalStore.active}")
                )
            } catch (auditEx: Exception) {
                android.util.Log.w("StoreSectorRepo", "Falha ao gravar log de auditoria da loja: ${auditEx.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSector(sector: Sector): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore indisponível."))
        if (sector.storeId.isBlank()) return Result.failure(IllegalArgumentException("O setor deve pertencer a uma loja."))
        return try {
            val id = sector.id.ifBlank { db.collection("sectors").document().id }
            val now = System.currentTimeMillis()
            val finalSector = sector.copy(
                id = id,
                code = sector.code.ifBlank { id },
                createdAt = if (sector.createdAt == 0L) now else sector.createdAt,
                updatedAt = now
            )
            try {
                withTimeout(4000L) {
                    db.collection("sectors").document(id).set(finalSector).await()
                }
            } catch (toEx: TimeoutCancellationException) {
                android.util.Log.i("StoreSectorRepo", "Setor $id gravado localmente, sincronizando com a nuvem em segundo plano.")
            }

            try {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "admin_system"
                val isNew = sector.id.isBlank() || sector.createdAt == 0L
                val action = if (isNew) AuditAction.SECTOR_CREATED else if (!finalSector.active) AuditAction.SECTOR_DEACTIVATED else AuditAction.SECTOR_UPDATED
                auditRepository.logEvent(
                    action = action,
                    entityType = AuditEntityType.SECTOR,
                    entityId = id,
                    userId = currentUid,
                    userName = "Administrador",
                    userRole = "ADMIN",
                    storeId = finalSector.storeId,
                    sectorId = id,
                    description = "Setor ${finalSector.name} (${finalSector.code}) salvo para a loja ${finalSector.storeId}",
                    metadata = mapOf("sectorName" to finalSector.name, "storeId" to finalSector.storeId, "active" to "${finalSector.active}")
                )
            } catch (auditEx: Exception) {
                android.util.Log.w("StoreSectorRepo", "Falha ao gravar log de auditoria do setor: ${auditEx.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun defaultStores(): List<Store> {
        val now = System.currentTimeMillis()
        return listOf(
            Store(id = "LOJA_01", name = "Supermercado Queiroz - Matriz", code = "LOJA_01", active = true, createdAt = now, updatedAt = now),
            Store(id = "LOJA_02", name = "Supermercado Queiroz - Filial Centro", code = "LOJA_02", active = true, createdAt = now, updatedAt = now),
            Store(id = "LOJA_03", name = "Supermercado Queiroz - Filial Norte", code = "LOJA_03", active = true, createdAt = now, updatedAt = now)
        )
    }

    private fun defaultSectorsForStore(storeId: String): List<Sector> {
        val now = System.currentTimeMillis()
        return listOf(
            Sector(id = "${storeId}_GERAL", storeId = storeId, name = "Geral", code = "GERAL", active = true, createdAt = now, updatedAt = now),
            Sector(id = "${storeId}_PADARIA", storeId = storeId, name = "Padaria", code = "PADARIA", active = true, createdAt = now, updatedAt = now),
            Sector(id = "${storeId}_ACOUGUE", storeId = storeId, name = "Açougue", code = "ACOUGUE", active = true, createdAt = now, updatedAt = now),
            Sector(id = "${storeId}_HORTIFRUTI", storeId = storeId, name = "Hortifrúti", code = "HORTIFRUTI", active = true, createdAt = now, updatedAt = now)
        )
    }

    private suspend fun seedDefaultStores() {
        val db = firestore ?: return
        try {
            for (store in defaultStores()) {
                db.collection("stores").document(store.id).set(store).await()
                seedDefaultSectorsForStore(store.id)
            }
        } catch (_: Exception) {}
    }

    private suspend fun seedDefaultSectorsForStore(storeId: String) {
        val db = firestore ?: return
        try {
            for (sector in defaultSectorsForStore(storeId)) {
                db.collection("sectors").document(sector.id).set(sector).await()
            }
        } catch (_: Exception) {}
    }
}
