# Contributing

Design status must remain explicit. Mark proposed behavior, simulation, hardware-observed evidence, and implemented behavior separately. Link every feature to a requirement and acceptance gate. Keep diagrams, contracts, examples, and release notes in the same change as behavior.

Before a pull request: run `python tools/validate.py` in the documented environment; run the relevant firmware or Android build when that implementation changes; add focused tests for parser boundaries, schema changes, migration, security decisions and lifecycle state. Do not treat a screenshot, a firmware compilation, or a simulator result as vehicle compatibility evidence.

Contract changes need a version/compatibility note and examples. New PID definitions need source/license, exact vehicle scope, ECU routing, unit, decoder, sample vector and support evidence. Never copy a third-party catalog without checking its license. Document a reproducible bug using redacted logs and device/app/protocol versions.

Use short-lived feature branches and reviewed PRs after repository bootstrap. Require documentation/schema checks and relevant build jobs. Keep signing keys and personal vehicle identifiers out of Git. Product-wide license remains an owner decision; third-party notices must stay intact.

## Commit and backup routine

Commit completed, tested changes on the active feature branch and push that branch
to `origin` at the end of each work session. Routine commits and branch pushes are
authorized by the owner. Use `codex/` for new feature branches and keep related
implementation, tests and documentation in the same change.

Merge into `main` through a reviewed pull request after required checks and
applicable physical validation pass. Firmware releases are separate actions after
required hardware testing and explicit release authorization. Pushing a branch
does not publish a release or install anything on a device.

Session summaries should state the branch, latest commit, push result, verification
and remaining physical checks. If a push fails, preserve the local commits and
report the blocker. Do not force-push or rewrite shared history without explicit
authorization. The agent instructions in `AGENTS.md` carry this routine into future
project sessions.
