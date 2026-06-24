package dj.velox.client.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.AddressService
import dj.velox.client.domain.model.Address
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddressUiState(
    val addresses: List<Address> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * CRUD adresses (port d'`AddressNotifier`). Partagé au niveau du graphe pour que la liste
 * et le formulaire d'ajout/édition travaillent sur le même état.
 */
@HiltViewModel
class AddressViewModel @Inject constructor(
    private val service: AddressService,
) : ViewModel() {

    private val _state = MutableStateFlow(AddressUiState())
    val state: StateFlow<AddressUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            _state.value = AddressUiState(addresses = service.fetch(), isLoading = false)
        }
    }

    fun addressById(id: String): Address? = _state.value.addresses.firstOrNull { it.id == id }

    fun add(address: Address, onDone: () -> Unit) {
        viewModelScope.launch {
            val isFirst = _state.value.addresses.isEmpty()
            service.add(address.toMap(), isDefault = isFirst)
            load()
            onDone()
        }
    }

    fun update(id: String, address: Address, onDone: () -> Unit) {
        viewModelScope.launch {
            service.update(id, address.toMap())
            load()
            onDone()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val wasDefault = _state.value.addresses.firstOrNull { it.id == id }?.isDefault == true
            service.delete(id)
            val remaining = _state.value.addresses.filter { it.id != id }
            if (wasDefault && remaining.isNotEmpty()) {
                service.setDefault(remaining.first().id, remaining.map { it.id })
            }
            load()
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch {
            service.setDefault(id, _state.value.addresses.map { it.id })
            load()
        }
    }
}
