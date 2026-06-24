package dj.velox.client.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import dj.velox.client.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dj.velox.client.feature.auth.AuthViewModel
import dj.velox.client.feature.auth.EmailSignUpScreen
import dj.velox.client.feature.auth.ForgotPasswordScreen
import dj.velox.client.feature.auth.OtpScreen
import dj.velox.client.feature.auth.PhoneLoginScreen
import dj.velox.client.feature.auth.SessionState
import dj.velox.client.feature.auth.SignInScreen
import dj.velox.client.feature.auth.SignUpScreen
import dj.velox.client.feature.food.AddToOrderScreen
import dj.velox.client.feature.food.CartScreen
import dj.velox.client.feature.food.CartViewModel
import dj.velox.client.feature.food.FoodHomeScreen
import dj.velox.client.feature.food.FoodSearchScreen
import dj.velox.client.feature.food.OrderCompletedScreen
import dj.velox.client.feature.food.OrderTrackingScreen
import dj.velox.client.feature.food.TrackDeliveryScreen
import dj.velox.client.feature.food.RestaurantDetailScreen
import dj.velox.client.feature.home.HomeScreen
import dj.velox.client.feature.location.AddressPickerScreen
import dj.velox.client.feature.location.PickedPlace
import dj.velox.client.feature.onboarding.OnboardingScreen
import dj.velox.client.feature.orders.OrderHistoryScreen
import dj.velox.client.feature.taxi.ConfirmRideScreen
import dj.velox.client.feature.taxi.RideCompletedScreen
import dj.velox.client.feature.profile.AddAddressScreen
import dj.velox.client.feature.profile.EditProfileScreen
import dj.velox.client.feature.profile.LanguageSelectionScreen
import dj.velox.client.feature.profile.MyAddressesScreen
import dj.velox.client.feature.profile.ProfileScreen
import dj.velox.client.feature.profile.SupportScreen
import dj.velox.client.feature.taxi.TaxiHomeScreen
import dj.velox.client.feature.taxi.TrackingScreen
import dj.velox.client.ui.components.VeloxLoader

/** Destinations de navigation. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"
    const val EMAIL_SIGN_UP = "email_sign_up"
    const val FORGOT = "forgot_password"
    const val PHONE = "phone_login"
    const val OTP = "otp"

    // Zone authentifiée
    const val HOME = "home"
    const val TAXI_HOME = "taxi_home"
    const val TAXI_TRACKING = "taxi_tracking"
    const val CONFIRM_RIDE = "confirm_ride"
    const val RIDE_COMPLETED = "ride_completed"
    const val PICK_ADDRESS = "pick_address"
    const val FOOD_HOME = "food_home"
    const val FOOD_SEARCH = "food_search"
    const val RESTAURANT_DETAIL = "restaurant_detail"
    const val ADD_TO_ORDER = "add_to_order"
    const val CART = "cart"
    const val ORDER_TRACKING = "order_tracking"
    const val ORDER_COMPLETED = "order_completed"
    const val TRACK_DELIVERY = "track_delivery"
    const val PROFILE = "profile"
    const val ORDERS = "orders"
    const val ADDRESSES = "addresses"
    const val ADD_ADDRESS = "add_address"
    const val EDIT_PROFILE = "edit_profile"
    const val LANGUAGE = "language"
    const val SUPPORT = "support"

    fun restaurantDetail(id: String) = "$RESTAURANT_DETAIL/$id"
    fun addToOrder(restaurantId: String, menuId: String) = "$ADD_TO_ORDER/$restaurantId/$menuId"
    fun orderTracking(id: String) = "$ORDER_TRACKING/$id"
    fun orderCompleted(id: String) = "$ORDER_COMPLETED/$id"
    fun trackDelivery(id: String) = "$TRACK_DELIVERY/$id"
    fun addAddress(id: String? = null) = if (id == null) ADD_ADDRESS else "$ADD_ADDRESS?addressId=$id"
    fun pickAddress(title: String) = "$PICK_ADDRESS?title=${android.net.Uri.encode(title)}"
    fun confirmRide(
        pLat: Double, pLng: Double, pAddr: String,
        dLat: Double, dLng: Double, dAddr: String,
        distance: Double, vehicleId: String,
    ): String = "$CONFIRM_RIDE?pLat=$pLat&pLng=$pLng&pAddr=${android.net.Uri.encode(pAddr)}" +
        "&dLat=$dLat&dLng=$dLng&dAddr=${android.net.Uri.encode(dAddr)}&dist=$distance&vehicle=$vehicleId"
    fun rideCompleted(rideId: String) = "$RIDE_COMPLETED/$rideId"
}

/**
 * Racine de l'app (équivalent AuthWrapper Flutter) :
 * observe la session et route vers loader / auth / accueil.
 * L'AuthViewModel est partagé (une seule instance) entre la session et les écrans d'auth.
 */
