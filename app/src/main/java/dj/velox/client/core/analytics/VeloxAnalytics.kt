package dj.velox.client.core.analytics

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ParametersBuilder
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * Point unique pour les événements Firebase Analytics (noms/params centralisés).
 *
 * Utilise `Firebase.analytics` (singleton process-wide) → pas besoin d'injection Hilt, donc aucun
 * changement de constructeur dans les ViewModels (ni de casse des tests existants).
 *
 * Devise : « DJF » (Franc djiboutien, ISO 4217). Les events `screen_view`, `add_to_cart` et
 * `purchase` sont des events *recommandés* Firebase → ils alimentent les rapports standards.
 */
object VeloxAnalytics {
    // Nullable + runCatching : si Firebase n'est pas initialisé (tests unitaires JVM, init tardive),
    // `fa` reste null et tous les events deviennent des no-op — Analytics ne doit JAMAIS crasher.
    private val fa: FirebaseAnalytics? by lazy { runCatching { Firebase.analytics }.getOrNull() }
    private const val CURRENCY = "DJF"

    private inline fun log(name: String, crossinline params: ParametersBuilder.() -> Unit) {
        runCatching { fa?.logEvent(name) { params() } }
    }

    /** Vue d'écran Compose (la route de navigation sert de nom d'écran). */
    fun screenView(route: String) = log(FirebaseAnalytics.Event.SCREEN_VIEW) {
        param(FirebaseAnalytics.Param.SCREEN_NAME, route)
        param(FirebaseAnalytics.Param.SCREEN_CLASS, route)
    }

    /** Ajout d'un article au panier food. */
    fun addToCart(restaurantId: String, itemName: String, priceFdj: Int, quantity: Int) =
        log(FirebaseAnalytics.Event.ADD_TO_CART) {
            param(FirebaseAnalytics.Param.ITEM_ID, restaurantId)
            param(FirebaseAnalytics.Param.ITEM_NAME, itemName)
            param(FirebaseAnalytics.Param.CURRENCY, CURRENCY)
            param(FirebaseAnalytics.Param.VALUE, priceFdj.toDouble())
            param(FirebaseAnalytics.Param.QUANTITY, quantity.toLong())
        }

    /** Commande food effectivement créée/envoyée au restaurant (après « Poursuivre »). */
    fun orderPlaced(orderId: String, restaurantId: String, totalFdj: Int, paymentMethod: String) =
        log(FirebaseAnalytics.Event.PURCHASE) {
            param(FirebaseAnalytics.Param.TRANSACTION_ID, orderId)
            param(FirebaseAnalytics.Param.ITEM_CATEGORY, restaurantId)
            param(FirebaseAnalytics.Param.CURRENCY, CURRENCY)
            param(FirebaseAnalytics.Param.VALUE, totalFdj.toDouble())
            param(FirebaseAnalytics.Param.PAYMENT_TYPE, paymentMethod)
        }

    /** Course VTC confirmée (création réussie). */
    fun rideConfirmed(vehicleType: String, fareFdj: Double, distanceKm: Double, paymentMethod: String) =
        log("ride_confirmed") {
            param("vehicle_type", vehicleType)
            param(FirebaseAnalytics.Param.CURRENCY, CURRENCY)
            param(FirebaseAnalytics.Param.VALUE, fareFdj)
            param("distance_km", distanceKm)
            param(FirebaseAnalytics.Param.PAYMENT_TYPE, paymentMethod)
        }
}
