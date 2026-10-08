# Documentation index

Current status belongs in [current-state.md](current-state.md). This index routes readers to the right document; historical evidence does not define today's instructions.

## Active entry points

- [AI context and code map](ai-context.md): minimal reading, task ownership and checks.
- [Current state](current-state.md): implemented, physically observed and pending.
- [Remaining work](roadmap.md): unfinished priorities and acceptance evidence.
- [Setup](development/setup.md), [quality gates](development/quality.md), [release runbook](development/firmware-release-runbook.md).
- [TCM resume](development/tcm-session-resume.md), [TCM research/test plan](development/tcm-values-research-plan.md).
- [Requirements](requirements.md), [sources](sources.md), [design system](design/design-system.md).

## Architecture and implementation ownership

- [Android companion runtime](architecture/android-runtime.md)
- [Configuration storage and apply boundary](architecture/config-storage.md)
- [Architecture decisions](architecture/decisions.md)
- [Firmware runtime ownership and recovery](architecture/firmware-runtime.md)
- [Hardware capabilities and integration strategy](architecture/hardware-and-transports.md)
- [Multi-adapter ECM/TCM support](architecture/multi-adapter.md)
- [Off-road profile actions and gauge gestures](architecture/vehicle-actions.md)
- [System architecture](architecture/system.md)

## Wire protocols and compatibility

- [Development adapter binding contract](protocol/adapter-bindings-v1.md)
- [Companion BLE protocol, draft v1](protocol/ble-v1.md)
- [CEL, trouble codes, and local alerts, draft 0.1](protocol/diagnostics-and-alerts.md)
- [Experimental owner transfer on protocol 0](protocol/experimental-firmware-transfers.md)
- [Firmware updates and recovery, draft 0.1](protocol/firmware-update.md)
- [Authenticated hardware capacity snapshot](protocol/hardware-probe.md)
- [PID discovery and definition model, draft 0.1](protocol/pid-discovery.md)
- [Experimental authenticated Wi-Fi bulk transport](protocol/wifi-bulk-v1.md)

## Vehicle research, scoped reference material

- [Vehicle knowledge](vehicles/README.md)
- [Local diagnostic catalog research](vehicles/catalog-research.md)
- [Wrangler JK action-handler inventory](vehicles/jeep/jk-handler-analysis.md)
- [Jeep Wrangler JK diagnostic knowledge](vehicles/jeep/wrangler-jk.md)

## Focused runbooks and historical evidence

- [Offline hardening candidate](development/offline-hardening.md): all seven priorities, checks and hardware gates.
- [Historical runtime evidence](development/runtime-history.md): archived observations, not current instructions.
- [Maintainability review](development/maintainability-review.md): resolved findings and scope.

Use dates, versions and explicit limits inside each record. Old pending steps are superseded by current state and the active roadmap. Completed review plans were removed; their acceptance gates are consolidated in quality/runtime docs.

- [Active document readback on paired hardware](development/active-document-readback-validation.md)
- [Adapter and vehicle expansion](development/adapter-vehicle-integration-plan.md)
- [Android companion redesign validation](development/android-core-ux-validation.md)
- [Arc visual refresh validation](development/arc-visual-refresh-validation.md)
- [Configuration reconciliation and write precondition](development/config-reconciliation-validation.md)
- [Configurable pages and renderers](development/configurable-pages-and-renderers.md)
- [Configurable pages live validation](development/configurable-pages-live-validation.md)
- [dev.31 update and transfer-start investigation](development/dev31-update-and-startup-validation.md)
- [Display color quality correction](development/display-color-quality-validation.md)
- [Display settings qualification](development/display-settings-validation.md)
- [Metric and imperial display units validation](development/display-units-validation.md)
- [Phone draft and saved gauge comparison](development/draft-comparison-validation.md)
- [Firmware core hardening validation](development/firmware-core-hardening-validation.md)
- [Development firmware release runbook](development/firmware-release-runbook.md)
- [First Jeep capture results](development/first-jeep-capture-results.md)
- [Gauge touch and firmware performance review](development/gauge-performance-review.md)
- [GitHub hosted firmware validation](development/github-hosted-update-validation.md)
- [Hardware capacity live validation](development/hardware-capacity-validation.md)
- [Hemi transmission candidates and next capture](development/hemi-transmission-candidates.md)
- [Direct Mac exploration results](development/jeep-direct-exploration-results.md)
- [Jeep engine Session B](development/jeep-session-b-results.md)
- [JSS / PCS transmission integration research](development/jss-transmission-research.md)
- [M1 transport increment](development/m1-transport-status.md)
- [Pixel numeric configuration transfer, 2026-09-25](development/numeric-config-transfer-validation.md)
- [Offline adapter integration](development/offline-integration-results.md)
- [Offline development sprint, 2026-09-25](development/offline-sprint-2026-09-25.md)
- [GitHub OTA recovery matrix](development/ota-recovery-matrix.md)
- [Paired control hardware review](development/paired-control-validation.md)
- [Quality strategy and evidence gates](development/quality.md)
- [Development setup and checks](development/setup.md)
- [JSS TCM gear capture](development/tcm-gear-capture.md)
- [TCM integration: observed faults and remaining gates](development/tcm-integration-status.md)
- [Offline TCM work completed](development/tcm-offline-completion.md)
- [Resume the TCM values session](development/tcm-session-resume.md)
- [Physical TCM temperature goal](development/tcm-temperature-goal.md)
- [Additional TCM values: research and quick test plan](development/tcm-values-research-plan.md)
- [Pixel signed development update, 2026-09-25](development/update-live-validation.md)
- [Pixel update preflight, 2026-09-25](development/update-preflight-validation.md)
- [Development update recovery checks, 2026-09-25](development/update-recovery-validation.md)
- [USB migration to the 16 MB gauge layout](development/usb-partition-migration.md)
- [Foundation validation, 2026-09-25](development/validation-report.md)
- [First swapped-Jeep capture](development/vehicle-capture-runbook.md)
- [Authenticated Wi-Fi bulk validation](development/wifi-bulk-validation.md)
- [Wi-Fi transport security validation, 2026-09-26](development/wifi-transport-security-validation.md)

## Documentation maintenance

Update current state when behavior or evidence changes. Update the relevant wire/runtime doc when its boundary changes. Retire completed action plans and redirect their links; keep exact historical observations as labelled evidence. Run the document/link checks before committing.
