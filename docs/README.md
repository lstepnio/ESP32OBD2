# Documentation index

## Start here

1. [Repository instructions](../AGENTS.md)
2. [AI context and source map](ai-context.md)
3. [Current implementation and device evidence](current-state.md)
4. [Roadmap](roadmap.md) and [prioritized backlog](backlog.md)

[Ownership and maintenance](documentation.md) explains authority and update rules.
[Documentation map](documentation-map.json) provides the validated machine-readable inventory.
Read only the affected contract/runbook before source work; evidence is not required onboarding.

## Architecture

- [System overview](architecture/system.md), [Android ownership](architecture/android-runtime.md), [firmware ownership](architecture/firmware-runtime.md)
- [Interaction/recovery rules](architecture/interaction-recovery.md)
- [Vehicle connections and dashboard](architecture/vehicle-connections.md), [multi-adapter qualification](architecture/multi-adapter.md)
- [Configuration storage](architecture/config-storage.md), [architecture decisions](architecture/decisions.md)
- [Hardware strategy](architecture/hardware-and-transports.md), [local gestures and proposed controller actions](architecture/vehicle-actions.md)

- [Shared alerts, notifications and CEL/MIL plan](architecture/alerts-and-diagnostics.md): proposed lifecycle, context/history, clearing and implementation/test sequence

## Protocols

- Implemented development: [protected BLE/config/update wire](protocol/experimental-firmware-transfers.md), [adapter bindings](protocol/adapter-bindings-v1.md), [Wi-Fi bulk](protocol/wifi-bulk-v1.md), [hardware snapshot](protocol/hardware-probe.md)
- [Diagnostics and alerts](protocol/diagnostics-and-alerts.md): current read/threshold path plus explicitly proposed clearing/attention extensions
- Proposed public interfaces: [BLE v1](protocol/ble-v1.md), [PID discovery/import](protocol/pid-discovery.md), [production update contract](protocol/firmware-update.md)
- [Schemas and examples](../contracts/): shape coverage is broader than current runtime support

## Maintained procedures

- [Setup and exact checks](development/setup.md), [quality/evidence gates](development/quality.md)
- [Signed development release](development/firmware-release-runbook.md), [OTA interruption matrix](development/ota-recovery-matrix.md)
- [Dual-adapter recovery matrix](development/dual-adapter-recovery.md)
- [Reading catalog and alert maintenance](development/reading-catalog-alerts.md)
- [TCM session resume](development/tcm-session-resume.md), [bounded TCM research/test plan](development/tcm-values-research-plan.md), [vehicle capture](development/vehicle-capture-runbook.md)

## Product, design and research

- [Requirements](requirements.md): product goals and acceptance, not an implementation checklist
- [Design system](design/design-system.md), [round-display geometry](design/round-display-ui-guidelines.md)
- [Design audit](design/redesign/audit.md), [concept/IA](design/redesign/concept.md), [Customize review](design/redesign/customize-review.md): scoped design history and fixture evidence
- [Sources](sources.md), [reference projects](reference-projects-and-architecture.md)
- [Vehicle research index](vehicles/README.md), [catalog provenance/license](vehicles/catalog-research.md), [Jeep references](vehicles/jeep/wrangler-jk.md)

## Evidence

[Dated implementation/device evidence](evidence/README.md) retains rollout, capture,
review and measured behavior. Old pending instructions are historical. Use current
state and backlog to decide today's work, not the latest-looking archive title.
Screenshots remain labelled as simulated fixtures or physical captures in their records.

Paired-phone provider integration: [typed notices, review and qualification](architecture/phone-alert-providers.md).
