# Android companion

The companion opens around the round gauge preview. Gauge, Car and Settings are the default destinations. **Show advanced tools** in Settings reveals Expert. Customize keeps the selected page's preview, reading, layout and alerts in one workspace. Reading and layout choices open in focused sheets. Add page asks for a reading first; Manage pages handles ordering and removal. Alert changes stay in a local form until Save, with correct units and inline validation. Review and send remains in reach at the bottom.

The [design audit](../docs/design/redesign/audit.md), [interactive concept](../design/prototype/index.html), [native fixture gallery](../design/prototype/native.html) and [validation record](../docs/development/android-core-ux-validation.md) distinguish implemented software, example screens, physical phone observations and protected gauge readback.

## Build and run

Use JDK 17 and Android SDK platform 36. From `android/`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin :app:lintDebug
```

From the repository root, run `python3 tools/validate.py` in the repository virtual environment. Set `ANDROID_HOME` or use an ignored `local.properties` with `sdk.dir`. Install with `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`.

Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21 and Compose BOM 2026.05.00 remain pinned. Material 3 `1.5.0-alpha13` supplies `MaterialExpressiveTheme` without widening the existing API 36 toolchain. This deliberate prerelease UI dependency needs production review. Dependency verification remains enabled. No store release is configured.

For physical debugging, run `python3 tools/adb_debug_awake.py start` before the session and `stop` afterward. The script preserves the phone's original wake settings. A manual lock still requires the user to unlock the phone. The debug app keeps its window awake while visible; update delivery remains foreground-only.

## Screens and capability boundaries

| Destination or flow | Main job | Current boundary |
| --- | --- | --- |
| Gauge | View pages, customize, review and send | Values and histories are visibly labelled Preview; no live telemetry |
| Setup | Find a gauge and authenticate this phone using its physical code | Real Android bonding and protected owner readback; adapter setup is unavailable |
| Customize | Choose readings and page order, numeric/arc/bar/trend/dual layouts, add reading-specific alerts, preview states, review, send | Capability and projection checks block unsupported layouts/readings; vehicle support remains unverified until discovery |
| Car | Choose a local vehicle profile, inspect the last fault snapshot and plain-language explanations | No adapter pairing or code clearing; no vehicle traffic is fabricated; incomplete/stale results remain visible |
| Settings | Local gauge name, rotation, updates, appearance, advanced toggle, About | Name is local; brightness is unavailable; Forget opens Android's actual Bluetooth settings |
| Updates | Check installed version and recover an uncertain update | Production OTA is unavailable; development releases stay in Expert |
| Expert | PID examples, Mode 01 decoder, bounded Mode 01/09/22 request check, second-adapter preference, diagnostics, self-check, signed development packages | Custom definition execution, vehicle discovery and simultaneous adapters remain unverified/unavailable |

Opening or returning to the app starts foreground discovery automatically after Nearby devices permission. A remembered gauge is selected exclusively; otherwise one discovered gauge opens physical-code pairing and multiple gauges require a choice. Unbonded devices never receive an automatic configuration read. Existing owner bonds reconnect without a tap.

The controller makes bounded reads, closes each connection and refreshes about every 20 seconds. **Gauge ready** means a recent protected check, not continuous telemetry. Failures rediscover the same target with 2, 5, 10, 20 and then 30 second pauses; Bluetooth-off and permission states stay visible. Leaving the foreground cancels automatic work. A user read/send/update preempts and awaits automatic cleanup before taking the existing operation lease. Automatic work never retries a configuration send or update. Persistent unknown/rollback outcomes survive reconnects. New-pair and permission-denied UX still require physical qualification. Ordinary screens show human sentences and icons. Technical facts are available in Expert > Device data after enabling advanced tools. No hidden gestures reveal advanced tools.

Compact windows use a navigation bar; windows of at least 600 dp use a rail. Wider content uses two panes where space permits, and large text stacks it. A separating hinge is excluded from the content area. Edge-to-edge insets and predictive Back are handled by the app shell. Physical foldable and gesture-navigation qualification are still separate checks.

## Implementation ownership

| Location | Responsibility |
| --- | --- |
| `MainActivity.kt` | Android permissions, file picker, window/fold integration and activity lifetime |
| `ui/CompanionApp.kt` | Navigation, persistent operation banner, global errors and activity progress sheet |
| `ui/home`, `ui/customize`, `ui/car`, `ui/settings`, `ui/expert`, `ui/setup` | Stateless feature composables and UI callbacks |
| `ui/state` | Immutable per-screen contracts, presentation mapping, central transaction copy, allowlisted technical details and appearance preferences |
| `:core:designsystem` | Generated tokens, Expressive theme, semantic colours, components and shared preview fixtures |
| `connection/ForegroundConnectionController` | Foreground-only read loop, bounded retry cadence and preemption for user operations |
| `AppViewModel` | Existing device operations and profile methods, bridged to a lifecycle-collected `StateFlow<CompanionUiState>` |
| Existing codec, projection, storage and transport classes | Unchanged wire/security/data rules |

`design/tokens.json` is the source for Android and browser tokens. Run `python3 tools/generate_design_tokens.py`; `--check` detects drift. Legacy gauge tokens were retained without renaming. This work changes no firmware or LVGL renderer.

## Configuration trust

The existing typed transaction states remain authoritative. **Saved & running on gauge** requires the expected running revision and SHA-256, `running=true`, the trial flag cleared and no previous-generation recovery. A storage acknowledgement, completed upload or generic successful read cannot show that confirmation. The update stepper cannot reach Done while restart confirmation is pending. After app recreation, the same confirmation may be recovered only if the complete locally projected bytes match the verified saved digest and the strict running proof also matches; comparing visible fields alone is insufficient.

Review lists every transmitted page, binding, layout and alert setting. A protected read supplies the base revision and digest; the existing sender checks both again before beginning. Conflict, rejection, rollback and unknown outcomes remain visible, and recovery requires a fresh check plus explicit review before another send. Page browsing alone does not change the transmitted settings. The existing signed-update interruption journal and reconciliation rules are retained.

A verified saved configuration can be adopted into the phone from Expert diagnostics only when the existing mapper can represent it safely. Unknown fields never count as matches. Expert > Device data includes the complete saved JSON, comparison fields, PID/service/source, runtime identity, hardware/partition metadata and package signatures. Its allowlist excludes Wi-Fi sessions, credentials and bond secrets. The technical report has no clipboard action.

Historical hardware records remain available: [configuration transfer](../docs/development/numeric-config-transfer-validation.md), [saved readback](../docs/development/active-document-readback-validation.md), [comparison](../docs/development/draft-comparison-validation.md), [reconciliation](../docs/development/config-reconciliation-validation.md), [signed updates](../docs/development/update-live-validation.md), [update recovery](../docs/development/update-recovery-validation.md), [hosted release journey](../docs/development/github-hosted-update-validation.md) and [Wi-Fi self-check](../docs/development/wifi-transport-security-validation.md). They are historical evidence, not new claims from this redesign.

## Previews and image checks

Component `@Previews` use the eight states in `design/fixtures/ui-states.json`. Round-preview parameters cover all five layouts and normal, warning, critical, stale and offline states. Debug-only screen fixtures render 14 production screens in compact/expanded, light/dark and 200% text previews. Every screen fixture says Example and has no device or repository connection.

`GoldenScreenshotTest` compares 72 checked-in images: 16 component states and 56 screen/layout/theme combinations. The baseline device is a Pixel 10 Pro, Android 17/API 37, using a fixed density and font scale inside the fixture renderer. Run on that baseline environment; a different font rasterizer or platform may need a separately reviewed baseline. Missing images fail. Differences above 0.05% of pixels, allowing three channel levels of antialias variation, fail and produce a diff image.

Build `:app:assembleDebug :app:assembleDebugAndroidTest`, install both APKs with `adb install -r`, then run:

```bash
adb shell am instrument -w -r \
  -e class com.lstepnio.egauge.GoldenScreenshotTest,com.lstepnio.egauge.PrimaryJourneyTest,com.lstepnio.egauge.CustomizeJourneyTest,com.lstepnio.egauge.PresentationAccessibilityTest \
  com.lstepnio.egauge.test/androidx.test.runner.AndroidJUnitRunner
