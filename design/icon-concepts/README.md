# eGauge app icon concepts

Direction A, Precision gauge, selected by the user on 2026-09-29. Implemented as the Android launcher icon. Website assets retain their current treatment.

- **A, Precision gauge (recommended):** refines the open circular dial and needle already used in `website/favicon.svg`. Strongest continuity and immediate instrument recognition.
- **B, Electric e:** a circular lowercase e monogram. More distinctive as a lettermark, less obviously automotive.
- **C, Digital dial:** combines an instrument arc with rising data bars. Emphasizes configurable live readings, but is more detailed at small sizes.

All concepts use the current Android/design token colors: graphite `#0C1114` and lime `#B7F36B`. The website currently uses the related, softer palette `#182019` / `#d9ee86`.

`concept-board.svg` compares enlarged, 48 px, circular, monochrome-themed, and 32 px previews. These are design studies, not verified Android adaptive icon masks or physical phone observations. The SVG tile backgrounds are presentation shapes; production adaptive assets should separate the foreground mark and full-bleed background.

## Selected Android asset

The manifest uses `@mipmap/ic_launcher` for both normal and round launcher entries. Separate graphite background and lime vector foreground let the launcher apply its own mask. Android 13 and later receive the same foreground silhouette as a monochrome layer for themed icons. The 108 dp vector scales the mark to 80% around its center, keeping the strokes inside the central 66 dp safe area. No pre-rendered corner mask or shadow is baked into the foreground.

The app's minimum supported Android API is 29, so pre-adaptive legacy raster launcher resources are unnecessary. Store publishing assets are separate from the launcher resources.

`selected-preview.png` is rendered from the Android vector geometry, showing illustrative squircle, circular, and themed treatments. It is a source-level preview, not a physical phone observation. Physical launcher appearance still needs device validation.

Reference: [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

Validation on 2026-09-29: `:app:assembleDebug` and `:app:lintDebug` passed. APK metadata resolves the eGauge application icon to the packaged adaptive launcher resource. The generated artwork preview was visually inspected. No phone installation or physical launcher check was performed.

## Physical phone installation, 2026-09-29

The initial APK from `codex/obd-definition-catalog` displayed a profile-version error on the Pixel 10 Pro because this checkout only reads profile schemas 1 through 3. Its screen reported that saved data had not been replaced. The current companion is in `/Users/lukasz.stepniowski/.codex/worktrees/egauge-redesign/ESP32OBD2`, branch `codex/background-firmware-ready`, base commit `808da7b`, which reads schema 4. The same five icon resource/manifest changes were applied there; build and lint passed, and that corrected APK was installed with data retained.

Final installed APK SHA-256: `90a7859c64feb0390357798fe97a5943829687d29306cae5d5fbb4b402368ff8`.

Physical phone observations: Android App info shows the selected lime-on-graphite gauge icon. The current companion opens without the profile error, shows a two-page saved setup with coolant temperature first, and displays `Gauge ready`. The displayed reading is explicitly labeled `PREVIEW`; this is not a live vehicle reading or a new physical gauge validation. Phone wake settings were restored. Home-screen themed mode has not been separately exercised.

Future phone installs must use the current companion checkout, not the older catalog branch APK.
