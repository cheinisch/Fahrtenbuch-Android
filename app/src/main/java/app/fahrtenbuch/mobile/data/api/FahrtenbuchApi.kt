package app.fahrtenbuch.mobile.data.api

import app.fahrtenbuch.mobile.data.model.ApiMessage
import app.fahrtenbuch.mobile.data.model.BluetoothMacRequest
import app.fahrtenbuch.mobile.data.model.CreateVehicleRequest
import app.fahrtenbuch.mobile.data.model.DeregisterVehicleRequest
import app.fahrtenbuch.mobile.data.model.Dashboard
import app.fahrtenbuch.mobile.data.model.DeviceInfo
import app.fahrtenbuch.mobile.data.model.ForgotPasswordRequest
import app.fahrtenbuch.mobile.data.model.LoginRequest
import app.fahrtenbuch.mobile.data.model.LoginResponse
import app.fahrtenbuch.mobile.data.model.LogoutRequest
import app.fahrtenbuch.mobile.data.model.OdometerReadingRequest
import app.fahrtenbuch.mobile.data.model.OdometerReminder
import app.fahrtenbuch.mobile.data.model.OdometerReminderSettings
import app.fahrtenbuch.mobile.data.model.PairLoginRequest
import app.fahrtenbuch.mobile.data.model.PairOptionsRequest
import app.fahrtenbuch.mobile.data.model.PairOptionsResponse
import app.fahrtenbuch.mobile.data.model.PairStatusResponse
import app.fahrtenbuch.mobile.data.model.PasskeyInfo
import app.fahrtenbuch.mobile.data.model.RefreshRequest
import app.fahrtenbuch.mobile.data.model.RefreshResponse
import app.fahrtenbuch.mobile.data.model.ResetPasswordRequest
import app.fahrtenbuch.mobile.data.model.SessionInfo
import app.fahrtenbuch.mobile.data.model.StartTrackingRequest
import app.fahrtenbuch.mobile.data.model.StopTrackingRequest
import app.fahrtenbuch.mobile.data.model.Tag
import app.fahrtenbuch.mobile.data.model.TagRequest
import app.fahrtenbuch.mobile.data.model.TripTagsRequest
import app.fahrtenbuch.mobile.data.model.TotpStatus
import app.fahrtenbuch.mobile.data.model.TokenPair
import app.fahrtenbuch.mobile.data.model.TrackPoint
import app.fahrtenbuch.mobile.data.model.TrackingPointsRequest
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripInput
import app.fahrtenbuch.mobile.data.model.UpdateVehicleRequest
import app.fahrtenbuch.mobile.data.model.User
import app.fahrtenbuch.mobile.data.model.Uuid
import app.fahrtenbuch.mobile.data.model.Vehicle
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import app.fahrtenbuch.mobile.data.model.HealthResponse
import app.fahrtenbuch.mobile.data.model.MeSettingsResponse
import app.fahrtenbuch.mobile.data.model.UserSettingsDto
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.Response

// Ohne diese Annotation erzeugt Kotlin für Map<String, Any?> den Java-Typ Map<String, ?>,
// den Retrofit zur Laufzeit ablehnt („Parameter type must not include a type variable or wildcard“).
@JvmSuppressWildcards
interface FahrtenbuchApi {

    // ---------------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------------

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @GET("auth/methods")
    suspend fun authMethods(): app.fahrtenbuch.mobile.data.model.AuthMethods

    // Anmeldung mit Google/Microsoft (OpenID Connect, App-Flow mit PKCE)
    @GET("auth/oauth/providers")
    suspend fun oauthProviders(): List<app.fahrtenbuch.mobile.data.model.OAuthProvider>

    @POST("auth/oauth/{provider}/login")
    suspend fun oauthLogin(
        @Path("provider") provider: String,
        @Body request: app.fahrtenbuch.mobile.data.model.OAuthLoginRequest
    ): app.fahrtenbuch.mobile.data.model.OAuthAuthorizationResponse

    @POST("auth/oauth/exchange")
    suspend fun oauthExchange(
        @Body request: app.fahrtenbuch.mobile.data.model.OAuthExchangeRequest
    ): LoginResponse

    @POST("auth/passkeys/login/options")
    suspend fun passkeyLoginOptions(): ResponseBody

