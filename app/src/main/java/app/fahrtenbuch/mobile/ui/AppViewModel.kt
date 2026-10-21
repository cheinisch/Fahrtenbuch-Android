package app.fahrtenbuch.mobile.ui

import app.fahrtenbuch.mobile.data.auth.PasskeySupport
import android.app.Application
import app.fahrtenbuch.mobile.R
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
import app.fahrtenbuch.mobile.data.model.Uuid
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
    /** Straßenroute der ausgewählten Fahrt (Map-Matching), leer = nur GPS-Punkte. */
    val tripMatchedRoute: List<TrackPoint> = emptyList(),
    val tripDetailsLoading: Boolean = false,
    val tripDetailsError: String? = null,
    /** Alle Tags des Kontos bzw. des Geräts (für die Auswahl in den Fahrtdetails). */
    val availableTags: List<app.fahrtenbuch.mobile.data.model.Tag> = emptyList(),
    /** Auf dem Server aktivierte Anmeldungen mit Google/Microsoft (für den Login-Bildschirm). */
    val oauthProviders: List<app.fahrtenbuch.mobile.data.model.OAuthProvider> = emptyList(),
    /** Vom Server angebotene Anmeldeverfahren (TOTP, Passkeys); ausgeblendet, was fehlt. */
    val authMethods: app.fahrtenbuch.mobile.data.model.AuthMethods = app.fahrtenbuch.mobile.data.model.AuthMethods(),
    /** Passkey-Verwaltung in den Einstellungen: Server bietet Passkeys an und das Gerät kann sie. */
    val passkeysAvailable: Boolean = false,
    /** Google/Microsoft-Anmeldung wartet auf den Zwei-Faktor-Code. */
    val oauthMfaRequired: Boolean = false,
    val tracking: Boolean = false,
    val selectedVehicleId: String? = null,
    val error: String? = null,
    val serverConfigured: Boolean = false,
    val serverUrl: String = "",
    val loggedIn: Boolean = false,
    val vehiclesLoadedFromServer: Boolean = false,
    val user: app.fahrtenbuch.mobile.data.model.User? = null,
    val preferences: app.fahrtenbuch.mobile.data.model.UserPreferences = app.fahrtenbuch.mobile.data.model.UserPreferences()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    /** Übersetzter Text aus den String-Ressourcen (Sprache des Geräts). */
    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private val store = SessionStore(application)
    private var repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
    private val _state = MutableStateFlow(
        AppUiState(
            loading = false,
            appMode = store.appMode,
            serverConfigured = store.serverConfigured,
            serverUrl = store.baseUrl,
            loggedIn = store.refreshToken != null,
            preferences = app.fahrtenbuch.mobile.data.model.UserPreferences.fromApi(
                store.distanceUnit, store.speedUnit, store.dateFormat, store.timeFormat, store.timeZone,
                gravatarEnabled = store.gravatarEnabled,
                gravatarAvailable = store.isServerMode && store.gravatarAvailable,
                mapMatchingEnabled = store.mapMatchingEnabled,
                mapMatchingAvailable = store.isServerMode && store.mapMatchingAvailable
            )
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
            str(R.string.error_server_scheme)
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
                    str(R.string.error_qr_server)
                }
                store.baseUrl = server
                repository = FahrtenbuchRepository(getApplication(), ApiFactory.create(store), store)
                val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                repository.pairLogin(payload, deviceName)
            }.onSuccess {
                // Der App-Modus muss auch im UI-Zustand auf SERVER wechseln (gespeichert ist er
                // schon). Sonst blieb die Einrichtung nach erfolgreichem Koppeln einfach stehen.
                _state.value = _state.value.copy(
                    appMode = store.appMode,
                    serverConfigured = true,
                    serverUrl = store.baseUrl,
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
                    error = it.message ?: str(R.string.error_pair_failed)
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
                error = result.exceptionOrNull()?.message ?: str(R.string.error_passkey_start)
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
                        error = it.message ?: str(R.string.error_passkey_failed)
                    )
                }
        }
    }

    private var pendingOAuthTicket: String? = null

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")

    /** Prüft nach Eingabe der Serveradresse, welche Anmeldeverfahren der Server anbietet. */
    fun loadAuthMethods() {
        if (!store.isServerMode) return
        viewModelScope.launch {
            val methods = repository.authMethods()
            val providers = methods.oauthProviders.map { id ->
                app.fahrtenbuch.mobile.data.model.OAuthProvider(id, id.replaceFirstChar { it.uppercase() }, true)
            }
            _state.value = _state.value.copy(authMethods = methods, oauthProviders = providers)
        }
    }

    /** Anmeldung bei Google/Microsoft starten; [openUrl] öffnet die Anbieterseite im Browser. */
    fun startOAuthLogin(provider: String, openUrl: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, oauthMfaRequired = false)
            runCatching { repository.startOAuthLogin(provider) }
                .onSuccess { url ->
                    // Ladeanzeige aus: der Nutzer ist jetzt im Browser und kann abbrechen
                    _state.value = _state.value.copy(loading = false)
                    openUrl(url)
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = ApiFactory.readableError(it)) }
        }
    }

    /** Rücksprung fahrtenbuch://oauth/callback?ticket=… bzw. ?error=CODE aus dem Browser. */
    fun handleOAuthCallback(uri: android.net.Uri) {
        val error = uri.getQueryParameter("error")
        val ticket = uri.getQueryParameter("ticket")
        when {
            error != null -> _state.value = _state.value.copy(loading = false, error = oauthErrorMessage(error))
            ticket != null -> exchangeOAuthTicket(ticket, null)
        }
    }

    fun submitOAuthTotp(code: String) {
        val ticket = pendingOAuthTicket ?: return
        exchangeOAuthTicket(ticket, code)
    }

    fun cancelOAuthMfa() {
        pendingOAuthTicket = null
        _state.value = _state.value.copy(oauthMfaRequired = false, error = null, loading = false)
    }

    private fun exchangeOAuthTicket(ticket: String, totpCode: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.completeOAuthLogin(ticket, totpCode, deviceName()) }
                .onSuccess { user ->
                    pendingOAuthTicket = null
                    _state.value = _state.value.copy(
                        loggedIn = true,
                        loading = false,
                        user = user,
                        error = null,
                        oauthMfaRequired = false
                    )
                    refresh()
                }
                .onFailure {
                    if (it is app.fahrtenbuch.mobile.data.api.OAuthMfaRequired) {
                        pendingOAuthTicket = ticket
                        _state.value = _state.value.copy(
                            loading = false,
                            oauthMfaRequired = true,
                            error = if (totpCode != null) str(R.string.oauth_mfa_invalid) else null
                        )
                    } else {
                        _state.value = _state.value.copy(loading = false, error = it.message ?: str(R.string.error_login_failed))
                    }
                }
        }
    }

    private fun oauthErrorMessage(code: String): String = when (code) {
        "OAUTH_NOT_LINKED" -> str(R.string.oauth_error_not_linked)
        "OAUTH_CANCELLED", "access_denied" -> str(R.string.oauth_error_cancelled)
        else -> str(R.string.oauth_error_generic, code)
    }

    /** Prüft, ob der Menüpunkt „Passkeys“ in den Einstellungen erscheinen soll. */
    fun loadPasskeySupport() {
        if (!store.isServerMode || !PasskeySupport.isDeviceSupported(getApplication())) {
            _state.value = _state.value.copy(passkeysAvailable = false)
            return
        }
        viewModelScope.launch {
            val supported = repository.serverSupportsPasskeys()
            _state.value = _state.value.copy(passkeysAvailable = supported)
        }
    }

    // Passkeys verwalten (Einstellungen). Die UI ruft den Credential Manager selbst auf,
    // weil er einen Activity-Kontext braucht; hier nur die Server-Seite.
    suspend fun listPasskeys(): Result<List<app.fahrtenbuch.mobile.data.model.PasskeyInfo>> =
        runCatching { repository.passkeys() }.onFailure { /* Anzeige im Dialog */ }

    suspend fun passkeyRegisterOptions(): Result<String> = runCatching { repository.passkeyRegisterOptions() }

    suspend fun registerPasskey(registrationResponseJson: String, name: String): Result<Unit> =
        runCatching { repository.registerPasskey(registrationResponseJson, name) }

    suspend fun renamePasskey(id: String, name: String): Result<Unit> =
        runCatching { repository.renamePasskey(id, name); Unit }

    suspend fun deletePasskey(id: String): Result<Unit> = runCatching { repository.deletePasskey(id) }

    fun readableError(error: Throwable): String = ApiFactory.readableError(error)

    /** Zuletzt verwendeter Anmeldename (z. B. nach Wiederherstellung eines Backups). */
    fun lastLoginName(): String = store.lastLoginName.orEmpty()

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
                        error = it.message ?: str(R.string.error_reset_request)
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
                        error = it.message ?: str(R.string.error_reset_change)
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
                        error = it.message ?: str(R.string.error_login_failed)
                    )
                }
        }
    }

    /**
     * Export für die Statistik: im Server-Modus über die API (Steuer-PDF bzw. CSV), sonst – oder
     * wenn der Server nicht erreichbar ist – auf dem Gerät aus den vorhandenen Fahrten erzeugt.
     */
    suspend fun export(request: app.fahrtenbuch.mobile.data.model.ExportRequest): app.fahrtenbuch.mobile.data.model.ExportResult {
        if (store.isServerMode) {
            runCatching { repository.serverExport(request) }.onSuccess { return it }
        }
        val current = _state.value
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            app.fahrtenbuch.mobile.data.export.LocalExport.create(
                context = getApplication(),
                request = request,
                allTrips = current.trips,
                vehicles = current.vehicles,
                prefs = current.preferences
            )
        }
    }

    /** Für „Über die App“: Version und Status des Servers. */
    suspend fun serverHealth(): app.fahrtenbuch.mobile.data.model.HealthResponse? = repository.serverHealth()

    /** Anzeige-Einstellungen laden (Server-Modus: vom Server, sonst lokal). */
    fun loadPreferences() {
        viewModelScope.launch {
            val preferences = repository.preferences()
            _state.value = _state.value.copy(preferences = preferences)
        }
    }

    fun updatePreferences(
        preferences: app.fahrtenbuch.mobile.data.model.UserPreferences,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.updatePreferences(preferences) }
                .onSuccess { saved ->
                    _state.value = _state.value.copy(loading = false, preferences = saved)
                    onSaved()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(loading = false, error = ApiFactory.readableError(error))
                }
        }
    }

    fun setGravatarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setGravatarEnabled(enabled) }
                .onSuccess { _state.value = _state.value.copy(preferences = it) }
                .onFailure { _state.value = _state.value.copy(error = ApiFactory.readableError(it)) }
        }
    }

    fun setMapMatchingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setMapMatchingEnabled(enabled) }
                .onSuccess { prefs ->
                    // Abgeschaltet: geladene Straßenroute sofort verwerfen
                    if (!prefs.showMapMatching) matchedRouteTripId = null
                    _state.value = _state.value.copy(
                        preferences = prefs,
                        tripMatchedRoute = if (prefs.showMapMatching) _state.value.tripMatchedRoute else emptyList()
                    )
                }
                .onFailure { _state.value = _state.value.copy(error = ApiFactory.readableError(it)) }
        }
    }

    fun loadAccount() {
        loadPreferences()
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
                    str(R.string.error_server_scheme)
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
        loadPreferences()
        if (store.isLocalMode) {
            viewModelScope.launch {
                val vehicles = repository.vehicles(forceNetwork = false)
                val trips = repository.trips(forceNetwork = false)
                applyLocalData(vehicles, trips, loading = false)
                _state.value = _state.value.copy(error = null, vehiclesLoadedFromServer = false)
                resolveTripDetails()
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
                            error = str(R.string.error_session_expired)
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
                            error = str(R.string.error_session_expired)
                        )
                    } else {
                        _state.value = _state.value.copy(
                            loading = false,
                            vehiclesLoadedFromServer = false,
                            error = if (localVehicles.isEmpty() && localTrips.isEmpty()) {
                                str(R.string.error_server_unreachable)
                            } else {
                                str(R.string.info_offline_local)
                            }
                        )
                    }
                }
        }
    }

    private fun applyLocalData(vehicles: List<Vehicle>, trips: List<Trip>, loading: Boolean) {
        val selected = _state.value.selectedVehicleId
            ?.takeIf { id -> vehicles.any { it.id == id } }
            ?: defaultVehicleId(vehicles)
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

    /** Zuletzt geöffnete Fahrt – verspätete Antworten einer anderen Fahrt werden verworfen. */
    private var detailsTripId: Uuid? = null

    /** Fahrt, für die die Straßenroute schon angefragt bzw. geladen ist. */
    private var matchedRouteTripId: Uuid? = null

    /**
     * Straßenroute der geöffneten Fahrt laden, sobald sie als gematcht bekannt ist.
     * Wird auch nachträglich aufgerufen: Fahrten aus dem lokalen Speicher kennen
     * mapMatching nicht, erst die Antwort des Servers liefert es nach.
     */
    fun ensureMatchedRoute(trip: Trip) {
        if (!trip.isMapMatched || !_state.value.preferences.showMapMatching) return
        if (detailsTripId != trip.id || matchedRouteTripId == trip.id) return
        matchedRouteTripId = trip.id
        viewModelScope.launch {
            runCatching { repository.tripMatchedRoute(trip.id, trip.mapMatching?.matchedAt) }
                .onSuccess { route ->
                    if (detailsTripId == trip.id) _state.value = _state.value.copy(tripMatchedRoute = route)
                }
                // Fehler: GPS-Route bleibt; beim nächsten Anlass erneut versuchen
                .onFailure { if (matchedRouteTripId == trip.id) matchedRouteTripId = null }
        }
    }

    fun loadTripDetails(trip: Trip) {
        detailsTripId = trip.id
        _state.value = _state.value.copy(tripPoints = emptyList(), tripMatchedRoute = emptyList(), tripDetailsLoading = true, tripDetailsError = null)
        if (!store.isLocalMode && trip.syncState != "SYNCED") {
            _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = str(R.string.error_route_after_sync))
            return
        }
        viewModelScope.launch {
            runCatching { repository.tripPoints(trip.id) }
                .onSuccess { _state.value = _state.value.copy(tripPoints = it, tripDetailsLoading = false) }
                .onFailure { _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = ApiFactory.readableError(it)) }
        }
        matchedRouteTripId = null
        ensureMatchedRoute(trip)
    }

    fun updateTripCategory(
        trip: Trip,
        type: TripType,
        specialCategory: app.fahrtenbuch.mobile.data.model.SpecialCategory? =
            app.fahrtenbuch.mobile.data.model.SpecialCategory.fromApi(trip.specialCategory)
    ) {
        if (!store.isLocalMode && trip.syncState != "SYNCED") {
            _state.value = _state.value.copy(tripDetailsError = str(R.string.error_sync_trip_first))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(tripDetailsLoading = true, tripDetailsError = null)
            runCatching { repository.updateTripCategory(trip, type, specialCategory) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        tripDetailsLoading = false,
                        trips = _state.value.trips.map { if (it.id == updated.id) updated else it }
                    )
                }
                .onFailure { _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = ApiFactory.readableError(it)) }
        }
    }

    fun updateTripPurpose(trip: Trip, purpose: String?, contact: String?, note: String?, onDone: () -> Unit) {
        if (!store.isLocalMode && trip.syncState != "SYNCED") {
            _state.value = _state.value.copy(tripDetailsError = str(R.string.error_sync_trip_first))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(tripDetailsLoading = true, tripDetailsError = null)
            runCatching { repository.updateTripPurpose(trip, purpose, contact, note) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        tripDetailsLoading = false,
                        trips = _state.value.trips.map { if (it.id == updated.id) updated else it }
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = ApiFactory.readableError(it)) }
        }
    }

    /** Tags für die Auswahl laden (Server, sonst gespeicherte bzw. lokale Tags). */
    fun loadTags() {
        viewModelScope.launch {
            runCatching { repository.tags() }
                .onSuccess { _state.value = _state.value.copy(availableTags = it) }
        }
    }

    /** Neuen Tag anlegen und an den Aufrufer zurückgeben (z. B. um ihn direkt auszuwählen). */
    fun createTag(name: String, onCreated: (app.fahrtenbuch.mobile.data.model.Tag) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(tripDetailsError = null)
            runCatching { repository.createTag(name) }
                .onSuccess { tag ->
                    val all = (_state.value.availableTags.filterNot { it.id == tag.id } + tag).sortedBy { it.name.lowercase() }
                    _state.value = _state.value.copy(availableTags = all)
                    onCreated(tag)
                }
                .onFailure { _state.value = _state.value.copy(tripDetailsError = ApiFactory.readableError(it)) }
        }
    }

    fun setTripTags(trip: Trip, tags: List<app.fahrtenbuch.mobile.data.model.Tag>, onDone: () -> Unit) {
        if (!store.isLocalMode && trip.syncState != "SYNCED") {
            _state.value = _state.value.copy(tripDetailsError = str(R.string.error_sync_trip_first))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(tripDetailsLoading = true, tripDetailsError = null)
            runCatching { repository.setTripTags(trip, tags) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        tripDetailsLoading = false,
                        trips = _state.value.trips.map { if (it.id == updated.id) updated else it }
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(tripDetailsLoading = false, tripDetailsError = ApiFactory.readableError(it)) }
        }
    }

    /** Adressen und Strecke für Fahrten auf dem Gerät ergänzen und die Liste danach aktualisieren. */
    private suspend fun resolveTripDetails() {
        val changed = runCatching { repository.resolveLocalTripDetails() }.getOrDefault(false)
        if (changed) {
            val trips = repository.trips(forceNetwork = false)
            _state.value = _state.value.copy(trips = trips)
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
                    ?: defaultVehicleId(local)
            )

            // This screen only depends on /vehicles. A failure while syncing
            // trips or tags must not make a successful vehicle request look broken.
            runCatching { repository.vehicles(forceNetwork = true) }
                .onSuccess { vehicles ->
                    // Use the actual server response immediately. Re-reading Room here
                    // made a successful request look empty when local persistence failed.
                    val selected = _state.value.selectedVehicleId
                        ?.takeIf { id -> vehicles.any { it.id == id } }
                        ?: defaultVehicleId(vehicles)
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
                            str(R.string.error_vehicles_load, ApiFactory.readableError(error))
                        } else {
                            str(R.string.info_offline_vehicles, ApiFactory.readableError(error))
                        }
                    )
                }
        }
    }

    /** Vorauswahl: Standardfahrzeug vom Server bzw. lokal, sonst das erste. */
    private fun defaultVehicleId(vehicles: List<Vehicle>): String? =
        (vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull())?.id

    fun setDefaultVehicle(vehicleId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.setDefaultVehicle(vehicleId) }
                .onSuccess { vehicles ->
                    _state.value = _state.value.copy(loading = false, vehicles = vehicles)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = str(R.string.error_default_vehicle, ApiFactory.readableError(error))
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
                        error = it.message ?: str(R.string.error_vehicle_create)
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
                        error = it.message ?: str(R.string.error_bt_assign)
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
                    // Lädt die laufende Fahrt in die Liste, damit die Startseite Startzeit und Dauer zeigen kann.
                    refresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        error = it.message ?: str(R.string.error_tracking_start)
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
                    // Auch im Server-Modus: Adressen schon anzeigen, solange die Fahrt noch nicht hochgeladen ist.
                    if (!store.isLocalMode) viewModelScope.launch { resolveTripDetails() }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        error = it.message ?: str(R.string.error_tracking_stop)
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
