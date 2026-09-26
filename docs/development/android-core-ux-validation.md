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

No Android device was connected during the final offline validation pass. Run the instrumentation journey and capture large-text, landscape, RTL, TalkBack, cold-start, and frame evidence on the Pixel when available. The current environment also had no OBD adapter, so live PID evidence, DTCs, and one- or two-adapter behavior were not exercised.

The development-release environment and signing secret are configured on GitHub. Publishing the first prerelease waits for the implementation commit to exist on GitHub, after which the workflow output must be exercised on the phone and gauge. Stable OTA remains blocked on production trust, per-board qualification, and recovery evidence.

The app icon is intentionally deferred until product naming and branding are decided.

