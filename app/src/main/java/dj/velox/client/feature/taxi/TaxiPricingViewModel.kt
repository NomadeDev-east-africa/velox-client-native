package dj.velox.client.feature.taxi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.TaxiPricingService
import dj.velox.client.domain.model.RideChoice
import dj.velox.client.domain.model.TaxiCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Catalogue véhicules avec tarifs live (`config/taxiPricing`, pilotée depuis l'admin).
 * Démarre avec les valeurs par défaut de [TaxiCatalog] (jamais de prix à 0/vide au premier
 * rendu), mis à jour dès que le document Firestore répond — voir [TaxiPricingService].
 */
@HiltViewModel
class TaxiPricingViewModel @Inject constructor(
    pricingService: TaxiPricingService,
) : ViewModel() {

    val choices: StateFlow<List<RideChoice>> = pricingService.streamPricing()
        .map { TaxiCatalog.withPricing(it) }
        .catch { emit(TaxiCatalog.choices) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaxiCatalog.choices)

    fun byId(id: String): RideChoice = choices.value.firstOrNull { it.id == id } ?: TaxiCatalog.byId(id)
}
