# Fahrtenbuch for Android

A mileage logbook app for Android. Fahrtenbuch records your trips with GPS, can start automatically when your phone connects to your car via Bluetooth, and lets you categorize each trip as business, private or commute. It works fully offline on the device or together with a self-hosted [Fahrtenbuch server](https://github.com/cheinisch/fahrtenbuch).

> **Status:** early development – current version **0.5.0**. An open test on Google Play is in preparation. See the [changelog](CHANGELOG.md) for what changed.

## Features

- **Trip recording** – GPS tracking in a foreground service, started manually or automatically when a paired Bluetooth device (your car) connects
- **Two modes**
  - **Local:** everything stays on the device; places and distance are resolved on the device
  - **Server:** sync with your own Fahrtenbuch server, offline-first with background sync
- **Easy server setup** – pair the app by scanning a QR code from the web app, or enter the server address manually; sign in with password, TOTP, passkey or – if the server has it set up – a linked Google or Microsoft account
- **Trip list** – grouped by day, filter by period, vehicle, tag and category, daily and filtered totals
- **Trip details** – vector map with the recorded route (MapLibre + OpenFreeMap), timeline with start and destination, distance, duration and average speed, fullscreen map
- **Categories** – business, private, commute, uncategorized; the home screen highlights trips that still need a category
- **Special categories** – optional second level such as medical, volunteering, education, job application or moving
- **Purpose, contact, note and tags** – editable per trip; new tags (e.g. "Vacation") can be created right in the trip details
- **Vehicles** – multiple vehicles with a default vehicle, each with an optional Bluetooth device for auto-start
- **Statistics** – filter by year, month, category and vehicle; total distance, driving time, distance per category and special category, per month or per day
- **Export** – PDF (default) or CSV for any period; in server mode the server's tax report is used, in local mode the app creates the file itself
- **Local times and time zones** – trip times are always shown in the local time of the trip (daylight saving time included), independent of the device's time zone
- **Personal preferences** – kilometers or miles, km/h or mph, date format (DD.MM.YYYY / MM/DD/YYYY), 12/24-hour time and time zone; synced with your server account in server mode
- **Phones and tablets** – phones use a portrait layout with a bottom bar; tablets and foldables get a navigation rail, list and details side by side and multi-column screens
- **Light and dark theme**, English and German UI
- **Permissions assistant** – guides through location, background location ("Allow all the time"), Bluetooth and notifications, and can re-check them later from the settings

## Requirements

| | |
|---|---|
| Android | 10 (API 29) or newer |
| Build | Android Studio with AGP 9, JDK 17, Gradle 9 (wrapper included) |
| Server (optional) | [cheinisch/fahrtenbuch](https://github.com/cheinisch/fahrtenbuch) – a current version is needed for editing purpose/contact/note and for per-trip time zones; older servers keep working without these |

## Getting started

```bash
git clone https://github.com/cheinisch/Fahrtenbuch-Android.git
cd Fahrtenbuch-Android
./gradlew assembleDebug        # build a debug APK
./gradlew installDebug         # install on a connected device / emulator
```

Or open the project in Android Studio and run the `app` configuration.

On first start the app shows a location notice, the permission assistant and then lets you choose between **local mode** and **connecting a server**.

### Connecting a server

- **QR code:** in the web app open "Connect mobile app" and scan the one-time code.
- **Manual:** enter the server's public address. `/api/v1` is appended automatically, so `https://fahrtenbuch.example.com` is enough.

For local development the debug default points to `http://10.0.2.2:3000/api/v1/`, which is the host machine as seen from the Android emulator. Self-signed HTTPS certificates can be allowed in the server settings (not recommended outside your own network).

## Permissions

| Permission | Why |
|---|---|
| Location (fine/coarse) | Record route, distance, start and destination |
| Background location | Keep recording with the screen off and allow Bluetooth auto-start |
| Foreground service (location) | Run the trip recording reliably |
| Bluetooth connect | Detect your car for auto-start (Android 12+) |
| Notifications | Show that a trip is being recorded (Android 13+) |
| Internet / network state | Sync with the server, load map tiles, resolve addresses |

All permissions are optional; without them trips can still be started manually. Location data is only used for trip recording. See the [privacy policy](https://fahrtenbuch.app/privacy.html).

## Architecture

Single-activity app written in Kotlin with Jetpack Compose and Material 3.

```
app/src/main/java/app/fahrtenbuch/mobile/
├── MainActivity.kt          # edge-to-edge, theme, MapLibre init
├── data/
│   ├── api/                 # Retrofit API, ApiFactory (auth/refresh), SessionStore (prefs)
│   ├── export/              # PDF/CSV export created on the device
│   ├── local/               # Room database (offline trips, track points, cache)
│   ├── model/               # API and UI models
│   └── repository/          # FahrtenbuchRepository – single source for UI data
├── location/                # TripTrackingService, Bluetooth auto-start, reverse geocoder
├── sync/                    # WorkManager offline sync
└── ui/
    ├── AppViewModel.kt      # app state (StateFlow) and actions
    ├── FahrtenbuchApp.kt    # navigation, bottom bar / navigation rail
    ├── common/              # formatting, category colors, tags, display preferences, window size
    ├── map/                 # MapLibre route map
    ├── navigation/          # bottom navigation and navigation rail
    ├── permissions/         # shared permission checklist
    ├── screens/             # screens; dialogs live in their own files (settings/, trips/, home/, statistics/)
    └── theme/
```

- **Offline-first:** Room is the primary source. Trips are recorded locally and uploaded by `OfflineSyncWorker` when a connection is available.
- **Maps:** MapLibre Native with OpenFreeMap vector styles (no API key). Change the style URLs in `ui/map/TripRouteMap.kt` to use your own tiles.
- **Places in local mode:** start/destination via the Android `Geocoder` (needs internet), distance computed from the GPS track.
- **Time zones:** timestamps are stored in UTC; each trip keeps the IANA time zone of the device at start and end, so times are shown in local time with correct daylight saving time.
- **Adaptive layout:** `ui/common/WindowLayout.kt` provides the window width class (phone < 600 dp, tablet portrait 600–839 dp, tablet landscape ≥ 840 dp). Phones are locked to portrait.

### Tech stack

Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Room · WorkManager · DataStore · Retrofit / OkHttp / Moshi · Play Services Location · MapLibre Native · ZXing · AndroidX Credentials (passkeys) · AboutLibraries

## Localization

Strings live in `app/src/main/res/values/strings.xml` (English, default) and the translations `values-de` (German), `values-fr` (French), `values-it` (Italian), `values-es` (Spanish), `values-pt` (Portuguese), `values-nl` (Dutch), `values-pl` (Polish), `values-sv` (Swedish), `values-nb` (Norwegian), `values-fi` (Finnish) and `values-da` (Danish). Every user-facing text must exist in all files – a missing key silently falls back to English.

## Contributing

Issues and pull requests are welcome. Please keep the existing structure (one file per dialog, strings in both languages) and describe how you tested your change.

## Support

Fahrtenbuch is free and open source. If it helps you and you'd like to give something back, you can support the project on Ko-fi – it goes toward hosting, domains and new features.

[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/cheinisch)

## License

Fahrtenbuch for Android is free software, licensed under the [GNU Affero General Public License v3.0](LICENSE).

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), tiles by [OpenFreeMap](https://openfreemap.org) / OpenMapTiles. Licenses of all third-party libraries are listed in the app under *Settings → About the app → Show all licenses*.
