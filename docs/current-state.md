# Current implementation and evidence

Updated 2026-10-07. This is the authoritative status summary for this checkout.
A source build, a simulated response and an owner observation are different evidence.
Recheck Git, installed firmware and connected devices before a new hardware session.

## Baseline

- Board: Waveshare ESP32-S3-Touch-LCD-1.28, GC9A01 240 × 240 LCD, CST816S touch,
  16 MB flash and 2 MB PSRAM. ESP-IDF 5.4.1 with pinned components.
- App: native Kotlin/Jetpack Compose Android, package `com.lstepnio.egauge`.
  iOS work is deferred. Normal app gauge values are labelled previews, not live telemetry.
- Repository: public `lstepnio/ESP32OBD2`, verified 2026-10-06. Release discovery
  verifies signatures and compatibility; visibility alone does not prove feed availability.
- Latest recorded physical gauge: `0.2.0-dev.43`, configuration revision 42, protected running confirmation and cleared trial.
  Source version is in `firmware/gauge/version.txt`; it may be newer than the installed image.
- The owner has one Vgate, swapped between separate ECM and TCM connectors on a
  2010 Wrangler with 5.7 L Hemi / JSS ZF 8HP70 swap and confirmed PCS TCM-2800.
  Diagnostic endpoint/bridge topology is not fully identified.

## Feature status

| Area | Implemented and evidence | Remaining qualification |
| --- | --- | --- |
| Pairing | Foreground Android discovery, system passkey dialog, protected owner readback and firmware owner persistence; subsequent Jeep work used the paired phone | Broader bond-loss/recovery and alternate phone matrix |
| Engine telemetry | Gauge reads attributed 7E8 replies. Owner reported RPM agrees with dash, disappears after unplugging adapter and returns after reconnect | Vehicle/adapter matrix, long soak and achieved polling rates |
| Transmission | One active TCM profile routes 7E1/7E9; combined Gear + Temperature page. Owner confirmed page worked and both readings cleared/returned on adapter loss/recovery | Independent temperature sensor/scale reference, gears 2..8, current/target divergence |
| Faults | Mac captured full stored/pending/permanent TCM lists. Version 15 protected full snapshots and Android source/category groups implemented and tested offline | New long-read transfer on Pixel and full fault polling alongside Gear + Temperature |
| Settings | Brightness, orientation, Metric/Imperial units and automatic saved-page interval persisted through existing settings path; initial app units follow locale region, saved choice/confirmed gauge wins | App dev.39 protected post-restart settings persistence passed; owner confirmed five-second physical page cycling; physical unit rendering remains separate |
| Dashboards/alerts | Numeric, Arc, Bar, Trend and Dual renderers; bounded page/alert configuration, stronger palette and enlarged arc; host logic tests | Daylight/color review, alert transitions and hidden-page behavior on real vehicle |
| Configuration | Atomic slots, exact review/projection, revision/hash conflict checks, trial/fallback and stored/running identity | Physical interruption matrix and all supported layouts/alerts on hardware |
| Updates | Signed development catalog/bundle verification, owner BLE bootstrap, authenticated private Wi-Fi transfer, A/B trial confirmation and recovery journal; App/Wi-Fi installs recorded | Hardware power-loss/expiry matrix, production trust and performance measurements |
| Multiple adapters | Android vehicles have an optional Expert TCM child, profile schema 8; remembered gauges retain independent vehicle/source contexts. Source dev.42 adds independent ECM/TCM workers and the debug app combined send/status path | Installed; two-adapter plus phone and recovery matrix pending; public link capacity is one |

## Public and experimental boundaries

Public capability JSON keeps `configWrite:false` and `ota:false`. The bounded
extensions (`cfg:4`, `ad:1`, `ds:3`, hardware and optional Wi-Fi features) expose
specific development paths; they are not general production support claims.
Public flags must follow implementation and physical evidence.

