# Android companion architecture

**Implementation status, 2026-09-25:** a native Kotlin/Jetpack Compose debug app builds. It includes Garage, Design, PIDs, and Device screens, versioned local vehicle profiles with separate drafts, five simulated round renderers, searchable example PID catalog, Mode 01 decoder lab, coolant threshold preview, a public BLE capability reader, bounded authenticated built-in reading selection, and an experimental owner-only numeric ECM configuration sender. No adapter, live telemetry, firmware update, code clearing, or general configuration transfer is implemented. The [browser prototype](../design/prototype/index.html) remains a separate design review artifact.

## Build and run

Install JDK 17 and Android SDK platform 36. From `android/` run `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir` for your machine. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Open `android/` in Android Studio, or install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. No signing or store release is configured.

Gradle 8.13, AGP 8.13.2, Kotlin/Compose compiler 2.3.21 and Compose BOM 2026.05.00 are pinned. The Gradle wrapper checksum and dependency verification metadata are checked in. AGP 8.13.2 supports Kotlin 2.3 and API 36.1; this app compiles and targets API 36 using the installed SDK. [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## Current behavior

The app opens in demo mode. Every synthetic value is labeled. Each local vehicle profile keeps its own layout, example PID, source, and coolant warning/critical, hysteresis, and dwell values. These alert settings remain local unless the user explicitly sends the experimental numeric ECM profile. The version 1 profile store migrates the earlier single draft and stops editing if stored data has an unreadable or future format. New profile IDs conform to the device configuration ID pattern; older local IDs are preserved until an explicit migration is designed. Profile names are user labels, not vehicle capability evidence. Full Apply remains disabled and now lists concrete blockers: draft validity, gauge write capability, source-specific vehicle evidence, and the unfinished general transfer. "Find nearby gauge" requests the platform BLE permissions, filters for the eGauge service UUID, connects, reads capabilities, and closes the discovery link. The app retains that discovered device for the current app session, so the control action does not start a new scan that might select another gauge. A later device picker must make gauge identity explicit when multiple gauges are nearby. When `quickSelect` is advertised, Device offers **Set preview reading on gauge**. Android connects GATT and reads protected state first, then starts OS bonding if authentication is required. A two-byte authenticated write selects one of five built-in readings only after that read succeeds. The app reports success only after an authenticated state readback. This path passed on the tested Pixel and gauge; see the hardware review. Android 10/11 request location for BLE scanning; Android 12+ request Nearby Devices. A missing gauge, disabled Bluetooth, denied permission, timeout, or unsupported protocol remains an explicit state.

On 2026-09-25, a Pixel 10 Pro discovered the powered gauge and read protocol 0 capabilities. Android 12+ scanning needs `neverForLocation` on `BLUETOOTH_SCAN` because the app does not derive location from scans; without it this Pixel delivered zero scan results despite granting Nearby Devices. Android negotiated MTU 256 before reading the capability characteristic. Without MTU negotiation, the long JSON characteristic was corrupted during the Android read. These findings apply to the tested phone and firmware build; broader device behavior remains to be checked.

The PID explorer searches sample standard requests by name, source, category and hex request. Decoder lab accepts a pasted Mode 01 response for the selected example and rejects malformed, mismatched, overlong, or vehicle-specific input. A separate offline custom-request lab accepts only bounded Mode 01, 09, or 22 read syntax and shows a response prefix for a selected ECM or TCM source. It cannot prove support or decode manufacturer data, and sends no OBD request. CEL and update panels show their intended location and unavailable status, without pretend data or active clear/update controls. The firmware branch with this capability characteristic is [M1 transport PR](https://github.com/lstepnio/ESP32OBD2/pull/1); the app can run without it in demo mode.

## Experimental numeric configuration

On a paired owner phone, read gauge capabilities, open Design, and use **Send experimental numeric profile**. The app derives a version 1 document from the bundled template: RPM, coolant, and speed on numeric pages, with the selected supported reading first and the local coolant thresholds. It reads the active revision, sends the SHA-256 digest and bounded chunks, requests verification and commit, then reconnects after reboot to compare active revision and hash. The full **Apply to gauge** remains disabled because nonnumeric layouts, TCM routing, broad PID discovery, and vehicle evidence are still missing. A transfer has not yet been observed on the Pixel after this implementation; build and installation alone are not validation.

## Implementation boundary

The future Apply gate requires an active vehicle session and an observed responding PID tied to the active local profile, adapter, and ECU. The current app has no vehicle session, so example catalog entries never satisfy this gate. This is a UI preflight, not a substitute for firmware validation or authenticated commit.

The current source packages in `:app` are model/state, versioned local profile storage, BLE capability/control client, PID decoder, and Compose UI. This avoids premature Gradle module overhead while their contracts settle. SharedPreferences holds only small non-secret drafts; migrate to DataStore/Room with further schema migration before large catalogs and sourced discovery evidence. Android keeps no passkey or bond secret in app storage; the OS owns BLE bonding. The public BLE read closes immediately after the capability response. The quick-select path closes after readback. General configuration transactions, interrupted-transfer recovery, durable revision conflict handling, and OTA remain required. A Pixel 10 Pro completed the passkey and protected quick-selection flow on the flashed gauge, including a later reconnect without a new pairing window.

Use Compose Material 3 primitives with eGauge color/type/spacing tokens, ViewModel + StateFlow, structured coroutines, immutable UI state, Room, DataStore, and constructor injection as the authenticated app grows. Current local state uses Compose snapshot state in a ViewModel; repositories and persistent profile storage are next. The initial minSdk is 29 and targetSdk is 36; recheck Play requirements before distribution. [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) support separation between UI and repositories.

## Package boundaries

Start with `:app`, `:core:model`, `:core:protocol`, `:core:bluetooth`, `:core:data`, `:core:designsystem`. Keep feature packages within app until build size/team ownership justify modules: onboarding, garage, dashboard-editor, pid-explorer, pid-lab, device-settings, updates, diagnostics, alerts. Protocol/model code has no Android dependencies. Avoid a module for every screen.

`GaugeRepository` exposes device and operation state. `VehicleRepository` exposes profiles and discovery evidence. `DashboardRepository` owns drafts/applied revisions. `UpdateRepository` owns release trust, downloads and reconciliation. A single GATT session actor serializes Android GATT operations and maps callbacks to cancellable requests with timeouts. Stale callbacks carry session generation and cannot complete requests on a new connection.

Interfaces: `GaugeTransport.connect/observeCapabilities/sendCommand/subscribe/close`, `PidCatalog.query/import/validate`, `ConfigRepository.draft/validate/apply/reconcile`, `UpdateRepository.check/download/verify/transfer/reconcile`. Repositories expose typed errors, not vendor GATT integer status codes directly to UI.

## Navigation and screens

Four phone destinations: **Garage**, **Design**, **PIDs**, **Device**. Updates live in Device, with a persistent operation banner during transfer. Wider layouts use a navigation rail and a detail/preview pane. Back navigation retains drafts; abandoning dirty changes needs a clear discard action.

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
