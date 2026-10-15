# Gauge UI/UX redesign proposal

**Status: revision 2 awaiting owner review. Updated 2026-10-14.** This is a proposed design and
implementation plan. Firmware, App behavior, protocol and installed devices are unchanged.
Existing [round-display rules](round-display-ui-guidelines.md) and
[alert framework](../architecture/alerts-and-diagnostics.md) remain the active contracts
until the approved rules are incorporated during implementation.

[Interactive gallery: 44 screen concepts](gauge-ui-proposal/index.html).
[Overview](gauge-ui-proposal/overview.png), [all layouts](gauge-ui-proposal/layouts.png),
[fault states](gauge-ui-proposal/status-states.png), [system states](gauge-ui-proposal/system-states.png).

## Revision 2 response to owner review

The submitted first review contained 27 approvals, 16 requests for changes and one
unreviewed example with feedback. Revision 2 applies that feedback across shared screen
rules, preserving the original submission and original native images for comparison.

| Feedback area | Revision 2 design |
| --- | --- |
| CEL, recorded faults and alerts | Unboxed symbols and text higher in the status lane. A small count appears for multiple faults; unknown remains `?` |
| Dual and missing-source screens | Both reading groups move up 16 px around a centered divider; shared enum/numeric rows follow the same geometry |
| Bar | Title, number and unit move upward; the lower scale is 6 px thick with more separation from the unit |
| Pairing and recovery instructions | Use `Open eGauge App` consistently |
| Gesture progress | Show `2/3` without repeating the swipe direction |
| Provider warning | Use a countdown arc; source attribution and exact expiry remain in context |
| Simulation | Remove the status capsule. A quiet `Demo` footer identifies deliberate generated bench input; the gallery explains when it is used |

**23 byte-identical approved screens retain approval. 21 revised screens need review.**
Four previously approved examples changed because they share these rules: missing ECM,
custom enum, paused update and critical demo. Their old approvals remain in the first
submission, but do not approve the revised images. Each changed card offers the original
image and saved first-review feedback. Raw owner feedback remains private on this Mac.

## Screen-by-screen approval form

The [gallery](gauge-ui-proposal/index.html) includes **Approve as-is**, **Request changes**,
a feedback field, **Save feedback**, and an explicit **Not reviewed** decision for each
example. Decisions save immediately to the local review service. Notes remain browser
drafts until Save feedback or Submit review acknowledges the durable write. Reloading
restores saved decisions and unsaved browser drafts. A partial submission retains all
unreviewed examples as unreviewed.

The review service listens only on this Mac's loopback interface. Private feedback is
saved under ignored `artifacts/gauge-ui-review/2026-10-14-review-v2.json`; it is not committed
to Git. The original `2026-10-14-review.json` submission remains intact. Each submission
retains its screen decisions separately from subsequent edits. The review is bound to the
exact example manifest/image digest. A stale first-review page cannot approve revision 2;
changed examples require a new decision. These are design decisions, not firmware
publication authorization.

To reopen the local form after stopping the server:

```sh
python3 docs/design/gauge-ui-proposal/review_server.py --port 8769
```

Open `http://127.0.0.1:8769/docs/design/gauge-ui-proposal/index.html`. The static HTML can
show mocks alone, but durable submission requires this local service.

## Recommendation

Use one instrument layout system: a reserved status lane, large readings, explicit units
and a small page position marker. Vehicle faults and threshold/provider warnings share
one status compositor. Critical attention uses one complete takeover with an explicit
acknowledgment. Optional inspection shows one fault or alert at a time.

The goal is quick recognition of a value, its meaning and any issue. Dense diagnostic
context stays available through the existing App. The gauge presents enough context
for its own fault and alert inspection without requiring a phone for the basic meaning.

## What the audit found

These are source findings plus the owner's physical photo, not a new hardware test:

| Boundary | Current problem | Proposed correction |
| --- | --- | --- |
| Dual reading + alert | Dual title starts at y=20; warning label occupies y=30..70, replacing only the single-reading title | Allocate a shared lane; the same geometry applies to every renderer |
| Fault indicator | `CEL 4 U0121` combines lamp, count and one code in a 132 px footer | Compact symbol + qualified count; inspect codes in a focused view |
| Units | Dual uses name + unit, producing `Engine RPM rpm` | Metadata controls one occurrence of the unit |
| Threshold banner | `WARNING` plus a producer label repeats severity and crowds the reading | Short condition with an icon; longer context in inspection |
| Trend/Bar | Trend occupies the main value/unit region; Bar meets the unit row | Renderer-specific chart rows within one common skeleton |
| Critical attention | Repeated multiline text and value/limit/unit strings, with competing content behind the panel | One dedicated attention layout, with producer-appropriate content |
| Page gesture/simulation | Independent labels occupy the same top region as other state | Priority-controlled temporary feedback and persistent simulation marking |
| Pairing | Fixed 176 × 100 label contains instructions, six-digit code and ID at 32 px | Separate title, value/instruction and identification rows |
| Global faults | Legacy summary aggregates reported MIL counts and first code; controller/coverage meaning is insufficient for the proposed inspection | Reuse bounded endpoint/category snapshots and retain freshness, category and origin |

## 1. Circular layout and typography contract

- Native surface: 240 × 240, center (120,120). Essential glyphs, visible status and controls
  stay inside radius 104; nonessential ring/track decoration may extend to radius 112.
- Compute fitting using the entire glyph rectangle and the narrowest circle chord it
  intersects. Rectangle width alone is insufficient, particularly in the lower Dual row.
- Status symbols center near y=42, or y=47 for the composed warning/MIL row. They have
  no capsule fill or border. Healthy normal pages leave this lane quiet; they do not add
  a healthy/connected sentence. Visible status keeps clear of the highest reading label.
- Single: short title near y=87; dominant value near y=126; unit near y=169. Bar uses
  title/value/unit centers 70/108/147 and a separate 6 px scale at y=180..186. Trend uses
  a 48 px value and a separate chart region.
- Dual: title/value centers near 72/97 and 135/160, with a subtle divider at y=120.
  Units sit beside the corresponding value when needed. Both readings have equal hierarchy.
- Values use Noto SemiBold 64, 48, 40 or 32 px; titles and status text use Noto Medium 16 px;
  single-reading units use 24 px. 48/40 px are proposed font additions, not installed fonts.
- Use measured font choice, preferably selected from the descriptor's expected value
  range and enum strings at page/unit change. Avoid font-size oscillation on normal updates.
  Unexpected larger values can step down safely; restoring size waits for a stable/page boundary.
- Maintain spacing between unrelated surfaces. Renderer-specific geometry must pass
  collision checks for every visible combination, including shared status and charts.
- No scrolling numbers, scrolling fault codes, marquee warnings or rapid flashing.
  Numeric values, units, code strings and pairing codes are never silently clipped.
- Retain the current strong green/amber/red palette and near-black background. Icons,
  wording and shape carry meaning in addition to color: triangle for warning, diamond
  for critical, engine for MIL and wrench for recorded codes. No routine brand footer or splash.
- Visible controls have expanded hit areas, targeting at least 48 logical pixels in the
  primary touch direction, clipped to the real circle. Review actual reach, bezel and
  touch response on the panel; monitor pixel size does not establish physical usability.

The concept generator checks glyph bounds with the production Noto TTF files. These
are rasterized design mocks, not LVGL captures. LVGL glyph metrics, RGB565 output,
contrast/glare and the physical touch targets must be checked during implementation.

## 2. Shared CEL, fault and alert presentation

