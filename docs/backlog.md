# Prioritized backlog

Updated 2026-10-07. This is the only current task/status list. Outcome order belongs
in [roadmap](roadmap.md); supported/installed behavior belongs in [current state](current-state.md).
Evidence files retain historical pending notes and do not create additional tasks.

## How to choose work

Choose the highest-priority ready item matching the owner's request. Preserve its
acceptance boundary and dependencies. Work offline while hardware is absent. Do not
start a vehicle session, enable a control, publish a release or merge main merely
because a task appears here. The owner's existing authorization governs each action.

Statuses: **Ready** means useful work can begin; **Needs device** means physical
qualification is required; **Blocked** names an unavailable dependency; **Deferred**
means out of current delivery scope. Priority is delivery order, not a date promise.
No task is In progress until a session actually starts it. Completed implementations
are in current state and evidence, with remaining qualification listed separately.

## Active tasks

| ID | Priority | Status | Task | Dependency / acceptance |
| --- | --- | --- | --- | --- |
| QUAL-01 | P0 | Needs device | Confirm dev.47 display and touch | Owner normal-page and swipe observation; protected image/health already passed |
| QUAL-02 | P0 | Needs device | Configuration/OTA interruption and uncertain outcome | [Recovery matrix](development/ota-recovery-matrix.md); stage/commit, app loss, Wi-Fi expiry, power loss and rollback; exact post-reboot identity before any retry |
| QUAL-03 | P1 | Needs device | One adapter moved between ECM/TCM ports | Whole vehicle pages stay editable; unavailable values clear; correct attributed values recover; [logical vehicle evidence](evidence/logical-vehicle-review.md) |
| QUAL-05 | P1 | Needs device | ECU-scoped CEL/MIL and full vehicle faults | [ADR-015 Phase 1](architecture/alerts-and-diagnostics.md#implementation-sequence-and-file-ownership): Dev.48 endpoint routing, fair polling and global presentation implemented with production fixtures; sequential ECM/TCM long-read/coverage qualification remains |
| FEATURE-05 | P1 | Qualification | Shared alert, notification and diagnostic-history framework | [ADR-015 Phases 2..4](architecture/alerts-and-diagnostics.md): Dev.48 threshold/MIL/synthetic adapters, shared attention/UI, bounded context/journal, transactional history, export and opt-in monitoring implemented; QUAL-11 owns physical/OEM/storage qualification |
| FEATURE-03 | P1 | Qualification | Explicit diagnostic code clearing | [ADR-015 Phase 5](architecture/alerts-and-diagnostics.md#clearing-codes-and-preserving-evidence): Dev.48 confirmed token/journal/addressed Engine clear and readback implemented behind default-off gate; offline policy tests passed, live Engine/JSS semantics require qualification |
| QUAL-11 | P1 | Needs device | Shared alerts, context and notification qualification | [ADR-015 matrix](architecture/alerts-and-diagnostics.md#focused-acceptance-matrix): autonomous gauge, hidden pages, Pixel lifecycle, power/storage loss, clear before/after; actual vehicle clearing only explicit parked owner action |
| QUAL-06 | P1 | Blocked | Expand/validate JSS TCM meanings | Independent controller/calibration reference needed for sensor scale, gears 2..8, current/target divergence, shaft speeds/slip; [bounded research plan](development/tcm-values-research-plan.md); pressure remains raw |
| QUAL-07 | P1 | Needs device | New readings/alerts, units and circular legibility | Decimal and gear rendering, hidden-page TCM alerts, dwell/stale/recovery, unit labels, arc clearance, vivid colors, daylight/night; no new support inferred from vectors |
| QUAL-04 | P1 | Blocked | Two physical adapters plus phone | Owner currently has one adapter; re-add explicit child only after second adapter arrives; [dual matrix](development/dual-adapter-recovery.md), sibling continuity, no cross-source attribution |
| QUAL-08 | P1 | Needs device | Controlled recovery/performance soak | Worker gaps, queue drops, free/largest blocks, freshness and polling latency; compare single/dual load; durations and cycles in [quality gates](development/quality.md) |
| FEATURE-07 | P1 | Ready | Capability-gated Auto display orientation | [Orientation policy](architecture/hardware-and-transports.md#automatic-display-orientation): bounded IMU identity/health and axis mapping, persisted mode distinct from applied angle, negotiated settings and coordinated touch transform; current board sensor path is unimplemented, advertise only after physical qualification |
| CODE-01 | P2 | Ready | Continue typed failure migration | Replace remaining control-flow message matching at specific transport boundaries; preserve cancellation, protocol copy compatibility and verified outcomes; no broad rewrite |
| CODE-02 | P2 | Ready | Extract the next concrete coordinator owner | AppModel/transport extraction only with a feature or measured maintenance benefit; keep one lifecycle/operation lease, coupled saves and scoped state; independently test failure boundaries |
| QUAL-09 | P2 | Needs device | Accessibility and adaptive/OEM matrix | TalkBack, keyboard/switch, 200% text, RTL, fold/hinge and alternate Android generation; fixture success is not user/device qualification |
| QUAL-10 | P2 | Needs device | Adapter/vehicle expansion | One alternate BLE/GATT profile and another vehicle with captured identity, header/parser vectors and field loss/recovery evidence; [capture runbook](development/vehicle-capture-runbook.md) |
| RELEASE-01 | P2 | Ready | Production trust and Play readiness | Review signing/provisioning, release threat model and compatibility; follow [release runbook](development/firmware-release-runbook.md); owner handles consequential Play actions; no production claim from prereleases |

## Deferred capabilities

| ID | Status | Scope | Entry gate |
| --- | --- | --- | --- |
| FEATURE-01 | Deferred | Broader manufacturer packs and compound/oxygen-sensor readings | Licensed provenance, exact decoder/status semantics, bounded source routing and vectors; [catalog rules](development/reading-catalog-alerts.md) |
| FEATURE-02 | Deferred | Live companion telemetry and optional physical sensors | Measured radio/resource budget, bounded freshness and protected protocol; [hardware strategy](architecture/hardware-and-transports.md) |
| FEATURE-04 | Deferred | High idle, ABS/ESC and other controller actions | Matching controller procedures, bounded protected execution, outcome/restoration and interruption evidence; [action design](architecture/vehicle-actions.md); local page-jump foundation already implemented |
| FEATURE-06 | Deferred | Authorized third-party alert providers | Dev.48 synthetic provider seam and [integration guide](architecture/phone-alert-providers.md) are implemented; live feed requires documented access, licensing, TTL/attribution and qualification under [ADR-015](architecture/alerts-and-diagnostics.md#future-external-provider-seam) |
| PLATFORM-01 | Deferred | iOS | Explicit owner reprioritization; do not spend implementation time now |

## Updating a task

Before implementation record its ID, scope, dependency and intended evidence in the
session/PR. A source change and its pending physical gate may have separate status.
After verification update the task, current state and focused evidence in the same
commit. Avoid append-only milestone summaries or repeated copies of this table.
If a new task supersedes an old one, preserve its acceptance requirement and link
that relationship. Do not call an unavailable measurement completed or turn an old
pending note into current authorization.
