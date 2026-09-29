# eGauge design system

The Android companion puts the user's display first: a large round preview, simple reading names and one clear action. This specification supersedes earlier Android navigation and visual guidance. It does not change the gauge renderer, hardware geometry, authentication, or protocol contract.

## Visual language

Graphite, neutral surfaces, lime accents, large tabular readings and soft 28 dp cards. Light mode uses warm white surfaces and dark green action text. Android dynamic colour is optional and affects app chrome only. Warning, critical and round-preview colours retain fixed meanings. Colour is always paired with an icon and a human sentence.

[Tokens](../../design/tokens.json) have separate `android` roles for light/dark colours, type, spacing, shape, motion and adaptive breakpoints. The existing top-level gauge colours, type and geometry are unchanged. `tools/generate_design_tokens.py` generates Android values and browser CSS; `--check` detects drift. No token rename or firmware change is required.

Body text is 16 sp; labels are 14 sp; titles are 32 sp. Reading values are 64 sp in the mock display. Application text honours font scaling. The circular preview has a scalable text equivalent so its simulated device geometry never constrains accessible labels. Controls have at least 48 dp touch targets and grow with content.

## Navigation and tasks

Gauge, Car and Settings are the default destinations. Settings has one **Show advanced tools** toggle, revealing Expert and advanced fields. Customize is a preview-led dashboard for pages, layouts and reading-specific alerts, followed by a full send review. Setup walks through discovery, physical-code association and adapter availability. Details is a named sheet with selectable text, reachable from every feature.

Compact windows use a bottom bar. From 600 dp the app uses a rail. From 840 dp it can show preview and controls side by side; large text returns content to a stack. Respect system bars, keyboard insets, fold hinges and predictive back.

## Trust and status

- Foreground discovery and owner reconnection are automatic. **Gauge ready** requires a recent protected check; it does not claim a persistent connection or live readings. Searching, reconnecting, Bluetooth-off and permission states remain explicit. Only initial code association and ambiguous gauge selection require a choice.
- Example values and histories always say **Preview** or **Example** next to that content.
- **Saved & running on gauge** requires the expected running revision and hash, trial cleared and no previous-generation recovery. A stored readback or 100% transfer cannot claim this.
- Sending, restarting and checking remain distinct stages. The operation stays visible across destinations.
- Critical alerts, stale/offline readings, errors, rollback and unknown outcomes remain in the default path.
- Automatic reconnection may refresh the protected readback before reviewing an unknown result. Never replay a commit automatically.
- A changed page, layout or limit returns to unsent status. Browsing the page carousel must not create an edit.
- Unimplemented adapter setup, live data, code clearing and public updates use truthful empty states.

## Components and copy

Status card, connection pill, round preview, reading tile, page carousel, limit editor, progress stepper, Details sheet, empty/error panel and primary action share the eight-state [fixtures](../../design/fixtures/ui-states.json). Component previews cover default, loading, disabled, error, success, stale, offline and critical states.

Buttons use verbs: **Find gauge**, **Pair gauge**, **Customize**, **Send to gauge**, **Check gauge**, **Install**. Default copy never exposes revision, hash, PID, ECU, transport phase or source-model terminology. Errors finish with one next step. Detailed facts remain exact and selectable in the scrollable sheet. Settings switches and radio options use the entire named row as a target. No account, analytics or secrets in logs/export/backup.

Each supported standard reading can have one transmitted alert. People choose whether it should alert when the value rises above or falls below limits. Default labels are **Warn above**, **Critical above**, **Warn below**, and **Critical below**. Reset margin and timing remain inspectable in Details. Unsupported layouts, readings, or sources stay preview-only with an explanation before send. All transmitted pages and alert settings are included in review.

## Artifacts and verification

See the [audit](redesign/audit.md), [complete baseline string inventory](redesign/strings.csv), [IA and flows](redesign/concept.md), and [interactive prototype](../../design/prototype/index.html). Eight key concept screens have light/dark and compact/expanded captures in `docs/design/redesign/mockups/`.

The [native gallery](../../design/prototype/native.html) shows 64 reviewed screenshot fixtures across light/dark, compact/expanded and component states. Both galleries are simulations. Native software tests, physical phone observations and physical gauge observations are separate evidence categories. The two-minute setup target requires a timed participant test and working adapter setup; it is not established by a mockup.

Android implementation uses [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3), [adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation) and [predictive back](https://developer.android.com/develop/ui/compose/system/predictive-back). See the [validation record](../development/android-core-ux-validation.md) for the current measured result.

Gauge-side legibility and safe circular geometry remain documented in [round-display guidelines](round-display-ui-guidelines.md). This redesign introduces no LVGL or firmware changes.
