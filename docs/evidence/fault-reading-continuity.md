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

## Original PR #123 checks

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

## Combined integration after PR #122

The owner authorized one combined release with Mainline. PR #122 merged at
`e166fa02822a6842d20d4c2b1ac83886eeb1a801` after four successful checks on its
corrected `3e17eaa` source. PR #123 integrates that base before publication; its
original independent 100 ms policy is superseded by one 150 ms analog/RPM-text
owner with original-observation age accounting and continuity fencing.
The corrected local-semantic/transfer blocker split is retained unchanged.

Combined source checks passed: 173 host Python tests, 14 review-server tests and
native sanitizer fixtures; 331 debug/305 release JVM tests; Android build/lint and
instrumentation compilation; all three production LVGL variants; firmware build
with image size `0x1993a0`, internal static RAM 164602 bytes and existing gates.
Source-generation, configuration, missed-invalid/stale gaps, raw cache/history,
RPM cadence, critical/zero bypass and delayed-sample tests exercise the one path.

The first combined 93-test UI run completed with one failure. Its stack points to
Espresso keyboard closure selecting the unfocused base Activity behind a dialog in
the native-200% target editor. The test helper now addresses the focused window's
IME controller and awaits native hidden insets; it does not press Back, retry a tap
or weaken any reachability/value assertion. The full suite then passed all 93
tests with no failures or skips (4m01s), including actual Android 200% target entry,
Car/history/filter/notification paths and compact/large-text captures. The failed
run is retained privately under `artifacts/checks/combined-ui-before-imefix/`.

Mainline owns the single signed release, App/Wi-Fi installation and combined device
evidence update. This source integration performs no further device operations.
Fresh pre-delivery protected reads confirmed dev.74 healthy, configuration revision
65 and the retained digest; display preferences now have revision 48, Auto enabled
and 15-second Smart cycling. Both command states were Unavailable with operation 0.
Those are cache observations indoors, not proof of live recovery or vehicle control.
Private setup/history backups and original phone notification/wake preferences were
handed to the sole device owner. The before-install Car capture reproduced exactly
Last drive / 24 alerts recorded; no history was deleted to change that presentation.

Final presentation review also retained simulated-diagnostic markers in historical
summaries and preserved independent provider episode IDs when a slot is reused.
Two focused JVM regressions passed in both variants; Android lint passed. These
are App presentation-preservation corrections, with no firmware-source change.
The full final-head CI gate is still required before merge/publication.
