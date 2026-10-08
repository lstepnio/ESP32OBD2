# eGauge design system

The Android companion puts the user's display first: a large round preview, simple reading names and one clear action. This specification supersedes earlier Android navigation and visual guidance. It does not change the gauge renderer, hardware geometry, authentication, or protocol contract.

## Visual language

Graphite, neutral surfaces, vivid green accents, large tabular readings and soft 28 dp cards. The round preview uses the gauge firmware's near-black background, text, track, green normal state, amber warning and red critical state. Light mode uses warm white surfaces and a darker green for readable action text. Android dynamic colour is optional and affects app chrome only. Warning, critical and round-preview colours retain fixed meanings. Colour is always paired with an icon and a human sentence.

[Tokens](../../design/tokens.json) have separate `android` roles for light/dark colours, type, spacing, shape, motion and adaptive breakpoints. `tools/generate_design_tokens.py` generates Android values and browser CSS; `--check` detects drift. The preview arc uses the firmware's thicker stroke and track colour while keeping its own simulated layout. This palette update does not change firmware.

Body text is 16 sp; labels are 14 sp; titles are 32 sp. Reading values are 64 sp in the mock display. Application text honours font scaling. The circular preview has a scalable text equivalent so its simulated device geometry never constrains accessible labels. Controls have at least 48 dp touch targets and grow with content.

## Navigation and tasks

Gauge, Car and Settings are the default destinations. Settings has one **Show advanced tools** toggle, revealing Expert and advanced fields. Customize is one workspace with a swipeable page preview and contextual reading, layout and alert rows. Choices open focused sheets; Add page chooses a reading before creating a page. Manage pages uses compact numbered rows with named move/remove options. The alert form commits only on Save. Its unit-aware fields validate range, ordering, reset distance and delays. The primary action stays in a fixed footer. See the [Customize review](redesign/customize-review.md) for the audit, workflow and images. Setup walks through discovery, physical-code association and adapter availability. Technical facts live in Expert > Device data. Everyday screens have no generic details control.

Compact windows use a bottom bar. From 600 dp the app uses a rail. From 840 dp it can show preview and controls side by side; large text returns content to a stack. Respect system bars, keyboard insets, fold hinges and predictive back.

## Trust and status

- Foreground discovery and owner reconnection are automatic. **Gauge ready** requires a recent protected check; it does not claim a persistent connection or live readings. Searching, reconnecting, Bluetooth-off and permission states remain explicit. Only initial code association and ambiguous gauge selection require a choice.
- The universal connection pill appears on every page. It opens independent phone/gauge/adapter status and links to Updates when appropriate. Routine reconnecting stays here; task failures, stale settings, partial fault checks and unknown mutations remain visible in their relevant surface.
- Example values and histories always say **Preview** or **Example** next to that content.
- **Saved & running on gauge** requires the expected running revision and hash, trial cleared and no previous-generation recovery. A stored readback or 100% transfer cannot claim this.
- Sending, restarting and checking remain distinct stages. Updates owns detailed update progress; other destinations retain the shared activity entry point. Only a gauge-confirmed success can be dismissed, manually or after six seconds; failures, rollbacks and unknown outcomes remain until the user checks them.
- Signed development packages may be checked and downloaded during an authenticated foreground session. Installation always requires a tap. A failed release is held for that gauge to avoid repeated prompts.
- Critical alerts, stale/offline readings, errors, rollback and unknown outcomes remain in the default path.
- Automatic reconnection may refresh the protected readback before reviewing an unknown result. Never replay a commit automatically.
- A changed page, layout or limit returns to unsent status. Browsing the page carousel must not create an edit.
- Adapter setup is available through Car. Live companion telemetry and code clearing remain unavailable; previews stay labeled examples. Public capability qualification remains separate from development paths.

## Components and copy

Status card, connection pill, round preview, reading tile, page carousel, unit-aware limit field, progress stepper, empty/error panel and primary action share the eight-state [fixtures](../../design/fixtures/ui-states.json). Component previews cover default, loading, disabled, error, success, stale, offline and critical states.

Buttons use verbs: **Find gauge**, **Pair gauge**, **Customize**, **Send to gauge**, **Check gauge**, **Install**. Default copy never exposes revision, hash, PID, ECU, transport phase or source-model terminology. Errors finish with one next step. Detailed facts remain exact in Expert > Device data. Settings switches and radio options use the entire named row as a target. No account, analytics or secrets in logs/export/backup.

Every selectable catalog reading can have one transmitted alert, including TCM readings and readings absent from pages. Numeric rules use finite decimal Above/Below limits; gear rules use named position equality. People choose the applicable condition. Default labels are **Warn above**, **Critical above**, **Warn below**, and **Critical below**. Reset margin and timing remain inspectable in Expert > Device data. Unsupported layouts, readings, or sources stay preview-only with an explanation before send. All transmitted pages and alert settings are included in review.

## Artifacts and verification

See the [audit](redesign/audit.md), [complete baseline string inventory](redesign/strings.csv), [IA and flows](redesign/concept.md), and [interactive prototype](../../design/prototype/index.html). Eight key concept screens have light/dark and compact/expanded captures in `docs/design/redesign/mockups/`.

The [native gallery](../../design/prototype/native.html) shows 64 reviewed screenshot fixtures across light/dark, compact/expanded and component states. Both galleries are simulations. Native software tests, physical phone observations and physical gauge observations are separate evidence categories. The two-minute setup target requires a timed participant test and working adapter setup; it is not established by a mockup.

Android implementation uses [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3), [adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation) and [predictive back](https://developer.android.com/develop/ui/compose/system/predictive-back). See the [validation record](../evidence/android-core-ux-validation.md) for the current measured result.

Gauge-side legibility and safe circular geometry remain documented in [round-display guidelines](round-display-ui-guidelines.md). This redesign introduces no LVGL or firmware changes.

## Current component and messaging rules

Compact titles and large text place the connection pill beneath the heading; expanded
layouts can share a row. Settings and profile dialogs use `DialogContent` so choices
scroll without hiding native actions. Settings choices are keyed to gauge identity.
Shared controls use the existing radius/spacing tokens; explicit renderer geometry
is not replaced with arbitrary app layout tokens. Home exposes edit, next and
previous page accessibility actions alongside touch gestures.

Car starts with the selected vehicle and any required setup action, followed by one
Vehicle health card and a compact Alerts card. Fault codes open a named, focused
sheet; empty Stored/Pending/Permanent lists and exact coverage stay in Expert.
Recent alerts prioritize current active events; Alert history contains export and
history deletion. No disabled report-management controls occupy the main screen.
Adapter and Gauge gestures use shared settings rows; routine connection retries
remain in the universal pill. Sheets scroll at large text sizes and close when the
active vehicle changes, preventing actions on the previously selected vehicle.

One logical vehicle may contain an optional second adapter. The summary preserves
warnings, incomplete checks and the affected controller; a healthy engine never
certifies transmission checks. Codes are deduplicated across categories per
controller, retain last-checked/category meaning and never merge across controllers.
No-warning status only means the warning lamp is off, not that the vehicle is fault-free.
Exact category coverage and raw snapshots stay in Expert.
Disabled stale settings say Last checked. Global notices are reserved for actionable
profile/association/preference failures, not each routine connection retry.

See [product assessment and implementation evidence](../evidence/product-optimization.md)
for the dated review and workflow evidence. Current remaining qualification belongs
to [the backlog](../backlog.md).
