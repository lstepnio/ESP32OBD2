# Repository agent instructions

Read [current state](docs/current-state.md), then the affected row in the
[source map](docs/ai-context.md) and [backlog](docs/backlog.md). Historical reports
are evidence, not operating instructions. Android and firmware are active; basic iOS and browser-USB configuration are planned
in the roadmap, with implementation/qualification tracked in the backlog.

## Commands and layout

Prerequisites and fresh-clone setup: [README](README.md#development).
Run from root, after activating `.venv`; source ESP-IDF before firmware checks.

```sh
python tools/dev.py lint                         # host + firmware + Android
python tools/dev.py format --check               # Python, C, Kotlin; generated files excluded
python tools/dev.py test                         # Python, native C, firmware fixtures, Android JVM
python tools/dev.py build                        # host fixtures, firmware image, debug APK
python tools/dev.py test --target android-ui      # disposable managed emulator, no physical phone
# Focus work: --target host|firmware|android; format: --language python|c|kotlin
```

`firmware/gauge/main/` owns embedded runtime; `android/app/` owns the companion;
`android/core/designsystem/` owns shared Compose UI; `contracts/` owns wire/data
schemas and reading catalog; `tools/` owns checks/capture; `docs/` owns maintained
contracts/status and dated evidence. `data/vehicle-definitions/` is research only.

## Efficient changes

- Inspect branch/worktree, upstream and dirty files first; preserve unrelated work.
- Search the affected area with `rg`; read its implementation, tests and contract.
  Do not load entire evidence trees, imported catalogs or managed dependencies.
- Make the smallest cohesive change that preserves architecture/styling. Remove dead
  code or stale guidance only after verifying references and remaining acceptance.
- Ask only for critical missing information/authorization; continue independent work.
- Run checks for the changed boundary. Repeat broad tests only for a concrete risk.
  A build, fixture or serial log is not physical qualification.
- Follow [documentation ownership](docs/documentation.md). Keep one topic authority;
  update code/contracts together. Major choices belong in [ADRs](docs/decisions/README.md).
- Feature completion includes a website-impact review. Follow [website maintenance](website/README.md#maintenance-cadence)
  for daily publication, owner-led roadmap updates and current App/circular-gauge screens;
  keep marketing capture/publication off the normal build path.
- Do not use an em dash in user-facing responses.

## Runtime boundaries

- Follow [interaction/recovery](docs/architecture/interaction-recovery.md) for automatic
  settings/connection reads, cancellable polling, bounded backoff and quiet recovery.
  Reuse the universal connection widget; technical data belongs in Expert.
- A normal vehicle has one primary adapter. An optional swap TCM is its child and changes
  transport routing only, never page/reading/alert availability. Preserve per-gauge
  assignments, legacy inputs and sibling operation during independent source loss.
  Follow [vehicle connections](docs/architecture/vehicle-connections.md) and the
  [dual recovery matrix](docs/development/dual-adapter-recovery.md).
- Keep missing/stale/invalid values unavailable, never zero. Preserve ECU/source,
  revision, session and monotonic age. Never present stale/fixture data as current.
- BLE/UI callbacks must not block. Serialize writes; keep snapshots short, queue
  admission bounded, external waits on one monotonic budget, and cancellation coupled
  to the underlying resource. Await bounded cleanup before releasing the operation
  lease. Reject retired callbacks; never blindly replay uncertain writes.
- Coupled setup uses `LocalSetupTransactions`/`DurableWrites` and one off-main
  `LocalSetupStore` commit. Publish only acknowledged state; retain malformed/newer
  records and legacy read-only migration inputs. Preserve cancellation through
  `suspendResult`; verified gauge truth survives phone journal cleanup failure.
- Configuration success requires exact running revision/hash and cleared trial.
  Update success requires exact signed post-boot identity and healthy OTA state.
- Reuse [shared alerts](docs/architecture/alerts-and-diagnostics.md) for thresholds,
  MIL/fault and provider notices: one lifecycle/history/attention/notification owner.
  Follow its extension checklist and [provider guide](docs/architecture/phone-alert-providers.md).
  Preserve autonomous gauge truth, immutable context, scoped identities and silent
  replay. Acknowledge, snooze, history deletion and ECU clearing are distinct.
- Follow [memory admission/placement](docs/architecture/firmware-runtime.md#memory-placement-and-admission-budget).
  Keep CPU-only bulk storage in PSRAM and required DMA/ISR/flash stacks internal.
  Extend existing worker/queue/memory health logs; adapter absence is not a task stall.
  Never hide stalls with watchdog feeding/reset loops or claim OOM immunity.
- Sensor-driven capabilities require runtime presence/support checks and qualification;
  [Auto orientation](docs/architecture/hardware-and-transports.md#automatic-display-orientation)
  is independent of page cycling. Public capability flags remain disabled until qualified.

## Generated and sensitive areas

- Do not hand-edit generated catalog, tokens, wire fixtures or fonts. Use their
  generator (`tools/generate_*.py --check`, [font procedure](firmware/gauge/assets/fonts/README.md)).
- Do not edit downloaded `managed_components`, `.gradle`, build output or research
  imports as a runtime fix. Explicit dependency pins, locks and checksums require review.
- Keep signing keys, private captures, device addresses and preference backups out of Git.
  Do not erase bonds/config/App data, change eFuses or promote capability flags incidentally.

## Hardware and delivery

- Keep one active owner per phone/gauge; coordinate other chats before device mutations.
  Never run broad connected tests on the owner phone. Use the managed emulator offline.
- For authorized live Android debugging, run `python3 tools/adb_debug_awake.py start`
  before the session and `stop` afterward to restore timeout/wake settings. Manual
  locks still require the user to unlock. Record phone and gauge observations separately.
- Field firmware installation is App/Wi-Fi. USB recovery requires separate explicit
  authorization and the [release/recovery procedure](docs/development/firmware-release-runbook.md).
- Follow [CONTRIBUTING](CONTRIBUTING.md): small tested commits, `codex/` branches,
  push at session end, no shared-history rewriting. Routine commits/pushes are authorized;
  main merge and firmware publication require separate owner authorization and checks.
- Report branch/commit, push result, verification scope and remaining physical gates.
  Never equate a pushed branch, green CI, release publication and installed firmware.
