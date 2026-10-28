# Feature context index

Use this to find the authoritative details for a feature. [Current state](current-state.md)
owns implemented, installed and physically qualified facts; [backlog](backlog.md)
owns remaining tasks and [roadmap](roadmap.md) orders outcomes. The [source map](ai-context.md)
locates modules/tests. This index deliberately does not duplicate those status lists.

| Feature area | Authoritative details |
| --- | --- |
| Vehicles, saved profiles, per-gauge assignments and optional child TCM | [Vehicle connections](architecture/vehicle-connections.md), [phone persistence](architecture/android-runtime.md#storage-and-recovery) |
| Pairing, owner access, connection status and automatic recovery | [Interaction/recovery](architecture/interaction-recovery.md), [protected BLE](protocol/experimental-firmware-transfers.md) |
| PID catalog, custom definitions, units and polling classes | [Reading/alert catalog](development/reading-catalog-alerts.md), [typed definitions](../contracts/pid-definition.schema.json) |
| Pages, layouts, circular rendering and display behavior | [Gauge UI contract](design/gauge-ui-proposal.md), [display geometry](design/round-display-ui-guidelines.md), [firmware runtime](architecture/firmware-runtime.md) |
| Brightness, manual rotation, units and timed cycling | [Display settings wire/storage](protocol/experimental-firmware-transfers.md#legacy-selection-pairing-and-display-settings) |
| Smart cycling, interest priorities and context | [Smart cycling](architecture/smart-page-cycling.md) |
| Sensor-driven Auto orientation | [Orientation policy and support boundary](architecture/hardware-and-transports.md#automatic-display-orientation) |
| Sampling/cache continuity and scheduling | [Firmware runtime](architecture/firmware-runtime.md#polling-diagnostics-and-alerts), [continuity qualification](development/gauge-continuity-plan.md) |
| Threshold alerts, notifications, context and history | [Shared alerts/diagnostics](architecture/alerts-and-diagnostics.md) |
| CEL/MIL, code lookup/filtering and explicit clearing | [Diagnostics contract](protocol/diagnostics-and-alerts.md), [shared semantics](architecture/alerts-and-diagnostics.md) |
| Third-party alerts and phone-content experiments | [Provider integration](architecture/phone-alert-providers.md), [roadmap boundaries](roadmap.md#product-boundaries) |
| Shortcuts, action pages, Air up/Winch and ABS/TCS | [Vehicle actions](architecture/vehicle-actions.md), [parked qualification](development/jeep-action-qualification.md) |
| Air-up duration estimates and coolant/fallback protection | [Vehicle actions](architecture/vehicle-actions.md), [current protection state](current-state.md#raised-idle-coolant-guard) |
| Configuration transactions, validation, trial and rollback | [Configuration storage](architecture/config-storage.md) |
| Signed firmware, App/Wi-Fi maintenance and recovery | [Update contract](protocol/firmware-update.md), [release/recovery procedure](development/firmware-release-runbook.md) |
| Browser firmware recovery and release selection | [Recovery tool](../web-recovery/README.md) |
| Hardware targets, private adapters and future surfaces | [Platform/release architecture](architecture/platforms-and-releases.md), [hardware/transports](architecture/hardware-and-transports.md) |
| Memory, worker health and resilience | [Firmware ownership/budget](architecture/firmware-runtime.md), [qualification](development/memory-qualification.md) |
| Temporary screen showcase | [Showcase procedure](development/display-showcase.md) |
| Android navigation, shared UI and Expert/support/export | [Android architecture](architecture/android-runtime.md), [design system](design/design-system.md), [current consumer experience](current-state.md) |
| Additional vehicles, iOS and browser configuration | [Roadmap](roadmap.md), [backlog entry gates](backlog.md) |
| Public website and current product visuals | [Website maintenance](../website/README.md) |

Every feature change follows the [completion rule](documentation.md#feature-completion-rule):
update its topic owner with behavior/defaults, UX, ownership, compatibility, limits,
tests and qualification. Preserve one source of truth and link to it.
