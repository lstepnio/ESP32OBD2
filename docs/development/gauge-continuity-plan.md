# Gauge continuity and polling plan

Proposed 2026-10-27. The owner reported blank readings after page taps, occasional
reading gaps, cycling that did not run at five or ten seconds, and a startup CEL
icon or intermittent question mark. This document owns the proposed changes and
acceptance sequence; [backlog PERF-01](../backlog.md) owns delivery status.

## Evidence and limits

The last verified physical image is dev.63. Published dev.64 already replaces the
ordinary idle Start confirmation with a deliberate hold and replaces routine
Starting/Return/verification copy with progress rings. That installation was
interrupted before transmission. Do not attribute old action screens to the new
renderer or change command guards to fix presentation.

The source audit at merged PR #99 identified:

- `ui.c` clears both visible samples and the sample queue on every page selection.
  Hidden readings have no retained display cache. This directly explains a blank
  first frame even if a background response was recently received.
- `ui.c` creates diagnostic and alert symbols visible before its first status tick.
  This can produce an unearned initial icon. It does not prove the cause of a
  longer-lasting lamp after a real ECU response.
- `page_action_tick()` expires gesture progress but not a pressed state whose
  release never arrives. `ui_action_pending()` then blocks cycling. Active action
  menus and the shared transfer gate also deliberately block cycling. None has yet
  been identified as the cause of this physical report.
- The scheduler admits diagnostics without reserving freshness headroom. A
  source-level timing experiment with five 500 ms PIDs, 100 ms successful reads,
  the worker's 100 ms delay and full diagnostic budgets produced a 2,600 ms
  successful-sample gap, exceeding common 2,000 ms freshness limits. This is a
  scheduling reproduction, not a measured adapter trace.
- Scoped parked-idle input reads separately request speed, RPM and coolant. Their
  successful payloads do not enter the normal UI sample callback. Periodic identity
  reads also consume the same serial adapter. More requests do not mean fresher
  displayed readings.
- A normal request failure immediately invalidates the display before the retry.
  The question mark instead represents incomplete diagnostic count coverage; its
  underlying MIL/code ages are already 60/120 seconds. Raising a numeric PID's
  timeout cannot fix that diagnostic presentation.

## Intended experience

A page change shows the newest valid sample immediately, without waiting for a
request. Tap, swipe, App selection and automatic cycling use the same path. A
missed refresh may retain the last good value within its display age budget;
page navigation never renews that budget. Actual unavailable data stays dashes,
never zero or invented interpolation.

The gauge shows no warning solely because it is booting or checking diagnostics.
A confirmed ECU warning remains an icon. Show a fault count only when that count
is trustworthy; remove `?` from the instrument face. Coverage and troubleshooting
belong in the App's existing Expert details, not a new routine gauge screen.

## 1. Bounded reading cache and startup presentation

The first source change implements the already requested small fix; it is not yet
installed. It replaces the pending-only display queue with one fixed-capacity
latest-value store. Source, PID, configuration revision and adapter generation
identify each sample. Original timestamps are retained; explicit invalid data,
source loss, maintenance and scope changes clear hidden as well as visible entries.
A retired callback cannot restore old data after a reconnect.

Warning widgets initialize hidden; the normal truthful status path makes them
visible. No arbitrary boot timer suppresses a confirmed fault. The fixed mailbox
replaces the old 32-entry FIFO. Target type sizes grow by 1,000 bytes while removing
768 bytes of FIFO payload plus queue overhead, limiting net dynamic growth to at
most 232 bytes. Physical latency and loaded-memory qualification remain pending.

## 2. Cycling as an explicit UI policy

Separate the interval/deadline from the reason cycling is paused. Record a bounded
reason enum in existing debug diagnostics: gesture, action page, attention,
maintenance, missing owner or insufficient pages. Ordinary polling and phone
status reads must not keep restarting the interval.

Handle lost touch/release and expire an impossible gesture after its recognition
window. Do not generate a tap or command when recovering it. Returning to readings
must clear the action-page pause. Choose one consistent resume rule: restart a
full configured interval after deliberate user interaction, without catch-up
bursts. During an actual held touch or foreground action, do not cycle underneath
its control. Treat an action still executing in the background separately from a
visible action screen, preserving access to Stop and critical attention.

Verify the App's saved interval by protected readback, then trace the device's
pause reason and next deadline. A successful settings write alone is not proof
that cycling runs. Test five and ten seconds before and after an action, source
loss, reconnection, alert dismissal, App disconnect and reboot.

## 3. Reading priority and age policy

