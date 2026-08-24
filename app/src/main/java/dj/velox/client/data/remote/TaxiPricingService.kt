package dj.velox.client.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import dj.velox.client.domain.model.TaxiPricingTier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tarifs VTC pilotés depuis l'admin — document `config/taxiPricing`. En cas d'absence du
 * document (pas encore créé côté admin) ou d'erreur, émet une map vide : [dj.velox.client.domain.model.TaxiCatalog.withPricing]
 * conserve alors les valeurs par défaut du catalogue statique — jamais d'écran cassé.
 * Le tarif effectif est de toute façon revalidé côté Cloud Function à la création/fin de
 * course (`onTaxiRideCreated` / `onRideUpdated`) : cette lecture ne sert qu'à l'estimation
 * affichée au client.
 */
@Singleton
class TaxiPricingService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun streamPricing(): Flow<Map<String, TaxiPricingTier>> = callbackFlow {
        val reg = firestore.collection(COLLECTION).document(DOC_ID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyMap()); return@addSnapshotListener }
                trySend(snapshot?.data?.let(::parsePricing) ?: emptyMap())
            }
        awaitClose { reg.remove() }
    }

    private fun parsePricing(data: Map<String, Any?>): Map<String, TaxiPricingTier> =
        VEHICLE_TYPES.mapNotNull { id ->
            val tier = data[id] as? Map<*, *> ?: return@mapNotNull null
            val basePrice = (tier["basePrice"] as? Number)?.toDouble() ?: return@mapNotNull null
            val pricePerKm = (tier["pricePerKm"] as? Number)?.toDouble() ?: return@mapNotNull null
            val includedKm = (tier["includedKm"] as? Number)?.toDouble() ?: 0.0
            id to TaxiPricingTier(basePrice = basePrice, includedKm = includedKm, pricePerKm = pricePerKm)
        }.toMap()

    companion object {
        private const val COLLECTION = "config"
        private const val DOC_ID = "taxiPricing"
        private val VEHICLE_TYPES = listOf("standard", "comfort")
    }
}
