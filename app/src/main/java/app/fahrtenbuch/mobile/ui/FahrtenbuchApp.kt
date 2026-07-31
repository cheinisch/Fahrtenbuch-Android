package app.fahrtenbuch.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fahrtenbuch.mobile.ui.screens.ConnectServerScreen
import app.fahrtenbuch.mobile.ui.screens.DashboardScreen
import app.fahrtenbuch.mobile.ui.screens.LoginScreen
import app.fahrtenbuch.mobile.ui.screens.ManualServerSetupScreen
import app.fahrtenbuch.mobile.ui.screens.PairConfirmationScreen
import app.fahrtenbuch.mobile.ui.screens.QrPairingScreen
import app.fahrtenbuch.mobile.ui.screens.SettingsScreen
import app.fahrtenbuch.mobile.ui.screens.StatisticsScreen
import app.fahrtenbuch.mobile.ui.screens.TripsScreen
import app.fahrtenbuch.mobile.ui.screens.WelcomeScreen
import app.fahrtenbuch.mobile.ui.theme.ThemeMode
import app.fahrtenbuch.mobile.data.model.PairQrPayload

private data class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun FahrtenbuchApp(
    appViewModel: AppViewModel = viewModel(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val state by appViewModel.state.collectAsState()

    if (!state.serverConfigured) {
        FirstStartNavigation(appViewModel)
        return
    }

    if (!state.loggedIn) {
        LoginScreen(appViewModel)
        return
    }

    val navController = rememberNavController()
    val destinations = listOf(
        Destination("tracking", "Tracking", Icons.Default.LocationOn),
        Destination("trips", "Fahrten", Icons.Default.ListAlt),
        Destination("statistics", "Statistik", Icons.Default.BarChart),
        Destination("settings", "Einstellungen", Icons.Default.Settings)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "tracking",
            modifier = Modifier.padding(padding)
        ) {
            composable("tracking") { DashboardScreen(appViewModel) }
            composable("trips") { TripsScreen(appViewModel) }
            composable("statistics") { StatisticsScreen(appViewModel) }
            composable("settings") {
                SettingsScreen(
                    viewModel = appViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        }
    }
}

@Composable
private fun FirstStartNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    var pendingPairPayload by remember { mutableStateOf<PairQrPayload?>(null) }

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(onConnect = { navController.navigate("connect") })
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
                    pendingPairPayload = payload
                    navController.navigate("pair-confirm")
                }
            )
        }
        composable("pair-confirm") {
            val state by viewModel.state.collectAsState()
            val payload = pendingPairPayload
            if (payload == null) {
                navController.popBackStack()
            } else {
                PairConfirmationScreen(
                    payload = payload,
                    loading = state.loading,
                    apiError = state.error,
                    onBack = {
                        if (!state.loading) {
                            pendingPairPayload = null
                            navController.popBackStack()
                        }
                    },
                    onConfirm = { viewModel.pairLogin(payload) }
                )
            }
        }
    }
}
