# Smart page cycling milestone

**Proposal, not implemented.** Owner review requested 2026-10-27. FEATURE-11 in
the [backlog](../backlog.md) owns status. This document owns the proposed behavior,
UX, implementation sequence and acceptance; existing runtime contracts remain
authoritative until implementation. No release or device change is authorized here.

## Product decision

Add one **Smart** switch to the existing Auto-cycle pages setting. Keep its interval
and saved page order. Under ordinary conditions the gauge follows that order at
the configured interval. When a configured reading deserves attention, it can
bring a page containing that reading forward, then resume the ordinary rotation.

Owner decisions:

- Early switches are allowed with a minimum viewing time.
- Beyond active alerts, prioritize temperatures and unusual changes.
- Reuse the high/low thresholds already configured in the App and firmware.
- Keep the user-facing configuration simple; tune the policy centrally in code.

Recommendation: deterministic rules with explicit priority bands, not a learned
anomaly model or a single opaque score. This is presentation guidance, not fault
diagnosis, a new alert engine, or vehicle control. The principal risk is an
unpredictable display: normal warming, acceleration and noisy sensors must not
continually steal attention. Conservative defaults and replayable decisions are
more valuable than a large number of heuristics.

## App and gauge experience

Use the existing Settings > Auto-cycle pages editor, shared controls and save path.

```text
Auto-cycle pages

Interval                   Every 10 seconds
Smart                                  [on]
Prioritize alerts and changing readings.

                         Save to gauge
```

- Retain Off / 5 / 10 / 15 / 30 / 60 seconds. The interval picker above is a compact
  design sketch; reuse the existing radio choices rather than adding another step.
- Show Smart in the same editor, below the interval. Default Off for existing and
  new gauges. Turning it on does not require configured alerts; their presence
  improves its decisions. Do not add a warning or setup wizard when none exist.
- With interval Off, disable the Smart switch and retain its saved preference.
  Copy: **Choose an interval to use Smart.** No cycling or interest sampling while Off.
- Settings summary: **Every 10 seconds · Smart** when active; otherwise the current
  summary. With one saved page, retain the preference but never switch or repeatedly
  redraw that page. No persistent status message for this case.
- Save interval and Smart together through one acknowledged operation. Read both
  automatically on connection. Preserve per-gauge scope, revision conflict handling,
  cancellation, and the current saved state if a write fails.
- Older firmware keeps the current editor without a working Smart switch. Do not
  pretend the phone alone can provide autonomous Smart behavior.
- Gauge faces, page dots and manual page order stay unchanged. No Smart badge,
  reason banner, extra sound or extra confirmation. Existing alert presentation
  explains an alarm; debug reasons belong in Expert/support data only.
- A tap/swipe selects the next/previous saved page relative to the displayed page.
  A completed manual selection starts one full interval of protection from Smart.
  Resume the ordinary cursor after that selected page. Touch holds and existing
  action, critical-attention, pairing, showcase and maintenance pauses still win.

## What counts as interesting

Only saved, enabled reading pages are candidates. A PID available in the catalog
but absent from the dashboard does not cause a page to be created or a new query.
Hidden-page alerts continue to work through the existing alert framework.

| Priority | Input | Proposed result |
| --- | --- | --- |
| Existing critical attention | Shared alert UI owns attention | Keep its current presentation/pause; Smart never competes with it |
| Active alert | Existing eligible warning/advisory for a mapped PID | Highest page priority, ordered by existing severity; new warning or severity escalation may qualify for an early switch |
| Approaching a configured limit | Fresh numeric value within a validated approach band | Prefer the associated page; sustained movement toward the limit can qualify for an early switch |
| Unusual thermal change | Explicit catalog/profile rule, fresh multi-sample trend | Prefer at a scheduled decision; do not create an alarm from the heuristic |
| Cold-running context | Available engine-running evidence and qualified thermal context | Modest temperature-page preference during warm-up, never an alarm |
| Ordinary readings | Everything else | Existing saved-order rotation |

