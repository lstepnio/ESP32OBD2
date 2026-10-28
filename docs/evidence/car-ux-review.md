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


## 2026-10-28 Pixel installation and indoor checks

PR #115 source `37029a33050bec2acfbfae3c36f20f9ad5809e2c` passed all four CI
checks in run `40757136601` and merged as `f2dbf7596dbbb378eec9485c0f097ed79159ef10`.
The merge tree matches the checked source. Debug App and instrumentation packages
were rebuilt from that tree and installed with data-preserving replacement.
The installed base APK matches the local build SHA-256
`1059966e1f082f467fc1073e2fc4b49b82441ff7b96473f6e6e35b5eb1e70cda`.
The App version label remains `0.2.0-dev.53`; source identity distinguishes this update.
No firmware was installed during this task.

Private before/after backups verified the schema 18 to 19 migration added only one
existing alert's `enabled: true` default. All other profile fields, array order,
gauge associations and saved settings remained equal. SQLite integrity passed and
ordered logical rows remained equal after the complete UI/recovery session:
65 events, 51 episodes, 17 configurations, one cursor and zero reports.
Raw history was retained while ordinary views used the shared filtering projection.

Actual Pixel checks covered the simplified Car overview, Current/History navigation,
direct historical fault detail, truthful unavailable historical context, the saved
coolant alert editor with cancellation, and Advanced hidden-code rules. All four
Transmission-scoped rules remained present: U0121, P1DCA, P1DF3 and U0140.
The Add alert picker included Transmission temperature and Gear; it was closed
without saving. Visual inspection found no clipping or navigation blockers in the
captured Car and hidden-code screens. No fixture data was loaded onto the owner phone.

The read-only diagnostic journey passed in 18.73 seconds. The automatic connection
journey passed in 75.268 seconds, including resume, Bluetooth off/on recovery,
picker cancellation and protected hardware/firmware readback. Both confirmed healthy
running dev.72, saved configuration revision 65 and digest
`5fe79e9b78e95211fc12d7459140b3a63ecd4426573ed3a967c59b35e8fdcec8`.
The five pages, actions, alerts and filters were preserved. No vehicle commands,
ECU clearing, configuration writes or firmware transfers were issued.

Original phone alert preferences and timeout/charging-wake settings were restored
and verified. The App was left open on Car. The merged feature branch was removed
locally and remotely; private captures/backups remain ignored under
`artifacts/car-app-pr115/`. Website visuals remain queued for the normal daily batch.

Both vehicle adapters were absent indoors. Live filtered/unfiltered/partial ECM/TCM
behavior, notification delivery under real vehicle conditions and per-gauge
acknowledgement remain separate qualification gates. The expected retained idle
Unknown state was not cleared or treated as a new vehicle observation.
