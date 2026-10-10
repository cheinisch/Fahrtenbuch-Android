package app.fahrtenbuch.mobile.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.api.AppMode
import app.fahrtenbuch.mobile.data.model.PairQrPayload
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.data.model.TrackPoint
import app.fahrtenbuch.mobile.data.model.Vehicle
import app.fahrtenbuch.mobile.data.repository.FahrtenbuchRepository
import app.fahrtenbuch.mobile.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val appMode: AppMode = AppMode.UNSET,
    val vehicles: List<Vehicle> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val tripPoints: List<TrackPoint> = emptyList(),
    val tripDetailsLoading: Boolean = false,
    val tripDetailsError: String? = null,
    val tracking: Boolean = false,
    val selectedVehicleId: String? = null,
    val error: String? = null,
    val serverConfigured: Boolean = false,
    val serverUrl: String = "",
    val loggedIn: Boolean = false,
    val vehiclesLoadedFromServer: Boolean = false,
    val user: app.fahrtenbuch.mobile.data.model.User? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private var repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
    private val _state = MutableStateFlow(
        AppUiState(
            loading = false,
            appMode = store.appMode,
            serverConfigured = store.serverConfigured,
            serverUrl = store.baseUrl,
            loggedIn = store.refreshToken != null
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            var lastFinishedId: java.util.UUID? = null
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfosForUniqueWorkFlow("fahrtenbuch-sync")
                .collect { jobs ->
                    val finished = jobs.firstOrNull { it.state.isFinished }
                    if (finished != null && finished.id != lastFinishedId) {
                        lastFinishedId = finished.id
                        if (store.hasAuthenticatedSession) refresh()
                    }
                }
        }
        if (store.isServerMode && store.serverConfigured && store.refreshToken != null) {
            refresh()
            loadAccount()
        } else if (store.isLocalMode) {
            refresh()
        }
    }

    fun chooseLocalMode() {
        store.selectLocalMode()
        SyncScheduler.cancel(getApplication())
        _state.value = _state.value.copy(appMode = AppMode.LOCAL, serverConfigured = false, loggedIn = true, error = null)
        refresh()
    }

    fun startServerMigrationSetup() {
        store.beginServerMigration()
        _state.value = _state.value.copy(appMode = AppMode.UNSET, loggedIn = false, error = null)
    }

    fun configureServer(url: String) {
        val normalizedInput = url.trim()
        require(normalizedInput.startsWith("https://") || normalizedInput.startsWith("http://")) {
            "Die Serveradresse muss mit https:// oder http:// beginnen."
        }
        store.selectServerMode()
        store.baseUrl = normalizedInput
        repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
        _state.value = _state.value.copy(
            appMode = AppMode.SERVER,
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

    suspend fun passkeyOptions(): Result<String> {
        _state.value = _state.value.copy(loading = true, error = null)
        val result = runCatching { repository.passkeyLoginOptions() }
        if (result.isFailure) {
            _state.value = _state.value.copy(
                loading = false,
                error = result.exceptionOrNull()?.message ?: "Passkey-Anmeldung konnte nicht gestartet werden."
            )
        }
        return result
    }

    fun passkeyLogin(authenticationResponseJson: String, deviceName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.passkeyLogin(authenticationResponseJson, deviceName) }
                .onSuccess { user ->
                    _state.value = _state.value.copy(
                        loggedIn = true,
                        loading = false,
                        user = user,
                        error = null
                    )
                    refresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        loggedIn = false,
                        error = it.message ?: "Passkey-Anmeldung fehlgeschlagen."
                    )
                }
        }
    }

    fun cancelAuthLoading(message: String? = null) {
        _state.value = _state.value.copy(loading = false, error = message)
    }

    fun forgotPassword(email: String, onAccepted: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.forgotPassword(email) }
                .onSuccess {
                    _state.value = _state.value.copy(loading = false, error = null)
                    onAccepted()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Passwort-Reset konnte nicht angefordert werden."
                    )
                }
        }
    }

    fun resetPassword(token: String, password: String, onChanged: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.resetPassword(token, password) }
                .onSuccess {
                    store.clearTokens()
                    _state.value = _state.value.copy(loading = false, loggedIn = false, error = null)
                    onChanged()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Passwort konnte nicht geändert werden."
                    )
                }
        }
    }

    fun login(email: String, password: String, totpCode: String?, deviceName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.login(email, password, totpCode, deviceName) }
                .onSuccess { user ->
                    _state.value = _state.value.copy(
                        loggedIn = true,
                        loading = false,
                        user = user,
                        error = null
                    )
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

    fun loadAccount() {
        if (!store.hasAuthenticatedSession) return
        viewModelScope.launch {
            runCatching { repository.me() }
                .onSuccess { user ->
                    _state.value = _state.value.copy(user = user, error = null)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(error = ApiFactory.readableError(error))
                }
        }
    }

    fun updateAccount(
        displayName: String?,
        username: String?,
        email: String?,
        serverUrl: String,
        allowInvalidCertificates: Boolean,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val requestedServer = serverUrl.trim()
                require(requestedServer.startsWith("https://") || requestedServer.startsWith("http://")) {
                    "Die Serveradresse muss mit https:// oder http:// beginnen."
                }
                store.baseUrl = requestedServer
                store.allowInvalidCertificates =
                    requestedServer.startsWith("https://", ignoreCase = true) && allowInvalidCertificates
                repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
                repository.updateProfile(displayName, username, email)
            }
                .onSuccess { user ->
                    _state.value = _state.value.copy(
                        loading = false,
                        user = user,
                        serverUrl = store.baseUrl,
                        error = null
                    )
                    onSaved()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = ApiFactory.readableError(error)
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
        if (store.isLocalMode) {
            viewModelScope.launch {
                val vehicles = repository.vehicles(forceNetwork = false)
                val trips = repository.trips(forceNetwork = false)
                applyLocalData(vehicles, trips, loading = false)
                _state.value = _state.value.copy(error = null, vehiclesLoadedFromServer = false)
            }
            return
        }
        if (!store.serverConfigured || store.refreshToken == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            // Room is the primary source: render cached data immediately.
            val localVehicles = repository.vehicles(forceNetwork = false)
            val localTrips = repository.trips(forceNetwork = false)
            applyLocalData(localVehicles, localTrips, loading = true)

            runCatching { repository.syncFromServer() }
                .onSuccess {
                    if (!store.hasAuthenticatedSession) {
                        _state.value = _state.value.copy(
                            loading = false,
                            loggedIn = false,
                            vehicles = emptyList(),
                            trips = emptyList(),
                            error = "Deine Sitzung ist abgelaufen. Bitte melde dich erneut an."
                        )
                        return@onSuccess
                    }
                    val vehicles = repository.vehicles(forceNetwork = false)
                    val trips = repository.trips(forceNetwork = false)
                    applyLocalData(vehicles, trips, loading = false)
                    _state.value = _state.value.copy(
                        vehiclesLoadedFromServer = true,
                        error = null
                    )
                }
                .onFailure {
                    if (!store.hasAuthenticatedSession) {
                        _state.value = _state.value.copy(
                            loading = false,
                            loggedIn = false,
                            vehicles = emptyList(),
                            trips = emptyList(),
                            error = "Deine Sitzung ist abgelaufen. Bitte melde dich erneut an."
                        )
                    } else {
                        _state.value = _state.value.copy(
                            loading = false,
                            vehiclesLoadedFromServer = false,
                            error = if (localVehicles.isEmpty() && localTrips.isEmpty()) {
                                "Server nicht erreichbar und noch keine lokalen Daten vorhanden."
                            } else {
                                "Offline-Modus: Lokale Daten werden angezeigt."
                            }
                        )
                    }
                }
        }
    }

    private fun applyLocalData(vehicles: List<Vehicle>, trips: List<Trip>, loading: Boolean) {
        val selected = _state.value.selectedVehicleId
            ?.takeIf { id -> vehicles.any { it.id == id } }
            ?: vehicles.firstOrNull()?.id
        _state.value = _state.value.copy(
            loading = loading,
            vehicles = vehicles,
            trips = trips,
            tracking = store.activeTripId != null,
            selectedVehicleId = selected
        )
    }

    fun refreshLatestTripDetails() {
        val trip = _state.value.trips.firstOrNull() ?: return
        if (trip.syncState != "SYNCED") return
        viewModelScope.launch {
            runCatching { repository.getTripDetails(trip.id) }
                .onSuccess { remote ->
                    _state.value = _state.value.copy(
                        trips = _state.value.trips.map { current ->
                            if (current.id == remote.id) remote else current
                        }
                    )
                }
        }
    }

    fun loadTripDetails(trip: Trip) {
        _state.value = _state.value.copy(tripPoints = emptyList(), tripDetailsLoading = true, tripDetailsError = null)
        if (!store.isLocalMode && trip.syncState != "SYNCED") {
            _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = "Die Route ist erst nach vollständiger Synchronisierung verfügbar.")
            return
        }
        viewModelScope.launch {
            runCatching { repository.tripPoints(trip.id) }
                .onSuccess { _state.value = _state.value.copy(tripPoints = it, tripDetailsLoading = false) }
                .onFailure { _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = ApiFactory.readableError(it)) }
        }
    }

    fun updateTripCategory(trip: Trip, type: TripType) {
        if (!store.isLocalMode && trip.syncState != "SYNCED") {
            _state.value = _state.value.copy(tripDetailsError = "Bitte die Fahrt zuerst synchronisieren.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(tripDetailsLoading = true, tripDetailsError = null)
            runCatching { repository.updateTripCategory(trip, type) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        tripDetailsLoading = false,
                        trips = _state.value.trips.map { if (it.id == updated.id) updated else it }
                    )
                }
                .onFailure { _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = ApiFactory.readableError(it)) }
        }
    }

    fun syncNow() {
        if (!store.isServerMode || !store.hasAuthenticatedSession) return
        SyncScheduler.pushIfOnline(getApplication())
        refresh()
    }

    fun loadVehicles() {
        if (store.isLocalMode) { refresh(); return }
        if (!store.hasAuthenticatedSession) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            // Room remains the source of truth. Show it immediately.
            val local = repository.vehicles(forceNetwork = false)
            _state.value = _state.value.copy(
                vehicles = local,
                selectedVehicleId = _state.value.selectedVehicleId
                    ?.takeIf { id -> local.any { it.id == id } }
                    ?: local.firstOrNull()?.id
            )

            // This screen only depends on /vehicles. A failure while syncing
            // trips or tags must not make a successful vehicle request look broken.
            runCatching { repository.vehicles(forceNetwork = true) }
                .onSuccess { vehicles ->
                    // Use the actual server response immediately. Re-reading Room here
                    // made a successful request look empty when local persistence failed.
                    val selected = _state.value.selectedVehicleId
                        ?.takeIf { id -> vehicles.any { it.id == id } }
                        ?: vehicles.firstOrNull()?.id
                    _state.value = _state.value.copy(
                        loading = false,
                        vehicles = vehicles,
                        selectedVehicleId = selected,
                        vehiclesLoadedFromServer = true,
                        error = null
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        vehiclesLoadedFromServer = false,
                        error = if (local.isEmpty()) {
                            "Fahrzeuge konnten nicht vom Server geladen werden: " +
                                ApiFactory.readableError(error)
                        } else {
                            "Offline-Modus: Lokale Fahrzeuge werden angezeigt. Serverfehler: " +
                                ApiFactory.readableError(error)
                        }
                    )
                }
        }
    }

    fun createVehicle(name: String, licensePlate: String?, onCreated: (Vehicle) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.createVehicle(name, licensePlate) }
                .onSuccess { created ->
                    val vehicles = repository.vehicles(forceNetwork = false)
                    _state.value = _state.value.copy(
                        loading = false,
                        vehicles = vehicles,
                        selectedVehicleId = _state.value.selectedVehicleId ?: created.id,
                        vehiclesLoadedFromServer = true,
                        error = null
                    )
                    onCreated(created)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Fahrzeug konnte nicht angelegt werden."
                    )
                }
        }
    }

    fun setVehicleBluetooth(vehicleId: String, bluetoothMac: String?, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.setVehicleBluetooth(vehicleId, bluetoothMac) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        loading = false,
                        vehicles = _state.value.vehicles.map {
                            if (it.id == updated.id) updated else it
                        },
                        error = null
                    )
                    onSaved()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Bluetooth-Gerät konnte nicht zugewiesen werden."
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
