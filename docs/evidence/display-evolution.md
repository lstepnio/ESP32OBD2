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

No phone or gauge was accessed and installed dev.65/App dev.52 were not changed.
Authorized rollout must verify startup brightness/rotation, icon wake consumption,
all ten pages and mixed Dual + arc layouts, source loss/recovery, loaded sampling,
Smart comfort and Wi-Fi/BLE memory. Auto orientation additionally needs every
axis/touch transform, mounting/motion, bus failure and worker-stack check before its
qualification gates can be enabled. No public capability flags were promoted.

Website impact reviewed: these are uninstalled source changes. Preserve current
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