@Composable
fun RootContent(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val session by authViewModel.session.collectAsStateWithLifecycle()

    // Enregistre le token FCM dès que l'utilisateur est authentifié.
    LaunchedEffect(session.firebaseUser?.uid) {
        if (session.isAuthenticated) authViewModel.registerPushToken()
    }

    Box(modifier.fillMaxSize()) {
        when {
            session.isLoading -> VeloxLoader()
            session.isAuthenticated -> MainNavGraph(
                session = session,
                onLogout = authViewModel::signOut,
            )
            else -> AuthNavGraph(authViewModel)
        }
    }
}

/** Navigation de la zone authentifiée (accueil + taxi + food ; profil au module 6). */
@Composable
private fun MainNavGraph(session: SessionState, onLogout: () -> Unit) {
    val navController = rememberNavController()
    // Panier hissé au niveau du graphe → partagé entre liste / détail / panier.
    val cartViewModel: CartViewModel = hiltViewModel()
    // Adresses hissées → partagées entre la liste et le formulaire d'ajout/édition.
    val addressViewModel: dj.velox.client.feature.profile.AddressViewModel = hiltViewModel()

    // Évite une double navigation entre le deep-link (notif tapée) et la restauration
    // au lancement : le premier des deux qui s'exécute pose ce drapeau, l'autre s'abstient.
    var activeNavHandled by remember { mutableStateOf(false) }

    // Deep-link depuis une notification tapée → navigation vers le suivi concerné.
    val deepLinkVm: DeepLinkViewModel = hiltViewModel()
    val deepLink by deepLinkVm.pending.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        activeNavHandled = true
        when {
            link.rideId != null -> navController.navigate(Routes.TAXI_TRACKING)
            link.orderId != null -> navController.navigate(Routes.orderTracking(link.orderId!!))
        }
        deepLinkVm.consume()
    }

    // Restauration au lancement : si une commande/course active est en cache (après kill),
    // ouvrir directement son suivi — sans dépendre du payload de la notification.
    val activeSessionVm: ActiveSessionViewModel = hiltViewModel()
    val restoreTarget by activeSessionVm.restore.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { activeSessionVm.checkOnce() }
    LaunchedEffect(restoreTarget) {
        val target = restoreTarget ?: return@LaunchedEffect
        if (!activeNavHandled) {
            activeNavHandled = true
            when (target) {
                is ActiveSessionViewModel.Restore.ActiveOrder ->
                    navController.navigate(Routes.orderTracking(target.orderId))
                is ActiveSessionViewModel.Restore.ActiveRide ->
                    navController.navigate(Routes.TAXI_TRACKING)
            }
        }
        activeSessionVm.consume()
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                session = session,
                onOpenTaxi = { navController.navigate(Routes.TAXI_HOME) },
                onOpenFood = { navController.navigate(Routes.FOOD_HOME) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenOrders = { navController.navigate(Routes.ORDERS) },
            )
        }

        // ── Profil & historique ──
        composable(Routes.PROFILE) {
            ProfileScreen(
                session = session,
                onOpenOrders = { navController.navigate(Routes.ORDERS) },
                onLogout = onLogout,
                onBack = { navController.popBackStack() },
                onManageAddresses = { navController.navigate(Routes.ADDRESSES) },
                onAddAddress = { navController.navigate(Routes.addAddress()) },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onSelectLanguage = { navController.navigate(Routes.LANGUAGE) },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
            )
        }
        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(session = session, onBack = { navController.popBackStack() })
        }
        composable(Routes.LANGUAGE) {
            LanguageSelectionScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SUPPORT) {
            SupportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ORDERS) {
            OrderHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADDRESSES) {
            MyAddressesScreen(
                vm = addressViewModel,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.addAddress()) },
                onEdit = { id -> navController.navigate(Routes.addAddress(id)) },
            )
        }
        composable(
            route = "${Routes.ADD_ADDRESS}?addressId={addressId}",
            arguments = listOf(navArgument("addressId") { type = NavType.StringType; defaultValue = "" }),
        ) { backStackEntry ->
            val addressId = backStackEntry.arguments?.getString("addressId").orEmpty().ifBlank { null }
            AddAddressScreen(
                vm = addressViewModel,
                addressId = addressId,
                onBack = { navController.popBackStack() },
            )
        }

        // ── Taxi ──
        composable(Routes.TAXI_HOME) { entry ->
            val pickedRaw by entry.savedStateHandle.getStateFlow<String?>(PickedPlace.KEY, null).collectAsStateWithLifecycle()
            val pickDestTitle = stringResource(R.string.confirm_destination)
            TaxiHomeScreen(
                onBack = { navController.popBackStack() },
                onPickDestination = { navController.navigate(Routes.pickAddress(pickDestTitle)) },
                pickedDestination = PickedPlace.decode(pickedRaw),
                onConsumePicked = { entry.savedStateHandle[PickedPlace.KEY] = null },
                onConfirmRide = { pickup, dest, pAddr, dAddr, distance, vehicleId ->
                    navController.navigate(
                        Routes.confirmRide(pickup.latitude, pickup.longitude, pAddr, dest.latitude, dest.longitude, dAddr, distance, vehicleId),
                    )
                },
            )
        }
        composable(
            route = "${Routes.CONFIRM_RIDE}?pLat={pLat}&pLng={pLng}&pAddr={pAddr}&dLat={dLat}&dLng={dLng}&dAddr={dAddr}&dist={dist}&vehicle={vehicle}",
            arguments = listOf(
                navArgument("pLat") { type = NavType.StringType },
                navArgument("pLng") { type = NavType.StringType },
                navArgument("pAddr") { type = NavType.StringType },
                navArgument("dLat") { type = NavType.StringType },
                navArgument("dLng") { type = NavType.StringType },
                navArgument("dAddr") { type = NavType.StringType },
                navArgument("dist") { type = NavType.StringType },
                navArgument("vehicle") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val a = backStackEntry.arguments
            ConfirmRideScreen(
                pickup = dj.velox.client.domain.model.LatLng(a?.getString("pLat")?.toDouble() ?: 0.0, a?.getString("pLng")?.toDouble() ?: 0.0),
                destination = dj.velox.client.domain.model.LatLng(a?.getString("dLat")?.toDouble() ?: 0.0, a?.getString("dLng")?.toDouble() ?: 0.0),
                pickupAddress = a?.getString("pAddr").orEmpty(),
                destinationAddress = a?.getString("dAddr").orEmpty(),
                distance = a?.getString("dist")?.toDouble() ?: 0.0,
                vehicleId = a?.getString("vehicle").orEmpty(),
                onConfirmed = {
                    navController.navigate(Routes.TAXI_TRACKING) { popUpTo(Routes.HOME) }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.PICK_ADDRESS}?title={title}",
            arguments = listOf(navArgument("title") { type = NavType.StringType; defaultValue = "Confirmer" }),
        ) { entry ->
            AddressPickerScreen(
                title = entry.arguments?.getString("title") ?: stringResource(R.string.confirm),
                onConfirm = { address, lat, lng ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(PickedPlace.KEY, PickedPlace.encode(lat, lng, address))
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TAXI_TRACKING) {
            TrackingScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onCompleted = { rideId ->
                    navController.navigate(Routes.rideCompleted(rideId)) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(
            route = "${Routes.RIDE_COMPLETED}/{rideId}",
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) {
            RideCompletedScreen(
                onNewRide = { navController.navigate(Routes.TAXI_HOME) { popUpTo(Routes.HOME) } },
                onFinished = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
            )
        }

        // ── Food ──
        composable(Routes.FOOD_HOME) {
            FoodHomeScreen(
                cartViewModel = cartViewModel,
                onOpenRestaurant = { id -> navController.navigate(Routes.restaurantDetail(id)) },
                onOpenCart = { navController.navigate(Routes.CART) },
                onOpenSearch = { navController.navigate(Routes.FOOD_SEARCH) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.FOOD_SEARCH) {
            FoodSearchScreen(
                onOpenRestaurant = { id -> navController.navigate(Routes.restaurantDetail(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.RESTAURANT_DETAIL}/{restaurantId}",
            arguments = listOf(navArgument("restaurantId") { type = NavType.StringType }),
        ) {
            RestaurantDetailScreen(
                cartViewModel = cartViewModel,
                onOpenItem = { restaurantId, menuId ->
                    navController.navigate(Routes.addToOrder(restaurantId, menuId))
                },
                onOpenCart = { navController.navigate(Routes.CART) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.ADD_TO_ORDER}/{restaurantId}/{menuId}",
            arguments = listOf(
                navArgument("restaurantId") { type = NavType.StringType },
                navArgument("menuId") { type = NavType.StringType },
            ),
        ) {
            AddToOrderScreen(
                cartViewModel = cartViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CART) { entry ->
            val pickedRaw by entry.savedStateHandle.getStateFlow<String?>(PickedPlace.KEY, null).collectAsStateWithLifecycle()
            val pickAddrTitle = stringResource(R.string.confirm_address_action)
            CartScreen(
                cartViewModel = cartViewModel,
                session = session,
                onOrderPlaced = { orderId ->
                    navController.navigate(Routes.orderTracking(orderId)) {
                        // Vide la pile food jusqu'à l'accueil food : retour = accueil, pas le panier.
                        popUpTo(Routes.FOOD_HOME) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
                onPickAddress = { navController.navigate(Routes.pickAddress(pickAddrTitle)) },
                pickedAddress = PickedPlace.decode(pickedRaw),
                onConsumePicked = { entry.savedStateHandle[PickedPlace.KEY] = null },
            )
        }
        composable(
            route = "${Routes.ORDER_TRACKING}/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
            OrderTrackingScreen(
                orderId = orderId,
                onExit = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onConfirmDelivery = {
                    navController.navigate(Routes.orderCompleted(orderId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onTrackDriver = { navController.navigate(Routes.trackDelivery(orderId)) },
            )
        }
        composable(
            route = "${Routes.TRACK_DELIVERY}/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) {
            TrackDeliveryScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.ORDER_COMPLETED}/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) {
            OrderCompletedScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
            )
        }
    }
}

@Composable
private fun AuthNavGraph(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onStart = { navController.navigate(Routes.SIGN_IN) })
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                viewModel = authViewModel,
                onNavigateToSignUp = {
                    authViewModel.clearError()
                    navController.navigate(Routes.SIGN_UP)
                },
                onNavigateToPhone = {
                    authViewModel.clearError()
                    navController.navigate(Routes.PHONE)
                },
                onNavigateToForgot = {
                    authViewModel.clearError()
                    navController.navigate(Routes.FORGOT)
                },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                viewModel = authViewModel,
                onNavigateToSignIn = {
                    authViewModel.clearError()
                    navController.popBackStack()
                },
                onNavigateToPhone = {
                    authViewModel.clearError()
                    navController.navigate(Routes.PHONE)
                },
                onNavigateToEmailSignUp = {
                    authViewModel.clearError()
                    navController.navigate(Routes.EMAIL_SIGN_UP)
                },
            )
        }
        composable(Routes.EMAIL_SIGN_UP) {
            EmailSignUpScreen(
                viewModel = authViewModel,
                onNavigateToSignIn = {
                    authViewModel.clearError()
                    navController.popBackStack(Routes.SIGN_IN, inclusive = false)
                },
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PHONE) {
            PhoneLoginScreen(
                viewModel = authViewModel,
                onCodeSent = { navController.navigate(Routes.OTP) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.OTP) {
            OtpScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
