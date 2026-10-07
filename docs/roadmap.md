# Remaining work

Updated 2026-10-07. Completed behavior is tracked in [current state](current-state.md).
This list contains remaining work, with evidence gates rather than old milestone branches.

| Priority | Work | Completion evidence |
| --- | --- | --- |
| Next parked session | Full TCM fault snapshot on Pixel alongside Gear + Temperature | Correct source/revision, all available categories, 248-byte long read, unavailable after disconnect, new-session recovery |
| Next parked session | Advertised TCM voltage/RPM/speed/MIL and raw pressure correlation | Exact captured identity, labelled off-P/idle-P recordings, restored adapter; no unverified pressure units |
| Definition research | Temperature sensor meaning, shaft speeds, converter slip/lockup and full OEM fault coverage | Matching controller/calibration definitions plus independent reference comparisons |
| Vehicle qualification | Gears 2..8, current/target divergence, achieved rates and adapter soak | Passenger/unattended capture prepared before driving, physical comparison and bounded failures |
| App interaction qualification | Live Car fault refresh, adapter-loss status and concurrent user-operation preemption | Automatic settings, resume, Bluetooth recovery and Add gauge cancellation passed on Pixel/gauge; native goldens updated. Finish live adapter and interruption matrix |
| UI/settings qualification | Physical unit rendering, gesture/cycling interaction, bold colors/arc clearance, alerts | Protected settings persistence, owner-observed five-second cycling and gesture cooldown passed; finish cycling during a pending gesture, hidden-page alerts and daylight readability |
| Recovery hardening | Config/OTA interruption and power-loss matrix, Wi-Fi expiry and uncertain outcome UX | [Focused audit](development/recovery-hardening.md), [recovery matrix](development/ota-recovery-matrix.md), exact images and post-reboot identities |
| Adapter expansion | Second physical adapter plus phone coexistence, independent loss/recovery and sustained load | [Dual recovery matrix](development/dual-adapter-recovery.md); source dev.42 ready for qualification, measured radio/freshness/resource limits; public capacity stays one |
| Product hardening | Phone/adapter/vehicle matrix, accessibility, performance, production release trust and Play publication | [Quality gates](development/quality.md), reproducible artifacts and explicit release authorization |
| Future scope | [Controller actions and physical gesture qualification](architecture/vehicle-actions.md), local page-action foundation implemented, high idle and ABS/ESC remain candidates | Local gesture tests, matching verified controller procedures, bounded execution/readback, restoration and interruption evidence before enabling each action |
| Future scope | Safe code clearing, broader manufacturer packs, live phone telemetry, optional sensors | Documented protocol and complete protected path before advertising support |

## Scope and priorities

Android and the standalone gauge are active. iOS is deferred. Everyday driving,
thermal/off-road use, performance and diagnostics remain product goals, but new
signals need source-specific support evidence. No exhaustive identifier sweep or
synthetic placeholder becomes a supported vehicle value.

Normal vehicles have one primary ECM connection. Expert can attach an optional TCM
child within the same vehicle; legacy standalone Transmission profiles remain usable
until explicitly attached. Each remembered gauge has its own vehicle/source context.
Installed dev.42 implements both workers and the combined app path; physical simultaneous operation still requires qualification. Keep one workspace and the existing settings store.

## Acceptance and maintenance

A change records requirement, behavior, protocol impact, meaningful tests and any
remaining physical check. Commit and push completed work under `AGENTS.md`; merge
through reviewed PRs. Hardware-pending development work stays explicitly bounded
and does not promote public capabilities. Firmware publication is a separate action.
Keep current status in one place and retire completed planning lists.

## Cohesive dual-source vehicle UI

The owner confirmed that the optional TCM belongs to its primary ECM vehicle.
Present ECM/TCM readings as one vehicle in ordinary UI, with one action picker and
combined Gauge preview/review when both adapters are enabled. Preserve source routing
and independent recovery internally; setup details stay in Expert. App dev.38 implements one combined reading/page editing flow with saved cross-controller
page order. Mixed-source readings in one Dual page remain unsupported by firmware. Do not introduce another vehicle profile for a transmission child.

Vehicle editor review and remaining mixed-controller Dual/TCM alert boundaries are recorded in [vehicle dashboard review](development/vehicle-dashboard-review.md).
