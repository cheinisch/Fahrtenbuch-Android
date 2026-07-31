package app.fahrtenbuch.mobile.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.model.PairQrPayload
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.data.model.Vehicle
import app.fahrtenbuch.mobile.data.repository.FahrtenbuchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val tracking: Boolean = false,
    val selectedVehicleId: String? = null,
    val error: String? = null,
    val serverConfigured: Boolean = false,
    val serverUrl: String = "",
    val loggedIn: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private var repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
    private val _state = MutableStateFlow(
        AppUiState(
            loading = false,
            serverConfigured = store.serverConfigured,
            serverUrl = store.baseUrl,
            loggedIn = store.accessToken != null
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        if (store.serverConfigured && store.accessToken != null) refresh()
    }

    fun configureServer(url: String) {
        val normalizedInput = url.trim()
        require(normalizedInput.startsWith("https://") || normalizedInput.startsWith("http://")) {
            "Die Serveradresse muss mit https:// oder http:// beginnen."
        }
        store.baseUrl = normalizedInput
        repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
        _state.value = _state.value.copy(
            serverConfigured = true,
            serverUrl = store.baseUrl,
            loggedIn = false,
            loading = false,
            error = null
        )
    }

    fun resetServer() {
        store.clearServer()
        repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
        _state.value = AppUiState(
            loading = false,
            serverConfigured = false,
            serverUrl = store.baseUrl,
            loggedIn = false
        )
    }


    fun pairLogin(payload: PairQrPayload) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val server = payload.server.trim()
                require(server.startsWith("https://") || server.startsWith("http://")) {
                    "Der QR-Code enthält keine gültige Serveradresse."
                }
                store.baseUrl = server
                repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
                val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                repository.pairLogin(payload, deviceName)
            }.onSuccess {
                _state.value = _state.value.copy(
                    serverConfigured = true,
                    loggedIn = true,
                    loading = false,
                    error = null
                )
                refresh()
            }.onFailure {
                store.clearTokens()
                _state.value = _state.value.copy(
                    serverConfigured = false,
                    loggedIn = false,
                    loading = false,
                    error = it.message ?: "Die Geräteanmeldung ist fehlgeschlagen."
                )
            }
        }
    }

    fun login(email: String, password: String, totpCode: String?, deviceName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.login(email, password, totpCode, deviceName) }
                .onSuccess {
                    _state.value = _state.value.copy(loggedIn = true, loading = false)
                    refresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Anmeldung fehlgeschlagen."
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _state.value = _state.value.copy(
                loading = false,
                loggedIn = false,
                vehicles = emptyList(),
                trips = emptyList()
            )
        }
    }

    fun refresh() {
        if (!store.serverConfigured || store.accessToken == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.vehicles() to repository.trips() }
                .onSuccess { (vehicles, trips) ->
                    _state.value = _state.value.copy(
                        loading = false,
                        vehicles = vehicles,
                        trips = trips,
                        tracking = store.activeTripId != null,
                        selectedVehicleId = _state.value.selectedVehicleId ?: vehicles.firstOrNull()?.id
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "Offline-Modus: Lokale Daten werden angezeigt."
                    )
                }
        }
    }

    fun startTracking(onStarted: () -> Unit) {
        val vehicleId = _state.value.selectedVehicleId ?: return
        viewModelScope.launch {
            runCatching { repository.startTracking(vehicleId, TripType.PRIVATE, null) }
                .onSuccess {
                    _state.value = _state.value.copy(tracking = true, error = null)
                    onStarted()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        error = it.message ?: "Tracking konnte nicht gestartet werden."
                    )
                }
        }
    }

    fun stopTracking(onStopped: () -> Unit) {
        val tripId = store.activeTripId ?: return
        viewModelScope.launch {
            runCatching { repository.stopTracking(tripId, null, null) }
                .onSuccess {
                    _state.value = _state.value.copy(tracking = false)
                    onStopped()
                    refresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        error = it.message ?: "Tracking konnte nicht beendet werden."
                    )
                }
        }
    }

    fun setTracking(active: Boolean) {
        _state.value = _state.value.copy(tracking = active)
    }

    fun selectVehicle(id: String) {
        _state.value = _state.value.copy(selectedVehicleId = id)
    }

    fun changeServerUrl(url: String) = configureServer(url)
}
