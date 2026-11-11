# Current implementation and evidence

Updated 2026-11-10. This is the current source/device snapshot, not a delivery diary.
The [backlog](backlog.md) owns next acceptance outcomes; the [roadmap](roadmap.md)
owns their order. Recheck the active branch, CI and device owner before delivery.

## Installed baseline

| Item | Latest recorded state | Evidence and limits |
| --- | --- | --- |
| Pixel | App dev.58 / code 58 rebuilt from main `4f36ef2`, including the sunset-location recovery fix | [Device refresh](evidence/development-device-refresh.md); installed APK digest and existing signer verified; setup and history retained |
| Gauge | LCD signed dev.86; private OLED Balanced `0.2.0-dev.84-amoled.2-w15`; both healthy | Exact authenticated ELF hashes match verified signed artifacts. Existing images retained; no reflash or release publication was necessary |
| Saved setup | LCD dashboard revision 68; OLED revision 7; Day 100%, Night 10%, Connection icons On | Both dashboard revisions/hashes unchanged; App toggle Off/On and automatic settings readback passed. OLED Night corrected from 20% to 10% |
| Physical UI | Owner confirmed clean dot-free layouts and working swipes on both boards; OLED button brightened then dimmed | OLED Balanced retained. Earlier 10% icon legibility accepted; vehicle OBD2 icon loss/return remains pending |
| Retained recovery | No vehicle controls, Restore/reset confirmation or recovery-record clearing performed | Phone update journal clear; exact firmware health verified. This session does not establish vehicle-control restoration |
| Hardware / vehicle | S3-LCD-1.28 v1 and S3-OLED-1.75 v1; modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / PCS TCM-2800 | Reference LCD uses the public development path; OLED firmware remains private. Controller/vehicle definitions remain calibration-specific |

These are recorded observations, not live polling. Private setup backups and signed
OLED artifacts remain outside the public repository. [Earlier dev.81 delivery](evidence/dev81-combined-delivery.md)
retains its original setup/history and recovery observations; those are not the
current device baseline.

## Latest merged delivery

Main `4f36ef2` passed all four quality lanes. The App includes sunset-location
recovery from PR #157; LCD dev.86 enables the connection footer from PR #155.
The private OLED remains on its approved Balanced integration and shared core pin.
The [refresh evidence](evidence/development-device-refresh.md) records build checks,
exact installed identity, preservation comparisons and bounded repository cleanup.
No stable-channel migration or unfinished feature branch was included.

## Implemented product

