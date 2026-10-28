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

Car starts with the selected vehicle, Car settings and any required setup action.
One **Needs attention** list combines current threshold/provider episodes and decoded
fault codes, with three rows and View all when needed. Routine global banners do
not duplicate the Car/Alerts list; critical attention remains globally available. Known diagnostic episodes do
not duplicate their code rows. A row opens its explanation directly. Fresh code
categories remain useful even if another category or the warning lamp is unavailable.

With complete usable checks and no visible issues, show **No active alerts** and its
check age. Hidden-only complete checks look the same. Startup has no health placeholder,
incomplete-check task, unknown CEL or hidden-count footer. With no current evidence,
previous activity is explicitly Last drive. The universal pill owns connection/retry
status, including a missing optional child; no extra engineering-status panel is added.

**Vehicle actions**, **Alert settings** and **History** are direct utilities. Car settings
owns vehicle selection, adapter and gauge entry points; hidden-code and child-adapter
management stay in Advanced. Current and History use a full-screen workspace with
retained tab/scroll; details return to their origin. Changing cars dismisses stale
selection/edit state. Reports, retention/deletion and guarded ECU clearing stay in
contextual Options. There is no generic Details step or nested sheet chain.

Threshold detail uses the recorded **At alert** value, limit and time. Got it changes
attention only; eligible noncritical alerts offer Snooze 5 min. Missing data cannot
read as resolution. Fault meaning is lookup-first, scoped and conservative. Technical
capture windows and coverage remain out of routine detail. History/export/notifications
share the [hidden-code policy](../architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters).

Car and Gauge customization reuse one catalog and unit-aware alert editor. Enablement
retains the complete phone draft; disabled rules are Off and absent from the wire.
The richer warning/critical model stays explicit rather than being flattened into an
invented single limit. Reset and timing are disclosed under Alert behavior. New rules
are created only by Save. An isolated connected save uses the existing transaction;
unrelated pending setup requires review, and local success never means gauge success.
Disabled stale settings say Last checked. Global notices are reserved for actionable
profile/association/preference failures, not each routine connection retry.

See [product assessment and implementation evidence](../evidence/product-optimization.md)
for the dated review and workflow evidence. Current remaining qualification belongs
to [the backlog](../backlog.md).

## Expert task menu

Expert is a compact task menu, not an engineering dashboard. Show vehicle-scoped
Second adapter and Code filters entries, then Fault diagnostics and Device data.
The debug-only display preview is a direct start/stop action with generated-data
copy; synthetic phone notices belong in test fixtures, not the product menu. Curated values, manual support retries and setup-import actions belong
inside Device data. Normal foreground refresh and the universal connection pill
remain automatic and shared across destinations.

Second adapter opens one setup sheet with discovery, selection and the dual-link
switch together. Preserve primary/child routing, firmware capability gates,
independent freshness and explicit removal confirmation. Legacy transmission
migration appears only when a migration target exists. Sheets scroll with large
text, retain keyboard/system insets and reset when the active vehicle changes.
Use familiar task names; transport addresses and coverage details stay inside their
specific tool. Hiding detail never changes diagnostic truth or saved settings.

Device data is an allowlist of actionable support facts, not an object dump. Keep
identity, access failures, configuration comparison differences, exact firmware
identity, update verification and memory/reset health. Do not list the whole reading
catalog, obsolete fixed-index selections, complete configuration JSON, raw protocol
objects, duplicate alert fields or signature bytes. Fault-category evidence belongs
in Fault diagnostics; firmware rechecks belong in Updates. Support actions precede
support facts so long readbacks never bury recovery controls. Routine successful
read messages stay inside Device data, not on the Expert landing page.

## App-wide task and recovery consistency

- Gauge previews keep their example label. A single-page dashboard does not suggest
  swiping between multiple pages.
- Settings exposes display choices, alerts and appearance. Rename/add/select gauges
  belong in Your gauges; manual bond/move-owner instructions belong in Pairing help.
  Offline settings say Connect a gauge, not an indefinite active read. Previously
  checked values stay labelled Last checked and cannot be edited until fresh.
