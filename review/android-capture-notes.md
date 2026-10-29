# Android capture evidence

Captured 2026-10-29 (America/Denver), current source base
`083b0013363a697ab832179ee1af71b4456ca181` plus the new capture-only
`WebsiteReviewCaptureTest.kt`. No production Android code, existing test fixture,
build configuration or dependency was changed. Other concurrent review work is
outside this Android assignment.

## Delivered coverage

72 PNGs in `review/screens/android/`, with per-image hashes and provenance in
`review/screens/android/manifest.fragment.json`:

| Routes / variant | Phone light | Phone dark | Large light | Large dark |
| --- | --- | --- | --- | --- |
| Gauge, Car, Settings, Expert, Setup, Customize, Alerts, Alert settings, Car Advanced, Updates | Yes | Yes | Yes | Yes |
| Empty Alerts (same image state as default Alerts) | Yes | Yes | Yes | Yes |
| Setup searching/loading | Yes | Yes | Yes | Yes |
| Gauge permission required | Yes | Yes | Yes | Yes |
| Updates feed error | Yes | Yes | Yes | Yes |
| Populated synthetic current alert list | Yes | Yes | Yes | Yes |
| Customize review | Yes | Yes | Yes | Yes |
| Advanced hidden fault codes | Yes | Yes | Yes | Yes |
| Advanced second-adapter summary | Yes | Yes | Yes | Yes |

All data are synthetic. Default Gauge/Customize/Updates use production debug
`ScreenFixtures`; other states are immutable test values. Example RPM, coolant,
faults, adapter IDs/addresses and version labels are fictional. The labels in
Updates fixtures do not establish the installed or downloadable release.

## Method and fidelity

Focused Android instrumentation renders production screen composables, then uses
Compose `captureToImage().asAndroidBitmap()` to export PNG. It uses the existing
Gradle managed `ciPhone` (Pixel 2, API 35 Google APIs arm64-v8a, SwiftShader indirect).
The AVD config records a physical framebuffer of 1080 x 1920 pixels, density 420.
The images are **composition captures**, not full-device screenshots. LocalDensity
is deliberately overridden to 1 pixel/dp and fontScale 1: phone is 390 x 844 dp/px,
large is 1000 x 800 dp/px. The large image is a large-layout fixture on the same
emulator, not evidence from a physical tablet. Production EGaugeTheme typography
and API 35 default sans-serif fallback are used, without downloaded fonts. Both
explicit light/dark palettes use dynamicColor=false.

A static navigation shell mirrors the production bottom-bar/rail choices with
advanced tools enabled. Screens retain their real internal layouts, labels,
colors, scroll containers and controls. The shell does not exercise CompanionApp
navigation, fold insets, global operation/alert banners or Android edge-to-edge
insets. SystemUI is excluded and demo mode was not changed.

CarAlertWorkspace and CarAlertSettingsScreen require an AppViewModel. The harness
creates a model without an Activity or foreground coordinator, using only the
disposable emulator's default storage. It starts no discovery or transport action.
Default and empty Alerts both capture the actual empty workspace. The populated
alert variant uses the production AlertWorkspaceList with a static header/tabs;
it does not prove a model-owned current alert can be reached through the app.
Alert settings combines synthetic CustomizeUiState with default phone policy;
its saved-on-phone panel is an expected fixture/model comparison, not a verified
save. Second-adapter coverage is the production Advanced summary with synthetic
bindings; the chooser sheet and discovery are not captured.

No physical Pixel, gauge, controller or vehicle was operated. The only direct adb
attempts were read-only `wm size`, `wm density` and SDK queries explicitly targeting
`emulator-5554`; the managed emulator had already gone offline, so these returned
`device offline`. Physical display metadata above comes from the managed AVD
config, not those failed queries. This evidence does not qualify BLE, radio,
controller compatibility, durable saves, updates or physical display behavior.

## Reproduction and results

The already-configured JAVA_HOME was verified to point to the installed JDK 17;
`android/local.properties` supplied the existing SDK. No dependency installation
or environment guessing was needed. Run from the repository root:

```sh
./android/gradlew -p android :app:ciPhoneDebugAndroidTest --no-daemon \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lstepnio.egauge.WebsiteReviewCaptureTest
python3 review/tools/AndroidCaptureManifest.py
```

Final focused result: **BUILD SUCCESSFUL in 57s**, 1 test passed, 0 failures,
72 PNGs verified for expected filenames, dimensions and SHA256. `capture.log`
retains the final command output. The manifest helper copies only an exact
72-image set and rejects missing/extra files or unexpected dimensions.

Earlier attempts are explicit: initial compilation failed on a RowScope call and
missing OperationState arguments, then was fixed. The first 64-image run passed
in 58s. Adding the Advanced variants exposed invalid synthetic adapter identities;
that second attempt failed with `Adapter identity is invalid`. IDs were corrected
to valid lowercase fixture IDs, and the permission-state connection pill was
corrected before the final successful run. `compile.log` and
`capture-attempt2-failure.log` preserve those failures. A TabRow deprecation warning
remains in the capture-only synthetic alert header, consistent with the production
workspace's current TabRow usage.

## Review observations and remaining coverage

- Inspected Car phone/light shows the current Needs attention grouping for coolant
  and P0300 with distinct warning text, rather than the older gallery's fault view.
- Inspected Gauge large/dark uses the production two-panel adaptation and a rail;
  the primary capture retains the production preview label inside the round gauge.
- The permission fixture shows the permission pill, but the Home screen still
  renders a blank carousel area, `Hold to edit`, Action pages and Customize. This is
  a source-level state presentation observation; this synthetic combination is not
  proof of the model's exact permission-denied presentation on a real phone.
- Gauge's production fixture includes `Air up, Winch and ABS + TCS` in Action pages.
  That screenshot must not be used as evidence that public vehicle controls or
  ABS/TCS execution are enabled or qualified.
- Light/dark phone/large primary surfaces are covered. Dialogs, bottom sheets,
  full scroll depth, 200% text, keyboard/TalkBack, RTL, fold/hinge, notification
  permission dialogs, installed app navigation and actual live loading/recovery
  lifecycles remain unqualified. No interaction/a11y completion claim follows from
  a successful capture test.

Parent follow-up: the new capture-only Kotlin file was formatted with the existing
pinned ktlint tool. The exact focused capture test passed again (1/1, BUILD
SUCCESSFUL in 1 minute); `capture-format-recheck.log` retains that result.
PNGs and their manifest were regenerated afterward, so the harness SHA256 refers
to the formatted source used in the latest successful run.

Original command logs/XML referenced above are now retained unchanged inside
`raw-tool-evidence.zip`, under their original relative paths; the SHA256 index
allows individual verification. This packaging does not change test outcomes.
