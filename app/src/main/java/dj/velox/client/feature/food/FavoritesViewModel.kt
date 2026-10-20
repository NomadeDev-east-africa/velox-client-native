package dj.velox.client.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.data.remote.FavoriteRestaurantsService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Favoris restaurants partagés (hissé au niveau du graphe authentifié). **Réactif à l'uid** :
 * bascule automatiquement sur les favoris du compte courant, et se vide à la déconnexion —
 * même si l'instance survit au changement de compte (VM scopée à l'Activity).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val service: FavoriteRestaurantsService,
) : ViewModel() {

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    init {
        repo.authState
            .flatMapLatest { user ->
                if (user == null) flowOf(emptySet()) else service.streamFavorites(user.uid)
            }
            .onEach { _favorites.value = it }
            .launchIn(viewModelScope)
    }

    /**
     * Bascule optimiste : l'UI est mise à jour immédiatement, puis rollback si Firestore
     * refuse l'écriture (ex. règle `onlyFields` sans `favoriteRestaurants`).
     */
    fun toggle(restaurantId: String) {
        val uid = repo.currentUser?.uid ?: return
        val makeFavorite = restaurantId !in _favorites.value
        val previous = _favorites.value
        _favorites.value = if (makeFavorite) previous + restaurantId else previous - restaurantId
        viewModelScope.launch {
            runCatching { service.setFavorite(uid, restaurantId, makeFavorite) }
                .onFailure { _favorites.value = previous }
        }
    }
}
