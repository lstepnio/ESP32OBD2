# Car workspace UX review

> Historical evidence. This dated review records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Reviewed 2026-10-07 against the current design system, logical vehicle architecture,
shared alert framework and running Pixel App. Scope: Car presentation and focused
sheets. Android remains dev.48; firmware, protocol and persistent schemas are unchanged.

## Findings and changes

The original first viewport showed report export/deletion and repeated Engine and
Transmission Stored/Pending/Permanent empty categories. Vehicle selection and
adapter setup appeared below that diagnostic content. Connection messages duplicated
the universal pill. The adapter sheet did not scroll at large text sizes.

The revised screen uses existing cards, spacing, typography, semantic colors and
settings rows. Selected car and necessary setup precede one Vehicle health card,
compact recent Alerts, Adapter and Gauge gestures. Routine reconnecting is handled
by the shared pill; a remembered gauge is not asked to pair or set up again.
Waiting uses neutral text, not a successful-state color.

- Fault codes open a named sheet. Category duplicates merge per controller, while
  Engine/Transmission attribution and last-checked category labels remain intact.
- Incomplete, stale and unavailable checks remain visible. A healthy engine cannot
  hide an unavailable transmission check, and lamp-off status is not an all-clear.
- Exact diagnostic category coverage moved into Expert. No generic Details link
  was added to everyday pages.
- Recent alerts prioritize current active events by severity and retain simulation,
  freshness, severity, lifecycle and attention meanings. Full history, export and
  protected history deletion live in Alert history. Existing context/report data,
  global banner, acknowledgment and gated clearing use the same framework.
- Scrollable sheets and vehicle-keyed local state prevent a sheet from operating
  on the previous car after vehicle switching. Existing profile add/delete and
  adapter selection functions remain in place.

## Verification

- Android debug App/test APK build and lint passed; 172 JVM tests passed, including
  ten new presentation cases for partial/truncated results, sibling loss, lamp-off
  codes, controller-scoped deduplication and independent alert meanings.
- Fifteen focused tests passed on an isolated emulator in a phone-sized viewport:
  large text at 200%, fault attribution, remembered-gauge recovery, vehicle switch
  while a sheet is open, shared connection/settings controls, and synthetic alert
  history/context/private export. These are fixtures, not live vehicle results.
- Repository contract/documentation validation passed.
- Data-preserving debug APK installation on the Pixel passed. The actual Car page
  was visually inspected and captured; searching/waiting is neutral and does not
  prompt a known gauge setup. Main-page report/delete and empty category controls
  are absent. The Alert history sheet was opened on the Pixel; report/deletion
  controls were visible there without performing either action. Private captures stay outside committed documentation.
- Pixel vehicle/page storage, appearance and gauge association compared byte-for-byte
  unchanged before/after installation. No adapter selection, ECU command or gauge
  firmware/settings write was performed for this UI pass.

Live warning/fault interaction with a vehicle and simultaneous adapter qualification
remain under the existing backlog gates. This UI pass does not qualify those paths
or change firmware public capability flags.

## 2026-10-28 approved Car and hidden-code implementation

The owner approved [Car review 1](../design/car-ui-proposal.md) and requested
implementation. The source replaces overlapping panels with one attention list,
direct decoded fault detail, full-screen Current/History and focused alert settings.
Raw history/context remains immutable; saved vehicle intent projects all ordinary
and Expert surfaces, notifications and normal reports. Only Advanced rule management
names hidden codes. Firmware source and wire schema are unchanged; phone schema 19
retains disabled rules without transmitting them.

Local checks: debug/release JVM suites, debug App/test APK compilation, debug APK
assembly, Android lint, Kotlin formatting, host lint and documentation/tooling tests.
A full 73-test managed API 35 run passed, plus a focused 29-test run around the
save refinements. Fixtures cover current/history scoping, duplicate category/MIL
rows, controller/gauge isolation, known/unknown explanations, large text, offline
real-model saves, TCM alert save completion, notification cancellation/silent unhide,
real SQLite/ZIP redaction and unchanged raw evidence. Quick-save tests prove that
staged primary/child bindings, routing and other settings require full review.
Startup MIL/count-zero and unavailable summaries have explicit negative cases.

`docs/assets/app/car.png` is refreshed from RepositoryGalleryTest using production
composables and the actual background/navigation colors. The image shows fixed
fixture data, not a new physical or vehicle observation. Existing round-gauge art
is unchanged. The website's next normal daily batch can pick up this shared asset;
no website publication was performed here.

This work did not install an APK, change saved owner setup, publish firmware, clear
codes, issue vehicle commands or touch the Pixel/gauge. CI remains a separate PR
gate. Physical Pixel usability, applied/pending state on actual assigned gauges and
parked filtered/unfiltered/partial ECM/TCM behavior remain open. Dev.72's inherited
count-zero MIL policy remains under its separate labelled-capture boundary.
