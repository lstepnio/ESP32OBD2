# eGauge continuation notes

## Current cleanup authority, 2026-10-28

PR #119 (`codex/remaining-pr-reconciliation`) consolidates the preserved project
work. Its final verification/merge/closure comment owns the exact accepted revision,
CI run and outcome. Read the live PR and current checkout before resuming; this
file is a source continuation index, not a deployment or live-health certificate.

Main #93 merged as `690439b`; its original assessment head `8261de3` remains in
remote branch `codex/assessment-remediation-preserved-20261028`. The consolidation
retains #116 head `7b766c7`, main #117 `ce8c381` and #118 head `6d04d43` as
ancestors. It preserves concurrent idle-recovery and scoped container-trust fixes,
rather than forcing an outdated protected merge. Implementation `968bb07` also
fixes legacy diagnostics fallback on retired IO; six regressions require a fresh
owner session only after confirmed unscoped unsupported-command rejection. Staging
abort cleanup also uses a fresh owner, verifies transfer ID/phase and retains
commit/activation uncertainty; six regressions cover the cleanup/lease boundary.
Vehicle command uncertain-write readback likewise uses a fresh read-only owner
without resubmitting Start/Stop; five real owner-channel cases cover that boundary.

## Preservation records

- `docs/evidence/repository-maintenance.md`: initial superseded PR cleanup.
- `docs/evidence/pr-unique-reconciliation.md`: #41/#59/#74/#77 retained work and
  intentional supersession, including gesture/reset races, alert eligibility and
  current waiting-logo allocation stability.
- `docs/evidence/final-pr-consolidation.md`: #57/#58/#64/#72/#112, concurrent
  #117/#118 and the legacy diagnostics compatibility correction.
- Individual PR closure/merge comments retain the final source/verification links.
  Source branches and discussion are retained; no old positive patch is discarded
  merely because its branch was stale.

## Verification and boundaries

Local full affected checks passed: 172 host tests, 14 review-server tests, 42 Node
fixtures and native sanitizers; 322 debug and 296 release Android JVM tests,
instrumentation compilation, APK build and lint; ESP-IDF 5.4.1 image build and
normal/parked production LVGL/configuration fixtures with deterministic PSRAM-mask
failure. Formatting, host lint and contract/document links passed. Remote CI must
accept the final head, not an earlier head. The previous integrated UI head passed
87 managed fixtures; current exact-head results belong to #119's completion record.
Compact/200% alert workflows and native/Android trend captures were visually reviewed.

The companion probe is inactive, without UI/startup/service/provider activation.
Jeep research capture remains explicit and opt-in; TIPM mapping is unqualified.
Public/control/qualification masks remain unchanged. Historical dev.71 evidence
must not replace the newer owner dev.72 recovery report. Current state and dated
records distinguish exact image readbacks from owner reports and physical tests.

No release publication, website/backend deployment, APK installation, gauge flash,
live capture, vehicle action or physical qualification is part of this cleanup.
Other chats' release/device tasks are separate authorities. The unrelated dirty
`codex/obd-definition-catalog` checkout remains untouched. Managed worktrees and
ignored local test artifacts are not deployment artifacts.
