# AI context and task routing

Read this after the root `AGENTS.md`, then read [current state](current-state.md).
Do not load all historical reports or third-party catalogs to begin a task.

## Start a session

1. Inspect `git status --short`, branch/upstream and the attached worktree. Use the
   current suitable checkout. Do not reset dirty files or edit another task's checkout.
2. Read current state and choose the affected area from the table below.
3. Search with `rg` and read the implementation plus its focused tests/contract.
   Verify names and constants in code before applying old notes.
4. Implement the smallest cohesive change. Ask only for genuinely missing input,
   destructive actions or an authorization that has not already been given.
5. Run checks for the changed boundary. Record software tests separately from phone,
   gauge and vehicle observations. Commit and push using root instructions.

## Runtime map

| Task | Read/edit first | Contract or evidence |
| --- | --- | --- |
| Gauge startup, touch, page/setting application | `firmware/gauge/main/main.c`, `src/ui.c`, `src/display_settings.c` | [Firmware runtime](architecture/firmware-runtime.md) |
| Adapter connect/routing/ELM replies | `src/ble_mgr.c`, `src/ble_obd.c`, `src/elm_response.c`, `src/obd_adapter_profile.c` | [Adapter bindings](protocol/adapter-bindings-v1.md) |
| Vehicle action or gesture design | [Proposed profile actions](architecture/vehicle-actions.md), `main.c`, `src/ui.c`, existing per-source workers | No control support follows from telemetry or static catalog fragments; unknown write outcomes must not be blindly retried |
| Fault state | `src/diagnostics_state.c`, Android `VehicleDiagnostics.kt`, `ui/state/DiagnosticPresentation.kt` | [Diagnostics](protocol/diagnostics-and-alerts.md) |
| Config validation/projection/persistence | `src/config_document.c`, `src/config_runtime.c`, Android `ConfigurationProjection.kt`, `GaugeDraftComparison.kt`, `ProfileStore.kt` | [Config storage](architecture/config-storage.md) |
| BLE commands and bytes | `src/ble_companion.c`, Android `GaugeConfigTransferClient.kt`, `GaugeProtocolCodec.kt` | [BLE](protocol/ble-v1.md), [development extensions](protocol/experimental-firmware-transfers.md) |
| Signed updates/Wi-Fi | `src/ota_transfer.c`, `src/wifi_bulk.c`, Android `WifiBulkClient.kt`, update classes | [Release runbook](development/firmware-release-runbook.md), [Wi-Fi](protocol/wifi-bulk-v1.md) |
| Vehicle hierarchy and gauge assignments | Android `ProfileStore.kt`, `ProfileDocumentCodec.kt`, `VehicleConnections.kt`, `GaugeAssociationStore.kt` | [Vehicle connections](architecture/vehicle-connections.md) |
| App state and operations | Android `AppModel.kt`, `connection/`, `ui/state/`, `AppOperation.kt` | [Android runtime](architecture/android-runtime.md) |
| App UI/style | Android `ui/`, `core/designsystem/`, `design/tokens.json` | [Design system](design/design-system.md) |
| Jeep capture and offline replay | `tools/obd_capture.py`, `obd_explore.py`, `obd_tcm_values.py`, `tools/tests/` | [TCM resume](development/tcm-session-resume.md) |
| Catalog research | `tools/research/`, `data/vehicle-definitions/` | [Catalog scope/license](vehicles/catalog-research.md) |

Firmware paths in the table are relative to `firmware/gauge/main/`. Android Kotlin
files are under `android/app/src/main/java/com/lstepnio/egauge/` unless specified.
Third-party research definitions are not executable vehicle support. Never run
catalog writes/activation routines during an exploratory read session.

## Core invariants

- Gauge owns the adapter; Android connects to the gauge. A Mac capture needs the
  gauge to release the single-client adapter. Single-source setup is default; development dev.41 also executes an explicitly
  bound ECM/TCM pair. Public radio capacity remains unqualified.
- BLE callbacks enqueue bounded work. Transfer workers own flash operations;
  LVGL owns widgets. Do not block callbacks or add nested subsystem locks.
- Missing/invalid/stale data is unavailable, not zero. Source, ECU, revision,
  session and monotonic age remain attached to evidence.
- Configuration success needs expected running revision/hash and cleared trial state.
  Firmware update success needs post-reboot identity confirmation.
- App previews are labelled examples. Technical detail belongs in Expert. Preserve
  existing architecture and visual tokens; do not introduce a second settings model.
- Do not change public capability flags based on compilation or simulations alone.
- Keep private captures under ignored `artifacts/`; never commit secrets or device IDs.

## Choose verification by impact

| Changed area | Minimum meaningful checks |
| --- | --- |
| Docs, schemas, tokens | `.venv/bin/python tools/validate.py`, `git diff --check` |
| OBD/capture/parser/diagnostics | Host tests in [setup](development/setup.md), C sanitizer fixtures; firmware build if C changes |
| Android state/codec/UI | Relevant unit tests, debug build and lint; compile instrumentation tests when their contracts change |
| Firmware runtime/config/update | IDF build and affected host C fixtures; use [quality gates](development/quality.md) for physical qualification |
| Visible UI | Inspect existing fixtures or capture relevant new example screens if behavior/layout changes |

Use [setup](development/setup.md) for exact toolchain commands. Do not reinstall
working toolchains or run broad repeated tests once the changed risk is verified.
Do not install/flash devices, run BLE discovery, clear codes, drive, or publish a
release as an incidental validation step. Follow the owner's existing authorization.

## Maintain the context

`current-state.md` owns current status; `roadmap.md` owns unfinished work; protocols
own wire details; runtime docs own implementation responsibilities. Historical
reports are evidence, not new instructions. Replace stale active text rather than
adding another competing summary. Record dates, exact image identity and evidence
limits. Delete superseded planning docs after preserving still-relevant gates.

## Standing interaction policy

Follow [automatic refresh and resilient interactions](architecture/interaction-recovery.md)
when changing the App or firmware. Shared status UI, automatic settings reads, bounded
foreground retries, independent adapter status and negative-scenario verification are
project defaults. The document maps the implementation and distinguishes source tests
from remaining hardware qualification.
