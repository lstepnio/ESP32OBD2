# Android and iOS companion parity

Reviewed 2026-09-29. The executable foundation was added and tested at
`666726c57691e05ae73a081395aae6080bb8acbe` on `codex/obd-definition-catalog`.
The product review also inspected newer local Android branches without switching
the shared checkout: `codex/egauge-customize-workspace` at `bff4545` and
`codex/background-firmware-ready` at `cbc40fd`. These are locally inspected source
snapshots, not a claim about remote merge status or a fresh build of those branches.
Concurrent website, Android publishing and icon work is outside this change.

## Architecture decision

Use native SwiftUI, a small Swift domain/protocol package, Core Bluetooth,
Network.framework, URLSession and CryptoKit. Keep Android on Kotlin/Compose.
Share versioned contracts, fixtures, acceptance criteria and design intent;
use native controls and permission flows. No Android rewrite or cross-platform
UI migration is needed to begin iOS.

The initial SwiftUI companion and Swift core package are now in `ios/`. The
simulator probe is separate development tooling. Proposed deployment floor:
iOS 18, subject to the supported-device decision before release. A current
simulator does not establish minimum-OS coverage.

## Android findings

**Use the newer preview-led experience as the iOS product target.** Do not port
the older four-tab engineering interface just because it is checked out here.
The core runtime parser and confirmation function are identical across the
inspected old/new branches, so the shared fixtures apply to both.

| Area | Executable checkout | Newer inspected Android target |
| --- | --- | --- |
| Navigation | Gauge, Readings, Vehicle, Settings | Gauge, Car, Settings; Expert only after enabling advanced tools |
| Customize | Older page editor | Selected-page preview, reading/layout sheets, add-page reading choice, manage pages, alert form with explicit Save, review/send |
| Persistence | Profiles schema 3, coolant fields | Schema 4, migration from 1/2/3, reading-specific alerts with above/below direction, units, bounds, priority and dwell |
| UI structure | Large activity with screen functions | Thin activity, stateless feature screens, immutable presentation state and a design-system module |
| Connection | User-triggered discovery | Foreground remembered-gauge discovery, protected readiness checks, bounded retry, freshness, preemption by user operations |
| Updates | Manual catalog check/download | Latest local candidate quietly prepares a verified newer release; installation still requires a tap, failed release hold persists per gauge |

For reproducible source inspection, use `git show <commit>:<path>` with these
commits. Key newer paths are `ui/CompanionApp.kt`, `ui/customize/`, `ui/state/`,
`connection/ForegroundConnectionController.kt`, `AutomaticFirmwareUpdate.kt`,
`ProfileDocumentCodec.kt`, and `ConfigurationProjection.kt` under the Android
source tree. The newer branch's `android/README.md` and
`docs/development/android-core-ux-validation.md` document recorded Pixel journeys,
physical protected reads, accessibility checks and screenshot fixtures. Those
records are historical evidence; this task did not rerun them.

Good extraction boundaries exist in `GaugeProtocolCodec`, `ConfigurationProjector`,
`ProfileDocumentCodec`, `GaugeDraftComparison`, `RuntimeConfirmation`, `AppOperation`,
`HostedFirmwareCatalogCodec`, and `UpdateRecoveryJournal`. Preserve observable
rules with fixtures before moving code. Some pure types still live inside Android
ViewModel/transport files, so source sharing is not currently plug-and-play.

In the executable checkout, `MainActivity.kt` has 1,318 lines; `AppModel.kt` has 982. The ViewModel constructs
Bluetooth/persistence dependencies directly. The operation mutex serializes work,
but Bluetooth clients establish separate connections per operation. iOS should
use one retained central manager and retained peripherals, with a serialized
session, cancellation, deadlines and stale-callback generation checks. The newer
Android UI extraction already addresses the activity problem; reuse its screen
contracts and generated `design/tokens.json` values as the iOS design input.

Some documentation is stale. The README describes numeric-only configuration and
version-2 profiles; current code writes version-3 profiles, one to eight pages and
numeric/arc/bar/trend/dual renderers when firmware advertises `cfg:2`.
[Dev.19 evidence](configurable-pages-live-validation.md) records a five-page
transfer and authenticated readback, explicitly excluding live vehicle renderer
behavior and page-by-page physical display inspection. `sendNumericConfiguration`
is now a historical method name, not the complete feature boundary.

