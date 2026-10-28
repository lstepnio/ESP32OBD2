# Gauge continuity implementation and qualification

Approved 2026-10-27. [Backlog PERF-01](../backlog.md) owns delivery status.
The owner reported blank readings after page taps, intermittent gaps, five/ten-second
cycling that stopped, and a startup CEL or intermittent question mark. Source
implementation is described below. Installed identities and current qualification
belong in [current state](../current-state.md). The later startup CEL investigation
retains warning/filter proof rules while accelerating unanswered initial scans;
source changes are not evidence of a new physical installation.

## Decisions and implemented boundaries

- A fixed 32-reading mailbox replaces the pending-only FIFO. Page entry uses the
  latest valid original-age sample immediately. Source, PID, revision and generation
  stay scoped; hidden samples also clear on disconnect, pause or invalidation.
- Cycling uses an explicit pause reason and full-interval resume. Routine background
  command ownership no longer resets the timer. Lost releases cancel recognition
  without firing an action; genuinely visible controls and critical attention pause
  cycling. This fixes source-level failure paths, not a proven physical diagnosis.
- Serial adapter workers share successful numeric replies from idle guard reads
  with display, alerts and ordinary polling cadence. Identity responses never become
  readings. Exact service/responder/session attribution is checked before decoding.
  Guards retain their own reads, deadlines and failure handling.
- Scheduling uses urgency, visible/upcoming-page priority, bounded round-robin
  fairness and completion timestamps. Diagnostics reserve full timeout/recovery and
  upcoming guard/identity costs, pre-refreshing at-risk readings once. Impossible
  windows still permit diagnostic progress and increment explicit counters.
- Catalog classes govern display retention independently of alert/control ages.
  Existing custom definitions and explicit timing overrides retain their settings.
  A transient timeout does not fabricate a sample or reset its age. Explicit invalid
  replies and source loss still clear it. No wire schema, App migration or saved
  configuration rewrite is required.
- The gauge retains compatible last-successful diagnostic presentation only within
  existing age limits. Raw failed-attempt evidence remains available. Changed MIL
  state/count invalidates filter proof, reconnect clears it, and partial/unsupported
  coverage cannot prove faults disappeared. The face omits unknown/partial counts;
  a confirmed warning still shows its icon. New status widgets begin hidden.

The authoritative runtime behavior is in [firmware ownership](../architecture/firmware-runtime.md#polling-diagnostics-and-alerts),
[reading classes](reading-catalog-alerts.md#display-and-polling-classes) and
[diagnostic semantics](../architecture/alerts-and-diagnostics.md). Extend these
contracts and their tests rather than duplicating policy in UI consumers.

## Offline evidence

Production native fixtures exercise original-age cache/page entry, invalidation,
retired source/revision callbacks, lost release, five/ten-second pause/resume,
timestamp wrap, source isolation and unknown-count visibility. Duration-charged
scheduler tests cover overload, two endpoints, all code categories, per-PID backoff,
shared-sample accounting and 32-reading fairness. Diagnostic/filter tests cover
failed refresh retention/expiry, changed MIL/count, unsupported/partial replies,
new faults and reconnect. Existing command replay retains Start/Stop, thermal,
fallback timer, restoration and sibling isolation checks.

Normal and parked LVGL/configuration fixtures and the host checks pass. Firmware
builds retain the existing memory gate. The cache grows net dynamic allocation by
at most 232 bytes after removing the old FIFO payload, before saved queue metadata.
Two 376-byte schedulers are fixed in PSRAM instead of worker stacks; the larger
policy array replaces the old interval array. No new task, framebuffer, unbounded
history, dependency or public capability is added. Final build/CI status belongs
to the PR; these results are not physical timing or OOM qualification.

## At home: installation and indoor checks

Dev.65 installation, five Wi-Fi lifecycles, automatic/Bluetooth recovery and
same-setup restart passed on 2026-10-27. The owner confirmed clean startup/action
navigation and ten-second cycling/resume. Exact identities and evidence limits
are in the [rollout record](../evidence/action-pages-indoor.md#2026-10-27-dev65-continuity-and-action-ui-rollout).
The matrix below remains the reusable broader acceptance procedure; partial
indoor coverage does not establish loaded-adapter behavior or all pause reasons.

After CI, authorized merge/publication and App/Wi-Fi installation:

1. Verify the exact signed image and healthy boot, saved setup, bindings, filters,
   display settings and action navigation. Do not erase bonds or configuration.
2. Set five-second cycling, then ten seconds. Check after tap/swipe, entering and
   leaving actions, critical-attention dismissal, App disconnect and reboot.
3. Confirm clean startup with no speculative CEL or `?`, and correct known warning
   fixtures. Exercise missed-release recovery without triggering an action.
4. Record stack minima, internal heap/largest block, PSRAM, allocation failures,
   worker progress and repeated Wi-Fi lifecycle results. Physical display/touch
   observations remain distinct from protected readback and host renders.

## One parked Jeep session

Compare reading ages/gaps and the bounded `poll` counters under the same dashboard,
with simultaneous ECM/child-TCM, phone connected/disconnected and one adapter
removed at a time. Test manual and automatic page changes plus owner-supervised
Air up/Winch Start/Stop and coolant/fallback protection. Targets:

- No blank first frame when a valid cached sample exists.
- No stuck cycling pause; full configured interval resumes consistently.
- No unknown-count glyph or initialization-only warning; confirmed faults remain.
- Fewer duplicate ordinary/guard requests and fewer age-budget misses without
  delayed Stop, diagnostic starvation or sibling loss.

A slow adapter or overloaded configuration can still exceed freshness limits.
Counters expose this; never extend data age indefinitely or synthesize values to
make a test appear successful. Capture fresh App/circular-gauge visuals and update
the website after qualified rollout under its existing daily publication rule.
