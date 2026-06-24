package dj.velox.client.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dj.velox.client.domain.model.Address
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CRUD des adresses de livraison — sous-collection `users/{uid}/addresses`
 * (miroir d'`AddressNotifier`). Aucune écriture si l'utilisateur n'est pas connecté.
 */
@Singleton
class AddressService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private fun col(): CollectionReference? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).collection("addresses")
    }

    suspend fun fetch(): List<Address> = runCatching {
        val c = col() ?: return emptyList()
        c.orderBy("createdAt").get().await().documents
            .map { Address.fromFirestore(it.id, it.data ?: emptyMap()) }
    }.getOrDefault(emptyList())

    suspend fun add(data: Map<String, Any?>, isDefault: Boolean): String? = runCatching {
        val c = col() ?: return null
        c.add(data + mapOf("isDefault" to isDefault, "createdAt" to FieldValue.serverTimestamp()))
            .await().id
    }.getOrNull()

    suspend fun update(id: String, data: Map<String, Any?>) {
        runCatching {
            col()?.document(id)?.update(data + mapOf("updatedAt" to FieldValue.serverTimestamp()))?.await()
        }
    }

    suspend fun delete(id: String) {
        runCatching { col()?.document(id)?.delete()?.await() }
    }

    /** Met `isDefault=true` sur [defaultId] et `false` sur les autres (batch). */
    suspend fun setDefault(defaultId: String, allIds: List<String>) {
        runCatching {
            val c = col() ?: return
            val batch = firestore.batch()
            allIds.forEach { batch.update(c.document(it), "isDefault", it == defaultId) }
            batch.commit().await()
        }
    }
}
