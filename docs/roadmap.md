# Implementation roadmap

Status: M0 is complete. M1 firmware transport, recovery, scheduler, alert, and ownership foundations are implemented on `feat/owned-gauge-control`; real adapter and three-link acceptance criteria remain open. M2 has an experimental authenticated configuration slice with prior numeric-page evidence. The eight-page and five-renderer expansion is source-built and needs physical transfer, display, touch, and reboot validation before promotion. Complete vertical slices with documentation and evidence before expanding the catalog.

| Milestone | Deliverables | Exit criteria |
| --- | --- | --- |
| M0: design foundation | Repository, baseline provenance, architecture, UI prototype, schemas, quality gates | Prototype review and contract/example checks; user priorities recorded |
| M1: transport and autonomy | Board module extraction, ELM stream parser, per-ECU samples, two-adapter-plus-phone BLE spike, stale data, local alert engine | ECM + TCM + phone links coexist, or measured limits and fallback decision are documented while LVGL renders; parser fixtures; stale/alert behavior verified without phone |
| M2: configuration vertical slice | Native Android app, versioned local profiles, explicit association, serialized operations, bounded transfer, exact numeric review, two-slot durable config, active-document readback, stored/running identity, and a restricted numeric Apply path | Repeat final app/firmware regression; interruption retains old config; one real adapter-backed reading configured end to end |
| M3: discovery and diagnostics | Standard per-ECU discovery, catalog UI, scoped manufacturer packs, numeric decoder lab, MIL/DTC/readiness, guarded clear flow | Partial/cancelled discovery; sample provenance; invalid definition rejection; clear semantics verified with emulator before explicit vehicle action |
| M4: dashboard and alerts | Five LVGL renderers, Android editor, page sets, units/rotation/brightness, warning/critical rules and history, on-gauge DTC page | Pixel/physical readability; hidden-page alerts; hysteresis/dwell/stale/acknowledgment tests; all three use-case templates |
| M5: updates and recovery | USB partition migration, signed bundle and GitHub catalog workflows, exact-board hosted selection, BLE transfer plus negotiated Wi-Fi spike, A/B rollback, Android foreground operation and recovery journal | Run the [GitHub OTA recovery matrix](development/ota-recovery-matrix.md); wrong image rejection; recovery to known working version; no false success |
| M6: beta hardening | Hardware/phone matrix, soak, accessibility, performance budgets, maintenance docs | Quality gates in [quality strategy](development/quality.md) recorded with artifacts |

## External crowd-sourced road alerts, proposed extension

**ALERT-EXT-001, discovery after M4.** Explore a phone-supplied road-alert layer inspired by JBV1's crowd-sourced alerts. This is separate from the gauge's autonomous OBD threshold and MIL/DTC alerts. The [architecture and feasibility note](architecture/external-road-alerts.md) compares direct BLE, Android/iOS companion, cloud microservice, and hybrid paths, with source access, licensing, phone lifecycle, privacy, radio coexistence, and stale-data gates. JBV1 integration itself is conditional on a documented, permitted alert export or agreement with its developer; no such interface has been verified.

| Phase | Scope | Exit criteria |
| --- | --- | --- |
| A: source and experience discovery | Confirm what JBV1 exposes, seek developer permission/API details, assess licensed alternatives, and prototype labeled road-alert states with synthetic data | Written source-rights decision; driver-reviewed hierarchy and expiration behavior; no public live-feed claim |
| B: local phone-to-gauge slice | Versioned, owner-authenticated external-alert GATT messages; Android first, then iOS parity, from a fixture or authorized source | Physical phone/gauge latency, reconnect, lock/background, stale/expiry, two-adapter coexistence, and local-alert precedence evidence |
| C: authorized live feed pilot | Phone fetches a permitted feed, filters by route/location and sends bounded nearby events; optional backend adapter only if source terms and operations justify it | Source agreement, measured availability/cost, privacy review, offline degradation, and road testing on both platforms |
| D: product decision | Evaluate phone-only versus hybrid service and supported regions/sources | Release gate passes without weakening M1/M4 autonomous alerts; otherwise retain as experimental |

## First engineering slice, completed in source

The branch contains bounded ELM response assembly, independent adapter contexts, owner-authenticated companion control, atomic configuration, local warnings, callback-safe status, reconnect recovery, and an independent application tick. The remaining part of this slice is hardware measurement with one and two real adapters plus the phone and display.

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
