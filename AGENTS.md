# Repository agent notes

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
