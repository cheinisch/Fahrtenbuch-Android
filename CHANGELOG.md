# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Per-trip time zone: the device records its time zone at the start and end of every recording (also for Bluetooth auto-start) and sends it to the server (`timeZone` on `/tracking/start` and `/tracking/stop`). Trip times are shown in the local time of the trip, using `startTimeZone`/`endTimeZone` from the server (fallback: account time zone) or the recorded device zone in local mode. IANA zone ids make daylight saving time work per timestamp. Local CSV export includes the time zone.

- Time zone setting under *Units & date format*: trip times and days are shown in this zone regardless of the device's time zone. In server mode it is the account time zone (`timezone`, default Europe/Berlin), saved via `PATCH /users/me/profile`; in local mode a zone can be chosen or the device zone used.

- Special categories from the server API (`specialCategory`: medical, volunteering, education/studies, job application, moving, other) as a second classification level next to the trip type.
- Trip details: special category selection (chips, "None" removes it); saved via `PUT /trips/{id}/classify` in server mode and on the device in local mode.
- Special category chip in the trip list and on the latest trip card; statistics card "By special category".
- Statistics: category filter also offers special categories.
- Statistics: export button. Choose a period (prefilled from the statistics filter) and PDF (default) or CSV; vehicle and category filters are applied. In server mode the server creates the tax report (`GET /export/pdf`, `GET /export/csv`); in local mode – or if the server is not reachable – the app creates the PDF/CSV on the device. The file is saved via the Android file picker and can be shared directly.
- Trip details: purpose, contact and note can be edited (tap the purpose card). Saved via `PUT /trips/{id}/classify` in server mode (requires the matching backend, which now applies `purpose`, `contact` and `notes` there) and on the device in local mode; values entered offline are sent before the trip is completed on the server.

### Changed

- Local database schema version 8 (`specialCategory`, `startTimeZone` and `endTimeZone` for synced and offline trips; `purpose`, `contact` and `notes` for offline trips, with migrations).
- Redesigned statistics: filters for year, month, category and vehicle; total distance with trip count, driving time and average per trip; distance by category with bars; distance per month (tap a bar to filter) or per day.

### Fixed

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

[Unreleased]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/937f08b...HEAD
[0.4.2]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/721d319...937f08b
[0.4.1]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/0179389...721d319
[0.4.0]: https://github.com/cheinisch/Fahrtenbuch-Android/compare/4fed1e4...0179389
[0.3.6]: https://github.com/cheinisch/Fahrtenbuch-Android/commit/4fed1e4
