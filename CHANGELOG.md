# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.3] - 2026-10-16

Version code 31.

### Added

- Each trip now gets its own ID on the device when it starts, which the app sends as `clientTripId` to the server (`/tracking/start`). A repeated start of the same trip returns the existing server trip instead of failing. If a trip is still recording on the server but was already ended on this device, the next sync finishes it (points and stop); if the device discarded it, the server copy is deleted (moved to the archive). Recordings from the web dashboard or other devices are never touched. Requires the matching server version; older servers keep working with the vehicle/start-time fallback.

### Changed

- When the server reports a different trip that is still recording, the sync log now names its vehicle and start time, so the conflicting trip can be found in the web dashboard.

### Fixed

- Sync error texts for a vehicle missing on the server and for a generic sync failure were hard-coded in German; they now follow the app language.

## [0.5.2] - 2026-10-16

Version code 30.

### Added

- The login screen asks the server which sign-in methods it offers (`GET /auth/methods`): the TOTP field and the passkey button are hidden when the server disables them (`TOTP_ENABLED`/`PASSKEYS_ENABLED`); older servers without the endpoint keep showing all login methods. The Passkeys entry in the settings only appears if passkeys are actually supported: the server offers them (on older servers: the passkey list endpoint exists) and the device can use them (Android 14+, or Google Play services on older versions); otherwise it is removed. Before the check has finished the entry stays hidden.
- Passkeys in the settings (server mode): lists all passkeys of the account – created in the web dashboard or in the app – with creation date, last use and sync status; create a new passkey on the device via the Android Credential Manager, rename and delete. Requires a current server: it publishes the official app in `/.well-known/assetlinks.json` and accepts its origin, so that passkeys work in the browser and the app alike (self-built APKs via `ANDROID_APP_CERT_SHA256`). The file must be publicly reachable, otherwise password managers such as Bitwarden report that the app does not support passkeys.
- Backup (Settings › Backup): optional backup to the Google account via Android backup (off by default, can be switched on by the user) and a ZIP backup that can be saved and restored at any time. Both contain vehicles, trips (incl. GPS points, offline trips and tags) and settings, including server address and user name; login data is never included. After restoring on a new device you sign in again – the login form is pre-filled.
- Sign in with Google or Microsoft: after entering the server address manually, the login screen asks the server which external sign-ins are set up (`GET /auth/oauth/providers`) and shows the matching buttons. The provider page opens in a Custom Tab; the app uses PKCE (`appCodeChallenge`/`appCodeVerifier`) and the redirect `fahrtenbuch://oauth/callback`, then exchanges the ticket via `POST /auth/oauth/exchange`. Accounts with two-factor authentication are asked for the TOTP code. The Google/Microsoft account must be linked to the Fahrtenbuch account in the web dashboard first.
- Profile picture from Gravatar in the settings (server mode), if one exists for the account's e-mail address; otherwise the initials are shown as before. Only the SHA-256 hash of the address is sent to Gravatar. Can be switched off in the profile (synced with the server setting `gravatarEnabled`); if the server disables Gravatar (`GRAVATAR_ENABLED=false`), the app does not load it at all.

### Fixed

- Trips could get stuck in sync with `HTTP 409: Es wird bereits eine Fahrt aufgezeichnet`: if a sync was interrupted after the server had created the trip but before the app stored its ID, every retry tried to start it again. The app now takes over the running server trip when vehicle and start time match (newer servers answer such a repeated start with the existing trip directly). A different trip still recording on the server is reported with a clear message. In addition, a manual sync no longer cancels a running one, and ending a trip no longer overwrites a server ID stored at the same moment.
- Android backup previously included everything, also access/refresh tokens and the device ID. Restoring such a backup on another device could reuse an already rotated refresh token and two devices shared one device ID. Tokens, device ID and the running recording now live in a separate preferences file that is never backed up (existing installations are migrated automatically).
- Saving the profile (and other requests with free-form JSON bodies) failed with "Parameter type must not include a type variable or wildcard": Retrofit rejected the `Map<String, Any?>` parameters Kotlin compiles to wildcard types.

## [0.5.1] - 2026-10-14

Version code 27.

### Added