The README's foreground service, Room and DataStore are architecture targets.
The manifest declares no service; current code uses ViewModel coroutines and
SharedPreferences. Sustained background updates are not an implemented parity
requirement. The broader vehicle definition archive is research data; the current
phone catalog is still bounded examples, not full vehicle discovery.

## Parity matrix

The matrix's product target includes the newer source described above. The iOS
app now implements the offline parts of PAR-01, PAR-02 and PAR-03; read-only
Core Bluetooth for PAR-05 is source-built and unverified on iPhone. PAR-07's
runtime parser/confirmation subset has executable shared fixtures. Remaining
rows are planned. Android hardware evidence below comes
from checked-in records. No physical phone, gauge or vehicle was exercised here.

| ID | Android user outcome | iOS acceptance gate |
| --- | --- | --- |
| PAR-01 | Gauge, Car, Settings; opt-in Expert; preview-led Customize, focused sheets; labeled synthetic values | Native SwiftUI navigation and sheets matching the newer flow. Same tasks, disabled states and truthful labels; simulator journeys, large text, narrow/large screens; later physical VoiceOver |
| PAR-02 | Up to eight local profiles; newer schema 4 migrates 1/2/3; per-profile draft/topology/alerts | Versioned Codable model and atomic persistence; shared migrations through schema 4, process restart, no draft loss; no cloud sync implied |
| PAR-03 | One to eight ordered stable-ID pages; five renderers; five ECM readings; reading-specific above/below alerts and explicit Save/Cancel | Same projection/review and validation; correct reading units/ranges, unique alert identities/readings, direction, priority/hysteresis/dwell. Honor cfg:1/cfg:2 bounds; never silently remove pages or change renderers |
| PAR-04 | Example PID browser, Mode 01 decoder, bounded Mode 01/09/22 offline request lab | Shared valid/invalid vectors and restrictions; examples never establish support or trigger OBD traffic |
| PAR-05 | Bounded BLE scan, candidate chooser, remembered gauge, owner proof | Service-filtered Core Bluetooth discovery; denied/off/cancel/timeout/multiple devices/reconnect; physical iPhone gate for pairing, authentication and retained bond |
| PAR-06 | Owner-protected reading selection, rotation and saved state | Preserve read-before-write and durable readback; physical gauge display and touch check |
| PAR-07 | Reviewed config; revision+hash preflight; offsets; commit/reboot/healthy confirmation/rollback | Shared runtime fixtures implemented now; full transport still planned. No success at 100% bytes; simulated fault cases, then physical conflict/reboot/rollback tests |
| PAR-08 | Verified full saved document; per-page comparison; compatible draft import | Hash exact received bytes; detect mixed revisions; compare all pages; unknown fields unavailable; import changes phone only |
| PAR-09 | Protected diagnostic/hardware snapshots with freshness | Same parsing and monotonic-time expiry; stale on invalid clock/session. Real DTC/vehicle semantics require adapter evidence |
| PAR-10 | Signed GitHub development catalogs and bundles; exact compatibility; expiry; monotonic generation and immutable digest | URLSession, CryptoKit P-256 DER signatures, pinned public key, bounded ZIP import; shared wrong-board/tamper/expiry/rollback vectors |
| PAR-11 | Experimental BLE OTA and temporary Wi-Fi AEAD v1/v2 bulk OTA | Core Bluetooth, Hotspot Configuration and Network.framework; identical bytes/offsets/batches/trust; physical consent/routing/lock/interruption tests |
| PAR-12 | Durable update journal; target/previous/trial/other-gauge/legacy distinctions | Atomic journal before mutation; authenticated reconciliation after restart; no blind commit retry or lost failure state |
| PAR-13 | Live vehicle discovery, app telemetry, code clearing and dual-adapter operation are unavailable or unqualified | Keep unavailable until implemented and verified. Public configWrite/ota flags remain false; simulations cannot enable them |
| PAR-14 | Newer foreground auto-reconnect selects only remembered gauge; ready after protected check, stale after 30 seconds; bounded retry 2/5/10/20/30 seconds | Scene lifecycle drives read-only connection work. User operation cancels and awaits automatic cleanup before its lease. No auto send/install or silent target substitution; shared scheduling/freshness tests and physical resume/radio tests |
| PAR-15 | Latest local candidate prepares only a newer compatible signed update; six-hour foreground check cadence, 15-minute failure retry; exact failed-release hold per gauge | Port decision rules after Android integration review. Download/verify before Update ready; install requires tap; unresolved/trial states block offers; distinguish quiet preparation from OS background execution |