| Situation | Gauge presentation | Meaning / interaction |
| --- | --- | --- |
| Fresh engine MIL On | Unboxed amber engine symbol, small checked count when multiple faults are known | Lamp is on; count is known distinct controller/code entries |
| Codes present, MIL Off | Unboxed muted wrench; small count for multiple faults | Recorded codes; no fabricated active engine warning |
| MIL On, count unknown | Engine symbol + `?` | Unknown never becomes zero |
| Partial code coverage | Known count with a partial marker, such as `4+` | Complete total is unknown; inspection identifies last-checked coverage |
| Count exceeds compact width | `9+` | Count is bounded for layout; exact total is in inspection/App |
| Transmission warning | Distinct warning symbol and human Transmission context | One vehicle dashboard; the child transport does not create a different page family |
| Warning threshold | Short reading alias + condition, such as `Coolant ↑` | Underlying value/title/unit remain visible |
| Threshold and MIL together | One measured unboxed row: warning symbol, short condition and secondary engine symbol | Both conditions remain visible, with no stacked paragraphs or timed message cycling |
| Critical entry/escalation | One takeover: condition, current value/unit, relevant limit/context, Acknowledge | One entry cue according to the shared framework |
| Critical acknowledged | Reading page + persistent red indicator | Attention is acknowledged; the condition and history are retained |
| Warning input lost | Last-seen indication; affected reading becomes `--` | No fabricated live value or implied resolution |
| No fresh diagnostic coverage | Neutral `Not checked` when relevant to inspection/state | A healthy sibling never certifies missing checks |
| Paired-phone/provider notice | Short notice inside an expiry arc; provider attribution and exact expiry in context | Existing trusted severity/TTL rules; local critical attention has priority |

Counts must deduplicate a code across categories within the same controller. The same
code on different controllers remains two entries. MIL-reported count and retrieved
fault count are distinct facts; the compact indicator cannot substitute one for the other.
Stored/permanent history alone does not imply a currently lit warning lamp.
An exact count of one adds no numeral to the symbol. A hidden numeral never changes the
underlying count or coverage state; unknown count remains visibly unknown.

The top lane has one owner. Priority follows the existing shared severity/attention
framework, preserving engine-lamp indication when another condition has priority.
Demo input is explicitly selected generated bench/test data. It is not an App preview
and is never enabled automatically when a live source disconnects. Every demo reading or
attention view retains a quiet `Demo` footer in place of page dots; no simulation capsule
competes with a warning. The App must also make the active demo mode clear. Provider
expiry uses a monotonic remaining-time arc; expiration returns to the prior reading view
without leaving a stale notice. Pairing/owner confirmation and update maintenance have their own view
states; underlying vehicle attention is retained and restored. Calibration remains a
bench-only mode. No status widget reinterprets a missing sample as healthy.

**Proposed inspection interaction:** tap the shared status target to inspect the highest
priority issue. Fault inspection shows Engine/Transmission, one complete code, category
and index (for example `Stored 1/4`). Swipe changes the inspected entry. Back returns to
the prior gauge page. Alert inspection uses its own typed condition/value/context.
A critical escalation takes precedence; automatic page cycling pauses during inspection
and attention, then resumes cleanly. Ordinary page gestures and pairing holds retain
existing semantics. Acknowledgment, leaving inspection and clearing codes remain separate.

## 3. Future custom-PID display contract

All catalog and future admitted custom definitions map to one bounded display descriptor.
The renderer consumes this descriptor and a typed sample view. It does not own transport,
controller selection, decoding, freshness policy or threshold evaluation.

| Descriptor field | Rule / fallback |
| --- | --- |
| Full name | Preserved for App/context and accessibility |
| Short label | One authored line preferred; measured at 16 px. App preview validates glyphs and fit. Label-only ellipsis is a last resort with the full name retained in context |
| Value kind | Numeric, bounded enum, or boolean. Other kinds require a defined presentation adapter |
| Canonical unit / display unit | Existing regional conversion first, formatting second; threshold and displayed value use the same unit semantics |
| Unit placement | Once per reading. `RPM` is self-describing; no appended `rpm`. Gear enums need no `gear` suffix. Converted units are never hardcoded into a label |
| Short alert alias / condition | An unambiguous authored alias such as Coolant, ATF or Oil, plus Above/Below arrow or named enum condition. Fits the composed status lane at 16 px, including a secondary MIL symbol; complete wording remains in context |
| Precision policy | Explicit decimals/significant digits, rounding and valid range; same policy in firmware and App preview |
| Compact format | Optional reviewed scientific/scaled format with explicit unit. Never silently discard digits or change scale to make a value fit |
| Display range | Determines Arc/Bar/Trend scale. An out-of-range value remains truthful while its decorative scale clamps visibly |
| Enum mapping | Bounded approved labels; valid unknown enum becomes `?`, missing/stale input becomes `--` |
| Freshness/availability | Typed Current, Missing, Stale, Invalid or Unsupported input. No substituted zero or last value presented as live |
| Renderer compatibility | Numeric is the universal fallback. Dual requires each full formatted value/unit to fit. A chart requires a numeric value and a useful scale |
| Normal accent | Shared theme or approved semantic-neutral preset; severity colors remain owned by the alert framework |

