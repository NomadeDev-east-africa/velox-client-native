package dj.velox.client.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistance locale Velox — équivalent Kotlin de HiveService (Flutter).
 *
 * Stratégie identique : on stocke des snapshots JSON (String) par clé.
 *   - On écrit APRÈS chaque mise à jour Firestore confirmée
 *   - On lit AU DÉMARRAGE pour affichage immédiat
 *   - Firestore reste la source de vérité
 *
 * DataStore est asynchrone : les lectures ponctuelles passent par `first()`.
 */
@Singleton
class VeloxLocalStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    // ─── Course active ───────────────────────────────────────────
    suspend fun saveRide(rideId: String, rideJson: String) = dataStore.edit {
        it[KEY_RIDE_ID] = rideId
        it[KEY_RIDE_DATA] = rideJson
    }

    suspend fun getRideId(): String? = read(KEY_RIDE_ID)
    suspend fun getRideJson(): String? = read(KEY_RIDE_DATA)
    suspend fun hasActiveRide(): Boolean = getRideId() != null
    suspend fun clearRide() = dataStore.edit {
        it.remove(KEY_RIDE_ID); it.remove(KEY_RIDE_DATA)
    }

    // ─── Commande active ─────────────────────────────────────────
    suspend fun saveOrder(orderId: String, orderJson: String) = dataStore.edit {
        it[KEY_ORDER_ID] = orderId
        it[KEY_ORDER_DATA] = orderJson
    }

    suspend fun getOrderId(): String? = read(KEY_ORDER_ID)
    suspend fun getOrderJson(): String? = read(KEY_ORDER_DATA)
    suspend fun hasActiveOrder(): Boolean = getOrderId() != null
    suspend fun clearOrder() = dataStore.edit {
        it.remove(KEY_ORDER_ID); it.remove(KEY_ORDER_DATA)
    }

    // ─── Panier ──────────────────────────────────────────────────
    suspend fun saveCart(cartJson: String) = dataStore.edit { it[KEY_CART_DATA] = cartJson }
    suspend fun getCartJson(): String? = read(KEY_CART_DATA)
    suspend fun hasCart(): Boolean = getCartJson() != null
    suspend fun clearCart() = dataStore.edit { it.remove(KEY_CART_DATA) }

    // ─── Préférence de thème (Apparence → Mode sombre) ──────────
    // Pendant du toggle Flutter `ThemeState.isDarkMode` (persisté). Défaut = true
    // (l'app force le dark « Kinetic Monolith » au premier lancement). Survit à la
    // déconnexion (préférence UI, pas une donnée de session).
    val darkModeFlow: Flow<Boolean> = dataStore.data.map { it[KEY_DARK_MODE] ?: true }
    suspend fun setDarkMode(enabled: Boolean) = dataStore.edit { it[KEY_DARK_MODE] = enabled }

    // ─── Notification en attente (deep-link FCM au démarrage) ────
    suspend fun savePendingNotification(type: String, orderId: String?, rideId: String?) =
        dataStore.edit {
            it[KEY_PENDING_TYPE] = type
            if (orderId != null) it[KEY_PENDING_ORDER] = orderId else it.remove(KEY_PENDING_ORDER)
            if (rideId != null) it[KEY_PENDING_RIDE] = rideId else it.remove(KEY_PENDING_RIDE)
        }

    suspend fun getPendingNotification(): PendingNotification? {
        val prefs = dataStore.data.first()
        val type = prefs[KEY_PENDING_TYPE] ?: return null
        return PendingNotification(type, prefs[KEY_PENDING_ORDER], prefs[KEY_PENDING_RIDE])
    }

    suspend fun clearPendingNotification() = dataStore.edit {
        it.remove(KEY_PENDING_TYPE); it.remove(KEY_PENDING_ORDER); it.remove(KEY_PENDING_RIDE)
    }

    // ─── Déconnexion : efface les données métier (garde les prefs UI) ──
    suspend fun clearAllSession() = dataStore.edit {
        listOf(
            KEY_RIDE_ID, KEY_RIDE_DATA, KEY_ORDER_ID, KEY_ORDER_DATA, KEY_CART_DATA,
            KEY_PENDING_TYPE, KEY_PENDING_ORDER, KEY_PENDING_RIDE,
        ).forEach(it::remove)
    }

    private suspend fun read(key: Preferences.Key<String>): String? =
        dataStore.data.map { it[key] }.first()

    data class PendingNotification(val type: String, val orderId: String?, val rideId: String?)

    private companion object {
        val KEY_RIDE_ID = stringPreferencesKey("rideId")
        val KEY_RIDE_DATA = stringPreferencesKey("rideData")
        val KEY_ORDER_ID = stringPreferencesKey("orderId")
        val KEY_ORDER_DATA = stringPreferencesKey("orderData")
        val KEY_CART_DATA = stringPreferencesKey("cartData")
        val KEY_PENDING_TYPE = stringPreferencesKey("pendingType")
        val KEY_PENDING_ORDER = stringPreferencesKey("pendingOrderId")
        val KEY_PENDING_RIDE = stringPreferencesKey("pendingRideId")
        val KEY_DARK_MODE = booleanPreferencesKey("darkMode")
    }
}
