# Implementation roadmap

Status: M0 is complete. An initial M1 transport increment is implemented on `feat/m1-transport-core`; the remaining M1 acceptance criteria are open. Complete vertical slices with documentation and evidence before expanding the catalog.

| Milestone | Deliverables | Exit criteria |
| --- | --- | --- |
| M0: design foundation | Repository, baseline provenance, architecture, UI prototype, schemas, quality gates | Prototype review and contract/example checks; user priorities recorded |
| M1: transport and autonomy | Board module extraction, ELM stream parser, per-ECU samples, two-adapter-plus-phone BLE spike, stale data, local alert engine | ECM + TCM + phone links coexist, or measured limits and fallback decision are documented while LVGL renders; parser fixtures; stale/alert behavior verified without phone |
| M2: configuration vertical slice | Native Android project, association, capabilities, read/edit/apply one page, versioned durable config | Restart phone/gauge; revision/hash reconcile; interrupted apply retains old config; one real supported reading configured end to end |
| M3: discovery and diagnostics | Standard per-ECU discovery, catalog UI, scoped manufacturer packs, numeric decoder lab, MIL/DTC/readiness, guarded clear flow | Partial/cancelled discovery; sample provenance; invalid definition rejection; clear semantics verified with emulator before explicit vehicle action |
| M4: dashboard and alerts | Five LVGL renderers, Android editor, page sets, units/rotation/brightness, warning/critical rules and history, on-gauge DTC page | Pixel/physical readability; hidden-page alerts; hysteresis/dwell/stale/acknowledgment tests; all three use-case templates |
| M5: updates and recovery | USB partition migration, signed bundle workflow, BLE transfer plus negotiated Wi-Fi spike, A/B rollback, Android foreground operation | Power-loss/phone-loss matrix; wrong image rejection; recovery to known working version; no false success |
| M6: beta hardening | Hardware/phone matrix, soak, accessibility, performance budgets, maintenance docs | Quality gates in [quality strategy](development/quality.md) recorded with artifacts |

## First engineering slice

Extract shared telemetry/status models and bounded ELM response assembly from the baseline. Add the companion service with only capabilities/read-only status and a fake Android transport. Measure both BLE links plus display. Then introduce one atomic configuration operation and one local warning rule. This exposes radio, memory, lifecycle and UX risks early before building every screen or catalog importer.

## Use-case templates

Everyday: speed, coolant, voltage/load where available, MIL summary. Off-road/thermal: large temperatures, trends, conservative warning visibility. Performance: RPM/load/throttle where available with measured achievable rate. Diagnostics: ECU/source, support/readiness, DTC categories, sample/decoder lab. Templates are suggestions populated only with data the selected vehicle/profile can supply; missing transmission/oil-pressure data is never fabricated.

## Acceptance tracking

Each implementation PR cites milestone, requirement, contract version, behavior changed, evidence and remaining limits. Record vehicle validation as compatibility entries, not informal global support statements. App and firmware release versions can differ; compatibility is negotiated by protocol/schema/features.

## Tracked open engineering work

- **MULTI-001:** independent adapter contexts and three-link hardware feasibility.
- **MULTI-002:** define and measure fallback interaction if concurrent connections cannot meet freshness needs.
- **OTA-001:** validate new partition layout and reversible configuration migration before USB provisioning.
- **PID-001:** identify actual ECM/TCM vehicles, adapter GATT profiles and documented manufacturer PID definitions.

## Hardware-aware transport extension

BLE provides association/control and a universal update path; Wi-Fi is designed as a negotiated faster bulk transport sharing the same operation state, trust checks and recovery semantics. Two-adapter radio coexistence, board sensors, PSRAM, USB and power management are covered in the [hardware and transport strategy](architecture/hardware-and-transports.md). Preferred production update transport remains subject to WIFI-001/RADIO-001 measurements.