```

Manual APK installation preserves local app data during these checks. To deliberately record new goldens, run only `GoldenScreenshotTest` with `-e recordGoldens true`, pull `/sdcard/Android/data/com.lstepnio.egauge/files/screenshots/` into `app/src/androidTest/assets/goldens/`, inspect every changed image, rebuild the test APK, then rerun without that flag. Ordinary test runs never update expected images. The [Customize review](../docs/design/redesign/customize-review.md) includes the revised flow and before/after images. `CustomizeJourneyTest` uses in-memory examples for editing tests; `PrimaryJourneyTest` asserts that viewing Customize leaves the phone's saved choices unchanged. Both accept `-e captureScreens light` (or another label) to save physical window captures.

`PhysicalConfigurationJourneyTest` is skipped unless explicitly run with `-e allowGaugeWrite true`. On a bench gauge it reads and validates a restorable original setup, chooses three readings through the UI, sends them, waits for strict running proof, then restores and verifies the original layout using a fresh protected base. It increments real configuration revisions. An uncertain first write is never retried automatically. Preserve the phone's non-secret local profile choices before this opt-in run and restore them afterward. It never queries a vehicle, clears codes, installs firmware or changes capability flags.

## Privacy and remaining qualification

No account or analytics. Android owns the BLE bond; the app stores no pairing code or bond secret. Backup remains disabled. Appearance preferences and profiles are local, and signed package verification is unchanged. Keep public capabilities disabled until the complete path is implemented and physically verified.

The two-minute first-time setup target remains unverified because adapter setup is not implemented. Fresh-owner code association, live OBD data, real code clearing, dual adapters, new update/rollback hardware runs, physical foldables, full RTL, performance measurements and participant usability testing remain separate qualification work. See the validation record for the checks actually performed in this change.

## Automatic connection verification

Regular activity tests pass the debug-only `debug_disable_auto_connect` intent extra, so screenshots never discover nearby hardware. Release builds ignore this extra. To run the separate paired-bench read-only journey after installing both APKs:

```bash
adb shell am instrument -w \
  -e class com.lstepnio.egauge.AutomaticConnectionJourneyTest \
  -e allowGaugeRead true -e toggleBluetooth true \
  com.lstepnio.egauge.test/androidx.test.runner.AndroidJUnitRunner
```

This opt-in test checks opening, activity stop/start and recovery after temporarily turning phone Bluetooth off/on. It restores Bluetooth in `finally`, verifies the same configuration revision/digest, and never calls configuration, firmware or car operations. Its screenshots and result are saved under app external files in `automatic-connection/`.
