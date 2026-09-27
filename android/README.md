# Android companion architecture

**Current execution record, 2026-09-26:** [Android core and UX review](../docs/development/android-core-ux-review-plan.md) and [validation](../docs/development/android-core-ux-validation.md) cover the implemented Gauge, Readings, Vehicle, and Settings navigation, progressive disclosure, single-adapter default, optional second-adapter preference, truthful operation state, and signed GitHub development update discovery.

**Implementation status:** the native Kotlin/Jetpack Compose app builds, tests, and lints. The routine path focuses on selecting readings, reviewing the exact supported numeric configuration, sending it, and confirming the running revision. Technical PID labs, protocol identity, and the optional second adapter are contextual details. No adapter, live telemetry, code clearing, or general renderer transfer is implemented. The [browser prototype](../design/prototype/index.html) remains a separate design review artifact.

## Build and run

Install JDK 17 and Android SDK platform 36. From `android/` run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir` for your machine. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Open `android/` in Android Studio, or install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. No store release is configured.

Gradle 8.13, AGP 8.13.2, Kotlin/Compose compiler 2.3.21 and Compose BOM 2026.05.00 are pinned. The Gradle wrapper checksum and dependency verification metadata are checked in. AGP 8.13.2 supports Kotlin 2.3 and API 36.1; this app compiles and targets API 36 using the installed SDK. [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## Current behavior

The app opens in offline preview and labels synthetic values. Each local vehicle profile keeps its draft and advanced topology preference. Version 1 profiles migrate to version 2; a TCM source enables the optional second adapter without rebinding data. "Find nearby gauge" requests platform BLE permissions, scans for a bounded window, and asks the user to choose if more than one compatible advertiser is present. The choice is remembered, while OS bonding and firmware owner authorization remain separate. A protected success changes owner state to authenticated. Android 10/11 request location for BLE scanning; Android 12+ request Nearby Devices.

On 2026-09-25, a Pixel 10 Pro discovered the powered gauge and read protocol 0 capabilities. Android 12+ scanning needs `neverForLocation` on `BLUETOOTH_SCAN` because the app does not derive location from scans; without it this Pixel delivered zero scan results despite granting Nearby Devices. Android negotiated MTU 256 before reading the capability characteristic. Without MTU negotiation, the long JSON characteristic was corrupted during the Android read. These findings apply to the tested phone and firmware build; broader device behavior remains to be checked.

The PID explorer searches sample standard requests by name, source, category and hex request. Decoder lab accepts a pasted Mode 01 response for the selected example and rejects malformed, mismatched, overlong, or vehicle-specific input. A separate offline custom-request lab accepts only bounded Mode 01, 09, or 22 read syntax and shows a response prefix for a selected ECM or TCM source. It cannot prove support or decode manufacturer data, and sends no OBD request. The Device screen offers a protected read-only diagnostic snapshot and a signed development update path, but no code clear control. The firmware branch with this capability characteristic is [M1 transport PR](https://github.com/lstepnio/ESP32OBD2/pull/1); the app can run without it in demo mode.

## Experimental numeric configuration

On a paired owner phone, open Gauge and use **Send reviewed setup**. The app derives a version 1 document from the bundled template: RPM, coolant, and speed on numeric pages, with the selected supported reading first and the local coolant thresholds. One typed projection creates both the review and payload, and blocks unsupported renderer or TCM transfer. It reads the active revision, sends bounded chunks, verifies commit, then reconnects until the expected running revision and hash are healthy with the trial flag clear. Previous-generation recovery is shown separately. Two earlier Pixel transfers and separate durable readbacks succeeded; see [hardware evidence](../docs/development/numeric-config-transfer-validation.md).

The Device screen also offers **Verify saved configuration document**. This owner-only read retrieves the complete committed document in bounded chunks, checks revision and digest consistency across chunks, hashes the reconstructed bytes, and displays its profile ID and counts. This is a readback of gauge storage, separate from the phone draft and from live vehicle evidence. The paired Pixel read and verified revision 2 after the gauge rebooted; see [readback evidence](../docs/development/active-document-readback-validation.md).

Design offers **Refresh saved configuration** and compares that verified readback with the currently selected phone profile. It shows the profile ID, primary PID, renderer, source, and each coolant alert limit or dwell setting side by side, with counts for matches, differences, and unavailable fields. Draft edits update the comparison locally. Unknown or unsupported document fields remain unavailable rather than being treated as matches. The saved side is the last verified snapshot, so refresh before relying on it after changes elsewhere. A comparison does not write to the gauge or prove that the vehicle responds to any PID. The experimental sender always writes numeric pages; other renderer choices stay in the phone preview. The paired Pixel showed the expected change in match count for a local renderer edit and an unchanged saved revision after refresh; see [comparison evidence](../docs/development/draft-comparison-validation.md).

When all mapped fields and the vehicle profile ID are compatible, **Use saved settings in phone draft** copies the gauge snapshot into the active local profile without writing the gauge. An experimental send is disabled until the app has refreshed an authenticated revision and SHA-256. Immediately before BEGIN, Android reads protected status again and requires both values to match; a mismatch stops the transfer and asks for review instead of silently rebasing the draft. The paired Pixel exercised the import and disabled-before-refresh states; see [reconciliation evidence](../docs/development/config-reconciliation-validation.md). A deliberately stale concurrent-writer conflict still needs a controlled hardware exercise.

## Signed development updates

Settings can check GitHub Releases for a signed, fresh catalog and select an exact board, revision, partition layout, channel, and transfer protocol match. Download size and SHA-256 are checked before the existing P-256 bundle validation. Catalog generations are monotonic, and the app pins the content hash for an accepted generation so an immutable generation cannot later contain different signed content. Local `.egauge-dev-update` import remains an advanced path. Upload is foreground-only, and an interruption journal requires running-firmware reconciliation after process death. A [live update](../docs/development/update-live-validation.md), [changed-build retry](../docs/development/update-recovery-validation.md), gauge-reboot retry, aborted-trial rollback, and the [published GitHub journey](../docs/development/github-hosted-update-validation.md) were observed on the Pixel. Power-loss recovery, sustained background delivery, and production signing remain unverified, so public OTA stays false. See the [release runbook](../docs/development/firmware-release-runbook.md).

For a physical debug session, run `python3 tools/adb_debug_awake.py start` from the repository root before using the phone, then `python3 tools/adb_debug_awake.py stop` when finished. The script saves the phone's current screen timeout and charging wake setting, sets a 30-minute timeout and stay-awake-while-charging, and restores the originals on stop. `status` displays the current values. The debug APK also holds its own window awake whenever eGauge is visible. Neither measure overrides a manual lock or keeps a background app alive. Release builds hold the screen awake only during an active update.

## Implementation boundary

The future Apply gate requires an active vehicle session and an observed responding PID tied to the active local profile, adapter, and ECU. The current app has no vehicle session, so example catalog entries never satisfy this gate. This is a UI preflight, not a substitute for firmware validation or authenticated commit.

The current source stays in `:app` while boundaries settle. Pure codecs, configuration projection, profile migration, runtime confirmation, hosted catalog parsing, operation coordination, and recovery journals have independent classes and unit fixtures. SharedPreferences holds small non-secret profiles and journals; Room remains appropriate when a queryable PID/evidence catalog exists. Android keeps no passkey or bond secret. See [Android runtime architecture](../docs/architecture/android-runtime.md).

Use Compose Material 3 primitives with eGauge color/type/spacing tokens, ViewModel + StateFlow, structured coroutines, immutable UI state, Room, DataStore, and constructor injection as the authenticated app grows. Current local state uses Compose snapshot state in a ViewModel; repositories and persistent profile storage are next. The initial minSdk is 29 and targetSdk is 36; recheck Play requirements before distribution. [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) support separation between UI and repositories.

## Package boundaries

Start with `:app`, `:core:model`, `:core:protocol`, `:core:bluetooth`, `:core:data`, `:core:designsystem`. Keep feature packages within app until build size/team ownership justify modules: onboarding, garage, dashboard-editor, pid-explorer, pid-lab, device-settings, updates, diagnostics, alerts. Protocol/model code has no Android dependencies. Avoid a module for every screen.

`GaugeRepository` exposes device and operation state. `VehicleRepository` exposes profiles and discovery evidence. `DashboardRepository` owns drafts/applied revisions. `UpdateRepository` owns release trust, downloads and reconciliation. A single GATT session actor serializes Android GATT operations and maps callbacks to cancellable requests with timeouts. Stale callbacks carry session generation and cannot complete requests on a new connection.

Interfaces: `GaugeTransport.connect/observeCapabilities/sendCommand/subscribe/close`, `PidCatalog.query/import/validate`, `ConfigRepository.draft/validate/apply/reconcile`, `UpdateRepository.check/download/verify/transfer/reconcile`. Repositories expose typed errors, not vendor GATT integer status codes directly to UI.

## Navigation and screens

Four phone destinations: **Gauge**, **Readings**, **Vehicle**, **Settings**. Updates live in Settings, with a persistent operation banner during transfer. Wider layouts use a navigation rail. Drafts persist per vehicle.

| Screen | Primary action | Key states |
| --- | --- | --- |
| Garage | Connect / choose vehicle | Permission needed, radio off, scanning, gauge offline, associated |
| Association | Confirm physical code | Expired, mismatch, another owner, bonded |
| Adapter picker | Select adapter | No result, unknown profile, compatible candidate, handshake failed |
| Dashboard editor | Apply changes | Local draft, device mismatch, validation issue, conflict, saving, applied |
| PID explorer | Discover / add reading | Idle, partial scan, supported, responding, no response, not supported |
| Diagnostics | Read / clear codes | Categories, ECU scope, consent, unknown outcome, verified result |
| Alerts | Configure local rules | Draft, invalid range, active, acknowledged, source stale |
| PID detail/lab | Validate definition | Example decode, invalid request, unexpected response, range issue |
| Device | Settings / update | Current version, unsupported feature, maintenance busy, recovery |
| Update detail | Start update | Notes, preflight, transfer, interrupted, verifying, rebooting, healthy, rolled back |

## Android lifecycle

Request Nearby Devices/scan and connect permissions in context on Android 12+. Legacy Android 10/11 scanning needs the appropriate location permission and location-service handling; no location permission for unrelated features. Evaluate CompanionDeviceManager for system association, but association is not a GATT connection or application ownership proof. [Permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) and [association](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing) are separate flows.

A user-started firmware transfer runs in a `connectedDevice` foreground service with the platform-required permissions and ongoing notification. Handle notification denial without misreporting transfer state. Use WorkManager for retryable release download/check jobs, not to pretend a continuous BLE session survives process death. After process restart, reconcile operation ID, device offset and hash; never repeat a commit blindly. Background behavior follows [Android BLE lifecycle guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background).

## Local data and privacy

Configuration and catalog browsing work offline. Store owner keys in Android Keystore-backed storage and exclude secrets from backup/export. Room migration tests retain profiles. Export a versioned JSON bundle through the system document picker; validate schema, semantic bounds, and imported request policies before enabling it. No account or analytics required. Diagnostic export is opt-in with VIN, peer identifiers and raw session details individually selectable.

## Accessibility and UI acceptance

48 dp minimum interactive targets; body text at least 16 sp; 200% font scale without clipping critical actions; TalkBack names/states/order; no status conveyed by color alone. Semantics describe value, unit, freshness and threshold status. Disable decorative motion when system animations are disabled. Material dynamic color may tint app chrome, but does not remap fixed warning colors. Test phone rotation, process recreation, tablets/foldables, dark/light mode and RTL before general release.

## Separate ECM and TCM adapters

The design includes source-specific adapter selection, connection state, discovery, PID binding and alerts for swapped vehicles. [Dual-adapter feasibility and fallback TODOs](../docs/architecture/multi-adapter.md). Simultaneous operation remains unverified.

## Hardware-aware transport extension

BLE provides association/control and a universal update path; Wi-Fi is designed as a negotiated faster bulk transport sharing the same operation state, trust checks and recovery semantics. Two-adapter radio coexistence, board sensors, PSRAM, USB and power management are covered in the [hardware and transport strategy](../docs/architecture/hardware-and-transports.md). Preferred production update transport remains subject to WIFI-001/RADIO-001 measurements.
