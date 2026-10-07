# CEL, trouble codes, and local alerts, draft 0.1

Owner scope added 2026-09-25: show CEL codes and allow clearing; configure attention-getting ESP32 threshold alerts from Android. These are designed capabilities, not features of the current baseline.

## Diagnostic data

Read MIL commanded status and readiness from standard Mode 01 PID 01, confirmed emissions DTCs through Mode 03, pending through Mode 07, and permanent through Mode 0A when supported. Keep status per adapter source and responding ECU and timestamp each read. The physical dashboard CEL and an old cached reading are not assumed to agree; show age and live MIL evidence. ABS, SRS, body and manufacturer-specific codes require documented module profiles and must not be advertised as standard ELM support.

Model `DtcRecord { sourceId, ecuId, code, category: confirmed|pending|permanent, observedAt, sessionId, descriptionSource, descriptionVersion }`. A code is a diagnostic lead, not a proven failed component. If description is unavailable show the code plus “description unavailable”; never fabricate a generic description for manufacturer-specific meanings. Store vehicle/session-local before/after snapshots. Deduplicate a code within one ECU/category, while retaining it in multiple categories if reported.

Gauge page shows CEL state, code count/category, and one readable code with short description. Tap advances, long press opens local actions. App offers full list, ECU/category filters, captured freeze-frame details where implemented, readiness and explanation/provenance. Freeze-frame retrieval is optional and separately scoped; clearing may remove it.

## Clear codes flow

Clearing uses standard Mode 04 for supported emissions diagnostics. It is a vehicle-changing operation, isolated from custom PID definitions and from the normal read scheduler.