Use the alert engine's state, severity, dwell, reset/hysteresis, availability,
acknowledgement and snooze semantics. Do not re-evaluate high/low crossings in a
second engine. A snoozed or acknowledged episode must not regain attention through
the approach/trend rule for that same condition; escalation follows the existing
alert framework. Reading it on screen does not acknowledge or clear it.

For approach priority, evaluate only numeric above/below limits that have a positive
explicit approach distance, in canonical units. Use the configured warning boundary
when present, otherwise critical. Example: a high coolant limit of 110 C with a
reviewed 5 C approach band starts preference at 105 C, before the existing alarm
fires. A low limit reverses the direction. These numbers illustrate presentation,
not recommended engine limits. Gear/equality conditions get active-alert priority,
not an invented numeric distance. Handle high and low rules independently.

Never derive approach distance from an arbitrary graph range or percentage of a
Celsius/Fahrenheit value. Seed per-reading distances only for reviewed catalog
entries. Custom PIDs without metadata still get full active-alert priority and
normal cycling; they do not receive guessed thermal/rate behavior from their label.

Page rank is the **highest** eligible PID rank on that page, not a sum. A dual page
does not beat a numeric page merely by containing more readings. Break ties by
oldest actual display, then saved order. If two pages show the same PID, use that
tie-break and treat them as one interest reason for cooldown. Do not bounce between
alternate renderers of the same signal.

MIL/CEL remains under the existing diagnostic/filter contract. Do not invent a
numeric PID page for a generic fault or infer a specific failed sensor from a DTC.
A future dedicated diagnostic page can supply an explicit mapping through this same
selector. No startup-unknown, filtered fault, expired event or absent-source status
becomes a new Smart warning.

## Timing and fairness policy

These are proposed initial tuning values, subject to replay and owner review.
`T` is the configured interval; time arithmetic is monotonic and wrap-safe.

| Parameter | Initial proposal | Purpose |
| --- | --- | --- |
| Minimum time on any automatically selected page | 5 seconds | Matches today's shortest interval; no rapid flips |
| Manual-selection protection | Full T after selection or pause exit | Respect the driver's choice |
| Early-switch cooldown | 30 seconds globally | At most one Smart early interruption per 30 seconds |
| Same-reason revisit cooldown | max(30 seconds, 3 × T) | Stop a persistent condition monopolizing the display |
| Priority insertion budget | One before the next ordinary page | Keep every saved page in rotation |
| Non-alert candidate persistence | At least two distinct fresh samples spanning 3 seconds | Reject single spikes; repeated evaluation of one sample does not count |
| Interest evaluation | At most 5 Hz; update trends only on new observations | Bound CPU work without adding vehicle queries |

Maintain a saved-order **ordinary cursor**, separate from the displayed page. At
startup or manual selection it points to the following saved page. At a decision:

1. Honor all current pause reasons. Do not accumulate missed switches or replay them.
2. Remove invalid/stale/ineligible interest. Rank the remaining mapped pages.
3. If the ordinary cursor page is itself the best candidate, show it normally and
   advance the cursor. This consumes no extra insertion.
4. Otherwise, when an insertion is available, choose an eligible priority page and
   consume that insertion. Keep the ordinary cursor unchanged. The next automatic
   switch must serve the cursor before another priority insertion is possible.
5. With no useful candidate, follow the ordinary cursor at T, exactly as today.

Scheduled switches occur at T. An early switch additionally requires the five-second
viewing minimum, expired manual protection and global cooldown, an available
insertion, a candidate strictly more important than the currently displayed page,
and either a new/escalated warning or sustained approach toward a limit.
Warm-up and trend-only preferences wait for T. Alerts retain their independent
immediate attention behavior; Smart is not the mechanism for delivering an emergency.

When the best candidate is already visible, keep that page until its normal deadline;
do not reset the clock on every new sample. At the deadline serve the next ordinary
page. Repeated reports of the same episode do not manufacture new priority events.
If the cursor points to the currently displayed page, credit that visit and advance
once to avoid a redundant redraw. Commit selector state only after UI selection
succeeds; a failed LVGL lock must not consume a visit or insertion.

