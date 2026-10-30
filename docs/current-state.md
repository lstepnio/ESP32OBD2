# Current implementation and evidence

Updated 2026-10-30. This is the current source/device snapshot, not a delivery diary.
The [backlog](backlog.md) owns next acceptance outcomes; the [roadmap](roadmap.md)
owns their order. Recheck the active branch, CI and device owner before delivery.

## Installed baseline

| Item | Latest recorded state | Evidence and limits |
| --- | --- | --- |
| Pixel | App dev.57 / code 57 | [Dev.80 delivery](evidence/dev80-action-entry.md); replacement installation, setup/history preserved |
| Gauge | Signed dev.80 from `0ef89b7`, healthy OTA; ELF `889ae28fe30ccb7be491c50503773d2488114c77cc26c509eeca2b3e280e0a7a` | Full App/Wi-Fi upload, two maintenance cycles, automatic recovery/readback passed in [delivery evidence](evidence/dev80-action-entry.md) |
| Saved setup | Revision 66, whole-vehicle ECM + child-TCM dashboard, four TCM code filters | Settings/profiles/command records retained; 90 events, 67 episodes, 18 configuration contexts preserved in the delivery comparison |
| Physical UI | Owner confirmed clean navigation, first-configured-action entry and consistent Connecting styling | [Dev.79](evidence/dev79-action-ui.md) and [dev.80](evidence/dev80-action-entry.md); loaded vehicle UX remains separate |
| Retained command uncertainty | ABS/TCS Unknown, operation 1; Burnout inactive | No Restore, reset confirmation or vehicle command was sent in the indoor delivery |
| Hardware / vehicle | Waveshare ESP32-S3-Touch-LCD-1.28; modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / PCS TCM-2800, two same-model BLE adapters | Reference hardware only; controller/vehicle definitions remain calibration-specific |

These are the latest recorded observations, not live polling. Device access and the
original Android wake/notification policies were released/restored after delivery.
PR #126 contains recovery/adapter-role work; PR #127 contains compact action UI and
is stacked on #126. Neither was merged by those deliveries.

## Source work beyond the installed baseline

PR #128 adds the [product delivery framework](documentation.md#product-quality-and-public-story)
and the approved CODE-06 through CODE-09 follow-up. Per-action observation receipts replace
the shared freshness timestamp; storage/queue fixtures and admission telemetry,
compact current context, and source-aware public claims/screenshots are
implemented and verified offline. [Implementation evidence](evidence/product-quality-implementation.md)
records final checks. App dev.58 and firmware dev.81 are prepared for the owner-authorized combined
merge/release/Wi-Fi delivery. The installed baseline above remains unchanged until
exact post-install readback.

## Implemented product

| Area | Current behavior / authority | Remaining acceptance |
| --- | --- | --- |
| Logical vehicle | One primary adapter; optional swap TCM child changes routing, never reading/page/alert availability. Independent workers and source expiry. [Connections](architecture/vehicle-connections.md) | Earlier live simultaneous sources and independent loss/return passed; startup absence, loaded maintenance, swapped-port correction and soak remain under QUAL-03/04/08 |
| Reading experience | Six renderers, up to ten negotiated pages, units, brightness, manual rotation, timed/Smart cycling, icon-only waiting. Foreground warmup, bounded display-only easing/text stability preserve raw ages. [Display](architecture/display-experience.md), [sampling](development/reading-catalog-alerts.md) | Loaded continuity, rate/latency/resource and daylight/gesture matrix; no prediction or stale values promoted to fresh |
| Engine / TCM data | Header-attributed engine replies; captured JSS temperature and current gear. Catalog has 53 Mode 01 + two JSS readings. [Vehicle scope](vehicles/jeep/wrangler-jk.md) | RPM matched dash; TCM display/loss/return observed. Temperature meaning/scale, moving gears and broader vehicle compatibility remain unqualified |
| Alerts / faults | Shared threshold/MIL/provider-event lifecycle, scoped code lookup/filtering, bounded context/history, opt-in phone monitoring. [Alert owner](architecture/alerts-and-diagnostics.md) | Live filtering, autonomous attention, OEM/background/storage behavior and explicit ECU clearing qualification remain |
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
Do not clear the current Unknown marker to make the UI appear ready.

## Raised-idle coolant guard

The running primary-ECM high Coolant alert supplies the stop limit. Missing protection
blocks Start; high coolant latches Return. Missing/stale coolant caps the session at
the earlier original expiry or 60 seconds after loss, with a fresh physical-gauge
Start required for another fallback session. Recovery never extends the timer.
The App's four-tire Air up calibration resolves to existing bounded timer fields;
it is an estimate, not pressure telemetry. [Coolant/timer contract](architecture/vehicle-actions.md#coolant-protection-for-raised-idle)
owns details. Parked thermal/fallback and real compressor calibration remain open.

## Compatibility and public boundaries

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
- Live phone telemetry/providers, general diagnostic clearing, basic iOS and browser
  USB configuration remain gated/planned. Browser recovery flashes firmware only.
  Additional-board firmware defaults to private; [platform policy](architecture/platforms-and-releases.md).

## Immediate qualification and delivery context

Use backlog FEATURE-04 and QUAL-04/08 for parked Restore/reset, independent loaded
recovery and swapped ports. QUAL-02 owns configuration/OTA interruption; QUAL-07/13
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
