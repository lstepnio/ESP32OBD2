# Android core and UX implementation validation

Date: 2026-09-26. Branch: `feat/owned-gauge-control`.

## Implemented software evidence

- One ViewModel-owned operation coordinator prevents competing discovery, read, configuration, and update sessions.
- Configuration success waits for matching runtime revision/hash with trial cleared. Previous-generation recovery is a distinct outcome.
- Capability, diagnostics, and runtime parsing use bounded pure codecs. Transfer callback queues are bounded.
- Diagnostics expire after 30 seconds. Target changes clear device-specific state.
- Nearby discovery collects compatible gauges, asks when ambiguous, remembers the choice, and distinguishes public discovery from authenticated owner access.
- Profile format version 2 preserves the optional second-adapter preference. Version 1 TCM profiles migrate into advanced topology without source rebinding.
- Numeric review and payload share one typed projection. Unsupported renderer or source is blocked rather than converted silently.
- Default navigation is Gauge, Readings, Vehicle, and Settings. PID labs, raw details, and second-adapter controls use progressive disclosure.
- A signed, fresh, multi-entry GitHub release catalog selects by board, hardware revision, partition layout, channel, and transfer protocol. HTTPS downloads are bounded and rechecked by catalog hash plus the existing bundle signature.
- Firmware update stages have one visible operation model and an interruption journal. Debug builds keep the screen awake while visible.
- Narrow technical rows stack, threshold controls have descriptive semantics, headings are marked, and default navigation has a Compose instrumentation journey.

## Automated checks

Run from the repository root:

```bash
.venv/bin/python tools/validate.py
cd android
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew \
  :app:assembleDebug :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon
```

Core regression fixtures cover runtime trial/recovery, evidence aging, operation overlap, exact projection, legacy TCM migration, malformed protocol data, catalog signature, and exact compatibility. `PrimaryJourneyTest` checks primary navigation and verifies that TCM terminology is absent from the default Vehicle path. Quality CI runs all deterministic checks plus Android lint.

## Current limitations and pending external gates

The final debug APK, SHA-256 `cf90ed00261b561d4648fd5cbcc5af3f5b58b68324a33ec79ccc07afd26c3095`, was installed successfully on the Pixel 10 Pro over Wi-Fi ADB. The phone was at the secure lock screen, so Android could not resume the activity and the connected Compose test reported no app hierarchy. Its deterministic compilation passed, but this is not a device journey result. Run the journey and capture large-text, landscape, RTL, TalkBack, cold-start, and frame evidence after the phone is unlocked. The current environment also had no OBD adapter, so live PID evidence, DTCs, and one- or two-adapter behavior were not exercised.

The development-release environment and signing secret are configured on GitHub. Prerelease `dev-v0.2.0-dev.3` is published with a signed catalog and the Waveshare bundle. A clean download verified the catalog signature, exact bundle size/hash, ZIP members, board, protocol, image length, and image SHA-256. The release targets this integration branch because GitHub workflow dispatch cannot use a workflow absent from the default branch; after merge, subsequent releases use the protected workflow. The published package still needs the fresh-install phone-to-gauge journey. Stable OTA remains blocked on production trust, per-board qualification, and recovery evidence.

The app icon is intentionally deferred until product naming and branding are decided.