- Settings › Permissions also checks battery optimization: the app shows whether Android may put it to sleep (battery optimization or "Restricted" background usage) and opens the app's system settings to set battery usage to "Unrestricted". On Samsung devices a hint explains how to remove the app from "Sleeping apps"; other devices get a general hint about vendor power-saving settings.

### Fixed

- Connecting via QR code stayed on the confirmation screen although pairing succeeded: the app mode was saved but not switched in the UI state, so the setup only finished after restarting the app.

## [0.5.0] - 2026-10-14

Version code 26.

### Added

- Tablet layouts (from 600 dp width): navigation rail on the left instead of the bottom bar. In landscape (from 840 dp) the trip list and the trip details are shown side by side, the home screen, statistics and settings use two columns, vehicles are shown as a grid, and setup and login get a side panel with the app name. Trip details use two columns whenever there is enough width.

### Changed

- README updated for 0.5.0 (features, server requirements, architecture).
- Smartphones are locked to portrait orientation; tablets and unfolded foldables (smallest width ≥ 600 dp) can still rotate.

### Fixed

- Setup and login screens were unreadable in dark mode (light window background behind light text). All screens now draw the theme background; the window background is dark in dark mode, and the status/navigation bar icons follow the theme chosen in the app.

## [0.4.3] - 2026-10-14

Version code 25.

Some features need a current Fahrtenbuch server (`cheinisch/fahrtenbuch` from 2026-10-14): editing purpose, contact and note, and per-trip time zones. With older servers the app keeps working; those values are then simply not stored on the server.

### Added

- Redesigned statistics: filters for year, month, category and vehicle; total distance with trip count, driving time and average per trip; distance by category with bars; distance per month (tap a bar to filter) or per day.
- Statistics: export button. Choose a period (prefilled from the statistics filter) and PDF (default) or CSV; vehicle and category filters are applied. In server mode the server creates the tax report (`GET /export/pdf`, `GET /export/csv`); in local mode – or if the server is not reachable – the app creates the PDF/CSV on the device. The file is saved via the Android file picker and can be shared directly.
- Special categories from the server API (`specialCategory`: medical, volunteering, education/studies, job application, moving, other) as a second classification level next to the trip type:
  - selection in the trip details (chips, "None" removes it), saved via `PUT /trips/{id}/classify` in server mode and on the device in local mode;
  - chip in the trip list and on the latest trip card;
  - statistics card "By special category" and special categories in the statistics category filter.
- Trip details: purpose, contact and note can be edited (tap the purpose card). Saved via `PUT /trips/{id}/classify` in server mode and on the device in local mode; values entered offline are sent before the trip is completed on the server.
- Trip details: tags (e.g. "Vacation") can be assigned; new tags can be created directly in the dialog. Server mode uses `GET/POST /tags` and `PUT /trips/{id}/tags`; local mode stores tags on the device, and offline tags are matched by name (missing ones created) during sync.
- Per-trip time zone: the device records its time zone at the start and end of every recording (also for Bluetooth auto-start) and sends it to the server (`timeZone` on `/tracking/start` and `/tracking/stop`). Trip times are shown in the local time of the trip, using `startTimeZone`/`endTimeZone` from the server (fallback: account time zone) or the recorded device zone in local mode. IANA zone ids make daylight saving time work per timestamp. The local CSV export includes the time zone.
- Time zone setting under *Units & date format*: trip times and days are shown in this zone regardless of the device's time zone. In server mode it is the account time zone (`timezone`, default Europe/Berlin), saved via `PATCH /users/me/profile`; in local mode a zone can be chosen or the device zone used.

### Changed

- Local database schema version 9 (`specialCategory`, `startTimeZone` and `endTimeZone` for synced and offline trips; `purpose`, `contact`, `notes` and tags for offline trips; tag colors). Existing data is migrated automatically.

### Fixed

- Trips with tags from the server could not be loaded: the API returns tags as objects (`{id, name, color}`), the app expected plain strings.
- Trip times were shown in the device time zone (e.g. UTC on an emulator) instead of local time; grouping by day, "today" and exports now use the selected time zone too.
- More robust parsing of timestamps with offset (`+02:00`) or without zone.

## [0.4.2] - 2026-10-13

Version code 24.

### Added

