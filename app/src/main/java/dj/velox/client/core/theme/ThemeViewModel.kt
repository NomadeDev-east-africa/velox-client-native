package dj.velox.client.core.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.local.VeloxLocalStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État du thème (Apparence → Mode sombre) — pendant du `ThemeNotifier` Flutter.
 *
 * La source de vérité est DataStore (`VeloxLocalStore.darkModeFlow`), si bien que
 * toutes les instances de ce ViewModel (racine pour appliquer le thème, profil pour
 * le toggle) restent synchronisées sans prop-drilling : écrire via [setDarkMode]
 * met à jour le flux observé par la racine, qui recompose le thème.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val store: VeloxLocalStore,
) : ViewModel() {

    val darkMode: StateFlow<Boolean> = store.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = true)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { store.setDarkMode(enabled) }
    }
}
