# AI context and source map

Start with [AGENTS.md](../AGENTS.md) for commands and boundaries, then
[current state](current-state.md). Read the affected [backlog](backlog.md) row,
source/tests and contract below. Search dated evidence only for a specific question.
The [documentation map](documentation-map.json) validates these source entry points.

## Source map

Firmware paths below are relative to `firmware/gauge/main/`. Android paths are
relative to `android/app/src/main/java/com/lstepnio/egauge/` unless specified.

| Area | Source entry points | Maintained contract |
| --- | --- | --- |
| Gauge startup/display/settings/gestures | `main.c`, `src/ui.c`, `src/display_settings.c`, `src/page_action.c` | [Firmware ownership](architecture/firmware-runtime.md), [actions](architecture/vehicle-actions.md), [temporary display showcase](development/display-showcase.md) |
| Vehicle shortcut commands | `src/vehicle_commands.c`, `src/vehicle_idle_coolant.c`, `src/vehicle_commands_runtime.c`, `src/jeep_idle_driver.c`, `src/jeep_abs_tcs_driver.c`; `VehicleCommands.kt`, `IdleCoolant.kt`, `AirUpTimer.kt`, `ui/car/AirUpTimerEditor.kt`, `ProfileActions.kt`, `GaugeAssociationStore.kt`, `ui/car/ActionPagesSheet.kt`, `ui/car/VehicleActionsSheet.kt` | [Shared owner and qualification gates](architecture/vehicle-actions.md#shared-command-owner), [wire](protocol/experimental-firmware-transfers.md#shared-vehicle-commands-v1) |
| Adapter/routing/ELM replies | `src/ble_mgr.c`, `src/ble_obd.c`, `src/elm_response.c`, `src/obd_adapter_profile.c` | [Bindings](protocol/adapter-bindings-v1.md), [vehicle model](architecture/vehicle-connections.md) |
| Fault state and presentation | `src/diagnostics_state.c`, `src/diagnostic_filter.c`; `VehicleDiagnostics.kt`, `DiagnosticCodeFilters.kt`, `FaultCodeCatalog.kt`, `FcaTransmissionFaultCodes.kt`, `ui/state/DiagnosticPresentation.kt` | [Implemented diagnostics](protocol/diagnostics-and-alerts.md), [shared alert/CEL architecture](architecture/alerts-and-diagnostics.md) |
| Shared events/history/providers | `src/alert_events.c`, `src/alert_runtime.c`, `src/diagnostic_clear.c`; `AlertEvents.kt`, `AlertRepository.kt`, `AlertNotifications.kt`, `AlertMonitoringService.kt`, `ExternalAlerts.kt`, `ui/AlertPresentation.kt` | [Shared alerts](architecture/alerts-and-diagnostics.md), [phone providers](architecture/phone-alert-providers.md) |
| Config compile/project/trial | `src/config_document.c`, `src/config_runtime.c`; `ConfigurationProjection.kt`, `GaugeDraftComparison.kt` | [Config storage](architecture/config-storage.md) |
| Phone setup and assignments | `ProfileStore.kt`, `ProfileDocumentCodec.kt`, `LocalSetupStore.kt`, `LocalSetupTransactions.kt`, `DurableWrites.kt`, `VehicleConnections.kt` | [Android storage](architecture/android-runtime.md#storage-and-recovery) |
| BLE bytes, owner and pairing | `src/ble_companion.c`; `GaugeConfigTransferClient.kt`, `GattSessionCleanup.kt`, `OwnerReadRecovery.kt`, `GaugeProtocolCodec.kt`, `BleCapabilityClient.kt`, `PairingFailure.kt` | [Implemented development wire](protocol/experimental-firmware-transfers.md); [bounded recovery](architecture/interaction-recovery.md#android-read-scheduling-and-hosted-http); public BLE v1 is proposed |
| Hardware platforms and distribution | `contracts/platform-policy.json`, `tools/platform_policy.py`, `inc/gauge_board.h`, `inc/gauge_surface.h`, `inc/gauge_presentation.h`, `src/gauge_board_reference.c`, `src/gauge_surface.c`; private hardware integration repository | [Platform/RC architecture](architecture/platforms-and-releases.md) |
| Website / browser USB bootstrap | `website/`, `web-recovery/`, `tools/build_website.py`, `tools/package_web_recovery.py` | [Website](../website/README.md), [USB recovery boundaries and provenance](../web-recovery/README.md) |
| Updates/Wi-Fi/recovery | `src/ota_transfer.c`, `src/wifi_bulk.c`, `src/wifi_bulk_io.c`; `WifiBulkClient.kt`, `FirmwareUpdatePersistence.kt`, `UpdateRecoveryJournal.kt`, `SocketIo.kt` | [Wi-Fi](protocol/wifi-bulk-v1.md), [release runbook](development/firmware-release-runbook.md) |
| App lifecycle/state/operations | `AppModel.kt`, `GaugeModels.kt`, `AppOperation.kt`, `connection/`, `ui/state/` | [Android ownership](architecture/android-runtime.md), [recovery rules](architecture/interaction-recovery.md) |
| UI/style/accessibility | `ui/`, `android/core/designsystem/`, `design/tokens.json` | [Design system](design/design-system.md) |
| Reading/alert catalog | `contracts/reading-catalog.json`, `tools/generate_reading_catalog.py`; `ReadingCatalog.kt`, `ui/customize/AlertForm.kt`; `src/alert_engine.c` | [Catalog maintenance](development/reading-catalog-alerts.md) |
| Worker health and memory budget | `src/worker_health.c`, `src/memory_health.c`, `firmware/gauge/lvgl_memory.h`, `tools/check_firmware_memory.py` | [Firmware health](architecture/firmware-runtime.md#worker-progress-and-memory-evidence), [placement budget](architecture/firmware-runtime.md#memory-placement-and-admission-budget) |
| Parked capture/replay | `tools/obd_capture.py`, `tools/obd_explore.py`, `tools/obd_tcm_values.py`, `tools/tests/` | [TCM resume](development/tcm-session-resume.md), [capture runbook](development/vehicle-capture-runbook.md) |
| Research definitions | `tools/research/`, `data/vehicle-definitions/` | [Scope/license](vehicles/catalog-research.md); not executable vehicle support |