Evidence: [current state](../current-state.md),
[Android runtime](../architecture/android-runtime.md),
[configuration reconciliation](config-reconciliation-validation.md),
[update recovery](update-recovery-validation.md),
[OTA matrix](ota-recovery-matrix.md),
[Wi-Fi security](wifi-transport-security-validation.md).

## Ongoing change process

1. First pin the intended integrated Android commit, including accepted newer UX,
   before starting each iOS slice. Every companion behavior change lists affected
   PAR IDs and updates this matrix.
   Firmware and versioned contracts define device behavior; Android bugs are not
   compatibility requirements.
2. Add platform-neutral input/expected-output fixtures under `contracts/parity/`.
   Kotlin and Swift read the same file, with no copied platform versions. Require
   exact bytes for wire frames, signatures, hashes and AEAD; normalized JSON for
   equivalent generated configurations unless serialization itself is contractual.
   Hash the actual transferred bytes, never a re-encoded saved document.
3. Implement small paired slices. A deferred platform gets a named owner and
   release gate in the PR. Platform API differences still need equivalent user
   outcomes and failure/recovery behavior.
4. CI runs contract/docs checks, Android build/unit/lint/instrumentation compilation
   and Swift core tests. Both languages now execute 17 shared runtime vectors.
   The macOS job also compiles the native app. Local simulator UI execution is a
   required acceptance gate for each iOS UI slice; promote it to a CI job after
   pinning a runner with the matching runtime. App compilation alone is not a
   completed user journey.
5. Run the same journeys with deterministic fake transports: offline, denied,
   radio off, two gauges, rejected auth, busy, stale revision, disconnect, trial,
   rollback, process death and unknown outcome. Compare behavior and accessibility,
   not pixel equality of native widgets.
6. Before release, review each row's implementation, shared tests, UI acceptance,
   hardware evidence and deviations. An unfinished feature is a release blocker
   or explicitly unavailable. A mock success cannot count as device support.

The [PR template](../../.github/pull_request_template.md) captures this process and
the [quality workflow](../../.github/workflows/quality.yml) enforces the initial
shared tests. Coverage is intentionally partial: projection, migration, catalog
trust, AEAD and recovery fixtures remain to be ported. There is no automatic
feature-completeness detector.

## iOS constraints that change the implementation

### Bluetooth and one-owner firmware