TCM temperature request 2204FE uses only the first byte as A−40 °C. Other bytes
remain uninterpreted. Captured current/target gear requests 225503/225504 compare
P=0D, R=0B, N=00 and stationary Drive=01; Drive is not a distinct selector value.
Pressure 225034 remains raw only. Shaft speed, converter slip/lockup and complete
OEM fault inventory lack matching validated definitions. Code clearing is a design,
not an implemented action. [Off-road profile actions and gesture triggers](architecture/vehicle-actions.md)
have an installed local page-action foundation; high idle and ABS/ESC control are not enabled. Simulated data never establishes vehicle compatibility.

## Next work

Follow [TCM resume instructions](development/tcm-session-resume.md) for the next
parked session. The owner deferred vehicle/BLE work; do not start it unattended.
Install through App/Wi-Fi, confirm exact firmware identity, check full fault lists
and reconnect behavior, and retain the working combined page.

[Roadmap](roadmap.md) tracks remaining priorities. [Evidence index](README.md)
links historical records. New observations update this file and the relevant
focused evidence record, rather than repeating status in every document.

## Provenance

Firmware board support originated from Janos Kutscherauer's esp32-obd2-meter at
`e1f4d8ffbb2bfe0fb38369e44d532319770ddc00`. Upstream MIT and font OFL notices remain
in the repository. Runtime, transport, ownership and UI have since changed;
the imported baseline behavior is not the current architecture.

## Automatic connection and settings UX update

Source implements one shared connection widget across native pages, an expandable
phone/gauge/active-adapter summary, concise active-source Car faults and automatic
foreground settings/adapter/diagnostics reads. Read failures back off independently;
ordinary Settings rows no longer require a read tap. Multiple-link aggregation is
tested; installed dev.42 adds the gated two-source runtime and readback. Physical
simultaneous operation remains unqualified.

Firmware candidate `0.2.0-dev.40` hardening separates display snapshots from NVS persistence, bounds writer
admission, makes busy status reads retryable, and bounds prompt deadline arithmetic
and RX draining. See [interaction/recovery policy](architecture/interaction-recovery.md)
for exact behavior, simulated screenshots and remaining physical checks. These
changes are installed on the Pixel and physical gauge through the signed App/Wi-Fi path.
The owner confirmed a normal physical page and working swipes.

Verification: 78 Android unit tests, debug APK and instrumentation compilation,
Android lint, eight isolated Pixel UI tests including 76 screenshot comparisons,
72 offline host tests, repository validation and the ESP-IDF firmware build passed.
The physical post-update test passed automatic protected reads on open/resume,
Bluetooth off/on recovery and Add gauge cancellation recovery, retaining settings,
vehicle profile contents and configuration revision/hash within that test. Exact
firmware version/ELF identity and healthy OTA state were asserted before reconnect.
Wire packet layouts, opcodes and public capacity flags are unchanged; busy snapshot
reads now return a retryable ATT resource error. See the focused
[rollout evidence](development/car-connection-rollout.md) for evidence boundaries
and remaining hardware qualification.

## Dual-source development follow-up

Source candidate `0.2.0-dev.42` implements simultaneous source workers, independent
fault/status snapshots and source-scoped recovery. Android uses one atomic combined
projection behind an explicit per-gauge Expert choice and development `da:1` gate.
This candidate is now published and installed with protected identity/health confirmation. Dual-source operation remains physically unqualified. The owner
still has one adapter. Software verification: 84 Android unit tests, 73 offline
host tests, production C compiler/core sanitizer fixtures, firmware/debug builds,
lint, instrumentation compilation and repository validation passed. Native UI and
dual hardware harness execution remain pending. Follow the [dual-adapter recovery plan](development/dual-adapter-recovery.md)
for exact software bounds and the parked/bench matrix; installed dev.40 evidence
above does not establish dev.42 physical behavior.

## Profile action foundation

Source dev.42 adds `Car > Actions`, persisted profile bindings and a local page
shortcut using repeated upward gauge swipes (default three within five seconds).
The existing atomic config/readback path carries the choice through `cfg:4` and
optional schema-2 `actions`. Signed dev.42 is installed with gesture support. No
vehicle commands are enabled; high idle and ABS/ESC remain controller research and
physical qualification work. See [ADR-013](architecture/vehicle-actions.md) for bounds,
interaction/recovery tests and the remaining execution contract.

