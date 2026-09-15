package dj.velox.client.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.data.remote.NotificationsService
import dj.velox.client.domain.model.AppNotification
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Historique de notifications persistant (live) + compteur non-lu dérivé du même flux. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationsService: NotificationsService,
    authRepo: AuthRepository,
) : ViewModel() {

    private val uid: String? = authRepo.currentUser?.uid

    val notifications: StateFlow<List<AppNotification>> =
        (if (uid != null) notificationsService.streamNotifications(uid) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> =
        notifications
            .map { list -> list.count { !it.read } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAsRead(notificationId: String) {
        val id = uid ?: return
        viewModelScope.launch { notificationsService.markAsRead(id, notificationId) }
    }
}
