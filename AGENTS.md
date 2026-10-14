# AGENTS.md

Guidance for AI coding agents (and humans) working on this repository. See `README.md` for the product overview.

## Project

Android client for the Fahrtenbuch mileage logbook. Kotlin, Jetpack Compose, Material 3, single activity. Backend: [cheinisch/fahrtenbuch](https://github.com/cheinisch/fahrtenbuch) – its OpenAPI spec (`api/openapi.yml`) is the reference for all endpoints.

- Package / applicationId: `app.fahrtenbuch.mobile`
- minSdk 29, targetSdk 36, compileSdk 37, JDK 17, AGP 9, Gradle wrapper 9.x
- Dependencies are declared directly in `app/build.gradle.kts` (the version catalog is not used for them)

## Commands

```bash
./gradlew assembleDebug           # build
./gradlew installDebug            # install on device/emulator
./gradlew testDebugUnitTest       # unit tests
./gradlew lintDebug               # Android lint
```

There is no CI yet; build locally before pushing larger changes. If you cannot build (e.g. no Android SDK in your environment), say so explicitly in your summary.

## Structure

- `data/api` – `FahrtenbuchApi` (Retrofit), `ApiFactory` (auth, token refresh, optional self-signed TLS), `SessionStore` (SharedPreferences: mode, server URL, tokens, display preferences)
- `data/local` – Room `OfflineDatabase`. **Schema changes need a new version and a `Migration`** (`exportSchema = false`, migrations are written by hand)
- `data/repository/FahrtenbuchRepository` – the only place the UI gets data from; handles local vs. server mode
- `location` – `TripTrackingService` (foreground service), `BluetoothVehicleReceiver` (auto-start), `ReverseGeocoder`
- `sync` – `OfflineSyncWorker`, `SyncScheduler` (WorkManager)
- `ui/AppViewModel` – single ViewModel exposing `StateFlow<AppUiState>`
- `ui/common` – shared formatting (`TripFormat.kt`), category colors/chips, `LocalUserPreferences`
- `ui/screens` – one file per screen; **every dialog/modal gets its own file** (`screens/settings/`, `screens/trips/`, `screens/home/`)
- `ui/permissions` – `PermissionChecklist`, shared by onboarding and settings
- `ui/map/TripRouteMap` – MapLibre map (OpenFreeMap styles, no API key)

## Conventions

- **Strings:** never hardcode user-facing text. Add every key to both `res/values/strings.xml` (English) and `res/values-de/strings.xml` (German). Escape `&` as `&amp;` and apostrophes as `\'`. Use `plurals` for counts.
  - In composables use `stringResource`; in the ViewModel use `str(R.string.…)`; in services/receivers use `context.getString`.
- **Formatting:** distances, speeds, dates and times go through the helpers in `ui/common/TripFormat.kt` with `LocalUserPreferences.current` (km/mi, km/h/mph, EU/US date, 12h/24h). Stored values are always km, km/h and ISO-8601 UTC – never convert stored data. Never use `ZoneId.systemDefault()` to display trip times: use `prefs.forTrip(trip)` (trip's `startTimeZone`/`endTimeZone`, else account/selected zone) with the format helpers, `tripLocalDate(trip, prefs)` and `today(prefs)`. Only when *recording* a trip is the device zone captured (`OfflineTripEntity.startTimeZone`/`endTimeZone`).
- **Tablets:** use `LocalWindowLayout.current` (`ui/common/WindowLayout.kt`): `useNavigationRail`/`wide` from 600 dp, `twoPane` from 840 dp. Phones keep the single-column layout; new screens should not assume a phone width (no fixed widths, use `widthIn(max = …)` for readable columns).
- **Edge-to-edge:** the app draws behind system bars. Screens outside the main `Scaffold` (onboarding, login, full-screen dialogs) must apply `safeDrawingPadding()` and be scrollable so they fit small screens.
- **Theme:** use `MaterialTheme.colorScheme` colors so light and dark mode both work; category colors come from `categoryColors()`.
- **Modes:** keep local mode working without any server. Server-only features must check `appMode` / `store.isServerMode`.
- **Permissions:** background location may only be requested after foreground location; all permissions must stay optional.
- Comments in the code base are mostly German; either language is fine, keep them short and explain *why*.

## Server API notes

- Base URL is normalized to end with `/api/v1/` (`SessionStore.normalize`).
- Display preferences: `GET/PATCH /users/me/settings` – `distanceUnit` (`km`|`mi`), `speedUnit` (`kmh`|`mph`), `dateFormat` (`EU`|`US`), `timeFormat` (`24h`|`12h`).
- Trip category changes use `PUT /trips/{id}/classify` so that GPS data and metadata are not overwritten; avoid `PUT /trips/{id}` for partial updates.
- Server version/status: `GET /health`.
- Trip time zones: send the device zone as `timeZone` on `/tracking/start` and `/tracking/stop`; trips return `startTimeZone`, `endTimeZone`, `timeZoneSource`.

## Git

- Small, focused commits with a descriptive message (imperative, English).
- The default branch is `master`.
- Do not commit `local.properties`, keystores or other secrets.
