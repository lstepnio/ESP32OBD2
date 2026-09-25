# Product requirements, draft 0.1

Owner decision on 2026-09-25: support **all** of everyday/engine-health/off-road, performance, and diagnostics/custom manufacturer PIDs. Product name “eGauge” is a working name.

## Outcomes

1. A configured gauge starts and shows fresh vehicle values without a phone.
2. A new owner can associate a gauge, select an adapter, discover available data, and apply a useful dashboard without understanding hexadecimal requests.
3. An experienced owner can inspect ECU responses, import a documented vehicle profile, define a bounded custom decoder, and understand its confidence and polling cost.
4. A user can update firmware with visible progress, authenticated images, and a defined recovery path.
5. Both interfaces explain unavailable data and incomplete operations instead of presenting success prematurely.

## Scope

| Area | Initial product requirements | Later extension |
| --- | --- | --- |
| Vehicles | Multiple local vehicle profiles; standard OBD support discovery per ECU | Curated manufacturer catalogs with explicit compatibility/license provenance |
| Adapter | Gauge scans, owner selects ECM/TCM sources, capabilities checked, remembered identities reconnect | More tested BLE UART profiles and firmware quirks |
| PID explorer | Search/filter by ECU, support, service, category, freshness; inspect raw sample, rate, decoder | Mode 06 test results and module-specific diagnostics as separately modeled features |
| Custom data | Signed/unsigned extraction, endianness, scale/offset, units, range, test vectors; bounded Mode 22 reads | Explicit protocol plugins for vehicles needing extra session handling |
| Dashboards | Numeric, arc, bar, trend, dual value; pages, units, rotation, brightness, warnings | Multiple physical gauges and custom sensors |
| Configuration | Offline drafts, device compatibility check, preview, atomic apply, revision conflict handling, export/import | Shared profile distribution |
| Updates | Signed release metadata + signed firmware, A/B boot slots, pause/retry and rollback | Negotiated Wi-Fi bulk transfer subject to coexistence/throughput measurements |
| Android | Kotlin/Compose; phone, tablet and foldable layouts; screen-reader and large-text support | Play distribution and wider legacy Android support |

No universal PID availability claim. Manufacturer-only values require a compatible definition and a responding ECU. EV, diesel, transmission, and chassis telemetry are modeled as possible profiles, not promised universal capabilities. CEL/MIL status, confirmed/pending/permanent DTC display, and explicit code clearing are included. See [diagnostics and alerts](protocol/diagnostics-and-alerts.md). No ECU programming, actuator control, security access, or arbitrary terminal commands in v1.

## Core user journeys and acceptance

- **First setup:** nearby gauge list -> physical identity confirmation -> encrypted association -> adapter selection on gauge -> capability discovery -> suggested pages -> preview -> apply. Rejected permissions, no adapters, multiple gauges, and interrupted setup have explicit retry paths.
- **Discovery:** visible scope and query estimate -> progress per ECU/range -> partial results immediately -> pause/cancel -> resume remaining work. A timeout is “no response,” not “unsupported.”
- **Dashboard design:** choose data and renderer -> adjust range/units/warnings -> preview fresh/stale/alert states -> validate device limits -> apply -> wait for durable device revision acknowledgment. An offline draft stays marked “not applied.”
- **PID lab:** import/create definition -> validate format and bounds -> decode a pasted response -> inspect expected vs actual -> request a paced live sample when connected -> save evidence with vehicle and ECU provenance. Imported requests remain inactive until explicitly enabled.
- **Update:** compatibility and available storage -> release notes -> download/verify -> owner starts maintenance -> transfer -> verify on device -> reboot -> confirm healthy version. A completed upload is not a completed update.

## Targets, subject to measurement

Gauge UI 30 fps goal, independent of PID sample rate; routine touch feedback <100 ms. Phone interactions aim for <100 ms local feedback. Reconnect with bounded exponential backoff 1/2/4/8/16/30 seconds plus jitter, cancelled when user disconnects. No guaranteed per-PID rate: schedule conservatively, publish achieved rate and age. Initial budgets: 8 pages, 2 readings/page, 32 enabled PIDs, 64 KiB configuration document. Firmware can advertise lower limits; app respects negotiated limits.

Performance mode prioritizes selected fast-changing channels. Off-road mode prioritizes thermal/load trends and readable warnings. Diagnostics mode favors provenance, raw samples and search. All three share the same data and configuration models.

## Open decisions

App minimum Android 10/API 29 is a proposal; confirm against actual phones. Choose product-wide license before inviting external contributions. Confirm target vehicles and adapter models through recorded sessions. Validate display units, accessible palettes, glove interaction, and enclosure orientation on the physical 1.28-inch display. None of these prevent the protocol and UI design work here.

## Diagnostics and threshold addition

Read CEL/MIL and trouble-code categories on both gauge and app. Allow explicit code clearing with consequences, vehicle-state checks and verified readback. App-configured warning/critical rules run on the gauge without the phone; include hysteresis, dwell, freshness, prioritization and acknowledgment. The [dedicated contract](protocol/diagnostics-and-alerts.md) defines these behaviors.

## Swapped vehicle with two interfaces

Support ECM and TCM through separate BLE adapters, as well as multiple ECUs behind a single adapter. Preferred topology is two adapter links plus the phone. Capability, scheduling, source-specific freshness, and fallback remain a required [feasibility investigation](architecture/multi-adapter.md). Connection switching is a tracked fallback TODO, not an implemented promise.
