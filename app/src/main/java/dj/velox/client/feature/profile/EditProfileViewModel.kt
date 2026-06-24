package dj.velox.client.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.UserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val photoUrl: String? = null,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val userService: UserService,
) : ViewModel() {

    private val _ui = MutableStateFlow(EditProfileUiState())
    val ui: StateFlow<EditProfileUiState> = _ui.asStateFlow()

    private var photoInitialized = false

    /** Initialise la photo affichée avec celle de la session (une seule fois). */
    fun initPhoto(url: String?) {
        if (!photoInitialized) {
            photoInitialized = true
            _ui.value = _ui.value.copy(photoUrl = url)
        }
    }

    fun save(name: String, phone: String?, birthDateMillis: Long?, onDone: () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isSaving = true, error = null)
            runCatching { userService.updateProfile(name = name, phone = phone, birthDateMillis = birthDateMillis) }
                .onSuccess { _ui.value = _ui.value.copy(isSaving = false); onDone() }
                .onFailure { _ui.value = _ui.value.copy(isSaving = false, error = it.message) }
        }
    }

    fun uploadPhoto(bytes: ByteArray) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isUploadingPhoto = true, error = null)
            runCatching { userService.uploadProfilePhoto(bytes) }
                .onSuccess { url -> _ui.value = _ui.value.copy(isUploadingPhoto = false, photoUrl = url) }
                .onFailure { _ui.value = _ui.value.copy(isUploadingPhoto = false, error = it.message) }
        }
    }
}
