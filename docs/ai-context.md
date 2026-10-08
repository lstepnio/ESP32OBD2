# AI context and task routing

Read root [AGENTS.md](../AGENTS.md), this file and [current state](current-state.md).
Read only the relevant [backlog](backlog.md) item, contract and source after that.
Do not load the evidence archive or imported catalogs to start ordinary work.

## Session workflow

1. Inspect branch/upstream, `git status --short` and attached worktree. Reuse a suitable
   active checkout; preserve dirty files and other tasks' work.
2. Identify the requested outcome and, when relevant, its backlog ID/acceptance gate.
   Separate implementation, simulated checks, phone/gauge observations and vehicle qualification.
3. Search affected source/tests with `rg`. Verify names/constants before using dated
   notes. Use the map below and the machine-readable [documentation map](documentation-map.json).
4. Implement the smallest cohesive change. Preserve architecture/styling and automatic
   bounded recovery. Ask only for critical missing input or required authorization.
5. Run the minimum meaningful checks below. Record remaining limits honestly, update
   the authoritative documents, commit and push under root instructions.

## Source map

Firmware paths below are relative to `firmware/gauge/main/`. Android paths are
relative to `android/app/src/main/java/com/lstepnio/egauge/` unless specified.

| Area | Source entry points | Maintained contract |
| --- | --- | --- |
| Gauge startup/display/settings/gestures | `main.c`, `src/ui.c`, `src/display_settings.c`, `src/page_action.c` | [Firmware ownership](architecture/firmware-runtime.md), [actions](architecture/vehicle-actions.md) |
| Adapter/routing/ELM replies | `src/ble_mgr.c`, `src/ble_obd.c`, `src/elm_response.c`, `src/obd_adapter_profile.c` | [Bindings](protocol/adapter-bindings-v1.md), [vehicle model](architecture/vehicle-connections.md) |
| Fault state and presentation | `src/diagnostics_state.c`; `VehicleDiagnostics.kt`, `ui/state/DiagnosticPresentation.kt` | [Diagnostics](protocol/diagnostics-and-alerts.md) |
| Config compile/project/trial | `src/config_document.c`, `src/config_runtime.c`; `ConfigurationProjection.kt`, `GaugeDraftComparison.kt` | [Config storage](architecture/config-storage.md) |
| Phone setup and assignments | `ProfileStore.kt`, `ProfileDocumentCodec.kt`, `LocalSetupStore.kt`, `LocalSetupTransactions.kt`, `DurableWrites.kt`, `VehicleConnections.kt` | [Android storage](architecture/android-runtime.md#storage-and-recovery) |
| BLE bytes, owner and pairing | `src/ble_companion.c`; `GaugeConfigTransferClient.kt`, `GaugeProtocolCodec.kt`, `BleCapabilityClient.kt`, `PairingFailure.kt` | [Implemented development wire](protocol/experimental-firmware-transfers.md); public BLE v1 is proposed |
| Updates/Wi-Fi/recovery | `src/ota_transfer.c`, `src/wifi_bulk.c`, `src/wifi_bulk_io.c`; `WifiBulkClient.kt`, `FirmwareUpdatePersistence.kt`, `UpdateRecoveryJournal.kt`, `SocketIo.kt` | [Wi-Fi](protocol/wifi-bulk-v1.md), [release runbook](development/firmware-release-runbook.md) |
| App lifecycle/state/operations | `AppModel.kt`, `GaugeModels.kt`, `AppOperation.kt`, `connection/`, `ui/state/` | [Android ownership](architecture/android-runtime.md), [recovery rules](architecture/interaction-recovery.md) |
| UI/style/accessibility | `ui/`, `android/core/designsystem/`, `design/tokens.json` | [Design system](design/design-system.md) |
| Reading/alert catalog | `contracts/reading-catalog.json`, `tools/generate_reading_catalog.py`; `ReadingCatalog.kt`, `ui/customize/AlertForm.kt`; `src/alert_engine.c` | [Catalog maintenance](development/reading-catalog-alerts.md) |
| Worker health and resource observations | `src/worker_health.c`, `main.c`, worker loops | [Firmware health](architecture/firmware-runtime.md#worker-progress-and-memory-evidence) |
| Parked capture/replay | `tools/obd_capture.py`, `tools/obd_explore.py`, `tools/obd_tcm_values.py`, `tools/tests/` | [TCM resume](development/tcm-session-resume.md), [capture runbook](development/vehicle-capture-runbook.md) |
| Research definitions | `tools/research/`, `data/vehicle-definitions/` | [Scope/license](vehicles/catalog-research.md); not executable vehicle support |

## Nonnegotiable boundaries

- The gauge owns each adapter; a Mac capture requires releasing its single-client link.
  One adapter is normal. An optional child changes routing, never the vehicle page list.
- Missing/invalid/stale values are unavailable, never zero. Keep ECU, source, revision,
  session and monotonic age through parsing and presentation; healthy siblings continue.
- BLE/UI callbacks are nonblocking; owning workers hold external resources. Use one
  monotonic budget, cancellation-safe IO and bounded cleanup before lease release.
- Coupled phone state uses one off-main serialized commit; publish only acknowledged
  state. Retain invalid/newer documents and uncertain outcomes rather than guessing.
- Config success requires expected running revision/hash and cleared trial. Update
  success requires the exact post-reboot signed image and healthy OTA state.
- Previews remain labelled examples; technical detail belongs in Expert. Controls,
  clearing and public capability promotion need a complete qualified protected path.
- Keep private captures, identifiers, preference backups and signing material out of Git.
  Ordinary commit/push authorization does not authorize main merge or release publication.

Details belong in [standing recovery rules](architecture/interaction-recovery.md),
not a second copy here. Owner instructions take precedence over repository guidance.

## Verification by impact

| Changed boundary | Minimum meaningful checks |
| --- | --- |
| Documentation | `.venv/bin/python tools/validate.py`, `git diff --check`; inspect authority/link/backlog changes |
| Catalog/schema/tokens | Validator and corresponding generator `--check`; affected decode/projection fixtures |
| OBD/capture/parser/diagnostics | Relevant host tests and C sanitizer fixtures; firmware build for C changes |
| Android state/codec/UI | Relevant JVM tests, debug build and lint; compile instrumentation when its contracts change |
| Firmware runtime/config/update | ESP-IDF build and affected host C fixtures; physical gates are distinct |
| Visible UI | Inspect relevant fixtures or focused screens; avoid repeating every golden without a concrete risk |

Exact commands and toolchains are in [setup](development/setup.md); hardware gates
are in [quality](development/quality.md). Never run broad connected tests on the owner
phone, reset its App data, flash, publish or access a vehicle as incidental validation.
Do not reinstall working toolchains or repeat broad checks after the risk is verified.

## Documentation updates

[Documentation ownership](documentation.md) defines where each fact belongs.
Current state owns support/devices, backlog owns tasks/status/blockers, roadmap owns
outcome order, protocols own bytes, architecture owns implementation responsibilities,
and evidence owns dated measurements. Replace stale active text; do not append a
competing summary. Update related facts/gates in the same commit as the code.
