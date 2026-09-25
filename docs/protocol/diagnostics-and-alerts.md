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

Threshold rules are part of the atomic configuration document. Each binds a PID definition **and ECU**, canonical unit, comparator (`above` or `below`), warning/critical boundary, hysteresis, trigger dwell, clear dwell, and acknowledgment/snooze duration. Both levels are optional individually but at least one is required. For `above`, critical > warning; for `below`, critical < warning. Validate range and hysteresis so release thresholds remain meaningful. Changing display units converts editor labels and limits, never the canonical stored rule.

The on-gauge alert engine evaluates fresh valid samples independently of the phone and active page. Entry requires threshold violation continuously for trigger dwell. Exit requires samples beyond the release boundary (threshold minus hysteresis for above, plus for below) for clear dwell. Evaluate using monotonic elapsed time, not a fixed sample count. Any stale/invalid interval breaks pending entry/exit dwell and freezes the last active severity with a visible data-unavailable qualifier; stale data must never resolve a warning. On fresh recovery, restart dwell timers. At boot start as unknown until sufficient fresh data exists.

## Attention and acknowledgment

Warning: amber border/badge, labeled value and threshold, one entry pulse, then steady display. Critical: full readable alert overlay with red border, large value/unit, explicit reason and page priority; no rapid flashing. Acknowledge restores the chosen dashboard with a persistent alert badge. If severity escalates or a new rule activates, interrupt again. A snooze suppresses repeated visual interruption for the configured period, not sampling, logging or the active badge. Provide reduced-motion option with steady visual cues. No onboard buzzer/haptic assumed; sound needs additional hardware.

Order multiple alerts by severity, then configured priority, then oldest activation. Show `1 of N` and allow inspection; enforce at most one overlay at once. MIL/DTC notification uses a separate engine-status indicator and event list, not a pretend numeric threshold. History records entry, escalation, acknowledgment, data loss and resolution with provenance. Bound retained events and batch flash writes. Configuration must warn when requested poll rates cannot support a requested short alert response time.

Acceptance: alerts work with phone disconnected, on hidden pages, under unit conversion, around noisy thresholds, with stale samples, after reboot, and with multiple simultaneous violations. Diagnostic clearing tests use fixtures/emulator first; a live vehicle clear is an explicitly chosen user action at the time, never an automated test.
