package app.fahrtenbuch.mobile.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.ui.common.LocalUserPreferences
import app.fahrtenbuch.mobile.ui.navigation.AppBottomBar
import app.fahrtenbuch.mobile.ui.navigation.AppNavigationRail
import app.fahrtenbuch.mobile.ui.common.LocalWindowLayout
import app.fahrtenbuch.mobile.ui.common.ProvideWindowLayout
import app.fahrtenbuch.mobile.ui.navigation.BottomDestination
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fahrtenbuch.mobile.ui.screens.AuthFrame
import app.fahrtenbuch.mobile.ui.screens.ConnectServerScreen
import app.fahrtenbuch.mobile.ui.screens.DashboardScreen
import app.fahrtenbuch.mobile.ui.screens.LoginScreen
import app.fahrtenbuch.mobile.ui.screens.ManualServerSetupScreen
import app.fahrtenbuch.mobile.ui.screens.PairConfirmationScreen
import app.fahrtenbuch.mobile.ui.screens.QrPairingScreen
import app.fahrtenbuch.mobile.ui.screens.SettingsScreen
import app.fahrtenbuch.mobile.ui.screens.StatisticsScreen
import app.fahrtenbuch.mobile.ui.screens.TripsRequest
import app.fahrtenbuch.mobile.ui.screens.TripsScreen
import app.fahrtenbuch.mobile.ui.screens.WelcomeScreen
import app.fahrtenbuch.mobile.ui.screens.LocationDisclosureScreen
import app.fahrtenbuch.mobile.ui.screens.LocationPermissionScreen
import app.fahrtenbuch.mobile.ui.screens.VehiclesScreen
import app.fahrtenbuch.mobile.ui.theme.ThemeMode
import app.fahrtenbuch.mobile.data.model.PairQrPayload
import app.fahrtenbuch.mobile.data.api.AppMode

