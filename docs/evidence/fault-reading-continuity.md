# Offline fault presentation and reading continuity

> Historical evidence. This dated review records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

2026-10-28. Source baseline: merged main `2751455`. This is offline evidence;
installed identities remain in [current state](../current-state.md).

## Scope and task paths

| Before | Source result |
| --- | --- |
| Car labels all retained episodes Last drive and shows an alert total | Previous activity opens history without implying a drive boundary or code count |
| Repeated MIL/code episodes fill history with indistinguishable rows | One scoped issue row opens its representative observation; detail shows record count and first/last dates |
| Unattributed warning detail may show another controller's codes | Event/controller-scoped evidence only; missing recorded codes are explicit |
| Last-known codes disappear from the ordinary Car task path | Direct Last checked code rows retain unavailable/current distinction |
| Fast reading widgets jump on each sample | Bounded causal presentation easing; measured cache, original ages and guards retained |
| Auto boot starts at manual fallback before sensor admission | Separate validated stable-angle checkpoint initializes display and runtime |

Behavior authorities: [shared alerts](../architecture/alerts-and-diagnostics.md#app-fault-and-history-presentation),
[display](../architecture/display-experience.md#foreground-reading-motion),
[orientation](../architecture/hardware-and-transports.md#automatic-display-orientation).
The [next Jeep plan](../development/jeep-action-qualification.md#next-session-configurable-idle-and-pedal-investigation)
prepares configurable targets and pedal attribution without enabling new commands.

## Executed checks

- Host lint/contracts/docs and format checks passed. Host tests: 173 Python suites,
  14 review-server tests, Node recovery fixtures and native sanitizer fixtures passed.
- Android debug/release JVM checks: 327/301 tests passed. Debug APK,
  instrumentation compilation and Android lint passed.
- The first broad managed-emulator run ended after 42 passes when the virtual
  device disappeared during an unchanged update-screen fixture. Its cleanup also
  reported `emulator-5554` missing. This is not a completed full-lane pass; failure
  evidence is retained privately under `artifacts/checks/emulator-aborted/`.
- Final focused managed UI: 48/48 passed, including the unchanged fixture from
  the interrupted run, source isolation, direct code navigation, hidden-code paths,
  notifications and compact/200% text. Visual review prompted a final history-row
  copy reduction. A subsequent full workflow rerun again lost its virtual device
  after three passes (during the unchanged 510-row fixture); it is retained under
  `artifacts/checks/emulator-polish-aborted/`. The final two affected history-row
  interaction/capture checks passed independently, and both images were reviewed:
  [compact](../assets/app/fault-history.png) and
  [200% text](../assets/app/fault-history-large-text.png). No assertion was relaxed
  and no fixture was removed. Full final-tree CI remains required.
- Production LVGL default, no-PSRAM fallback and parked-candidate fixtures passed,
  including raw-cache preservation, easing bounds/settlement, resets and immediate
  critical attention while the number is moving. Display NVS fault injection passed
  for malformed hints, failed commit, debounce, write throttling and power cuts.
- Firmware build passed with unchanged public capability gates. Image size
  `0x199290`; internal static RAM 164602 bytes passed the existing 196608-byte gate.

No live vehicle command, physical-phone test, firmware signing/publication,
installation or website deployment was performed. Loaded adapter cadence, visual
comfort, cold-boot orientation and read-only idle recovery require hardware checks.
Arbitrary RPM encoding and Winch pedal behavior remain unqualified. No dependency,
wire schema or owner settings revision change is introduced.

Website impact: inspect production Car/circular-gauge captures in the normal daily
window after qualification. This corrective change makes no new marketed vehicle
capability claim and does not authorize an extra publication.
