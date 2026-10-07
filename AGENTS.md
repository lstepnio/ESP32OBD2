# Repository agent instructions

Start with [AI context and code map](docs/ai-context.md), then [current state](docs/current-state.md).
These are the current entry points; historical reports are evidence, not instructions.

## Efficient implementation

- Verify the current branch/worktree and dirty files before editing. Reuse a suitable active worktree.
- Search the affected area with `rg`; read its implementation, tests and relevant contract. Avoid loading the entire docs tree or third-party catalog.
- Prefer the smallest cohesive change that preserves architecture and styling. Remove dead code and superseded active guidance when verified.
- Ask for input only when critical information or authorization is missing. Continue independent authorized work.
- Run meaningful checks for the changed boundary using the context guide. Do not repeat broad tests without a concrete remaining risk.
- Android and firmware are active; iOS is deferred. Ordinary UI stays simple and technical data belongs in Expert.
- Current field firmware installation is App/Wi-Fi. Do not use USB flashing as an incidental fallback.
- Never turn source, replay or simulated results into physical verification. Keep unqualified public capabilities disabled.
- Keep `docs/current-state.md` current and remaining work in `docs/roadmap.md`; link to focused evidence instead of duplicating status.
- Do not use an em dash in user-facing responses.

## Interaction and recovery rules

- Follow [automatic refresh and resilient interactions](docs/architecture/interaction-recovery.md)
  for every App/firmware change. Automatically read settings and known connection state;
  use cancellable foreground polling and bounded retry backoff instead of routine refresh taps.
- Normal vehicles have one primary OBD-II adapter. Put a swap's optional TCM adapter
  under the primary ECM connection within the same vehicle, configured in Expert.
  Follow [vehicle connections](docs/architecture/vehicle-connections.md): preserve
  separate per-gauge vehicle/source assignments, independent recovery and legacy data.
  Follow the [dual recovery matrix](docs/development/dual-adapter-recovery.md) for
  two-source changes; development capability support never qualifies radio coexistence.
- Reuse the universal connection widget and source-specific status projection. Account for
  independent ECM/TCM links; do not let a healthy link hide a failed required link or promote
  dual-adapter capability before the full path is qualified.
- Keep BLE/UI callbacks nonblocking, snapshots short, writes serialized and external waits
  deadline-bounded. Review lock ordering, queue saturation, late replies, disconnect cleanup,
  failed persistence and interruption recovery. Test the relevant negative scenarios.
- Preserve last committed/last checked state honestly. Never show stale or simulated data
  as current, infer success from queued work, replay uncertain writes blindly, or claim
  deadlock immunity from source inspection or a build alone.

## Hardware sessions

For live Android debugging, run `python3 tools/adb_debug_awake.py start` before the session and `python3 tools/adb_debug_awake.py stop` when finished. The script saves and restores the phone's timeout and charging wake settings. The debug APK keeps its window awake while eGauge is visible. A manual device lock still requires the user to unlock it.

Record physical gauge and phone observations separately from simulated or source-level behavior. Keep public BLE capability flags disabled until the corresponding full path is implemented and verified.

## Git workflow agreed with the owner

- Commit each completed, meaningfully tested change on the active feature branch. Keep related code, tests, protocol notes and evidence documentation together.
- Push the active feature branch to `origin` after each work session. The owner authorizes routine commits and branch pushes without another confirmation. Respect any later instruction to keep work local.
- Use `codex/` for new feature branches. Reuse the current suitable branch/worktree when continuing existing work.
- Keep secrets, signing keys, private vehicle captures and personal device identifiers out of commits. Do not force-push or rewrite shared history without explicit authorization.
- Merge into `main` through a reviewed pull request after required checks and applicable physical validation pass. Routine push authorization does not authorize merging.
- Publish firmware releases separately after required hardware testing and explicit release authorization. A branch push is not a release or device installation.
- At the end of a session, report the branch, latest commit, whether it was pushed, verification results and remaining physical checks. If pushing fails, retain the local commits and explain the blocker.