1. Owner opens Diagnostics on the app or gauge. Fetch current MIL/DTC/readiness snapshot where available. Present which ECU/protocol scope will receive the clear request; Mode 04 cannot promise to clear an individually selected code.
2. Explain that clearing can erase emissions diagnostic/freeze-frame information and reset readiness monitors; it does not repair the cause. Permanent codes generally clear only after the vehicle confirms the fault is resolved. [California BAR guidance](https://www.bar.ca.gov/obd-test-reference) is the source for permanent-code behavior.
3. Require stationary, ignition-on/engine-off context: use fresh speed/RPM where supported and explicit user confirmation of ignition/parked state. Unknown vehicle state requires local physical confirmation on the gauge; do not infer “parked” from no data. Show a dedicated confirmation sheet and a 2-second hold action with an accessible alternative. On-gauge flow provides the same scope/consequences through sequential readable screens.
4. `dtc.clear.prepare` returns a one-time 30-second operation token bound to owner, vehicle session, adapter source, ECU scope and current conditions. `dtc.clear.confirm` consumes it once. Suspend other diagnostic requests, restore known adapter routing and issue clear. A duplicate token returns the recorded state and never automatically retransmits a clear after an ambiguous timeout.
5. Read back MIL, relevant DTC categories and readiness after an appropriate protocol settle time. Report **clear request acknowledged**, **codes remain**, **permanent codes remain**, **verification incomplete**, or **request failed**. Disconnect after sending gives `RESULT_UNKNOWN`; reconnect and read state instead of repeating the command. An empty code response is not by itself proof the vehicle is repaired or inspection-ready.

Firmware enforces authorization and preconditions, not just Android UI. Physical gauge actions are owner-present actions and must follow the same workflow. Clear cannot run during discovery/update/config commit. UI mockup only simulates this flow and cannot send vehicle commands.

## Threshold rules

Threshold rules are part of the atomic configuration document. Each binds a PID definition **and ECU**, canonical unit, comparator (`above`, `below`, or named-gear `equals`), warning/critical boundary, hysteresis, trigger dwell, clear dwell, and acknowledgment/snooze duration. Both levels are optional individually but at least one is required. For `above`, critical > warning; for `below`, critical < warning. Validate range and hysteresis so release thresholds remain meaningful. Changing display units converts editor labels and limits, never the canonical stored rule.

The on-gauge alert engine evaluates fresh valid samples independently of the phone and active page. Entry requires threshold violation continuously for trigger dwell. Exit requires samples beyond the release boundary (threshold minus hysteresis for above, plus for below) for clear dwell. Evaluate using monotonic elapsed time, not a fixed sample count. Any stale/invalid interval breaks pending entry/exit dwell and freezes the last active severity with a visible data-unavailable qualifier; stale data must never resolve a warning. On fresh recovery, restart dwell timers. At boot start as unknown until sufficient fresh data exists.

The source dev.46 editor exposes alerts for every selectable catalog reading,
including the calibration-specific transmission temperature and gear. Gear rules
match chosen positions and clear after a nonmatch dwell; numeric thresholds accept
fractions. Full catalog bounds, compatibility, source routing and physical follow-up
are in [reading catalog and local alerts](../development/reading-catalog-alerts.md).

## Attention and acknowledgment

Warning: amber border/badge, labeled value and threshold, one entry pulse, then steady display. Critical: full readable alert overlay with red border, large value/unit, explicit reason and page priority; no rapid flashing. Acknowledge restores the chosen dashboard with a persistent alert badge. If severity escalates or a new rule activates, interrupt again. A snooze suppresses repeated visual interruption for the configured period, not sampling, logging or the active badge. Provide reduced-motion option with steady visual cues. No onboard buzzer/haptic assumed; sound needs additional hardware.

Order multiple alerts by severity, then configured priority, then oldest activation. Show `1 of N` and allow inspection; enforce at most one overlay at once. MIL/DTC notification uses a separate engine-status indicator and event list, not a pretend numeric threshold. History records entry, escalation, acknowledgment, data loss and resolution with provenance. Bound retained events and batch flash writes. Configuration must warn when requested poll rates cannot support a requested short alert response time.

Acceptance: alerts work with phone disconnected, on hidden pages, under unit conversion, around noisy thresholds, with stale samples, after reboot, and with multiple simultaneous violations. Diagnostic clearing tests use fixtures/emulator first; a live vehicle clear is an explicitly chosen user action at the time, never an automated test.

## Implemented read-only snapshot, dev.38-faults

The active Engine or Transmission profile polls Mode 01 PID 01 and Modes 03,
07 and 0A against its selected responder (7E8 or 7E9). The protected companion
command `39 <sequence:u32le>` selects a cached full diagnostic snapshot. Reading
it does not initiate a vehicle scan. The scheduler populates categories separately;
initial categories remain Not checked until their first accepted reply.

Version 15 is 248 bytes. Header (32 bytes):

| Offset | Field |
| --- | --- |
| 0 | Version 15 |
| 1 | Source: 0 Engine, 1 Transmission |
| 2 | Flags: bit 0 MIL fresh, 1 MIL on, 2 connected, 3 simulated |
| 3 | MIL result: 0 not checked, 1 available, 2 unavailable, 3 unsupported |
| 4 / 8 | Configuration revision / connection session, u32le |
| 12 / 16 | Snapshot / MIL observation monotonic milliseconds, u32le |
| 20 | Responding ECU, u16le |
| 22 / 23 | MIL reported count / known flag |
| 24..31 | Reserved zero |

Stored, Pending and Permanent blocks start at 32, 104 and 176. Each is 72 bytes:
result (same enum), flags (known, fresh, truncated), count (0..32), reserved zero,
u32le observation time, then 32 two-byte big-endian DTC slots. Unused slots must
be zero. Firmware never silently truncates: its 64-byte ISO-TP payload bound
currently permits at most 31 codes after service/count bytes. Oversized or malformed
responses are unavailable. Codes are deduplicated within each category only.

Freshness requires connection, real data and a successful most recent query.
MIL expires after 60 seconds, categories after 120 seconds. Android additionally
expires its receipt after 30 seconds and verifies source, ECU and configuration
revision. Unsigned time subtraction handles the gauge clock wrapping. Failure or
disconnect retains known codes as last checked; reconnect clears them for a new
session. Empty accepted lists are distinct from unknown or unavailable replies.
Explicit DTC negative replies 11, 12 and 31 are unsupported. Other failures,
including unsupported MIL queries, currently appear unavailable.

Version 5 / command 30 remains an Engine-only partial snapshot. New Android falls
back only on explicit unsupported command/length errors (GATT 6 or 13). Auth,
transport or packet validation failures never trigger fallback. New firmware
blanks version 5 Transmission data to prevent old apps labelling it as Engine CEL.
The owner identity gate remains required; public capability flags are unchanged.
Only the active source is sampled. The other source is Not checked.

This implementation reads codes only. The clearing flow above remains a design.
Physical qualification still requires the 248-byte long read on Pixel, category
coverage on the gauge and disconnect/reconnect checks with the real adapter.

## Independent development sources, dev.41

Each configured adapter has its own connected state, session, MIL and three fault
categories. Disconnect clears freshness only for that source; reconnect increments
its session and clears old categories. Source-specific `3A` reads keep the v15 layout;
`39` remains source zero. See [source selection](adapter-bindings-v1.md).
Snapshot lock contention yields a retryable resource error, not empty healthy data.
The app reads disconnected snapshots as last checked and keeps separate poll backoff.
No fault-clearing command is added.
