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