Use the existing catalog/configuration fields first. Rates below are proposed
starting ranges, to be adjusted from measured adapter latency, not promised rates.
Separate display retention from alert/control freshness before changing limits.
Visibility changes polling priority, never the original timestamp or per-reading
retention budget, so entering a page cannot itself make a sample stale.

| Work | Proposed request cadence | Display retention | Rule |
| --- | --- | --- | --- |
| Stop and required control guard | Existing proven deadlines | No relaxed control freshness | Highest priority; preempt ordinary work at safe transaction boundaries |
| Visible RPM, speed, gear | 250-500 ms where adapter permits | About 2-3 s | Highest ordinary priority; preserve real changes |
| Other saved fast readings / next cycling page | 500-1,000 ms | Same per-reading budget as when visible | Keep all saved pages warm; next page gets a modest boost |
| Coolant and transmission temperature | 1-2 s normally | About 5-10 s | A thermal alert or running idle action raises sampling priority; its protection limit stays independent |
| Fuel level and slowly changing counters | 5-10 s | About 15-30 s | Conservative defaults, not a blanket policy for every pressure/voltage/custom reading |
| MIL / code lists | Existing slower cadence, accelerated on change | Separate diagnostic policy | Bounded background service; cannot monopolize a read cycle |

Custom PIDs need an explicit change-rate class and bounded age policy, with
conservative defaults and validated overrides. Do not derive safety from the
label/unit alone. Avoid a new normal-user tuning screen; reuse catalog/profile
metadata and keep measured rates in Expert.

One transport should serve one request at a time. Reuse a correctly attributed
successful sample for display, alerts and control consumers with their own age
requirements, rather than asking the same ECU repeatedly. Preserve responder,
source and session identity; a TCM sample must never satisfy an ECM guard.

Schedule by urgency, required deadline and measured transaction cost, with aging
so hidden readings and diagnostics cannot starve. Before a long diagnostic request,
refresh readings whose deadline it would cross and reserve its full budget plus
recovery time. Account completion time, not just dispatch time. Reduce unnecessary
fixed gaps only after confirming the adapter prompt/RX state is idle. Keep separate
ECM/TCM workers; one slow or absent child cannot consume the parent's budget.

Classify a transient timeout separately from explicit invalid data or source loss.
A transient miss may keep a previous good display sample until its original age
limit, with bounded retry and backoff. Explicit invalid replies, disconnected
sources and expired scopes still clear immediately. Control and alert consumers
retain their stricter failure behavior. At sustained overload, reduce low-priority
work first; do not keep extending displayed freshness indefinitely.

## 4. Stable diagnostics without the question mark

Keep the last coherent diagnostic snapshot while a refresh is in progress, within
its bounded source/session lifetime. Commit a new category snapshot atomically.
A new MIL state/count or reconnect invalidates incompatible filter proof; incomplete
coverage never proves all faults are filtered. Preserve the existing raw fault
history and distinction between acknowledgement, filtering and ECU clearing.

Remove the unknown-count glyph independently of poll-policy changes. A known MIL
with an unknown count is an icon without a number. Unknown status alone draws no
CEL icon. A fully reconciled set of filtered codes follows the existing suppression
policy. Add fixtures for startup, partial lists, known MIL, filtered-only lists,
new unfiltered faults, failed refresh and source replacement.

## Delivery and acceptance

Use small independently reviewable changes: cache/startup, cycling recovery,
shared sample reuse, budget-aware scheduling, then catalog/retention tuning and
fault snapshot UX. Do not combine all of them into an unmeasurable timeout bump.
No extra task, framebuffer, database or third-party dependency is expected.

Offline tests must charge real simulated transaction durations and cover overload,
failed reads, dual endpoints, timestamp wrap, retired sessions, hidden-page expiry,
missed release and every cycle pause/resume path. Preserve Stop priority and the
existing thermal/fallback tests. Measure fixed RAM and lock duration against the
current ESP32 budget; keep counters bounded and use existing health logs.

Then one parked Jeep session compares per-reading age/gap and request count before
and after, with tap/swipe/automatic page changes, one adapter removed at a time,
phone connected/disconnected, and brief owner-supervised action Start/Stop. Targets:
no blank first frame for a valid cached sample; no false lamp at initialization;
no `?` on the gauge; no stuck cycling pause; measured reduction in duplicate work
and age-budget misses without delayed Stop, diagnostic starvation or sibling loss.
Record real failures instead of labelling stale data as fresh. Capture physical
screen behavior separately from source/tests and update website visuals after the
qualified rollout under the existing daily publication rule.