Action foundation verification: 89 Android unit tests, three isolated native emulator
UI tests, 73 offline host tests, production C configuration/gesture sanitizer fixtures,
debug/test APK builds, lint, firmware build and contract/link checks passed. The native
example and exact limits are recorded in ADR-013. Signed publication and App/Wi-Fi installation are now recorded below. Physical
three-upward-swipe page jump and normal horizontal swipes were confirmed by the owner.
Cooldown and cycle interaction still require physical observations. Earlier dual-source test totals describe that development
increment, not the latest action increment.

App dev.35 introduced compatibility with development `cfg:4`; installed App dev.38
retains it. Apps older than dev.35 reject that version.
Signed dev.42 and catalog generation 24 were published and installed on 2026-10-07.

Four Car screenshot references were updated and compared successfully on the API-36
emulator. The full Pixel golden suite remains pending: unchanged compact Settings
references show small arrow-glyph differences on this emulator. Original Settings
references and strict comparison tolerance are retained; ADR-013 records the limits.

## Dev.42 device installation

The physical Pixel installed App dev.35 and transferred signed dev.42 through
App/private Wi-Fi. Protected readback asserted the exact release ELF and OTA health 2;
one automatic opening/resume test passed with revision 31 and settings retained.
Initial post-reboot confirmation timed out, then protected reconciliation succeeded.
The phone lacks the gauge's matching saved TCM profile, so no shortcut configuration
was sent. Recover that profile before the gesture check. Owner screen/swipe
confirmation remains pending. See [rollout evidence](development/profile-actions-rollout.md).

## Single-source profile recovery

App dev.36 adds Expert “Use saved gauge setup” for a gauge profile missing from
local storage, with confirmed owner readback, supported source/pages/binding checks,
capacity bounds and no existing-profile overwrite. The Pixel recovered its TCM
profile while preserving all three existing profiles. The normal Car Actions and
review/send flow installed the three-upward-swipe TRANSMISSION shortcut. Protected
readback confirmed revision 32 and exact expected running digest with trial cleared;
only the action and base revision changed. The owner confirmed normal display and
horizontal swipes on dev.42. The owner confirmed the upward gesture jumps from GEAR to TRANSMISSION. The initial
configuration restart confirmation also timed out and later reconciliation passed;
App dev.38 addresses this recovery UX below. See the rollout evidence above. Recovery unit,
build/lint and instrumentation compilation passed (92 Android unit tests).

## One vehicle with a transmission child

App dev.37 permits the exact same adapter binding in ECM and TCM local drafts when
one adapter is moved between ports; simultaneous combined projection still requires
two distinct bindings. The Pixel's recovered TCM setup was attached to Jeep without
changing its engine draft, child pages or gesture action. No standalone transmission
vehicle remains. Normal review/send updated the gauge to Jeep at revision 33; protected
readback verified matching running digest and cleared trial. Only `vehicleProfileId`
and `baseRevision` changed from revision 32. Firmware remains signed dev.42.

When both adapters are enabled, Car Actions shows one combined page picker and routes
the selected target into its owning draft, clearing the previous gesture binding across
the vehicle. Off clears both bindings. Invalid targets or ambiguous page identities
are rejected. Source attribution remains internal to polling, routing and recovery.
App dev.38 supersedes the source-scoped editor with one combined editing flow
as recorded below. Verification: 94 Android unit tests, debug/test builds and
lint passed; one physical protected opening/resume readback test passed in 8.086 seconds.
Initial post-restart confirmation needed later reconciliation; App dev.38 addresses this below.

## Restart confirmation and unified editor

App dev.38 fixes restart-session timeouts being mistaken for caller cancellation.
Protected configuration and firmware confirmation reads now retry within bounded
budgets, while real cancellation propagates and no uncertain write is replayed.
A physical warm-App setup resend confirmed healthy revision 35 in 15.282 seconds,
without reopen or refresh. Fresh OTA confirmation with this App remains a separate
physical check. See [restart/editor rollout](development/restart-and-dashboard-rollout.md).