Illustrative descriptor (an internal design seam, not a new wire schema):

```yaml
shortLabel: Fuel rail pressure
valueKind: numeric
canonicalUnit: kPa
displayUnitPolicy: regional
precision: { decimals: 2, rounding: nearest }
renderer: numeric
compactFormat: disabled
```

Fitting order: select the authored short label and unit rule, format using declared
precision, choose the largest permitted font that fits the row's full circular envelope,
then use an explicitly permitted compact format if needed. If no truthful value fits
at 32 px, the App proposes a compatible layout/format before Send. A runtime unexpected
value that still cannot fit gets an explicit representation-error state and retained
exact context; it never silently becomes truncated digits or zero.

Validate existing byte/buffer limits and supported UTF-8 glyphs as well as character
count. Reserved UI controls/icons cannot be injected through labels. Arrow and severity
symbols are owned vector glyphs, not arbitrary characters supplied by a PID. Pairing codes
and fault codes have dedicated formatters. Numeric minima/maxima, negative signs,
long enum strings, decimal carries and region conversion are included in fitting tests.

## 4. Complete screen/state inventory

The screen families below define the coverage boundary. Exhaustive combinations are
parameterized fixtures; individual screenshots are representative examples.

| Family | Required states |
| --- | --- |
| All five reading renderers | Normal; min/max; negative; decimal; long label/unit; font fallback; above/below scale; unavailable; stale; invalid; unsupported; unit change; page change |
| Dual/mixed sources | Both current; either row absent; both absent; same-source rows; independently expiring/recovering inputs; enum + numeric; unequal value length |
| Faults and diagnostic coverage | MIL On/Off/unknown; no codes; known/partial/unknown/large count; Engine/Transmission/both; stored/pending/permanent; last checked; same code across categories/controllers |
| Threshold alerts | Above/Below warning/critical; enum rule; hidden-page producer; escalation/de-escalation; acknowledgment/snooze; expired input; simultaneous faults |
| Attention/inspection | One takeover; multiple active issues; missing current value; provider context without numeric limit; retained last-seen context; Back; interrupted/resumed view; new escalation |
| Connectivity | Source scan/retry/absence mapped to honest availability; sibling remains visible; phone disconnected without disrupting autonomous readings |
| Pairing/ownership | Waiting/closed window; ready; passkey; success; reset confirmation; timeout/cancel; failed attempt/retry; identity suffix when selection needs it |
| Configuration/maintenance | No pages; applying/restoring a dashboard; trial/confirmed/fallback outcomes; actual update progress; restart/healthy verification; paused/refused transfer; rollback |
| Gestures and cycling | Progress; completion; cooldown; canceled swipe; hold; cycling pauses/resumes; feedback suppressed by higher-priority status |
| Simulation and previews | Persistent Demo footer across generated bench readings, faults and overlays; App examples remain labelled previews; no automatic live-data fallback |
| Bench calibration | Color transport, neutral/contrast, concentric boundaries, font/long-number fit and production reference; isolated from distributed runtime |
| Lifecycle/rotations | Every layout in 0/90/180/270 degrees; page/config/unit changes; startup/restart and view return; Auto orientation when capability is eventually implemented |

Existing ordinary phone polls produce no gauge screen interruption. Maintenance/rollback
views shown in the gallery are proposed additions tied to actual typed operation state.
A brief pairing success message still auto-dismisses; any Done target is optional.
No boot logo is reintroduced. Diagnostic inspection is bounded; whole lists/context remain
available through the established App framework.

## 5. Implementation sequence after approval

1. **Standing contract and fixtures.** Incorporate approved layout/PID/compositor rules
   into maintained round-display guidance, design system and AI instructions. Add the
   descriptor/format fitting fixtures and the screen-state matrix. Update the code map.