- Default vehicle: loaded from the server (`isDefault`), preselected for new trips and listed first on the home screen.
- Vehicles screen shows a "Default" badge and lets you set another vehicle as default (`PUT /vehicles/{id}/default` in server mode, stored on the device in local mode; the first local vehicle becomes the default automatically).
- `README.md`, `AGENTS.md` and `CLAUDE.md` with project overview, build instructions and contributor/agent conventions.
- `CHANGELOG.md` following Keep a Changelog.

### Changed

- Local database schema version 5 (new `isDefault` column for vehicles, with migration).

## [0.4.1] - 2026-10-13

Version code 23.

### Added

- "About the app" dialog in the settings: app version, build, Android version and device, server version and status (`GET /health`), "Copy diagnostics" for bug reports.
- Links to the privacy policy, the source code and the issue tracker.
- License notice for the app (GNU AGPL v3) with link to the full text.
- Full list of open-source libraries and their licenses via AboutLibraries, generated automatically on every build.
- Map data attribution (OpenStreetMap contributors, OpenMapTiles, OpenFreeMap).
- `LICENSE` file (GNU Affero General Public License v3).

### Fixed

- Statistics cards showed English labels on German devices because the German translations were missing.

## [0.4.0] - 2026-10-13

Version codes 21 and 22.

### Added

- New app logo.
- Trip details: fullscreen map.
- Home screen: vehicle selection directly above the start button (one vehicle shown only, 2–4 as tiles, 5+ as swipeable row), locked while recording.
- Home screen: recording status with start time, vehicle and elapsed time; tiles for today and the current month; banner for uncategorized trips that opens the filtered trip list; latest trip card with mini route.
- Trip list: period filter with date range picker, vehicle and tag filters, category filter chips, summary of count and distance, trips grouped by day with daily totals.
- Bottom navigation: pulsing red dot on the Start tab while a trip is being recorded.
- Settings: profile card, separate connection section (server and connection security), sign-out with confirmation, compact version row.
- Settings: "Permissions" section with a dialog to check and re-request permissions or open the system settings.
- Setup assistant: permission step for location, background location ("Allow all the time"), Bluetooth and notifications.
- Display preferences in the user profile: kilometers/miles, km/h/mph, date format (DD.MM.YYYY / MM/DD/YYYY) and 12/24-hour time; loaded from and saved to the server account (`/users/me/settings`) in server mode, stored on the device in local mode.
- Local mode: start and destination addresses resolved on the device (Android Geocoder), distance computed from the GPS track, duration from start/end time.

### Changed

- Redesigned trip details: borderless map with title overlay, single metrics card (distance, duration, average speed), start/destination timeline with shortened addresses, 2×2 category selector including "Uncategorized", purpose/contact/note section.
- Redesigned home screen, trip list, settings screen and bottom navigation (all tabs labelled, clearer active indicator); the first tab is now called "Start".
- Trip map switched from osmdroid to MapLibre Native with OpenFreeMap vector tiles (no API key), including a real dark map style.
- Settings dialogs (profile, server settings, sign-out, permissions, units & date format) moved into their own files; server address and certificate check are no longer part of the profile dialog.
- Dates and times follow the device language and the new display preferences instead of fixed German formats.
- Onboarding and login screens respect system bars and the keyboard and scroll on small displays.
- All user-facing texts moved to string resources with complete English and German translations (trips, vehicles, login, onboarding, notifications, error messages).
- Starting a recording refreshes the trip list so the running trip's start time is available.

### Fixed

- Map tiles did not load because osmdroid was not configured with a user agent.
- Build failures when `dl.google.com` was slow: the Google repository now only serves Google/AndroidX artifacts and Gradle network timeouts were increased.
- "Continue" button on the location notice slid under the navigation bar.

## [0.3.6] - 2026-10-12

Version code 20. State before the changes listed above.

[Unreleased]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/v0.5.3...HEAD
[0.5.3]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/v0.5.2...v0.5.3
[0.5.2]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/v0.4.3...v0.5.0
[0.4.3]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/721d319...937f08b
[0.4.1]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/0179389...721d319
[0.4.0]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/4fed1e4...0179389
[0.3.6]: https://github.com/cheinisch/Fahrtenbuch-Android/commit/4fed1e4