- Android stores a device address; iOS exposes `CBPeripheral.identifier`, an opaque
  local UUID. Re-scan and reauthenticate when retrieval fails. Do not synchronize
  OS peripheral IDs between phones. A portable hardware identity would require a
  separate firmware contract. [Apple peer identity](https://developer.apple.com/documentation/corebluetooth/cbpeer/identifier)
- Core Bluetooth manages pairing; there is no Android-style `createBond`, bond-state
  polling or generic app-controlled unpair API to copy. Request protected access,
  handle system consent/errors, and declare owner authentication only after
  protected success. Test the gauge's displayed passkey and MITM requirements on
  an actual iPhone. Include contextual `NSBluetoothAlwaysUsageDescription`, not
  Android's legacy scan-location permission. [Core Bluetooth](https://developer.apple.com/documentation/corebluetooth)
- Firmware stores one `g_owner` in `ble_companion.c`, rejects a different peer and
  refuses a new pairing window while owned. Use a spare gauge or a planned physical
  owner handoff for iOS tests. Never erase the working Android bond as setup.
  Privacy-address resolution and retained bonds after reboot need hardware tests.
- iOS negotiates ATT sizing. Use `maximumWriteValueLength(for:)`, subtract our
  command overhead without subtracting Android MTU overhead twice, respect firmware
  bounds, use acknowledged writes initially and verify returned offsets. Long reads
  must work without `requestMtu(185/256)`. [Apple write sizing](https://developer.apple.com/documentation/corebluetooth/cbperipheral/maximumwritevaluelength(for:))
- AccessorySetupKit can be evaluated after the basic path; it is not required to
  start iOS or a substitute for firmware ownership proof.

### Wi-Fi and background execution

- Android binds only the maintenance socket to `WifiNetworkSpecifier`'s network.
  iOS uses `NEHotspotConfigurationManager`, a Hotspot Configuration entitlement
  and system approval. Expect a Wi-Fi association change; do not promise unchanged
  internet routing. Download and verify the entire update before joining the gauge.
  [Apple Wi-Fi overview](https://developer.apple.com/documentation/technotes/tn3111-ios-wifi-api-overview),
  [Hotspot Configuration](https://developer.apple.com/documentation/networkextension/nehotspotconfigurationmanager)
- With `joinOnce`, Apple documents disconnection after more than 15 seconds in the
  background, sleep, exit, or network change. Design foreground transfer and durable
  interruption recovery, including manual lock. Remove only our own temporary
  configuration and close the gauge session. [Apple joinOnce](https://developer.apple.com/documentation/networkextension/nehotspotconfiguration/joinonce)
- The outgoing local TCP connection needs `NSLocalNetworkUsageDescription` and
  a denied/retry/settings path. Simulator does not test local-network privacy.
  The current BLE-provided IP/raw-TCP protocol needs neither Bonjour discovery
  declarations nor broad ATS exceptions. [Apple local network privacy](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy)
- Core Bluetooth background modes and restoration are event-driven, not a general
  Android foreground-service equivalent or an uninterrupted OTA guarantee. iOS 26+
  adds Live Activity-related Bluetooth privileges, but these do not override
  temporary Wi-Fi lifetime. Qualify any background feature separately; start with
  foreground operation. [Apple background guidance](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html),
  [current Core Bluetooth notes](https://developer.apple.com/documentation/corebluetooth)

### Bytes, persistence and UI

- CryptoKit AES.GCM combined output is not this protocol's wire format. Explicitly
  serialize `header || tag || ciphertext`, with the existing 12-byte nonce,
  direction/sequence and full-header AAD. Add known-answer vectors before enabling
  network transfer. [Wi-Fi framing](../protocol/wifi-bulk-v1.md)
- Preserve P-256 DER signature handling. Bundles sign a 40-byte little-endian
  board/length/digest message; catalogs sign raw downloaded JSON. Avoid reserialization
  or accidental double hashing. Preserve archive entry/size bounds and exact board,
  revision, partition, channel and transfer-protocol matching.
  [Transfer protocol](../protocol/experimental-firmware-transfers.md)
- Codable is not schema validation: explicitly enforce versions, integer bounds,
  required/unknown fields where specified, duplicate identities, renderer/PID
  rules and transfer limits. Older schema-3 draft storage and send validation differ:
  temperature drafts can reach 250 C while executable configurations stop at 215 C.
  Newer schema 4 adds per-reading limits and migration. Define shared migration
  outcomes before porting; do not silently clamp imported drafts.
- Use atomic Application Support files for profiles/journals, small preferences
  for UI settings, and Keychain for future app-owned secrets. Bond keys belong to
  the OS. Temporary Wi-Fi keys stay in memory. Exclude secrets and stale operations
  from backup/export. No account or cloud is required for parity.
- Use native document import/share, 44-point targets, Dynamic Type, VoiceOver and
  Reduce Motion while preserving gauge semantics, terminology and truthful review/
  send behavior. Review the iOS product preview before broad UI implementation.

## Delivery sequence

1. Foundation: this review, process, shared runtime vectors, Swift core, CI job,
   simulator setup/probe tools, local profile tests and recorded validation.
2. Offline product: initial SwiftUI target, preview-led editor, schema-4 profile
   migration, reading-specific alerts and configuration projection now compile.
   Pin the integrated newer Android baseline; finish comparison/lab parity and
   shared migration/projection/decoder fixtures. The first two offline UI journeys
   pass on both local iOS 27 simulators; expand them as each slice lands. Clearly
   label synthetic values.
3. Simulated transport: injectable transport, deterministic fake gauge, operation
   actor, journal, timeouts/cancellation, signed catalog/bundle and AEAD fixtures.
4. Physical iPhone gate: passkey/auth/bond recovery, scan/reconnect/long reads,
   controls, conflict/config/reboot, actual Wi-Fi prompts/privacy/routing/lock,
   signed OTA/recovery. Use a spare gauge or intentional owner handoff.
5. Release gate: oldest supported OS and small device plus current OS; accessibility;
   signing and distribution; physical gauge display/touch; adapter/vehicle evidence
   for every vehicle feature advertised. TestFlight/App Store require appropriate
   Apple account/membership/signing, which simulator setup does not configure.

Without an iPhone we can build the offline app and deterministic failure tests.
A future native macOS Bluetooth harness could test gauge bytes on this Mac, but
its bonding/network/lifecycle observations would not qualify iOS. No Bluetooth
proxy, radio reconfiguration or owner reset is included in setup.