    @POST("auth/passkeys/login/verify")
    suspend fun passkeyLoginVerify(
        @Body request: RequestBody
    ): LoginResponse

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): RefreshResponse

    @POST("auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): ResponseBody

    @POST("auth/password/forgot")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): ApiMessage

    @POST("auth/password/reset")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): ApiMessage

    // ---------------------------------------------------------------------
    // Pairing
    // ---------------------------------------------------------------------

    @POST("auth/pair/options")
    suspend fun pairOptions(
        @Body request: PairOptionsRequest
    ): PairOptionsResponse

    @POST("auth/pair")
    suspend fun pair(
        @Body request: PairLoginRequest
    ): TokenPair

    @GET("auth/pair/{pairId}/status")
    suspend fun pairStatus(
        @Path("pairId") pairId: Uuid
    ): PairStatusResponse

    @DELETE("auth/pair/{pairId}")
    suspend fun cancelPair(
        @Path("pairId") pairId: Uuid
    ): ResponseBody

    // ---------------------------------------------------------------------
    // Benutzer
    // ---------------------------------------------------------------------

    @GET("users/me")
    suspend fun me(): User

    @PATCH("users/me/profile")
    suspend fun patchProfile(
        @Body body: Map<String, String>
    ): User

    @PUT("users/me")
    suspend fun updateMe(
        @Body body: Map<String, Any?>
    ): User

    @GET("users/me/sessions")
    suspend fun sessions(): List<SessionInfo>

    @DELETE("users/me/sessions/{id}")
    suspend fun deleteSession(
        @Path("id") id: Uuid
    ): ResponseBody

    // ---------------------------------------------------------------------
    // TOTP
    // ---------------------------------------------------------------------

    @GET("users/me/totp")
    suspend fun totp(): TotpStatus

    @POST("users/me/totp")
    suspend fun createTotp(
        @Body body: Map<String, String> = emptyMap()
    ): TotpStatus

    @DELETE("users/me/totp")
    suspend fun deleteTotp(): ResponseBody

    // ---------------------------------------------------------------------
    // Passkeys
    // ---------------------------------------------------------------------

    /** Alle Passkeys des Kontos – egal ob im Web-Dashboard oder in der App angelegt. */
    @GET("auth/passkeys")
    suspend fun passkeys(): List<PasskeyInfo>

    @POST("auth/passkeys/register/options")
    suspend fun passkeyRegisterOptions(): ResponseBody

    /** Antwort des Credential Managers (registrationResponseJson) plus „name“. */
    @POST("auth/passkeys/register/verify")
    suspend fun passkeyRegisterVerify(
        @Body body: RequestBody
    ): ResponseBody

    @PATCH("auth/passkeys/{id}")
    suspend fun renamePasskey(
        @Path("id") id: Uuid,
        @Body body: Map<String, String>
    ): PasskeyInfo

    @DELETE("auth/passkeys/{id}")
    suspend fun deletePasskey(
        @Path("id") id: Uuid
    ): Response<Unit>

    // ---------------------------------------------------------------------
    // Geräte
    // ---------------------------------------------------------------------

    @GET("users/me/devices")
    suspend fun devices(): List<DeviceInfo>

    @DELETE("users/me/devices/{deviceId}")
    suspend fun deleteDevice(
        @Path("deviceId") deviceId: Uuid
    ): ResponseBody

    // ---------------------------------------------------------------------
    // Fahrzeuge
    // ---------------------------------------------------------------------

    @GET("vehicles")
    suspend fun getVehicles(): List<Vehicle>

    @POST("vehicles")
    suspend fun createVehicle(
        @Body request: CreateVehicleRequest
    ): Vehicle

    @PUT("vehicles/{vehicleId}")
    suspend fun updateVehicle(
        @Path("vehicleId") id: Uuid,
        @Body request: UpdateVehicleRequest
    ): Vehicle

    /**
     * PUT /vehicles/{id} ersetzt das ganze Fahrzeug: nicht gesendete Felder (Hersteller,
     * Modell, VIN, Kilometerstand, Notizen, Leasingdaten …) werden geleert. Zum Ändern
     * einzelner Felder deshalb erst dieses Objekt holen, anpassen und komplett zurückschicken.
     */
    @GET("vehicles/{vehicleId}")
    suspend fun getVehicleRaw(
        @Path("vehicleId") id: Uuid
    ): MutableMap<String, Any?>

    @PUT("vehicles/{vehicleId}")
    suspend fun updateVehicleRaw(
        @Path("vehicleId") id: Uuid,
        @Body body: MutableMap<String, Any?>
    ): Vehicle

    @PUT("vehicles/{vehicleId}/default")
    suspend fun setDefaultVehicle(
        @Path("vehicleId") vehicleId: Uuid
    ): Vehicle

    @PUT("vehicles/{vehicleId}/bluetooth")
    suspend fun setBluetooth(
        @Path("vehicleId") id: Uuid,
        @Body request: BluetoothMacRequest
    ): Vehicle

    /** Fahrzeug abmelden (deaktivieren); die Fahrten bleiben erhalten. */
    @POST("vehicles/{vehicleId}/deregister")
    suspend fun deregisterVehicle(
        @Path("vehicleId") id: Uuid,
        @Body request: DeregisterVehicleRequest = DeregisterVehicleRequest()
    ): Vehicle

    /** Abgemeldetes Fahrzeug wieder anmelden. */
    @POST("vehicles/{vehicleId}/register")
    suspend fun registerVehicle(
        @Path("vehicleId") id: Uuid
    ): Vehicle

    // ---------------------------------------------------------------------
    // Kilometerstand-Erinnerung
    // ---------------------------------------------------------------------

    @GET("odometer/reminders")
    suspend fun odometerReminders(): List<OdometerReminder>

    /** 204 ohne Body, daher Response statt Typ. */
    @POST("odometer/reminders/{id}/ack")
    suspend fun ackOdometerReminder(@Path("id") id: Uuid): retrofit2.Response<Unit>

    @GET("odometer/reminder-settings/{vehicleId}")
    suspend fun odometerReminderSettings(@Path("vehicleId") vehicleId: Uuid): OdometerReminderSettings

    @PUT("odometer/reminder-settings/{vehicleId}")
    suspend fun saveOdometerReminderSettings(
        @Path("vehicleId") vehicleId: Uuid,
        @Body settings: OdometerReminderSettings
    ): OdometerReminderSettings

    /** Kilometerstand für einen Tag des Monats (month = YYYY-MM) eintragen. */
    @PUT("odometer/{vehicleId}/{month}")
    suspend fun saveOdometerReading(
        @Path("vehicleId") vehicleId: Uuid,
        @Path("month") month: String,
        @Body request: OdometerReadingRequest
    ): okhttp3.ResponseBody

    @DELETE("vehicles/{vehicleId}")
    suspend fun deleteVehicle(
        @Path("vehicleId") id: Uuid
    ): ResponseBody

    // ---------------------------------------------------------------------
    // Fahrten
    // ---------------------------------------------------------------------

    @GET("trips")
    suspend fun getTrips(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("vehicleId") vehicleId: Uuid? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0
    ): List<Trip>

    @GET("trips/{id}")
    suspend fun getTrip(
        @Path("id") id: Uuid
    ): Trip

    @POST("trips")
    suspend fun createTrip(
        @Body request: TripInput
    ): Trip

    @PUT("trips/{id}")
    suspend fun updateTrip(
        @Path("id") id: Uuid,
        @Body request: TripInput
    ): Trip

    @PUT("trips/{id}/classify")
    suspend fun classifyTrip(
        @Path("id") id: Uuid,
        @Body request: Map<String, String>
    ): Trip

    /** Wie classifyTrip, aber als rohes JSON, damit specialCategory = null (entfernen) mitgesendet wird. */
    @PUT("trips/{id}/classify")
    suspend fun classifyTripJson(
        @Path("id") id: Uuid,
        @Body body: okhttp3.RequestBody
    ): Trip

    /** Wie [deleteTrip], aber ohne Body-Erwartung (204) – für den Sync. */
    @DELETE("trips/{id}")
    suspend fun archiveTrip(@Path("id") id: Uuid): retrofit2.Response<Unit>

    @DELETE("trips/{id}")
    suspend fun deleteTrip(
        @Path("id") id: Uuid
    ): ResponseBody

    @GET("trips/{id}/points")
    suspend fun tripPoints(
        @Path("id") id: Uuid
    ): List<TrackPoint>

    @GET("trips/{id}/matched-route")
    suspend fun tripMatchedRoute(
        @Path("id") id: Uuid
    ): app.fahrtenbuch.mobile.data.model.TripMatchedRoute

    // ---------------------------------------------------------------------
    // GPS-Tracking
    // ---------------------------------------------------------------------

    @POST("tracking/start")
    suspend fun startTracking(
        @Body request: StartTrackingRequest
    ): Trip

    @POST("tracking/points")
    suspend fun uploadPoints(
        @Body request: TrackingPointsRequest
    ): ResponseBody

    @POST("tracking/stop")
    suspend fun stopTracking(
        @Body request: StopTrackingRequest
    ): Trip

    @GET("tracking/status")
    suspend fun trackingStatus(): Map<String, Any?>

    // ---------------------------------------------------------------------
    // Tags
    // ---------------------------------------------------------------------

    @GET("tags")
    suspend fun tags(): List<Tag>

    @PUT("trips/{id}/tags")
    suspend fun setTripTags(
        @Path("id") id: Uuid,
        @Body request: TripTagsRequest
    ): Trip

    @POST("tags")
    suspend fun createTag(
        @Body request: TagRequest
    ): Tag

    @PUT("tags/{tagId}")
    suspend fun updateTag(
        @Path("tagId") id: Uuid,
        @Body request: TagRequest
    ): Tag

    @DELETE("tags/{tagId}")
    suspend fun deleteTag(
        @Path("tagId") id: Uuid
    ): ResponseBody

    // ---------------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------------

    @GET("dashboard")
    suspend fun dashboard(): Dashboard

    // ---------------------------------------------------------------------
    // Karte
    // ---------------------------------------------------------------------

    @GET("map")
    suspend fun map(): Map<String, Any?>

    @GET("map/{tripId}")
    suspend fun mapTrip(
        @Path("tripId") id: Uuid
    ): Map<String, Any?>

    @GET("map/{tripId}/geojson")
    suspend fun geoJson(
        @Path("tripId") id: Uuid
    ): Map<String, Any?>

    // ---------------------------------------------------------------------
    // Geocoding
    // ---------------------------------------------------------------------

    @GET("geocoding/search")
    suspend fun geocodingSearch(
        @Query("q") query: String
    ): List<Map<String, Any?>>

    @GET("geocoding/reverse")
    suspend fun reverseGeocoding(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Map<String, Any?>

    // ---------------------------------------------------------------------
    // Statistiken
    // ---------------------------------------------------------------------

    @GET("statistics")
    suspend fun statistics(): Map<String, Any?>

    @GET("statistics/vehicles")
    suspend fun vehicleStatistics(): List<Map<String, Any?>>

    @GET("statistics/tags")
    suspend fun tagStatistics(): List<Map<String, Any?>>

    @GET("statistics/months")
    suspend fun monthStatistics(): List<Map<String, Any?>>

    // ---------------------------------------------------------------------
    // Einstellungen
    // ---------------------------------------------------------------------

    @GET("health")
    suspend fun health(): HealthResponse

    // ---------------------------------------------------------------------
    // Export (Steuer-Report als PDF bzw. Fahrten als CSV)
    // ---------------------------------------------------------------------

    @Streaming
    @GET("export/pdf")
    suspend fun exportPdf(
        @Query("from") from: String?,
        @Query("to") to: String?,
        @Query("vehicleId") vehicleId: Uuid?,
        @Query("type") type: String?,
        @Query("specialCategory") specialCategory: String?
    ): Response<ResponseBody>

    @Streaming
    @GET("export/csv")
    suspend fun exportCsv(
        @Query("from") from: String?,
        @Query("to") to: String?,
        @Query("vehicleId") vehicleId: Uuid?,
        @Query("type") type: String?,
        @Query("specialCategory") specialCategory: String?
    ): Response<ResponseBody>

    @GET("users/me/settings")
    suspend fun mySettings(): MeSettingsResponse

    @PATCH("users/me/settings")
    suspend fun updateMySettings(
        @Body body: Map<String, String>
    ): UserSettingsDto

    /** Wie updateMySettings, aber als rohes JSON (z. B. für Boolean-Werte). */
    @PATCH("users/me/settings")
    suspend fun updateMySettingsJson(
        @Body body: okhttp3.RequestBody
    ): UserSettingsDto

    @GET("settings")
    suspend fun settings(): Map<String, Any?>

    @PUT("settings")
    suspend fun updateSettings(
        @Body body: Map<String, Any?>
    ): Map<String, Any?>
}