| Area | Current behavior / authority | Remaining acceptance |
| --- | --- | --- |
| Logical vehicle | One primary adapter; optional swap TCM child changes routing, never reading/page/alert availability. Independent workers and source expiry. [Connections](architecture/vehicle-connections.md) | Earlier live simultaneous sources and independent loss/return passed; startup absence, loaded maintenance, swapped-port correction and soak remain under QUAL-03/04/08 |
| Reading experience | Six renderers, up to ten negotiated pages, units, brightness, manual rotation, timed/Smart cycling, icon-only waiting. Foreground warmup, bounded display-only easing/text stability preserve raw ages. [Display](architecture/display-experience.md), [sampling](development/reading-catalog-alerts.md) | Loaded continuity, rate/latency/resource and daylight/gesture matrix; no prediction or stale values promoted to fresh |
| Engine / TCM data | Header-attributed engine replies; captured JSS temperature and current gear. Source catalog has 61 Mode 01 readings (including eight oxygen voltage components) plus two JSS readings. Generic and JK/Hemi + JSS starter setups are available in source. [Vehicle scope](vehicles/jeep/wrangler-jk.md) | RPM matched dash; TCM display/loss/return observed. Temperature meaning/scale, moving gears and broader vehicle compatibility remain unqualified |
| Alerts / faults | Shared threshold/MIL/provider-event lifecycle, scoped code lookup/filtering, bounded context/history, opt-in phone monitoring. [Alert owner](architecture/alerts-and-diagnostics.md) | Live filtering, autonomous attention, OEM/background/storage behavior and explicit ECU clearing qualification remain |
| Android virtual gauge | Direct, read-only BLE dashboard using saved vehicle pages. Owner reported the single-adapter version worked on the Jeep. [Scope and setup](architecture/virtual-gauge.md) | Optional JSS child and Android Play Billing are implemented in source. Dual Vgate and Play license-tester qualification are pending. Defer storefront claims and refreshed marketing captures |
| App UX | Gauge/Car/Settings share connection status; Car uses one attention list, direct detail, Current/History and focused alert settings. Expert contains technical support/recovery. [Design](design/design-system.md) | Real vehicle workflow, accessibility/alternate phones and notification acceptance |
| Configuration / updates | Coupled phone setup saves, dual gauge generations, running revision/hash/trial verification; signed owner-protected Wi-Fi update and A/B health reconciliation. [Storage](architecture/config-storage.md), [release runbook](development/firmware-release-runbook.md) | Config commit interruption, trial rollback, rail-power loss and loaded radio matrix remain separate from indoor upload success |
| Recovery / resources | Bounded transport admission, retired callback fencing, independent backoff, worker/queue and memory health, PSRAM placement/admission limits. [Firmware ownership](architecture/firmware-runtime.md) | Neither OOM immunity nor complete scheduler/radio recovery is established; QUAL-08 owns loaded measurements |
| Role correction | Known saved transports are probed for exact captured ECM/TCM calibration+CVN. Opposite-only positive pair permits atomic runtime exchange, preserving configuration. [Role contract](architecture/vehicle-connections.md#role-verification-for-accidentally-swapped-adapters) | Native policy/transport tests pass; physical swapped-port, reconnect/reboot and sibling continuity remain |

## Actions and retained recovery

Normal menus have Air up, Winch and ABS/TCS. Burnout/Line Lock is paused and removed
from ordinary menus; legacy wire/journal recovery remains so old uncertainty is not
erased. Compact action entry starts at the first configured page, with common
Connecting presentation and deliberate physical controls. [Action contract](architecture/vehicle-actions.md)
owns admission, wording, Stop/Restore and persistence.

Air up/Winch use the captured fixed approximately 1,700 RPM setting in the explicit
owner-supervised parked candidate. Numeric target/minimum-RPM settings remain
phone-only drafts; their controller mapping and throttle behavior are unqualified.
The owner reported successful Air up, but coolant/fallback duration and thermal-stop
latency still need parked evidence. Read-only recovery now includes the captured
session prerequisite; persistent Unknown recovery on the actual ECM remains pending.

ABS/TCS owner testing exposed that ignition restart alone did not restore the Jeep;
JScan toggle plus restart restored it. Installed source adds explicit captured Return
before reset and retains an acknowledged-Return receipt across gauge reboot. A
positive reply is request acceptance, not physical restoration. Verify normal dash/
braking and explicit owner reset confirmation under the [parked runbook](development/jeep-action-qualification.md#brake-actions-follow-up).
Do not clear an unresolved recovery marker to make the UI appear ready.

## Raised-idle coolant guard

The running primary-ECM high Coolant alert supplies the stop limit. Missing protection
blocks Start; high coolant latches Return. Missing/stale coolant caps the session at
the earlier original expiry or 60 seconds after loss, with a fresh physical-gauge
Start required for another fallback session. Recovery never extends the timer.
The App's four-tire Air up calibration resolves to existing bounded timer fields;
it is an estimate, not pressure telemetry. [Coolant/timer contract](architecture/vehicle-actions.md#coolant-protection-for-raised-idle)
owns details. Parked thermal/fallback and real compressor calibration remain open.

## Compatibility and public boundaries

- Source builds distinguish the free reference platform from private boards.
  The free build uses one OBD-II adapter, timed cycling and navigation gestures.
  Private builds can enable dual adapters, Smart Cycle and vehicle Action Pages.
  The [platform policy](architecture/platforms-and-releases.md#platform-feature-flags)
  describes compatibility with existing setups. Installed baselines above
  describe earlier firmware.
- Phone profiles use schema 19, associations schema 2; gauge configuration schema 2.
  [Storage compatibility](architecture/android-runtime.md#storage-and-recovery) and
  [wire documents](README.md#protocols) own migration/version details.
- Ordinary qualification masks remain zero; public BLE `configWrite:false`,
  `ota:false` and qualified link capacity one remain. Owner candidates and protected
  development paths do not establish general public vehicle-control qualification.
- Auto orientation has the corrected mapping and sensor worker in the installed
  candidate. All-angle physical text/touch and cold-boot behavior remain pending;
  manual rotation and page cycling are independent. [Orientation](architecture/hardware-and-transports.md#automatic-display-orientation).
- TCM temperature currently uses first-byte A−40 °C; pressure remains raw. Gear
  meanings and topology are calibration-specific. No catalog-wide vehicle support.
- ESP32-to-phone telemetry, live external providers, general diagnostic clearing, basic iOS and browser
  USB configuration remain gated/planned. Browser recovery flashes firmware only.
  Additional-board firmware defaults to private; [platform policy](architecture/platforms-and-releases.md).

## Immediate qualification and delivery context

Next at the Jeep, verify the live OBD2 footer on both boards: independent loss/return
and unaffected sibling behavior on the OLED. Use one gauge at a time so they do not
compete for the same adapters. Use backlog FEATURE-04 and QUAL-04/08 for parked
Restore/reset, independent loaded recovery and swapped ports. QUAL-02 owns configuration/OTA interruption; QUAL-07/13
own physical rendering/gesture acceptance. Offline changes never close those gates.
The website published `13eef6b` on 2026-10-30 after all four CI lanes passed.
[Delivery evidence](evidence/product-quality-implementation.md#public-preview-and-handoff)
records 40 HTTPS file matches and live desktop/mobile checks. Its manifest identifies
deployed website source separately from installed App/FW. The [daily policy](../website/README.md#maintenance-cadence)
remains; closing documentation creates no second publication.

## Earlier status references

The [pre-consolidation snapshot](evidence/status-through-dev80.md) preserves exact
historical versions, measurements and previously contradictory prose. The links
below retain older section anchors for existing bookmarks; they are historical.

<a id="action-presentation-follow-up"></a>
<a id="action-recovery-presentation-follow-up"></a>
<a id="android-variant-quality-gates"></a>
<a id="approved-action-pages"></a>
<a id="approved-car-implementation-not-yet-installed"></a>
<a id="assessment-remediation-prepared-in-source"></a>
<a id="continuity-update"></a>
<a id="display-and-adaptive-sampling"></a>
<a id="front-lock-follow-up"></a>
<a id="installed-car-fault-experience"></a>
<a id="installed-foreground-fault-and-idle-target-follow-up"></a>
<a id="installed-owner-supervised-brake-candidate"></a>
<a id="known-reliability-limits"></a>
<a id="prepared-four-tire-air-up-estimate"></a>
<a id="preserved-pr-work-reconciled-in-source-2026-10-28"></a>
<a id="remaining-pr-source-preservation"></a>
<a id="resume"></a>
<a id="shared-platform-interface-foundation"></a>
<a id="smart-page-cycling"></a>
<a id="support-and-evidence"></a>
<a id="unreleased-field-recovery-follow-up"></a>
<a id="website-and-public-download-refresh"></a>

For earlier sections, use the [archived snapshot](evidence/status-through-dev80.md).
