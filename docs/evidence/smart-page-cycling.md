# Smart page cycling offline acceptance

> Historical evidence. Measurements and unfinished checks describe this session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). This record is not a standing implementation instruction.

Recorded 2026-10-27. This is dated source/test evidence, not installation or physical
qualification. [Current state](../current-state.md) owns device identities; the
[backlog](../backlog.md) owns remaining work. Owner approved FEATURE-11 design and
implementation with proposed defaults. No physical phone/gauge access, firmware
publication, main merge or website deployment was performed for this milestone.

## Implemented scope

The [Smart cycling contract](../architecture/smart-page-cycling.md) owns semantics
and tunable values. Source adds a deterministic selector, exact-definition catalog
hints, bounded original-age sample/alert projections, stable upcoming-page hints,
and a per-gauge acknowledged Smart setting alongside the existing interval.

Review corrected strongest-visible continuation, condition-scoped suppression,
selection/touch races, revisit cooldown accounting, frozen runtime-counter context,
and alert-lock contention. Pure-policy replays exercise the resulting behavior,
including repeated warnings, unavailable siblings, ordinary cursor fairness and
wrap-safe time. Existing warning, control and diagnostic engines remain independent.

The protected extension uses optional document capability `sp:1`, requests `68`/`69`
and state `17` (hex). Legacy `ds:3`, built-in capabilities and display byte layouts
retain their meanings. New firmware legacy writes preserve Smart; older Apps and
firmware keep ordinary controls. Storage uses two revision-scoped sidecars and the
unchanged v3 legacy record as activation point. Fault injection exercises interruption
before/after each commit and readback/reboot, plus corrupt/absent records and downgrade.

## Executed verification

- Host suite: 132 tooling tests and 12 review-store tests passed, plus existing native
  core/parser/action/trace/config tests. The Smart selector executes production C
  under AddressSanitizer/UndefinedBehaviorSanitizer with no hardware dependencies.
- Android: 237 debug JVM and 212 release JVM tests passed; instrumentation compilation,
  debug APK, lint and Kotlin format passed. Managed OptimizationUiTest passed 14/14,
  including Smart disabled/retained/cancel/summary/coupled save and legacy editor.
- Native firmware UI: normal and parked LVGL fixtures passed, including read-only
  scoped sample projection, retained original age, source/revision rejection and
  existing action/critical/maintenance/pairing behavior. Configuration binding fixtures
  compiled all 55 catalog definitions and existing alerts.
- ESP-IDF normal and parked builds and final documentation/format gates are recorded
  in the PR verification. These are local source builds, not signed release images.

## Resource evidence and limits

The selector state is 3,432 bytes on the native host. Together with the 32-entry
alert projection it uses 3,688 bytes of fixed PSRAM on the reference build, enforced
by a 4 KiB static assertion. No new task/stack, framebuffer or per-sample allocation
is introduced. The normal linked memory gate reports 164,434 internal static bytes
against 196,608, with the 65,536-byte LVGL arena in `.ext_ram.bss`; total external
BSS is 90,976 bytes. This is a placement/build result, not a runtime OOM guarantee.

Maximum selection time is logged on the device for later verification. The <1 ms
reference-board target, actual loaded sampling/control deadlines, heap/stack margins,
long-term recovery and subjective readable dwell remain physical gates. Thermal
hints use conservative synthetic replays; no vehicle sensor calibration or measured
normal temperature/rate distribution was established by this work.

The [matched comparison](../development/smart-page-cycling-plan.md) keeps the next
physical session short. Private local logs/builds remain under ignored
`artifacts/smart-page-cycling/`; do not publish device preferences or captures.
