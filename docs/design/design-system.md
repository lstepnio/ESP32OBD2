# eGauge visual and interaction system, draft 0.1

Direction: precise instruments with a calm companion workspace. The reading takes priority over ornament. A restrained graphite/lime palette connects the phone and gauge; amber/red are reserved for attention. Warm neutral surrounds in the review prototype are presentation chrome, not an app screen.

## Tokens and typography

[Shared tokens](../../design/tokens.json) define colors, spacing, radii, motion, and gauge geometry. Generate Compose and LVGL constants from these once renderer implementation begins. Until then, validate prototype token parity. Use bundled Noto Sans on gauge and native Android type on phone, with tabular numerals where supported. The baseline includes Noto fonts at limited sizes; new fonts need measured flash and glyph coverage budgets.

App body 16 sp, secondary labels 14 sp, section titles 28 sp, display reading 44-56 sp. Gauge primary value 56-72 px depending on digit count, unit 18 px, label 16 px; two-value page readings at least 36 px. Decimal precision belongs to the definition/display setting, never unpredictable float formatting. Long labels use a short display label; no continuously scrolling driving text. Temperature can use °C/°F on screen while contracts retain `degC`.

Minimum phone target 48 dp with accessible contrast: normal text >=4.5:1, large text/icons >=3:1 against background. Gauge controls use broad tap regions and a deliberate long press. Never shrink text indefinitely to fit. Gauge is a true circular clip; keep essential content within radius 104 around (120,120), and test long negative values and units there. Arc scale marks are secondary and can use the outer ring; important status text cannot.

The 240 px panel is circular even though its pixel buffer is square. At the bottom of glyphs whose baseline sits near y=215, the safe chord is only about 70 px wide, so a status such as SIMULATED can lose its outer letters. Put bottom status text around y=195-200 with a short label and at least 10-11 px type; reserve y>205 for nonessential marks. Use short gauge labels such as COOLANT and INPUT SPEED while retaining full names in the app and accessibility text. Center units under the primary number, keep the number within the central 166 px, and select from measured font sizes rather than scaling arbitrary strings to the rim. The Android preview and browser concept use these bounds; final LVGL layouts require physical screenshot and daylight legibility review.

The Compose preview uses fixed vertical bands in its 238 dp circle: label top 48, value top 77, unit top 141, optional bar/trend/second value at 158-169, and the short DEMO badge near the lower safe chord. This keeps extra renderer content from pushing the main reading toward the rim. These are preview layout coordinates, not measured LCD pixels. A future font-scale and long-value review should include negative values, five digits, degrees, and a DTC code on the physical gauge.

## Gauge renderers

| Renderer | Visual hierarchy | Best use |
| --- | --- | --- |
| Numeric | Label -> large centered value -> unit -> link/age status | Glanceable speed, temperature, voltage |
| Arc | One 240° scale with numeric center and threshold marks | RPM, load, boost when actually available |
| Bar | Large value above one bounded bar | Fuel/load, bounded range channels |
| Trend | Current value above a short graph with time span and gaps | Thermal/load behavior |
| Dual | Two vertically stacked value/unit pairs separated by a rule | Coolant + load or other paired readings |
| Diagnostics | CEL label, readable P-code, category and position | Trouble-code review |

Missing = `--` plus “No data”; stale = last reading visibly dimmed plus age; disconnected = `--` and “Adapter offline.” Demo always includes a visible DEMO/SIMULATED label. Negative and zero are valid numeric values when their definition permits them. Trends break on missing/stale samples and never connect across a session boundary. Out-of-range decoded samples show a decode issue instead of saturating a scale and pretending success.

Tap advances page, left/right swipe is an optional shortcut, long press opens a small local menu. A critical alert interrupts the page but does not discard selection. Tap acknowledgment returns to the page with an active badge; event inspection is reachable without the phone. Rotation is an explicit setting. Do not rely on automatic sensor rotation while driving.

## Android information architecture

Garage (vehicles, gauge, connection), Design (pages, renderers, units), PIDs (discovery, catalog, lab), Device (firmware, diagnostics, alerts, pairing, settings). Diagnostics and Alerts get prominent contextual shortcuts rather than being hidden in a settings overflow. On a tablet, editor and round preview appear side by side. A phone uses a persistent preview above controls where space allows, otherwise an explicit preview action.

Reusable components: connection pill with text/icon; reading tile with quality/age; renderer selector; round preview; PID row with ECU and evidence badge; threshold editor with unit/hysteresis; code card with category; transfer progress with stage; persistent operation banner; empty/error panel with one useful next action; revision/apply bar. Each has default, focus, disabled, loading, success and error states. Busy controls retain readable labels and do not masquerade as applied changes.

## Required flows

- **Design:** edits stay local until Apply. Show pending revision, compatibility validation and device acknowledgment. Unknown/rejected applies retain the draft and last known active revision.
- **PID explorer:** searchable catalog and separate scan progress. Inspect an item to see request/response, ECU, confidence, units and rate. “Add” binds that exact source; it does not combine different ECU results.
- **PID lab:** decoder fields, raw response, expected numeric result, source/license and vehicle scope. Bounded decoder capabilities are visible. Import errors point to fields.
- **Thresholds:** selectable data source, above/below comparator, warning and critical levels, dwell, hysteresis and preview. Show estimated response time and conversion to chosen units. Critical overlay can be previewed without touching vehicle data.
- **Diagnostics:** category counts, code details, ECU, age, readiness. Clear codes shows consequences and ECU scope, followed by verification. Permanent codes remain visible after simulated clear.
- **Update:** stage-specific progress and recoverable failure copy. An interrupted upload offers retry; trial boot shows “Checking new firmware”; rollback shows restored version and next action.

## Prototype scope

The [interactive prototype](../../design/prototype/index.html) includes layout selection, data selection, units, draft/apply state, stale/offline/critical scenarios, searchable synthetic PID results, a basic raw-byte decoder lab, local threshold preview, DTC confirmation/readback simulation, and firmware transfer pause/resume. It uses local state and simulated timing. It cannot discover actual adapters, clear actual codes, or flash a device. It is not a pixel-accurate LVGL renderer or a native Compose app.

Documented onboarding, profile import, detailed hysteresis tuning, actual association, and secure OTA are implementation work. Presentation defaults use synthetic standard data and an example vehicle name, not claims about the user's vehicle. In production all actions are capability-driven and powered by real device acknowledgments.

## Quality of the experience

No auto-hiding critical errors; no color-only state; no blocking spinner without status and cancellation for long work. Respect reduced motion; avoid rapid flashing. Large-text layouts may scroll vertically, while essential actions remain reachable. Screen readers announce state changes deliberately, not every incoming RPM sample. App previews and gauge renderers share golden state fixtures, but physical legibility needs daylight/night viewing on hardware.
