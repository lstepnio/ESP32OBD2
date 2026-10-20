# Repository maintenance verification

> Historical evidence. Recorded 2026-10-19; no new phone, gauge or vehicle observations.
> [Current state](../current-state.md) owns installed support; [backlog](../backlog.md) owns remaining work.

## Scope and behavior preservation

Owner approved audit items 1..9: shared checks, GitHub protections, transactional fonts,
formatting, dependency resolution, isolated UI CI, documentation consolidation,
merged-work cleanup and the README screen gallery. No version, protocol, capability,
configuration, device setting or release changed. Unique open PRs #41 and #57..59
were retained; maintenance does not merge their runtime/experimental work.

- ESP-IDF 5.4.1 firmware build, memory/dependency gates and production UI/config
  fixtures passed. Twenty-five comparable native RGB frames matched earlier output
  byte-for-byte. Six used fonts reproduce byte-for-byte with the pinned generator.
- Android debug build/lint, instrumentation compilation and 201 JVM tests passed.
  Unsigned release build/lint passed. Twenty-seven allowlisted UI tests passed twice
  on a disposable managed API 35 Pixel 2; no owner phone was used.
- Full host discovery passed 103 Python tests under tools and seven review-server
  tests, capture self-test and four native address/undefined sanitizer executables.
  The previously omitted GATT suite's stale hard-coded path count was corrected to
  test coverage of every protected read helper call; firmware behavior was unchanged.
- Formatting uses Ruff 0.17.1, clang-format 23.1.3 and checksum-verified ktlint 1.8.0.
  Include order is preserved. Mechanical commits are listed in `.git-blame-ignore-revs`.
- Supplemental `testReleaseUnitTest` ran 201 cases with the identical 29 failing
  case identities on unchanged main `fe75769` and maintenance. These fixtures assume
  debug-only TCM/development features. They are not the required debug gate; CODE-04
  tracks variant isolation. No feature gate was loosened to make them pass.

## Removal and consolidation

Deleted only the unreferenced generated `notosans_medium_32.c`, `_64.c` and `_128.c`
font sources. This removes source bulk, not a claimed runtime memory saving.
Moved `docs/architecture/decisions.md` to `docs/decisions/README.md` and fixed links;
kept the compact ADR ledger instead of creating boilerplate files. Rewrote long
Android/onboarding/status guidance, removed repeated rollout prose from active status,
and moved implemented pairing/display wire detail out of proposed BLE v1 into the
sole development protocol owner. Historical evidence and third-party notices remain.

## Gallery provenance

App PNGs in `docs/assets/app/` are production Compose screens rendered by
`RepositoryGalleryTest` with fixed state and compact navigation, including supported
display settings. Gauge PNGs in `docs/assets/gauge/` come from production LVGL/fonts
via `render_gauge_gallery.py`. They document appearance and do not establish vehicle
support. [Quality procedures](../development/quality.md#android-ui-and-screen-captures)
own regeneration; README captions use screen names.

## GitHub configuration

Main ruleset requires a PR, resolved review threads and strict quality checks; blocks
branch deletion/force updates; uses zero mandatory approvals for the single-maintainer
repository. Merged branches auto-delete. Development publication requires owner review
in the release environment, approved main/codex refs, successful quality on the exact
SHA and serialized publication; only the publishing job has write access.

Fourteen superseded PRs (#29..35, #37, #39, #40, #42, #44, #46, #47) were closed after
ancestry checks. Thirteen corresponding fully merged remote branches were removed.
The merged `codex/egauge-customize-workspace` base was retained for unique PR #41.
No shared history was rewritten and the unrelated dirty primary checkout was untouched.

## 2026-10-20 Android variant gates

CODE-04 follow-up, offline only. The untouched source baseline ran 214 release JVM
cases with 31 failures, all arising from development-only configuration projection
or catalog selection. The changes classify 29 positive development fixtures, exercise
only selectable readings in shared catalog projection tests, and add explicit debug/
release feature contracts. Release tests cover blocked TCM pages, alerts, mixed and
child routes; ordinary ECM projection; and retained but non-executable saved TCM data.
Runtime source and BuildConfig feature flags are unchanged. No phone was accessed.

The shared `tools/dev.py test --target android` now runs both variants and compiles
instrumentation in one Gradle invocation. The final local gate passed 215 debug and 191 release JVM tests, with no failures,
errors or skipped tests, and compiled the debug instrumentation source. Kotlin/Python
format checks and host lint/tests passed. The earlier 201-case/29-failure comparison
remains historical. Required CI now runs both JVM variants and preserves failure-only
XML/HTML diagnostics for seven days; no owner device or sensitive trace is involved.
