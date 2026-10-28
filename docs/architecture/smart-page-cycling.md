# Smart page cycling

FEATURE-11 adds an opt-in modifier to saved dashboard page cycling. This contract
owns behavior and tuning; [current state](../current-state.md) owns installed and
qualified facts, and [milestone qualification](../development/smart-page-cycling-plan.md)
owns the remaining physical comparison. Auto orientation is independent.

## UX and selection

Settings > Auto-cycle pages retains Off / 5 / 10 / 15 / 30 / 60 seconds and adds
one **Smart** switch for firmware advertising `sp:1`. Off disables the switch while
retaining the preference. The summary adds **· Smart** only when an interval is
active. No extra gauge badge, reason text, confirmation or notification is added.
Configured dashboards advertise the capability; the built-in legacy demonstration
pages retain their original capability and ordinary cycling. Saving a dashboard
uses the configured-dashboard path.

Smart defaults Off. When enabled, typical data follows the saved order at the
configured interval T. A priority insertion may bring another saved page forward.
The ordinary cursor remains pending and must be served before another insertion.
Every saved page, including an unavailable page, remains in the rotation. With
stable configuration and uninterrupted normal-reading operation, every page gets
a turn within 2 × N × T; normal rotation remains N × T. Existing pauses and manual
interaction suspend this bound. One page never cycles or redraws itself.

Priority is the highest qualifying reading on a page, not a sum:

1. Eligible active threshold episode, ordered by the existing severity.
2. Sustained proximity to a configured numeric high/low threshold.
3. Sustained unusual thermal rise in known normal-running context.
4. A modest temperature preference in cold-running context.
5. Ordinary saved-order rotation.

Tie-break by oldest actual page display, then saved order. Treat alternate renderers
of one PID as one interest reason. When the strongest interest is already visible,
serve the ordinary cursor at its deadline. Do not extend dwell every time a sample
arrives. A new episode or higher priority can supersede an older, lower-priority
revisit cooldown; the global early-switch limit still applies.

The shared alert engine owns crossing/dwell/reset, availability, acknowledgement,
snooze and escalation. Smart consumes its episodes without producing new alerts.
Suppress only the matching condition's approach rule; an independent eligible alert
on that PID still wins. Generic trend/warm-up interest cannot bypass acknowledged
attention. Equality/gear alerts qualify through active episodes; no numeric distance
is invented. Existing critical attention remains the UI owner and pauses cycling.
MIL/CEL stays under its diagnostic/filter contract; no DTC-to-PID guess creates a page.

## Timing and context

`smart_page_policy.h` centralizes bounded timing/context constants:

| Rule | Value |
| --- | --- |
| Minimum automatic page viewing | 5 seconds |
| Protection after manual selection or pause exit | One full T |
| Global early-switch cooldown | 30 seconds |
| Same-interest revisit cooldown | max(30 seconds, 3 × T) |
| Non-alert persistence | Distinct fresh observations spanning at least 3 seconds |
| Interest evaluation | At most 5 Hz |
| Thermal rate window | 10 seconds, with half-rate/delta release hysteresis |
| Cold entry / warm exit hint | Below 60 C / at least 75 C for 30 seconds |
| Maximum cold-context bonus | 10 minutes, then unknown until a new session/confirmed restart |

An early switch additionally needs an available priority insertion, expired manual
protection, greater importance than the visible page, and either a new/escalated
warning or sustained movement toward a configured limit. Warm-up and trend alone
wait for the scheduled interval. The existing immediate alert presentation does not
wait for Smart.

Fresh positive RPM establishes running. Runtime-only evidence must advance; a frozen
positive counter is insufficient. Fresh zero RPM overrides runtime. Missing RPM,
runtime or coolant leaves context unknown, without adding queries. Normal warming
in cold-running context does not trigger the rising-temperature heuristic. Context
never changes actual alert thresholds, control guards or sample freshness.

`contracts/reading-catalog.json` owns optional `smartInterest` metadata. The existing
generator validates and emits `smart_metric_catalog.inc` in flash. Initial coolant
and JSS temperature hints use a 5 C approach distance, 0.2 C/s rise rate and 3 C
minimum rise, with synthetic replay coverage. These are presentation tuning values,
not engine operating/safety limits or physical calibration. Rate preferences also
require normal-running context; alert and approach behavior works without context.

Match full semantic definition: ID, service/PID, responder, canonical unit and
numeric decoder/range. Unknown/modified definitions retain active-alert priority and
ordinary cycling without guessed thermal meaning. Explicit polling/freshness
settings remain authoritative. Adding a rule requires validation and replay tests;
there is no expression interpreter, learned baseline or user tuning panel.

## Ownership, isolation and budget

`smart_page_policy.c` is a pure, deterministic selector. `main.c` remains the single
selection owner and applies its choice through the existing UI and BLE selection
path. It commits cursor/cooldown state only after successful UI application. Under
LVGL ownership it rechecks selected page, activity, current pause, owner and maintenance
state so a touch or newly visible attention cannot be overwritten by a queued choice.
Priority selection also rechecks the source generation and live sample availability.

`ui_source_sample` reads the existing bounded mailbox under its short sample lock.
It preserves original observation time and checks revision, generation, source
readiness, pause and configured age. The policy never renews timestamps or widens
alert/control/display ages. Distinct PID state clears on source generation change;
healthy siblings continue. A dual page can win for a fresh reading while a missing
sibling remains unavailable. Configuration activation restarts the scoped runtime.

The shared alert owner provides one nonblocking episode projection. Contention skips
that interest evaluation rather than invalidating sensor history; the ordinary
cursor still advances at its deadline. Ordinary UI callbacks
and BLE callbacks never wait for Smart computations or flash. Touch, actions,
pairing, maintenance, showcase and critical-attention pauses retain existing ownership;
resume starts a full interval and never catches up missed switches.

Workers receive a stable upcoming-page hint through an atomic byte, replacing the
fixed next-index hint only while Smart is active. Existing polling priorities,
source fairness, control deadlines, diagnostics reservation and backoff remain the
budget authority. The hint adds neither PIDs nor direct reads. Navigation uses the
latest valid cache immediately and never waits for a prefetch response.

State is fixed for at most 32 readings/eight pages, placed in PSRAM, with a compile-time
4 KiB combined selector/projection gate. No new task, task stack, framebuffer,
per-observation allocation or network dependency is introduced. Selection logs record
page/reason/PID/early/cursor and measured maximum policy time; physical CPU/stack and
loaded adapter margins still need qualification. Scores/cursors never write flash.

## Settings and compatibility

The implemented extension bytes, validation, revision readback and legacy behavior
are defined once in the [protected wire contract](../protocol/experimental-firmware-transfers.md#smart-page-cycling-settings).
Legacy `ds:3` and its state shape remain unchanged; new Apps negotiate `sp:1` before
using extended requests. New firmware preserves Smart during legacy display writes.
Old firmware keeps ordinary cycling. Public BLE capability flags remain disabled.

The legacy v3 NVS record remains readable on downgrade. Two small Smart sidecars
carry version/flags/reserved/revision. Commit an inactive sidecar first, then the
legacy record as its activation point; retain the previously matched sidecar until
success. Boot uses only a valid exact-revision match, otherwise Smart Off. RAM
publishes the new settings only after successful commits. Failed/uncertain writes
require authoritative readback, and stale revisions cannot be replayed blindly.
An old firmware settings write changes the revision and invalidates unmatched Smart
state on a later upgrade. Preserve all existing display settings and ownership.
