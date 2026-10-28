# Display evolution offline checks, 2026-10-27

> Historical evidence. Use [current state](../current-state.md) for installed facts
> and [backlog](../backlog.md) for remaining work. This records source checks, not a
> release, installation or physical qualification.

## Scope and results

The candidate builds on Smart cycling PR #104. It implements ten saved pages,
Dual + arc, shared explicit gear colors, icon-only boot/no-data presentation,
reviewed hidden-PID stability backoff and a default-off Auto-orientation candidate.
[Display contract](../architecture/display-experience.md),
[sampling contract](../development/reading-catalog-alerts.md#display-and-polling-classes)
and [orientation contract](../architecture/hardware-and-transports.md#automatic-display-orientation)
own behavior and compatibility.

Executed offline:

- Host suite (135 primary and 12 website tests), native ASan/UBSan policy/storage/config fixtures and production LVGL
  fixtures passed. Tests cover original timestamps/source generation, touch/data
  wake, priority preemption, ten-page fairness, selected arc index and rejection,
  gear colors and stable-window reset/failure/wrap. Orientation covers dwell,
  frozen samples, retry and transform-failure backoff; no physical sensor claim.
- Normal and parked-idle ESP-IDF 5.4.1 firmware builds passed. The optional
  probe-enabled worker branch also compiled against IDF; both runtime gates remain
  off in distributed defaults.
- Android debug JVM: 243 tests; release JVM: 217 tests, all passed. Debug APK,
  instrumentation compilation, Android lint and Kotlin formatting passed.
- Focused managed emulator checks: 21 passed. These cover migration/capability
  preflight, ten-page editor, Dual + arc, gear preview and conditional Auto/manual
  choices. The screenshot fixture captures its unique Compose modal root rather
  than depending on Android's transient active-window accessibility root.
- Generated contracts/catalog/tokens and maintained documentation checks passed.

## Bounded resource evidence

The production LVGL host fixtures now use a fixed 64 KiB arena rather than host
malloc. The final normal/parked fixture monitor reported 62,112 managed bytes,
51,064 free, largest block 38,728 and 18% usage. This is the end-of-fixture snapshot,
not a radio/DMA/internal-heap peak. All tested compositions fit the bounded arena.
The logo reuses four retained drawing objects, no bitmap/framebuffer.

Final linked memory gate results:
normal internal static 164,522 bytes; parked candidate 164,626; external fixed BSS
91,632 in both. The existing 65,536-byte LVGL arena remains in external BSS.
Against the Smart-only normal build, that is +88 internal static and +656 external
fixed bytes. The stability array accounts for 640 external bytes. Two additional
runtime page records occupy approximately 216 additional PSRAM bytes. Ten-page
Smart state plus alert projection remains below its 4 KiB compile-time guard.
Both final linked gates passed; static placement alone cannot establish fragmentation, stack or coexistence margins under live traffic.

## Remaining acceptance

At the offline checkpoint, no phone or gauge had been accessed and installed
dev.65/App dev.52 were unchanged. The later rollout below is a separate observation.
Authorized rollout must verify startup brightness/rotation, icon wake consumption,
all ten pages and mixed Dual + arc layouts, source loss/recovery, loaded sampling,
Smart comfort and Wi-Fi/BLE memory. Auto orientation additionally needs every
axis/touch transform, mounting/motion, bus failure and worker-stack check before its
qualification gates can be enabled. No public capability flags were promoted.

At the offline milestone, website impact was reviewed while these were uninstalled
source changes. Preserve current
published capability claims; refresh circular production visuals and roadmap when
qualified under the existing publication cadence, outside the build path.

## Authorized rollout preparation

The owner authorized merging PR #104 followed by #105 after CI, installing the App,
publishing/installing through App/Wi-Fi and indoor checks. PR #104 merged as
`1111038`. Source preparation uses firmware `0.2.0-dev.66`, App `0.2.0-dev.53` and
retains the existing owner-supervised parked-idle release option. Auto orientation
probe/qualification, ABS/TCS execution and public capability flags remain off.
This paragraph records authorization/preparation only; installed outcomes require
exact signed post-boot and setup-preservation evidence below.

The explicit `verifyDisplayEvolution=true` variant of the existing physical display
persistence fixture checks `mp:10`, Dual + arc, Smart support and absent unqualified
Auto. It enables Smart at five seconds, verifies preservation across a same-setup
restart, and restores both the original interval and Smart preference in cleanup.
It retains the exact functional dashboard; expected configuration revision advances
by one. This is a test procedure until its device outcome is recorded.

The full Wi-Fi installation fixture compares saved display values separately from
additive negotiated Smart-policy support. A newly supported policy version is not
a preference change; support cannot regress, and every saved field/revision still
must match exactly. This avoids treating legacy-to-Smart discovery as data loss.

## 2026-10-27 combined rollout: merges and App readiness

The owner authorized PR #104 then #105 merge, App installation, signed App/Wi-Fi
firmware rollout, indoor checks and branch cleanup. Separate owner authorization
in the mainline task consolidated #103 into the same release. #104 merged as
`1111038`; corrected #103 merged as `1d3510f`; #105 integrated both and merged as
`639511b`. Exact reviewed source `fd8fae6` passed all four Foundation lanes in
[run 40635471472](https://github.com/lstepnio/ESP32OBD2/actions/runs/40635471472).
Source and merge have identical tree `4ac779949748bc2b68de225898f4a55478c1165e`.
Only PERF-01 backlog prose required manual merge resolution; both notes survived.

App `0.2.0-dev.53` (versionCode 53) and its matching test APK installed on the Pixel.
The scoped read-only action/configuration/boot readiness journey passed one test in
13.693 seconds against the still-running exact dev.65 image. It read confirmed
configuration and command availability, opened/dismissed action setup and retained
profiles, assignments and running setup. No vehicle action, ECU clear or firmware
write occurred in this check.

The private before/after App backup comparison found only `schemaVersion:17 -> 18`
in profiles; every other profile field was exactly equal. Gauge-assignment and
appearance preference files were byte-identical. Original Phone alerts=true was
temporarily switched off through Settings for the isolated journeys; debug awake
settings retain the original timeout/charging values for final restoration.

The mainline task owns the single signed dev.66 publication and App/Wi-Fi install,
plus standard update/recovery/memory checks. The display task owns the subsequent
Smart/display persistence fixture, one owner physical check and final preference
restoration. Private captures/backups are retained outside Git under ignored
`artifacts/device-rollout-dev66/`. This split establishes one device owner at a time.

## Targeted Smart/display persistence and final restoration

The mainline task verified the signed dev.66 App/Wi-Fi installation and standard
recovery checks separately in [rollout evidence](action-pages-indoor.md). Its fresh
pre-update configuration was revision 60, not the older documented 57; OTA retained
60. No older status document was used as a live revision expectation.

The explicit `verifyDisplayEvolution=true` persistence journey passed one test in
69.212 seconds. It confirmed ten-page, Dual + arc and Smart capabilities, with
unqualified Auto still absent. Original settings were rotation 270°, brightness
100%, Imperial units, ten-second cycling, Smart Off and display revision 37.
Saving five-second Smart On advanced display revision to 38. The exact same
functional dashboard was sent, and Smart remained On after the configuration
restart. Running revision 61 and digest
`787bc86f36ab902f059738a9c157c29a27316dac8076d3c1620b176ee4f1ecb0` were confirmed.
The restart confirmation took 30.184 seconds. Cleanup restored ten seconds and
Smart Off through the normal revision-verified write.

Final protected firmware/action/setup readback passed one scoped test in 13.588
seconds against signed dev.66 ELF
`6bde80eca04199bb2ed401ded08b23e53df9ea4d1b7923628deefb5117f7ac97`.
Both vehicle commands remain unavailable without vehicle inputs; no unresolved
command journal was found and no vehicle operation or fault clear was invoked.
The final document differs from the original only by its expected `baseRevision`
59 -> 60. All five pages, both source bindings, four filters and actions are exact.

Phone alerts were restored On through Settings. Every original alert-policy value
matched after restoration (XML serialization order changed). Assignment, appearance
and connection-permission preference files were byte-identical; profile content
still differed only by schema 17 -> 18. The debug awake helper reported inactive
with original 60,000 ms timeout and charging value 15 restored. The source/release
branches for #104/#105 were removed after verifying the signed tag retains the
reviewed source; unrelated worktrees and private evidence were preserved.

The owner confirmed on 2026-10-27: “Logo and all navigation work.” This covers the
clean text-free logo, first-tap wake, reading-page swipes, three-upward-swipe action
entry, left/right action browsing and downward return. This physical observation
is separate from the automated protocol/render results above. Loaded ECU cadence,
Smart priorities/comfort, alert/control freshness, dual-source loss/return and
memory coexistence remain Jeep gates. All ten pages and the new arc layout still
need physical review; Auto orientation retains its separate sensor/touch gate.

## Extended indoor qualification: ten actual pages

The owner requested the remaining indoor checks. On installed dev.66/App dev.53,
`TenPageDisplayJourneyTest` passed one scoped test in 192.721 seconds including a
90-second physical observation window. It first proved exact local append/remove
reversibility, then appended pages without changing originals. Running ten-page
revision 62/digest `fcdf698c93417a711e0cd1415b03cab1a863b223fd800edbc951bd7146964b00`
and cleared trial were verified; both arc slots and gear in the non-arc slot read
back. Existing source bindings, alerts, filters and actions remained exact.

The owner confirmed “All ten pages work” for tap-through, 10/10, wrap, clean labels
and no freezes. Values were unavailable indoors; this does not qualify populated
arc geometry. The complete original five-page document and original App profiles,
assignments and settings were restored with protected running proof at revision 63,
digest `92dc3ddc1cfbd98f3c50e989725880bf1a575324e8af08998bd83ce79a235d0b`.
No firmware/vehicle command or code clear occurred during this round trip.

The next source candidate adds four production Dual + arc preview frames and a
separate opt-in sensor qualification build. Five focused native/overlay tests and
bounded production LVGL normal/parked fixtures passed. Both normal and candidate
IDF builds passed locally. These are preparation facts, not installed Auto evidence;
signed publication, physical sensor behavior and enabled-worker coexistence remain
pending the [indoor procedure](../development/display-indoor-qualification.md).
Website impact: this is a supervised qualification milestone, not default Auto
support. Preserve public capability claims until the corresponding evidence exists.

## Dev.67 candidate trial and automatic rollback

PR #107 merged as `4604057`, tree-identical to reviewed source `6b115ec`, after all
four quality jobs passed. With explicit owner approval, release run `40643721581`
published signed dev.67, generation 48, with parked-idle and orientation-candidate
options enabled and ordinary board qualification/public flags off. Downloaded
catalog/image signatures, bundle bounds/hash, board/layout and descriptor were
verified. Candidate ELF was
`d51e7d282feadaf4634696b45737b1b356c7531bd5dc926b2506a339b839b1c7`.

The first Wi-Fi upload was interrupted by a power-on reset; the owner confirmed
power was interrupted. A fresh protected dev.66/action/setup check passed in
13.637 seconds. This is not evidence of heap exhaustion or a firmware panic.

One controlled retry sent the image and booted dev.67. Immediately after BSP touch
initialization, the main task overflowed its stack, before orientation-worker
startup. The corrupted backtrace contains the stack-overflow hook, not a reliable
application callee. The bootloader automatically rolled back to dev.66. The App
correctly reported that the sent image was not confirmed as running. Dev.67 release
notes are marked withdrawn; immutable assets and consumed generation 48 remain.
Post-rollback protected readback passed in 19.462 seconds: healthy exact dev.66,
original configuration revision 63/digest, settings, profiles and assignments
unchanged; no unresolved vehicle command. No Auto or populated-arc physical check
passed on dev.67.

The replacement dev.68 raises the separate ESP-IDF main configured stack from
3584 to 4608 bytes. IDF's additional 512-byte logging allowance makes actual task
allocations 4096 and 5120 bytes respectively. This costs 1 KiB of internal heap;
`app_tick` remains 4096 bytes. The synchronous early-logo refresh is a deep startup
path; the exact overflowing callee remains unproven. The resolved configuration
has a compile-time lower bound, plus boot/main and sensor watermark diagnostics.
Offline builds do not prove the replacement boots or qualifies Auto. A new signed
release and installation require owner approval; the current healthy image remains
dev.66. Populated Dual + arc and Auto physical/recovery checks remain pending.

## Dev.68 startup repair and sensor-worker rollback

The owner approved PR #108 and the replacement through Mainline. All four checks
passed at `4ca3cef`; PR #108 merged as `a6f91ca`, tree-identical to that source.
Release run `40695736352` published signed dev.68 generation 49. Catalog and image
signatures, board/layout, descriptor and bounds/hash passed. ELF was
`a2c81a53c792c1225aaa7813c0b404f854e9a5237ca311a0683ebb9eca0573cd`.
Fresh pre-install readback passed in 16.384 seconds with original configuration
63/digest unchanged.

The App/Wi-Fi upload booted dev.68. Main startup completed with 1104 bytes of stack
headroom; the UI initialized and the 2048-byte orientation worker started. The next
failure was a stack overflow with a corrupted task name. The observed SP below the
static sensor-stack boundary and inside/below its adjacent task record strongly
identifies the sensor worker. Exact application call attribution is unavailable.
The first sensor identity-read path can reach I2C NACK/timeout error logging and
nested newlib formatters; a quiet wrapper does not bound vendor-driver stack use.

The trial automatically rolled back to dev.66. Protected post-rollback readback
passed in 14.106 seconds, retaining the original setup/settings and unavailable
vehicle-command state. Dev.68 is marked withdrawn; its assets/generation remain
immutable. Neither Auto nor populated arc physical qualification passed.

The owner then authorized debugging, correction and firmware publication/installation
as needed for this issue. Dev.69 prepares one aligned 4096-byte internal static
sensor-stack budget shared with task creation and enforced in the linked ELF memory
gate. This adds 2 KiB of static internal storage; main/app worker budgets remain
unchanged. Its acceptance still requires real boot, sensor health/error-path margins,
Wi-Fi maintenance recovery, touch and physical orientation checks. Default/public
qualification remains off. This is a qualification repair, not a website claim.

## Dev.69 authorized USB installation

All four quality checks passed at `7b88d6259a78e28c23a5ebca55959fa6e3b2779b`
in run `40697770543`. PR #109 merged as `fb9825b`, tree-identical to that source.
Release run `40699800004` published signed dev.69 generation 50. Catalog and image
signatures, descriptor, board/layout and bounds/hash verified independently:
1,678,144 image bytes, SHA-256
`9b520a9512f87cba455ccf5cd485f8987c30e622b4fe431974ca720ba69c5561`, ELF
`1681a3fcf6032f8d91d95e969f737904e91c7781148891dd4273f5a7abb7506e`.
Normal qualification/public flags remain off; this is an orientation candidate.

The owner took the Pixel and explicitly authorized USB for this installation only.
The complete 303,104-byte region `0x8000..0x51fff` was backed up privately, including
actual partition table, NVS, PHY, OTA metadata and both configuration partitions.
The actual table matched the reviewed layout. A newly downloaded, signature-verified
dev.66 rollback image matched the active slot's flash digest. Read-only S3/security
checks passed; no eFuse or security setting changed.

Only application slot `0x60000` was written from the signed release. The write took
93.3 seconds at 115200 baud and verified its hash. A separate full-region digest
verification passed. Before reset, every backed-up persistent byte was identical;
no bootloader, table, NVS, PHY, OTA metadata or configuration write occurred. The
private before/after region SHA-256 was
`9e8f8bf4033f910f6b845e980086952f2692f29f6697026b3a40ceab486727e6`.
This direct application repair retains the previous healthy slot metadata; it is
not a new OTA trial-confirmation or App/Wi-Fi installation result.

Boot selected the expected slot and logged dev.69, the matching descriptor hash
prefix, configuration revision 63 with five pages, rotation 270 degrees, brightness
100%, and owner association present. The main startup watermark was 1112 bytes.
The 4096-byte sensor worker encountered an initial I2C NACK, retried, and recovered
to changing timestamps/samples with 1632 bytes of remaining stack. This directly
exercised the vendor error/logging path that exceeded the old budget. Observed
maximum sensor tick duration was 24,304 microseconds, showing why a nominal 10 ms
I2C timeout is not a proven whole-call bound. App/UI and other workers progressed;
three-minute internal free memory was 125,491 bytes, largest block 31,744 bytes,
with zero allocation failures. No overflow, reboot or stalled UI worker was observed.
This short bench observation is not a soak or OOM-immunity claim.

A separate sensor issue remains: at rest, X stays near 1999 mg while Y/Z vary around
140/-160 mg. Sensor transport health is not pose plausibility. The unchanged
0.85..1.15 g orientation policy rejects this vector and holds the saved angle.
Do not invent a calibration or relax gravity guardrails to make Auto appear to work.
Readback/configuration and labelled physical poses are the next diagnostic evidence.
Populated Dual + arc, touch/rotation alignment, Wi-Fi maintenance coexistence and
protected exact App readback remain pending the Pixel/owner's return.

The phone disconnected before monitoring/wake restoration. Original monitoring was
On, timeout 60000 ms and charging wake value 15; the temporary session still has
monitoring Off and the debug awake helper active. Restore them on reconnection,
using the fresh private phone backup rather than overwriting later owner changes.
Website impact: no Auto support or new physical capability claim is promoted.


## Dev.69 return checks

On the Pixel's return, protected exact dev.69/ELF and healthy state 2 readback passed
in 16.482 seconds. Original configuration revision 63/digest, profiles, assignments,
display settings and unavailable/no-unresolved vehicle-command state were retained.
The production App reconciled its older interrupted-update record from authenticated
running identity; no phone journal was edited manually.

`WifiMaintenanceJourneyTest` passed in 42.109 seconds: protected opens took 1601
and 1946 ms, both authenticated Wi-Fi status/close paths passed, and the boot nonce,
exact image, revision/digest and settings remained unchanged. Serial observations
show sensor health becoming stale during maintenance and fresh afterward, holding
manual angle 270 degrees. This qualifies enabled-worker/manual Wi-Fi suspension and
recovery, not Auto pose changes or loaded dual-adapter coexistence.

The owner labelled vertical USB-down, vertical USB-up and flat screen-up poses.
X stayed near +1999 mg across them; flat Z was approximately -974 mg with Y near
zero. This narrows the fault to X-channel/sensor-state/data-path behavior rather
than an across-all-axes scale assumption. It does not prove defective hardware.
A requested ten-second complete power removal is still awaiting owner confirmation;
register readback or a documented known-state initialization may be needed afterward.
No calibration was invented and the gravity guard was not relaxed. Populated arc,
Auto orientation and physical touch alignment remain pending.

Before pausing for that physical step, phone alerts were restored On through Settings,
advanced-tool preference was retained, and the debug awake helper reported inactive
with the original 60000 ms timeout and charging value 15. Private return backups and
serial evidence were retained; the passive serial reader was stopped.

## Dev.70 sensor-state diagnostic preparation

The owner confirmed removing all gauge power for ten seconds and reconnecting flat,
screen up. On dev.69, X remained near +1999 mg while Y was near zero and Z around
-971 mg. This rules out the attempted power-cycle reset as a remedy; it does not
prove hardware failure. Review against QMI8658C sections 5.4/5.7 found the existing
burst offsets and signed little-endian conversion consistent with the datasheet.
No calibration offset or relaxed gravity admission is justified.

Source dev.70 verifies every CTRL1/CTRL2/CTRL7 write by a separate exact readback,
reads revision only after auto-increment is verified, and records an independent
signed two-byte X read before burst sampling. It uses documented +/-2 g normal-power
31.25 Hz (`CTRL2=0x08`) rather than the earlier 11 Hz low-power mode. Every tick still
performs at most one bus operation; mismatch/error uses the existing five-second
retry and stale-angle hold. The raw evidence snapshot is bounded to 40 bytes; the
4 KiB internal stack and ELF memory admission remain enforced. Sensor sample phase
is now 9; old phase-4 logs remain historical dev.69 evidence.

This tests a mode/init interaction without pretending that normal mode fixes the
observed X channel. Exact verified configuration plus matching direct/burst saturation
would strengthen hardware/channel fault evidence. Publication/install and actual
results remain distinct; ordinary qualification/public gates stay off.

The first dev.69 showcase run observed the requested 25 frames, then failed its final
comparison because the test's direct settings read omitted negotiated Smart metadata
(`smartPolicyVersion` 0 versus 1). The revision and all saved settings matched. The
fixture now supplies the negotiated Smart capability, preserving the full equality
assertion; this is not a runtime preference change or an ignored assertion. The old
21-frame/revision-57 result file was recognized as stale and not counted as current.
The corrected nine-frame round trip passed in 83.651 seconds, including the four
new Dual + arc frames, with original configuration/settings/profiles retained.
Owner visual/hold-exit confirmation remains separate from protocol observation.

The owner subsequently confirmed the populated Gear/RPM and Coolant/Gear arc
previews: “Clear, no overlap or clipping.” This is the physical visual observation;
the owner then confirmed hold-exit returned to normal pages with working navigation.
