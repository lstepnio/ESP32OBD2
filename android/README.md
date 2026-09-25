# Android companion architecture

**Design only. There is no Android build or APK in this milestone.** The interactive [browser prototype](../design/prototype/index.html) reviews flows and visual language; production is native Kotlin and Jetpack Compose.

Use Compose Material 3 primitives with eGauge color/type/spacing tokens, ViewModel + StateFlow, structured coroutines, immutable UI state, Room, DataStore, and constructor injection. Choose and lock stable AGP/Kotlin/Compose versions when creating the first build; no floating dependencies. Proposed minSdk 29; target current stable SDK at implementation, checked against Play requirements if distributing there. [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) support separation between UI and repositories.

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