- Car has one adapter entry. Opening its chooser searches once when ready, with an
  explicit retry retained for recovery. Recomposition never starts another scan.
  Keep page gestures and independent source status; do not advertise future controls.
- Updates has one primary action, visible installed/available versions and honest
  recovery/hold state. The signed-file picker is a fallback when the online feed is
  unavailable, not a second everyday installation route. Version/signature/recovery
  guards and explicit installation remain unchanged.
- Setup invalidates candidate selection when discovered identities change. A lone
  candidate can be preselected; connecting and pairing still require explicit action.
- Customization, deletion and alerts retain confirmation/save boundaries. An empty
  reading catalog shows an actionable empty state rather than crashing. Preview
  examples and captured alert values use the same unit policy, with named gear states.
- Connections and activity sheets scroll at large text sizes. Genuine failures,
  unknown outcomes and source-specific missing checks must never disappear as cleanup.


## Fault explanations and notification continuity

Car, saved alert context, recent alerts, banners and phone notifications share the
offline code catalog and friendly producer names. Prefer a plain-language condition
title with the exact code below. No generic ECM/TCM landing step is required. A
code explanation describes the detected condition, never a failed-part diagnosis
or a driving-safety verdict. Unknown/manufacturer-specific meanings stay explicit.
The [shared alert contract](../architecture/alerts-and-diagnostics.md#offline-code-explanations)
owns lookup provenance and extension rules.

Keep Last checked on retained stale codes. Historical alert codes say Saved with
alert and never become current through reconnection or vehicle switching. Selecting
another vehicle dismisses live fault details. Explain category meaning in ordinary
language inside a code explanation; keep raw category/coverage reports in Expert.

Phone notifications name the system and, when captured, the first unfiltered code
and its explanation. Threshold notifications use the selected units. Tapping opens
the same saved episode, not a fresh scan or a code-clearing action. Preserve existing
opt-in, severity channels, foreground suppression, quiet replay, privacy and filters.


## UX completion standard

Apply this before extending a user-facing workflow. Reuse SettingsRow, Panel,
EditorScaffold, tokens and the universal connection pill. Identify the user task,
entry point, result and unnecessary steps removed; justify any new layout pattern.

- One owner per task. Current alerts, saved history, configured rules and Phone
  notifications have distinct destinations. Lead with the condition and useful action.
- Review empty, loading, unavailable/stale, failure, success and critical states.
  Missing evidence never implies a healthy vehicle or resolved alert.
- Keep destructive and consequential confirmation/review boundaries. Saving a rule
  changes the phone draft in Customize; Review and send retains admission and gauge verification.
  Car retains its guarded Save to gauge or explicit Save on phone result.
- Test the primary interaction and review rendered compact and 200% text captures.
  Count steps from the owning surface. Compilation alone does not establish usability.

### Alert task ownership

| Task | Path | Guardrail |
| --- | --- | --- |
| Respond to a current alert | Current → Got it / Snooze 5 min | Current real available owned episode only; busy controls disabled; critical cannot snooze |
| Understand an alert | Condition → full-screen detail | Recorded At alert value/limit/time, visible saved codes; technical capture stays out of routine detail |
| Review an older episode | History → episode | Full lazy list with timestamp/state, no Show all gate; retained tab and scroll |
| Edit a rule | Alert settings → configured rule / Add alert | Disabled rules retain limits and Off status; unconfigured readings appear only when adding |
| Apply changes | Customize: Save alert → Review and send; Car: Save to gauge / Save on phone | Phone save never claims gauge change; guarded admission/readback remains required |
| Delete history or clear codes | Contextual Options | Separate local deletion and guarded ECU clearing with existing confirmation/report policy |

Got it and snooze change attention only after confirmed gauge readback. Seen/snoozed
episodes stay in Current while active; the global attention banner stops after
confirmation. History, simulated, unavailable and stale evidence offers no live action.
The task path and rendered fixtures still need physical phone accessibility/legibility
and gauge attention/readback qualification before physical usability is claimed.