Under stable configuration, uninterrupted normal-reading operation and no existing
attention pause, every saved page is served within at most **2 × N × T**, where N
is the page count. Typical conditions retain the original N × T rotation. Manual
interaction and existing attention/maintenance pauses suspend this bound; do not
claim a wall-clock guarantee during those pauses.

Example, T=10 seconds, saved order RPM / Coolant / Transmission / Voltage:

| Time | Display | Reason |
| --- | --- | --- |
| 0 s | RPM | Ordinary rotation |
| 6 s | Transmission | New eligible high-temperature warning, after minimum dwell |
| 16 s | Coolant | Ordinary cursor resumes; priority cannot starve it |
| 26 s | Transmission | Its ordinary saved-order turn |
| 36 s | Voltage | Normal continuation; no redundant warning re-entry |

## Cold-running versus normal context

Context modifies low-level interest, never alert thresholds, freshness or command
guards. Do not hardcode one vehicle's safe temperatures into a universal policy.

- Detect running only from already available, fresh, semantically identified engine
  RPM or supported engine-runtime evidence. A phone connection is not engine state.
- Define `unknown`, `cold_running`, and `normal` context. A proposed reviewed coolant
  profile may enter cold_running below 60 C and leave above 75 C for 30 seconds;
  both values are tunable context hints, not assertions of operating temperature.
  No universal transmission warm-up threshold is inferred from coolant.
- Limit a cold-running preference session to ten minutes. If temperature never
  reaches its exit condition, remove the warm-up bonus rather than assert normal.
  Re-arm only after a confirmed engine stop/restart or a new source session; do not
  oscillate between phases as values cross one boundary.
- During cold-running, give a modest temperature preference but suppress the
  positive warming-rate heuristic. Configured high/low alerts still win immediately
  through their normal lifecycle, even when their trigger is expected at cold start.
- During normal operation, a sustained unusual rise can increase interest. During
  unknown context, use alerts and validated approach bands, but no guessed warm-up
  bonus or context-dependent trend rule.
- Missing context PIDs do not add mandatory polls or block Smart. An unconfigured
  RPM/coolant reading simply reduces context confidence. Explicit disconnect/session
  change clears affected trend/context state; healthy sibling readings continue.

Keep thresholds, change rates, direction, persistence and phase modifiers in a
small declarative policy table. Extend `contracts/reading-catalog.json` with optional
presentation-interest metadata and generate a flash-resident firmware table via
the existing catalog generator. Key it by semantic reading identity and definition
match, not display name, raw PID alone, or ECM/TCM transport position. Unknown or
modified definitions degrade to alerts plus ordinary cycling.

First release: reviewed coolant and supported transmission-temperature rules,
including approach distances, meaningful delta/rate and release hysteresis. Exact
rate cutoffs remain a replay-calibration task before enabling those rules. Numeric
metadata must be finite, dimensionally valid and bounded; missing metadata means
no heuristic. Firmware policy constants own timing/fairness. A later vehicle-profile
override can use the same typed table with validation, without adding advanced
sliders or a generic expression interpreter to this milestone.

## Architecture and compatibility

Source review baseline: main `8666c35`, which includes the continuity work.

| Boundary | Reuse / bounded change |
| --- | --- |
| Pause and timer | `src/page_save_policy.c` retains pause, full-interval resume and Off behavior; expose remaining eligibility to a small pure `smart_page_policy` module |
| Selection owner | `main.c` App task applies one selected-page decision through the current LVGL/page-position/BLE selection path; no second navigation owner |
| Samples | Extend the existing scoped latest-value mailbox access with a bounded read-only projection; preserve source, revision, generation, observation time and invalidation |
| Alerts | Project shared alert episodes to runtime PID/page identities; no new alert lifecycle or notification producer |
| Polling | Workers currently prefetch `(selected + 1)`. Publish one stable upcoming-page hint from the selector so the existing priority scheduler can prefetch the actual candidate |
| Settings | `display_settings.c`, `GaugeProtocolCodec.kt`, `GaugeConfigTransferClient.kt`, presentation state and `SettingsScreen.kt` gain one persisted Boolean and protected readback |
| Catalog | Existing generator supplies reviewed optional interest metadata; preserve custom definitions and explicit polling timing |