@Composable
fun FahrtenbuchApp(
    appViewModel: AppViewModel = viewModel(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    ProvideWindowLayout {
        AppContent(appViewModel, themeMode, onThemeModeChange)
    }
}

@Composable
private fun AppContent(
    appViewModel: AppViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val state by appViewModel.state.collectAsState()
    val windowLayout = LocalWindowLayout.current

    if (state.appMode == AppMode.UNSET) {
        AuthFrame { FirstStartNavigation(appViewModel) }
        return
    }

    if (state.appMode == AppMode.SERVER && !state.serverConfigured) {
        AuthFrame { FirstStartNavigation(appViewModel) }
        return
    }

    if (state.appMode == AppMode.SERVER && !state.loggedIn) {
        AuthFrame { LoginScreen(appViewModel) }
        return
    }

    val navController = rememberNavController()
    val destinations = listOf(
        BottomDestination(
            "tracking",
            stringResource(R.string.nav_home),
            Icons.Default.LocationOn,
            showRecordingDot = state.tracking
        ),
        BottomDestination("trips", stringResource(R.string.trips), Icons.AutoMirrored.Filled.ListAlt),
        BottomDestination("statistics", stringResource(R.string.statistics), Icons.Default.BarChart),
        BottomDestination("settings", stringResource(R.string.settings), Icons.Default.Tune)
    )
    var tripsRequest by remember { mutableStateOf<TripsRequest?>(null) }
    val openTrips: (TripsRequest) -> Unit = { request ->
        tripsRequest = request
        navController.navigate("trips") {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isSelected: (BottomDestination) -> Boolean = { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true ||
            // Fahrzeuge erreicht man über die Einstellungen
            (destination.route == "settings" && currentDestination?.route == "vehicles")
    }
    val onSelect: (BottomDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalUserPreferences provides state.preferences) {
    Scaffold(
        bottomBar = {
            // Tablets: Navigation links (siehe unten), Smartphones: unten
            if (!windowLayout.useNavigationRail) {
                AppBottomBar(destinations = destinations, isSelected = isSelected, onSelect = onSelect)
            }
        }
    ) { padding ->
        Row(Modifier.padding(padding)) {
        if (windowLayout.useNavigationRail) {
            AppNavigationRail(destinations = destinations, isSelected = isSelected, onSelect = onSelect)
        }
        NavHost(
            navController = navController,
            startDestination = "tracking",
            modifier = Modifier.weight(1f)
        ) {
            composable("tracking") {
                DashboardScreen(
                    viewModel = appViewModel,
                    onOpenTrips = { category -> openTrips(TripsRequest(category = category)) },
                    onOpenTrip = { trip -> openTrips(TripsRequest(tripId = trip.id)) },
                    onSetupVehicle = { navController.navigate("vehicles") }
                )
            }
            composable("trips") {
                TripsScreen(
                    viewModel = appViewModel,
                    request = tripsRequest,
                    onRequestHandled = { tripsRequest = null }
                )
            }
            composable("statistics") { StatisticsScreen(appViewModel) }
            composable("vehicles") { VehiclesScreen(appViewModel) }
            composable("settings") {
                SettingsScreen(
                    viewModel = appViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenVehicles = { navController.navigate("vehicles") }
                )
            }
        }
        }
    }
    }
}

@Composable
private fun FirstStartNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    var pendingPairPayloadJson by rememberSaveable { mutableStateOf<String?>(null) }

    NavHost(navController = navController, startDestination = "location-disclosure") {
        composable("location-disclosure") {
            LocationDisclosureScreen(onContinue = {
                navController.navigate("location-permission") {
                    popUpTo("location-disclosure") { inclusive = true }
                }
            })
        }
        composable("location-permission") {
            LocationPermissionScreen(onContinue = {
                navController.navigate("welcome") {
                    popUpTo("location-permission") { inclusive = true }
                }
            })
        }
        composable("welcome") {
            WelcomeScreen(
                onLocal = { viewModel.chooseLocalMode() },
                onConnect = { navController.navigate("connect") }
            )
        }
        composable("connect") {
            ConnectServerScreen(
                onBack = { navController.popBackStack() },
                onQrCode = { navController.navigate("qr") },
                onManual = { navController.navigate("manual") }
            )
        }
        composable("manual") {
            ManualServerSetupScreen(
                onBack = { navController.popBackStack() },
                onSave = { url -> runCatching { viewModel.configureServer(url) } }
            )
        }
        composable("qr") {
            val state by viewModel.state.collectAsState()
            QrPairingScreen(
                onBack = { navController.popBackStack() },
                loading = state.loading,
                apiError = state.error,
                onPairCode = { payload ->
                    pendingPairPayloadJson = payload.toSavedJson()
                    navController.navigate("pair-confirm")
                }
            )
        }
        composable("pair-confirm") {
            val state by viewModel.state.collectAsState()
            val payload = pendingPairPayloadJson?.let(::pairPayloadFromSavedJson)
            if (payload == null) {
                navController.popBackStack()
            } else {
                PairConfirmationScreen(
                    payload = payload,
                    loading = state.loading,
                    apiError = state.error,
                    onBack = {
                        if (!state.loading) {
                            pendingPairPayloadJson = null
                            navController.popBackStack()
                        }
                    },
                    onConfirm = { viewModel.pairLogin(payload) }
                )
            }
        }
    }
}

private fun PairQrPayload.toSavedJson(): String = org.json.JSONObject().apply {
    put("version", version)
    put("type", type)
    put("server", server)
    put("pairId", pairId)
    put("pairToken", pairToken)
    username?.let { put("username", it) }
    email?.let { put("email", it) }
    expiresAt?.let { put("expiresAt", it) }
}.toString()

private fun pairPayloadFromSavedJson(raw: String): PairQrPayload {
    val json = org.json.JSONObject(raw)
    return PairQrPayload(
        version = json.optInt("version", 1),
        type = json.optString("type", "pair"),
        server = json.getString("server"),
        pairId = json.getString("pairId"),
        pairToken = json.getString("pairToken"),
        username = json.optString("username").ifBlank { null },
        email = json.optString("email").ifBlank { null },
        expiresAt = json.optString("expiresAt").ifBlank { null }
    )
}
