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