Use valid cached data immediately on a switch. Interest evidence must satisfy its
own conservative freshness requirement, no looser than the relevant configured
alert/sample age; display retention alone cannot justify new interest. A dual page
may win for its fresh reading while an unavailable sibling still shows dashes.
Ordinary rotation still includes unavailable pages so missing sources are not hidden.

The prefetch hint is advisory, stable through candidate persistence, and never adds
requests outside configured readings, blocks navigation waiting for a response, or
raises polling above existing budgets. Keep control deadlines, diagnostics reservation
and source fairness unchanged. Recheck the candidate against current configuration
and sample generation immediately before selection. Do not switch based on retired
vehicle data, and never reset a sample timestamp to make the first frame look fresh.

No new RTOS task, dynamic allocation per observation, framebuffer, unbounded history,
network service or AI dependency. Proposed extra-state budget: at most 4 KiB fixed
PSRAM and 256 bytes internal state, no new task stack. Keep bounded trend statistics
for at most 32 readings and visit state for at most eight pages. Measure layout,
stack margin and selection time; target under 1 ms per decision on the reference
board, rather than claiming it before profiling. Log reason changes/selections only.

### Proposed settings extension, not a wire allocation

The existing codec accepts only `ds: 0..3`; simply advertising `ds: 4` would break
older Apps. Prefer an additive optional Smart capability and explicit extended
read/write request on the existing protected transport, retaining `ds: 3`, the
legacy state shape, and existing command meanings. Allocate exact opcodes/state
tags only after a collision audit and encode them in shared fixtures.

- Extended state includes interval, Smart preference, settings revision and policy
  version. Write carries interval + Boolean + expected revision. Reject malformed,
  unknown flags and stale revisions; verify exact saved values and next revision.
- Legacy writes on new firmware preserve the Smart preference; interval Off makes
  it inactive. New App on old firmware remains usable with ordinary cycling.
- Preserve the readable legacy NVS record. Evaluate two small revision-matched
  Smart sidecar slots: write/commit the next sidecar first, then commit the legacy
  display record as the activation point. Retain the previous matching slot until
  success so a torn write cannot destroy the previously committed preference.
  Boot uses a valid sidecar only when its revision matches the authoritative record;
  absence/mismatch defaults Smart Off. No reserved-zero field is silently repurposed.
- Old firmware ignores sidecars. If it changes legacy settings, a later upgrade
  defaults unmatched Smart state Off. Verify downgrade/upgrade and power-loss cases
  before settling the storage implementation; do not promise cross-key atomicity.
- Automatic page choices and scores remain volatile and never write NVS. Reboot
  starts fresh interest history; it does not restore a previous alert preference.
- Preserve public capability gates and Auto orientation independence. No configuration
  document schema change is needed for the initial catalog-backed policy.

## Implementation sequence and review gates

| Step | Deliverable | Effort / main risk |
| --- | --- | --- |
| 1. Approve behavior | This proposal plus Settings states and replay timeline examples | S; subjective switching comfort and tuning values |
| 2. Pure selector and replay | Deterministic timing, priority, ordinary cursor, cooldowns, context, reason records; initially no runtime hook | M; fairness or repeated-event bugs |
| 3. Settings compatibility | Negotiated protocol, storage migration, exact readback, one App switch and old/new matrix | M; mixed-version and interrupted writes |
| 4. Firmware integration | Bounded sample/alert projections, generated thermal metadata, stable prefetch hint; Smart Off parity | M; locking, stale scope, poll feedback and memory |
| 5. UX and indoor qualification | Settings screenshots, native gauge sequences and replay against compiled production policy | M; simulation cannot prove physical legibility/comfort |
| 6. Loaded qualification | One planned Jeep session after offline acceptance, compare fixed/Smart under matched load | M; noisy or slow data, actual driver preference |