Customize now presents one combined page list and engine/transmission reading picker
when both adapters are enabled. Profile schema 8 saves cross-controller page order;
older schemas retain their existing ordering. Review/projection use the same order,
internal routing retains parent/child ownership, and compatible second readings are
filtered for the firmware's current Dual-page limit. 101 unit tests, two isolated
native Pixel UI tests, debug/test builds and lint passed. Single-adapter vehicle state
is preserved; combined hardware operation remains unqualified. Physical cooldown,
cycling and touch behavior after restart still require owner observations.


## Service invalidation recovery and settings persistence

Installed App dev.39 handles repeated MTU/discovery callbacks, keeps validated
characteristics, and rediscovers during handshake on the same BLE link when the
gauge invalidates its service database. Invalidation after handshake still fails
the operation honestly; no uncertain write is replayed. An MTU callback deadline
allows discovery to proceed conservatively, with normal negotiated packet sizing
retained. The 750 ms handshake settling window keeps late invalidation ahead of
protected commands. Debug session failures retain diagnostic causes.

The Pixel/gauge persistence test passed in 58.049 seconds overall. The same warm
App confirmed revision 42 and fresh protected display settings in 22.065 seconds:
100% brightness, 270° orientation, Imperial units, five-second cycling. Exact setup
and shortcut survived. Cycling was restored to its original Off setting, and phone
wake preferences were restored. 101 Android unit tests, debug/test builds and lint
passed. Earlier timeout-only repair did not cover this cache-invalidation case;
see the [focused record](development/restart-and-dashboard-rollout.md). Owner confirmed five-second physical cycling and the App restored Off;
the owner also confirmed cooldown blocks an immediate repeat and permits a later
jump, with responsive navigation/touch after restart. Fresh firmware OTA with this App passed in the signed dev.43 follow-up below.
Physical cycling while a gesture is pending and two-adapter hardware qualification
remain pending.


## Fresh App/Wi-Fi confirmation qualification

Signed dev.43 from `a8f1f10`, catalog generation 25, was published with explicit
owner authorization and independently verified against the pinned trust key.
App dev.39 installed it through Settings and private gauge Wi-Fi; the same warm
App showed Update installed after authenticated post-reboot confirmation. No USB
flash, reopening or manual version check was needed to obtain that success.

The subsequent protected opening/resume test passed in 12.583 seconds, asserting
the exact hosted dev.43 ELF hash and healthy OTA state 2. Configuration revision 42,
full setup, profiles, gauge assignments and display settings were unchanged; update
recovery journal cleared. Settings remain 100%, 270°, Imperial, cycling Off. Phone
wake preferences restored. The owner confirmed the physical gauge is on a normal
page and page swipes work after this update. See the [signed rollout and measured timing limits](development/restart-and-dashboard-rollout.md).
Public capability flags and hardware radio qualification remain unchanged.


## Recovery hardening candidate

Android dev.40 and firmware source dev.44 add cancellation-aware total socket
deadlines, firmware session-generation invalidation and bounded listener readiness,
and immediate independent settings/source polling on resume. Wi-Fi OTA queue
entries and unactivated transfers also follow the session generation, releasing
the adapter-polling reservation after transport loss while preserving durable
activation and independent BLE transfers. The shared patterns
and staged verification are documented in [recovery hardening](development/recovery-hardening.md).
Installed gauge remains signed dev.43 until a separately authorized release is
installed and its exact identity confirmed. Protocol/public capability claims are
unchanged; physical interruption and simultaneous adapters remain open gates.

Offline checks passed: 106 Android unit tests, 74 host tests including sanitized
production-C socket negatives, debug/instrumentation APK builds, lint, ESP-IDF
5.4.1 build and repository validation. Pixel disconnected before installation or
physical testing, so App dev.39 + firmware dev.43 remain the last installed pair.
Native recovery tests, new firmware transport-loss and interruption checks remain
pending; source deadlines are not measured hardware latency claims.
