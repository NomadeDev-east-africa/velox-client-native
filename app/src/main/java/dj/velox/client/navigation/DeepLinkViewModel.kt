package dj.velox.client.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.core.notifications.DeepLink
import dj.velox.client.core.notifications.NotificationRouter
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Expose le deep-link en attente (issu d'une notification tapée) à la navigation. */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val router: NotificationRouter,
) : ViewModel() {
    val pending: StateFlow<DeepLink?> = router.pending
    fun consume() = router.consume()
}