Keep each step a small logical commit or focused PR. Introduce no new dependency.
Create a short ADR only once behavior/compatibility choices are approved; link this
proposal rather than duplicate its tables. On delivery, move enduring rules into
the existing runtime/catalog/protocol contracts, and retire completed planning text.

## Acceptance and test design

- **Off parity:** exact saved-order decisions, pause/resume timing, manual navigation,
  source clearing and no extra polling versus existing policy with Smart Off.
- **Timing/fairness:** 5/10/15/30/60 seconds; 0/1/8 pages; 32 readings; manual touch,
  prolonged holds, missed releases, all pause reasons, monotonic wrap, UI-lock failure,
  repeated/simultaneous/escalated alerts and two pages showing the same PID. Assert
  minimum dwell, early-switch cooldown, no duplicate redraw and the 2NT bound.
- **Thresholds:** above/below/equality; warning/critical; no threshold; partial rule;
  acknowledgement/snooze; escalation; boundary noise; changing limits. Use existing
  alert episodes and test exact page attribution, not duplicate threshold logic.
- **Context/trends:** cold start, warm restart, normal warming, unexpected thermal
  rise, falling temperature, missing RPM/coolant, long pauses, single spikes,
  irregular samples, unit changes, invalid numbers and unqualified custom metadata.
  The same canonical samples produce identical choices in Metric and Imperial.
- **Isolation:** ECM/child-TCM independent loss and return, shared-primary routing,
  retired revision/session, profile change, page deletion/reorder, and candidate
  expiry between ranking and display. Reset only affected scope; no source favoritism.
- **Compatibility/recovery:** old/new App and firmware, corrupt/absent sidecar,
  stale expected revision, torn storage at each commit point, lost acknowledgement,
  phone death, reconnect and reboot. Exact readback is required before success.
- **Performance:** record decision durations, fixed memory/stack delta, PID ages,
  request counts, timeout/backoff behavior, diagnostic progress and control deadlines
  versus fixed cycling using identical replay inputs. Smart must not degrade the
  existing loaded continuity/Stop/thermal-protection acceptance.
- **UI:** toggle label/summary, disabled/unsupported state, restored setting, TalkBack,
  large text, cancellation/conflict and ordinary user flow in managed emulator.
  Native frames preserve circular layout, existing alert attention and cached first
  frames. Physical tests separately confirm readable dwell and predictable touch.

Start with source-scoped synthetic replays for normal cruise, cold warming, rising
coolant toward an existing limit, persistent warning, two competing temperatures
and one missing adapter. Show an explainable timeline with selected page, reason,
sample age and next ordinary cursor. Use existing private captures where available;
never call them live qualification. Validate thermal tuning against labelled traces
before enabling heuristic entries.

The Jeep session observes normal operation; do not induce overheating or vehicle
faults to test ranking. Use indoor synthetic production-policy tests for those
conditions. Compare owner-approved fixed/Smart sequences and record comfort feedback
separately from timing/heap evidence. Release and App/Wi-Fi installation remain
separate owner-authorized steps.

## Milestone completion and scope limits

Done means one persisted, acknowledged switch; autonomous operation without the
phone; ordinary timing/order with typical data; prioritized existing alert PIDs;
bounded, explainable temperature/context behavior; all pause/fairness/isolation
tests; and measured loaded acceptance. A source/CI pass alone is not completion.

First version excludes automatic page creation, learned baselines, route/driver
profiling, automatic alert threshold changes, polling every catalog PID, bespoke
per-page controls, cloud scoring and new vehicle commands. General rate heuristics
for RPM/load/voltage can follow using the same metadata only after evidence shows
they help. Website impact: add Smart and current App/circular-gauge visuals after
qualified delivery under the existing daily maintenance policy, not as implemented
support while this remains a proposal.
