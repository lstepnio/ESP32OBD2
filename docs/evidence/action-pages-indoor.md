# Action-page indoor rollout

> Historical evidence. Measurements and unfinished checks describe this session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). This record is not a standing implementation instruction.

## 2026-10-20 signed dev.61

PR #89 merged as `0e3bd12`; all four Foundation quality gates passed for
`68d44a8`. Version-only PR #90 merged as `b5a8dc1`. Its exact reviewed source
`a6dd0291780722b0e0e2725491c67c2e4b478a8d` passed all four gates and the protected
[development publication](https://github.com/lstepnio/ESP32OBD2/actions/runs/39472981311).
The owner authorized merge, signed publication, App/Wi-Fi installation and indoor checks.

### Installed artifacts

- Pixel 10 Pro: updated debug App built from `a6dd029`; the App version label remains
  `0.2.0-dev.51`. Installed with replacement, without clearing App data or bonds.
- Original Waveshare ESP32-S3-Touch-LCD-1.28: signed
  [dev.61](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.61),
  catalog generation 42, image 1,643,008 bytes.
- ELF SHA-256: `4ee4474128f2ee25b8f924a2fe63b5578c75945ab6e45b01253a8253ba40fbc6`.
- Bundle SHA-256: `78ace5661cc251a1bcd4e3a1c3b042f0aba611f37cd61b001d33cdf4c033b5ad`.
- Downloaded catalog and application signatures, bundle length/hash, board,
  partition layout, transfer protocol and ESP descriptor verified before installation.
  Private signing material stayed in the protected workflow.

### Observed indoor checks

| Check | Result |
| --- | --- |
| App migration | Profiles 13 to 17; gauge associations 1 to 2. Prior profile fields preserved; only schema/default empty idle presets added. |
| Automatic connection before update | Protected opening/settings, foreground resume and hardware-to-firmware read chain passed against dev.60; revision 52 retained. |
| Hosted signed App/Wi-Fi update | Passed in 114.429 seconds including hosted preparation; update journey 74.153 seconds. Sending began at 18.314 seconds, verification at 58.005 seconds, exact healthy image confirmed at 74.151 seconds. |
| OTA acceptance | Alternate slot, health state 2, exact version/ELF and cleared phone update journal. Revision 52, digest, settings and profiles preserved by the firmware installation. |
| Five maintenance cycles | Protected open, authenticated Wi-Fi configuration/OTA status, close and unchanged boot nonce passed; open times 1,599 to 1,629 ms. |
| Bluetooth recovery | Opening 7,867 ms; foreground resume 2,796 ms; Bluetooth off/on recovery 12,252 ms. No reconnect tap or bond reset. |
| Maintenance teardown | Protected hardware snapshot reported Wi-Fi mode 255 (deinitialized), followed by fresh exact firmware read. |
| Pixel action setup | Real Gauge > Action pages opened and dismissed; profiles, assignments, settings and running revision/digest unchanged. No vehicle command invoked. |

The first update fixture stopped before any transfer because phone monitoring was
active. Monitoring was disabled through Settings to isolate the test and restored
at session end. No Bluetooth/USB firmware recovery was used.

### Action binding and preservation correction

The firmware update correctly retained the old `jumpPage` configuration. After the
owner reported that swipes still jumped to a reading, the gauge-specific override
was changed to `vehicleActions`: three upward swipes within five seconds, ordered
Air up (5:00), Winch (1:00), ABS/TCS. This is configuration, not a firmware fallback.

The initial configuration check caught an unrelated draft difference: the phone's
**pre-install backup** contained three TCM filters while the gauge had four. Sending
that draft temporarily omitted `U0140` at revision 53. Revision 54 restored it in
both phone and gauge. Comparing the complete initial/final documents showed only
the approved action binding, base revision and filter ordering changed. All four
filter codes (`U0140`, `U0121`, `P1DCA`, `P1DF3`), pages, limits and adapter bindings
were retained. The cause of the older phone/gauge filter divergence is unknown;
it was present before this migration.

The opt-in `ActionPagesConfigurationJourneyTest` now rejects unrelated draft
changes **before** sending. It can reconcile one explicitly supplied TCM filter,
uses the ordinary per-gauge save/review/send owners, and verifies exact healthy
readback and independently read unqualified/inactive command states. A prior
post-send fixture wait timed out on background command polling; the final assertion
uses isolated protected reads after draining the App operation owner. The corrected
fixture passed in 55.705 seconds at revision 55, preserving dashboard/settings and
confirming both command kinds remain unqualified and inactive. The final document
retains the same four filter codes; ordering differs without changing filter semantics.

### Memory evidence

Serial samples during upload and the five maintenance cycles recorded zero
allocation failures and zero worker queue drops. The upload's recorded internal
minimum was 93,979 bytes. Dev.61 maintenance minimum was 96,707 bytes; final three
Wi-Fi-release samples each showed 122,327 internal bytes free and a 31,744-byte
largest block. Minimum sampled application stack margin was 1,568 bytes.
Adapter absence produced bounded retries. These samples do not establish OOM
immunity, long-soak stability or loaded simultaneous-radio qualification.

## Physical observation and limits

The owner confirmed dev.61 returned to a normal page with clean text and working
swipes. The later report of old shortcut behavior was traced to the saved jump
binding and corrected as described above.

Action-branch entry, browse/return, edge/orientation/cycling and extended touch
qualification are recorded separately from ordinary-page confirmation. Native
fixtures cover deliberate hit regions, confirmation drift, holds/drags, direct
Stop, attention return and stable widget count, but do not replace physical checks.
Both vehicle-command qualification masks and public configuration/OTA flags remain
disabled. No vehicle command was issued. Parked Jeep identity/guards, actual
Start/Stop/restoration, thermal/fallback and child-source continuity remain open.

Raw preference/configuration backups, screenshots, addresses and serial logs remain
under ignored `artifacts/device-rollout-dev61/`. Website impact: existing command
marketing does not gain a new live-control claim; no extra website publication is
needed for this indoor qualification record.


## 2026-10-27 signed dev.62 parked-idle candidate

The owner authorized installation through App/Wi-Fi and indoor readiness checks.
PR #94 merged as `6509e81`; its source `49fc6a22ee597372eeaee69be90ac1d89094ba0c`
passed all four [quality gates](https://github.com/lstepnio/ESP32OBD2/actions/runs/40365131210).
The protected [publication](https://github.com/lstepnio/ESP32OBD2/actions/runs/40367425400)
built with `parked_idle_test=true`. The [scoped test policy](../architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate)
uses physical Park/brake confirmation; it does not qualify automatic Park/brake inputs.

### Installed identity

- Existing Pixel App `0.2.0-dev.51` retained; only the explicit instrumentation package
  was replaced. No App data, pairing, profile, binding or gauge configuration reset.
- Signed [dev.62](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.62),
  catalog generation 43, image 1,646,144 bytes.
- ELF SHA-256: `2b4713a4ce6032c7a2504f54e28bca8b7bab9d3fa9d6647c481aefb1d8c72292`.
- Bundle SHA-256: `6786fa4543274c2d2192853930d5420380be8c57482deee00d00701f935be47d`.
- Downloaded catalog/application signatures, target/layout, bundle/image hashes,
  descriptor and parked confirmation verified before the ordinary hosted App update.

### Automated device observations

| Check | Observation |
| --- | --- |
| Signed App/Wi-Fi installation | Passed; total fixture 118.604 seconds, update journey 75.071 seconds. Sending began at 19.219 seconds, verification at 59.353 seconds and exact healthy running confirmation at 75.070 seconds. These are stage observations, not wire throughput. |
| OTA acceptance | Expected version/ELF, healthy state 2, alternate slot and cleared update journal confirmed. |
| Automatic opening/resume | Fresh protected opening in 7,970 ms; foreground resume refreshed settings without a tap in 2,861 ms. Bluetooth off/on was not repeated. |
| Maintenance teardown | Hardware snapshot reported Wi-Fi mode 255 (deinitialized), then a fresh protected exact firmware read passed. Internal free/largest were 131,575 / 34,816 bytes in that snapshot. No serial heap minimum or loaded soak was measured. |
| Saved running setup | Revision 55 and digest `af613f471a6f25fe74b1eb412fa12dd83ee997dbc974fc89f4e505ba35686188` unchanged; three pages and both source bindings retained. |
| Action/coolant setup | Ordered Air up, Winch, ABS/TCS pages; three upward swipes within five seconds. Air up 300 seconds, Winch 60 seconds; primary coolant warning 105 °C / critical 115 °C retained. |
| CEL filters | All four exact TCM filters retained: U0140, U0121, P1DCA, P1DF3. |
| App action setup | Open/dismiss passed without saving. Direct protected reads confirmed Idle and ABS/TCS Unavailable, unqualified and not busy indoors; no unresolved command journal. No vehicle command invoked. |
| Phone preservation/cleanup | Final semantic comparison retained profiles, assignments, presentation and the complete alert policy. Original phone-alert monitoring was restored; update journal empty. Debug wake helper restored the original screen/charging settings. |

The initial USB connection was not visible to macOS/ADB; it later enumerated as
the authorized Pixel and the update proceeded. A private before/final preference
backup was kept. Phone alert monitoring was temporarily paused to avoid competing
Bluetooth reads, then restored. No Bluetooth or USB firmware recovery was used.

### Owner observation and field boundary

The owner confirmed normal pages, clean text, working swipes, three-up action entry,
horizontal action browsing and downward return on dev.62. This is separate from the
protected image/configuration readbacks and offline renderer tests.

Ready for the supervised parked **Air up/Winch** checks in the
[one-visit runbook](../development/jeep-action-qualification.md#one-visit-idle-test-candidate).
Start with a brief Start/Stop and verified normal idle before the full five-minute
Air up timer. Missing/stale coolant retains the at-most-one-minute fallback and a
new physical Start requirement; a known high value still stops control. The temperature stop limit remains configured.
Actual ECM identity acceptance, raised idle, immediate Return, thermal/fallback
behavior, adapter loss and independent child continuity on this image remain
vehicle observations to collect. ABS/TCS execution stays disabled. No claim of
loaded operation, automatic Park/brake verification or OOM immunity follows from
this indoor session.

Private backups, raw running configuration, screenshots and logs are under ignored
`artifacts/device-rollout-dev62/`. Website impact reviewed: no new public live-control
claim or website publication before the parked vehicle checks.

## 2026-10-27 dev.63 coolant status correction

PR #96 merged after all four Foundation checks passed (`40539808884`). Version-only
PR #97 also passed all four checks (`40542827440`) and merged as `361f6b8`.
The owner authorized merge, publication and App/Wi-Fi installation in this session.
Signed development release `dev-v0.2.0-dev.63` was built from exact checked source
`9a39b46dd6ff6efa8e679c0c06ab4136dc01d360` by release run `40544509171`, retaining
`parked_idle_test=true`. Catalog generation 44, catalog/application signatures,
board/layout/protocol and application descriptor were independently verified.

- Published image: 1,646,144 bytes; ELF SHA-256
  `fabcf978b786a587252459b250de83dfd74fe97388cf239f3e93e8f583c793db`.
- Before installation, the paired Pixel completed the scoped read-only automatic
  connection/read-chain test on dev.62 in 25.554 seconds.
- The existing App's hosted signed Wi-Fi journey passed in 110.626 seconds; update
  stages covered 73.603 seconds. Sending began at 15.477 seconds, verification at
  57.540 seconds and authenticated healthy confirmation at 73.588 seconds.
- Exact dev.63 ELF/version, alternate OTA slot, healthy state 2 and cleared update
  journal were confirmed. Configuration revision 55, digest, display settings and
  profiles remained unchanged.
- Final private preference comparison matched profiles, gauge association,
  presentation and the full restored alert policy. Temporary phone monitoring and
  debug wake settings were restored. A phone call was foreground after completion;
  no subsequent App UI navigation was performed.

The active action page now uses coolant-unavailable status bit 4, independently
of local-gauge Start bit 3. Runtime tests cover an RPM-only dashboard without a
coolant page. This changes no control admission, cutoff or fallback timer policy.
Owner-reported Air up success occurred on dev.62; its actual countdown/coolant
availability and thermal/recovery behavior remain unconfirmed. The owner subsequently confirmed clean normal pages, working swipes and three
upward swipes opening the action pages on physical dev.63. ABS/TCS remains disabled.

Private evidence is retained in ignored `artifacts/device-rollout-dev63/` and
`artifacts/dev63-published/`. Website impact reviewed: no new public feature claim
or publication is warranted for this status correction.

## 2026-10-27 approved review 4 implementation

The owner approved review 4 in chat and requested implementation. Its immutable
review assets retain the preceding per-screen feedback; this approval does not
claim a release, installation or vehicle qualification. Installed dev.63 remains
unchanged. The [runtime contract](../architecture/vehicle-actions.md#ordered-action-pages)
owns the behavior, rather than duplicating it in the gallery.

Prepared source replaces the repeated idle confirmation with a 900 ms local hold,
uses one-tap Stop from either idle page, removes the extra success screen, and
renders green/red/yellow pending-state arcs. High coolant blocks Ready/Start after
verified restoration. Missing coolant retains an explicit Continue then hold and
its existing one-minute cap. App configuration uses compact rows, one editor and
one actionable missing-limit warning; pressure estimates and pending setup retain
their existing owners. No command bytes, qualification flags or saved schemas change.

The production LVGL tests exercise early release, long-press versus hold duration,
movement and hit-region exit, repeated touch, status changed-and-restored, scope
changes, critical attention, missing coolant, direct Stop targeting, thermal
recovery, connection loss, unknown retention, empty/removed pages and 30 repeated
entries without widget growth. Native frames check circular glyph bounds, shared
alert/CEL placement and both ordinary and parked-candidate builds. The arc reuses
the existing UI tick and one widget tree, with no new task, framebuffer or fonts.

Local evidence is under `artifacts/checks/ui-renders*`; test and build logs are
local artifacts. Host checks passed 129 Python tests, 11 review-store tests and native sanitizer
fixtures. Firmware checks include production renderer/config binding and ESP-IDF
5.4.1 ordinary/parked builds; linked-memory checks verify the existing 64 KiB
LVGL pool remains in PSRAM and internal static memory remains within budget.
Android evidence includes 235 debug and 210 release JVM tests, lint, APK/test
compilation and ten focused managed-emulator action-page tests. Internal static
memory is 164,346 bytes for the ordinary image and 164,434 for the parked candidate,
within the 196,608-byte budget; the linked LVGL arena remains 65,536 external bytes.

Remaining: signed release and App/Wi-Fi installation after explicit approval,
physical hold/glove/edge usability and animation/heap observations, then the existing
parked coolant/Stop/recovery checks. Website impact was reviewed; publish current
production visuals with a qualified rollout, without marketing the proposal as an
installed feature or coupling website publication to the build.


## 2026-10-27 dev64 action UI rollout

PR #99 merged as `ba9548b` after all four Foundation quality gates passed for
`2b0ecf9`. The archive repair restores review 2 and review 3 assets to their original
submission digests; twelve review-store tests pass. The first emulator attempt on
that final source failed while downloading an invalid Android system-image ZIP,
before running tests. The isolated retry passed.

App `0.2.0-dev.52` was installed on the Pixel with an in-place APK update. Its
runtime source is `a33cda4`; the remaining merged changes only restore archived
review assets and their regression test. The paired-phone action-page journey
passed in 13.029 seconds: compact rows, the Air up editor and configured coolant
limit appeared; closing the editor retained profiles, per-gauge assignments,
running revision/digest and display settings. Direct protected command reads
reported unavailable indoors, with no unresolved command journal.

The updated App's automatic connection/recovery journey passed in 68.023 seconds
against installed dev.63. Initial protected confirmation took 10.174 seconds,
resume 2.851 seconds and Bluetooth off/on recovery 12.377 seconds. Cancelled gauge
selection and the hardware/firmware protected read chain also passed. Revision 55
and its digest were unchanged. Phone profile, association and presentation files
matched the private backup byte-for-byte after the App upgrade. These are App
checks against dev.63, not dev.64 firmware qualification.

Signed development release `dev-v0.2.0-dev.64`, catalog generation 45, was published
from `2b0ecf9` with the scoped parked-idle option enabled. Its tree matches merged
main. The catalog and application signatures, bundle/image sizes and hashes,
board/layout/protocol and application descriptor were independently verified.
The image is 1,648,400 bytes, with ELF SHA-256
`83f4b71bee5c2ee71c100dd65774a8d7613d9667402f8cac3941c638c395ca4d`.

The Pixel disconnected from USB before the firmware installation started. No
image was sent; the gauge remains on dev.63. App/Wi-Fi installation, post-update
maintenance cycles and physical display/touch confirmation remain pending. The
phone's alert monitoring was paused for the session; its original policy and wake
settings are retained privately and must be restored when the Pixel reconnects.
Saved setup and before-install protected configuration are backed up.

The merged PR branch was removed locally and remotely. Private captures and
backups remain under ignored `artifacts/device-rollout-dev64/`. No USB/Bluetooth
firmware fallback, configuration send or vehicle command was performed. Remaining
Jeep qualification still covers deliberate Start/Stop, coolant cutoff/fallback,
recovery and loaded source continuity. ABS/TCS execution remains disabled.

## 2026-10-27 dev65 continuity and action UI rollout

The owner authorized PR #101 merge, dev.65 publication, App/Wi-Fi installation and
indoor checks. PR #101 merged as `3a09c9f`. Its reviewed source `71400f8` passed all
four Foundation quality jobs in [run 40602150266](https://github.com/lstepnio/ESP32OBD2/actions/runs/40602150266).
Both commits have tree `c1e6290eddd524ab6ba8899728e4b0b3ecdc4be9`.
The merged-commit quality run failed first in
`IdleActionsUiTest.unqualifiedCarCanSaveAWinchDraftButCannotStart` with an empty
saved-preset observation. One failed-job retry passed that test but failed
`ActionPagesUiTest.openingDurationPreservesPressureEstimateUntilTimeChanges`
while scrolling. These remain unresolved intermittent UI-fixture failures under
QUALITY-01, not a green merged-commit run. Publication selected the already-green,
reviewed source under the release runbook's exact-source prerequisite; no quality
check, environment protection or assertion was disabled.

[Release run 40608948806](https://github.com/lstepnio/ESP32OBD2/actions/runs/40608948806)
published signed `dev-v0.2.0-dev.65`, generation 46, from `71400f8` with the existing
owner-supervised parked-idle option enabled. Catalog/image signatures, bundle
hash/length, board, partition layout, protocol and app descriptor were independently
verified. The application is 1,655,456 bytes, image SHA-256
`4d7232c6f4844cdedcdafbb62db67187151d5543e1ce4172c397d5339df0b8bd`,
ELF SHA-256 `6e3b8145260040f6bb768f53b365f4e60f458150a355a8404ac375856d5ca6de`.
Public capability flags and qualification masks remain disabled; ABS/TCS execution
is disabled. No APK runtime change was needed; Pixel App dev.52 remained installed.

The hosted signed App/Wi-Fi journey passed in 107.020 seconds overall; its measured
installation portion was 74.509 seconds. Authenticated preflight took 1.160 seconds,
gauge Wi-Fi startup 2.706 seconds total, Android network availability 9.985 seconds
and flash preparation 2.652 seconds. Sending began at 18.161 seconds, verification
at 58.106 seconds and healthy running confirmation at 74.494 seconds. The alternate
OTA slot, exact version/ELF, OTA health 2 and cleared recovery journal were checked.
Running revision 56, digest, display settings and profiles were unchanged by OTA.
Before installation, one action-page protected firmware read failed; its bounded
repeat passed against dev.63. Post-update action-page checks passed in 14.093 seconds.

Five authenticated Wi-Fi open/status/close cycles passed in 68.569 seconds without
reboot, firmware change or configuration change. Opens took 1.596, 1.995, 1.892,
1.991 and 1.957 seconds; fresh session IDs and the unchanged boot nonce were checked.
Automatic connection/recovery passed in 71.613 seconds: initial protected confirmation
14.704 seconds, foreground resume 3.270 seconds and Bluetooth off/on recovery
13.424 seconds. Gauge-picker cancellation and the hardware/firmware protected-read
chain passed. The final hardware snapshot reported Wi-Fi deinitialized and
121,687 internal free bytes with a 31,744-byte largest block.

Display persistence passed in 70.569 seconds. It saved five-second cycling, resent
the same functional setup and confirmed it after restart in 30.852 seconds. That
intentional same-setup write advanced configuration revision 56 to 57. Exact JSON
comparison excluding `baseRevision` retained the five pages, source bindings,
four CEL filters and actions. Rotation 270 degrees, brightness 100%, Imperial units
and the original ten-second interval were restored; profile/assignment state was
retained. The newer revision/digest is the expected storage result, not a changed
dashboard. No vehicle command or code clear was invoked.

The owner separately confirmed clean normal pages, working swipes, three-upward-swipe
action entry, Air up/Winch/ABS-TCS navigation, downward return and no startup CEL
question mark. After the interval was restored, the owner confirmed ten-second page
cycling and resumption after a tap/swipe. Serial pause-reason changes also observed
maintenance, touch, actions and return to running; those logs do not prove every
physical gesture or paused-state combination.

The temporary showcase protocol journey passed in 178.011 seconds, observed all
21 distinct frames, then stopped playback and checked unchanged revision 57,
digest, settings and profiles. This establishes protocol/render lifecycle coverage,
not owner visual approval of every fixture. Across 31 post-dev.65 memory snapshots,
minimum sampled internal free memory was 97,443 bytes, minimum-ever internal free
96,059 bytes and the smallest observed largest block 31,744 bytes. No allocation
failure or worker queue drop was observed. Minimum sampled stack margins were:
app 1,568, UI 2,532, primary adapter 792, child adapter 784, configuration 4,264,
OTA 3,032, Wi-Fi server 6,036 and Wi-Fi command 2,588 bytes. Adapter margins still
need loaded command/diagnostic measurement; these are bounded indoor observations.

Phone profile and presentation files matched the initial backup byte-for-byte.
The original notification-monitoring preference from the interrupted dev.64 session
was restored without replacing other current policy entries. The wake helper
restored its saved timeout/charging settings and reported inactive. The App was
reopened normally; serial and update-log captures were stopped. PR #101's branch
was removed locally/remotely, and the temporary signed-release branch was removed
after publication. Private backups, logs and verification results remain under
ignored `artifacts/device-rollout-dev65/`; no bonds or setup were erased and no USB
or Bluetooth firmware fallback was used. This evidence is consolidated into the
existing rollout documentation PR rather than creating another status document.

Remaining Jeep gates are loaded ECM/child-TCM page-cache continuity, real response
cadence/fairness, independent loss/return and parked idle Stop/coolant/fallback
timing. Indoor absence/retry and screen fixtures do not qualify vehicle telemetry,
all gears, thermal behavior, glove/edge usability or OOM immunity. Website impact
was reviewed; vehicle-performance claims and refreshed marketing captures remain
subject to loaded qualification and the existing daily publication cadence.

## 2026-10-27 startup CEL investigation

After the dev.65 rollout, the owner reported a CEL icon without a question mark
for several minutes while starting both the gauge and Jeep. This is an in-vehicle
observation, not an indoor regression. The owner confirmed the dash CEL stayed off
and the gauge icon cleared without code clearing. No raw startup trace has yet
attributed that occurrence to a particular ECU.

Source inspection confirms unknown initialization alone does not draw the icon.
A usable MIL-On response does, and the four scoped TCM filters cannot suppress it
until the existing complete/reconciled-code proof is available. A first failed
stored/pending request previously waited at least 60 seconds; permanent waited at
least 120 seconds. This is a reproducible slow-acquisition path consistent with
filter warm-up, not proof of the owner's actual startup cause. Genuine MIL-On or
unfiltered fault evidence is another explanation and must remain visible.

The initial dev.66 commit (`447b622`) distinguishes uninitialized/transient diagnostic jobs from valid
or definitively unsupported answers. Cold retries start at two seconds and back
off exponentially to the existing 240-second cap, retaining normal-reading
reservation, global background spacing and control priority. Known/unsupported
jobs keep established backoff; new MIL/count evidence resets category acquisition.
There is no blanket startup warning blackout, new suppression policy or wire change.
Presentation-change logs record endpoint, usable MIL/bit, reported/visible/filtered
counts, completeness and suppression for the next real capture.

Native sanitizer fixtures cover both scheduler endpoints, all four job kinds,
eight retry levels/cap, unsupported answers, settled failures, sibling cadence,
changed-MIL invalidation and clock wrap. A production scheduler/state/filter fixture
also covers the four actual scoped TCM rules: initial unavailable confirmed list,
two-second retry eligibility, complete filtered proof, then an added unfiltered
fault that remains visible. Restoring the old retry policy makes the
new timing assertion fail; the final implementation passes. Host checks passed
130 tooling tests, 12 review-store tests and native fixtures, including unchanged
diagnostic/filter proof rules. Normal and parked ESP-IDF builds and memory gates
passed. Scheduler records remain 376 bytes each; static internal memory remains
164,434 / 164,522 bytes and the 64 KiB LVGL pool remains in PSRAM. The new view log
retains two 32-bit words on the existing App tick stack and emits on changes. Loaded stack margins,
radio timing and physical startup behavior remain unmeasured for this candidate.

Next qualification: record `diag_view` transitions while starting the gauge and
Jeep, compare the physical dash lamp, verify fresh TCM lists match the four filters,
and measure time to suppression alongside normal readings. Include a genuine
unfiltered warning case. Publication/installation are not claimed; dev.65 remains
the installed image. Website impact was reviewed; appearance and product claims
remain unchanged pending actual qualification.

## 2026-10-27 action progress cleanup

The owner requested removal of the green play triangle and red stop square from
Air up. Prepared dev.66 removes both decorative glyphs from the shared action
renderer; colored progress arcs, Start/Stop controls, hold handling and the yellow
reconnection link remain. No command, timer, protocol or allocation is added.

Normal and parked production LVGL fixtures passed. Comparing all 61 frames in each
variant changed only Starting, Stopping, Verifying and thermal-stop frames; all
other frames remained byte-identical. Visual inspection confirmed the two glyphs
are absent while arcs and controls remain. Host checks and both firmware builds
passed. These are source/render checks, not observations on the physical gauge.
Dev.65 remains installed. Website impact was reviewed; no publication or historical
review-asset rewrite is included in this small UI change.

## 2026-10-27 intermittent CEL and minute polling

The owner subsequently reported intermittent CEL appearance and requested less
frequent checks. Inspection found that permanent-code presentation expired after
120 seconds, exactly its nominal refresh interval. A delayed/failed refresh can
therefore remove all-filtered proof while an unchanged MIL-On remains usable.
This reproduces a presentation path; attribution of the owner's actual occurrence
still requires a raw loaded trace.

Prepared dev.66 now limits automatic MIL checks to once per 60 seconds per endpoint,
including cold failures. Stored/pending steady cadence remains 60 seconds and
permanent 120 seconds. Incomplete initial code lists retain their bounded faster
retry path so filter classification does not wait a minute after one lost response.
Explicit ECU-clear verification is independent. Changed MIL/count comparison uses
known same-session evidence even after raw freshness expires, so the slower cadence
does not prevent acceleration of the required category refresh.

Gauge-only last-successful MIL presentation lasts at most three minutes; compatible
code-list presentation lasts at most ten minutes, spanning normal refresh and a
four-minute capped retry. Original observation timestamps remain unchanged. Raw
phone/history freshness remains 60/120 seconds; no stale observation is relabelled
as a new raw result. New unfiltered code data, changed MIL/count, incompatible or
unsupported evidence, disconnect and scope reset retain their invalidation rules.
The tradeoff is up to a minute plus transport admission to detect a new MIL, and
bounded last-known gauge presentation during absent replies; this does not disable
actual diagnostic warnings or turn unknown coverage into verified Off.

Native regressions cover healthy/cold-failed MIL rate limits for both endpoints,
loaded fairness from eligibility rather than from a not-yet-due diagnostic, filtered
proof across repeated minute refreshes and a failed permanent request, original
clock expiry, wrap, filter removal, changed count, same-count unfiltered replacement
and disconnect. Raw packet fixtures remain unchanged. Host tests, C format/host
lint and normal/parked firmware builds passed. No new task, heap buffer, protocol
or saved-configuration migration is introduced. Dev.65 remains installed; no new
physical-device or loaded-startup qualification is claimed. Website impact was
reviewed; publication/capture awaits the existing delivery cadence and qualification.


### Shared availability review correction

The merge review identified an additional raw/retained boundary: the shared
MIL/fault producer still used strict raw freshness. With 60-second scheduling and
a successful 500 ms reply, this could create unavailable/recovered journal events
on every otherwise healthy refresh. Merge was held for this finding.

The existing producer is now isolated in `diagnostic_alerts.c` and consumes the
same single retained snapshot as the gauge face. Shared event value timestamps
come from the original observations, while event-transition time remains current.
Raw diagnostic packets keep their strict freshness and failed-attempt evidence.
A native production-helper/state/event replay covers five minutes of successful
60-second-plus-500-ms replies for both endpoints: exactly the initial MIL/fault
entries occur, with no availability chatter or re-dated values. Snapshot contention
emits no false transition. Prolonged expiry emits one unavailable event, repeated
ticks do not repeat it, and real Off/On/disconnect still resolve/raise/invalidate.
The shared Android raw fixture remains unchanged. This corrects alert availability,
not the raw ECU record or vehicle-control guards; CI must pass the corrected head
before merging or publication.


## 2026-10-27 combined dev66 rollout

The owner authorized PR #103 merge, signed publication, App/Wi-Fi installation,
indoor checks and branch cleanup, then explicitly authorized coordination with
“eGauge Development (SmartPage)” for one combined release. PR #103 merged as
`1d3510f`; PRs #104/#105 contribute the approved Smart/display work. Combined reviewed
source `fd8fae652ada9176d223f16b97158858bcd2f031` passed all four checks in
[quality run 40635471472](https://github.com/lstepnio/ESP32OBD2/actions/runs/40635471472).
Its tree equals merged main `639511bd8a22312a5514f3c39e30d4f09b55f017`.
The valid shared-alert availability review finding was corrected before merge;
no branch rule was bypassed. App dev.53 / versionCode 53 and matching scoped test
APK were installed first. Its schema-17 to schema-18 migration changed only the
schema version, preserving every other profile field, assignments and presentation.

[Signed dev.66](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.66)
was published through [run 40636609288](https://github.com/lstepnio/ESP32OBD2/actions/runs/40636609288)
from that exact source. All 46 prior catalogs were checked before selecting generation
47. Catalog/image signatures, expiration, board/layout, descriptor version and source
tag were verified before transfer. The release retains the owner-supervised parked-idle
option; ordinary control masks, ABS/TCS execution, Auto probe/qualification and public
capability flags remain off.

- Image: 1,670,464 bytes; SHA-256 `3868517bc74d20c3335f976bef942ca7293d398a8d5c905656040779bfbd386a`.
- Running ELF: `6bde80eca04199bb2ed401ded08b23e53df9ea4d1b7923628deefb5117f7ac97`.
- Full hosted App/Wi-Fi fixture: one test passed in 107.613 seconds; installation
  journey 73.432 seconds. Selected stages were Sending at 16.799 seconds,
  Verifying at 57.636, Restarting at 58.785 and healthy exact identity at 73.423.
  These are sampled stage observations, not wire-throughput measurements.
- Alternate OTA slot, health 2 and an empty recovery journal were confirmed.
  The actual initial running revision was already 60; OTA preserved it and the
  exact document bytes/digest `cb7e48d2a66ec17ff300e5adaabc56ffd929e13b7d9106cb0e0d8390328b0045`.
  No older documented revision was restored. Five pages, two transport sources,
  four TCM filters, thresholds, action settings and display preferences remained.
- Five authenticated Wi-Fi open/status/close cycles passed in 61.524 seconds,
  retaining boot nonce, signed image, revision 60, digest and settings. Protected
  opens took 1.613 to 1.812 seconds. No reboot was observed during those cycles.
- Automatic open/resume, Bluetooth off/on, add-gauge cancellation and hardware-to-
  firmware read chaining passed in 74.193 seconds. Confirmation took 14.058 seconds
  on open, 3.419 on resume and 12.958 after Bluetooth returned. Wi-Fi mode 255 and
  cleared initialized-feature bit confirmed driver teardown. Owner identity,
  exact healthy image, settings, configuration and profiles remained unchanged.
- Phone profile, association, presentation, connection-permission, alert-policy,
  update-hold and recovery preference files were byte-identical across the standard
  installation/checks. Expected changes were the generation-47 catalog cache and
  Android's compilation-profile marker. Raw backups remain private.

Across the verified dev.66 window, passive serial capture recorded 31 lifecycle
memory snapshots and ten periodic health samples. Minimum sampled internal free
was 97,515 bytes, minimum-ever 96,119, smallest sampled largest block 31,744 and
minimum sampled PSRAM free 1,934,984. Allocation-failure counters stayed zero;
no queue drop, heap corruption, stack-canary panic or watchdog marker was observed.
Minimum observed stack margins were App 1,444, UI 2,996, primary adapter 752,
child adapter 760, configuration 4,256, OTA 3,024, Wi-Fi server 6,040 and Wi-Fi
command 2,576 bytes. Adapter/control deep-stack paths still require loaded
measurement; these are bounded indoor observations, not production/OOM guarantees.

Targeted Smart persistence then passed, intentionally advancing configuration
60 to 61; only baseRevision changed in the functional JSON. Original ten-second
cycling and Smart Off were restored, along with rotation 3, brightness 100 and
Imperial units. The final protected read confirmed the same healthy dev.66 ELF,
revision 61 / digest `787bc86f36ab902f059738a9c157c29a27316dac8076d3c1620b176ee4f1ecb0`
and no unresolved command state. Original phone monitoring was restored; the full
alert policy is semantically equal to its pre-session backup, and assignment,
presentation and connection preferences remain byte-identical. The wake helper
is inactive with original 60,000 ms timeout and charging flags 15 restored. Serial
capture was stopped. This session did not erase bonds or user data.

Smart/display capability, same-setup restart/persistence, interval/Smart restoration
and the single owner physical observation are owned by the
[display rollout evidence](display-evolution.md). They follow the standard checks
above; their intentional setup revision increment must not be attributed to OTA.
The expected indoor no-data screen is an icon-only logo; one tap wakes readings
and consumes that gesture before ordinary swipes/actions.

Remaining Jeep qualification includes actual startup MIL/filter attribution,
unfiltered and partial-coverage warnings, loaded primary/child freshness and radio
coexistence, adaptive/Smart timing and parked command/coolant/Stop stack margins.
Indoor absence and source/native fixtures do not prove those paths or OOM immunity.
No bonds or setup were erased, no ECU command was executed, and no USB/Bluetooth
firmware fallback was used. The #103 feature branch and temporary release ref were
removed; source remains pinned by the immutable release tag. Website impact was
reviewed: downloads follow the published release, while marketing capture/publication
remains under the separate daily cadence after physical qualification.