2. **Shared presentation layer.** Create a small pure-C formatter/layout policy used by
   `ui.c`, a bounded UI view model and one status compositor. Reuse existing alert and
   diagnostic producers. Add 48/40 px fonts with measured flash cost and needed glyphs.
3. **Reading and system layouts.** Apply to Numeric, Arc, Bar, Trend, Dual, pairing,
   shortcuts, simulation, actual maintenance states and bench calibration. Align Android
   preview labels/units to the contract while retaining its Preview designation.
4. **Fault/alert views.** Build the compact global indicator, cached endpoint/code
   inspection and one typed attention view. Snapshot scope/session/freshness stay explicit;
   stale callbacks cannot replace current context. Update the existing framework contract
   for view/interaction policy. Protocol changes only if required data is unavailable in
   the existing snapshots; preserve compatibility and current public capability flags.
5. **Offline then device verification.** Firmware build, production formatter/priority
   tests, LVGL-rendered fixture images and memory/timing comparison. Review actual LCD,
   rotation, touch, daylight/low light and dual-source recovery after an authorized signed
   App/Wi-Fi rollout. Document exact image and observed scope; commit/push under the
   existing workflow. Release publication retains its separate authorization gate.

## 6. Acceptance gates

- No element overlap or essential-content clipping across the matrix. Measure actual
  LVGL glyph/widget bounds, not only these concept TTF boxes.
- One label/value/unit per reading. Complete numeric/enum values, units, pairing codes
  and fault codes remain readable; no scrolling or hidden truncation.
- Fault presence, lamp state, historical category, partial coverage and last-seen state
  remain distinct. Duplicate framework producers cannot create duplicate CEL notices.
- Critical entry/escalation follows the reducer; repeat samples do not retrigger.
  Acknowledgment leaves active status intact. No arbitrary diagnosis from a code number.
- Same descriptor and precision semantics in firmware/App preview, Metric/Imperial and
  both sources. Adding a conforming PID needs metadata, not another special screen.
- UI objects and bounded formatting buffers are reused. Redraw only on visible changes;
  callbacks remain nonblocking. Measure internal memory/largest block/stack and UI worker
  gaps against dev.53, including loaded dual sources and updates; no fragmentation leaks.
- Physical review: direct/angled view, bright and dim settings, day/night, missed or
  accidental taps, existing swipes/holds, no-data recovery, fault inspection and critical
  attention. The small circle is a reason to minimize interaction, not add a miniature menu.
- Owner reviews the plan/mocks before implementation. The final device outcome is
  recorded separately from this proposal and any source/render fixture results.

## Tradeoffs and confidence

The reserved lane costs vertical space even when quiet, but prevents reflow and overlap
when a warning arrives. Separate inspection adds one optional tap while freeing routine
readings from code paragraphs. Two added font sizes increase flash usage; their glyph
sets and measured cost should be reviewed rather than adding a general font engine.

The strongest counterargument is that moving code text into inspection can hide useful
information during a glance. The persistent lamp/issue indicator and explicit critical
attention must therefore remain recognizable without a tap; user recognition checks
should decide whether the unboxed indicator needs an additional short label.

High confidence in the composition/fitting rules: the audit identifies the current
collision paths and the concept glyph checks caught real lower-row failures. Confidence
in final on-panel legibility and touch accuracy is provisional until LVGL and physical
review. These mocks do not establish that a short label, count or glyph is understood by
all drivers; a brief recognition check is part of acceptance.

## Design references

The local [physical round-display calibration](round-display-ui-guidelines.md) governs
geometry. Apple's [small-screen/watch guidance](https://developer.apple.com/design/human-interface-guidelines/designing-for-watchos)
supports brief, glanceable interactions; it is a design reference rather than a vehicle
certification claim. [W3C's use-of-color guidance](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html)
supports redundant text/shape cues. [LVGL label documentation](https://docs.lvgl.io/9.2/widgets/label.html)
defines text expansion, clipping, dot/scroll behavior and buffer lifetime requirements
that the implementation must handle explicitly.
