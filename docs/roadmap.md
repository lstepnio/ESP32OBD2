# Remaining work

Updated 2026-10-06. Completed behavior is tracked in [current state](current-state.md).
This list contains remaining work, with evidence gates rather than old milestone branches.

| Priority | Work | Completion evidence |
| --- | --- | --- |
| Next parked session | Full TCM fault snapshot on Pixel alongside Gear + Temperature | Correct source/revision, all available categories, 248-byte long read, unavailable after disconnect, new-session recovery |
| Next parked session | Advertised TCM voltage/RPM/speed/MIL and raw pressure correlation | Exact captured identity, labelled off-P/idle-P recordings, restored adapter; no unverified pressure units |
| Definition research | Temperature sensor meaning, shaft speeds, converter slip/lockup and full OEM fault coverage | Matching controller/calibration definitions plus independent reference comparisons |
| Vehicle qualification | Gears 2..8, current/target divergence, achieved rates and adapter soak | Passenger/unattended capture prepared before driving, physical comparison and bounded failures |
| UI/settings qualification | Metric/Imperial persistence, automatic page cycling, bold colors/arc clearance, alerts | Owner observations on the physical LCD, including hidden-page alert behavior and daylight readability |
| Recovery hardening | Config/OTA interruption and power-loss matrix, Wi-Fi expiry and uncertain outcome UX | [Recovery matrix](development/ota-recovery-matrix.md), exact images and post-reboot identities |
| Adapter expansion | Second physical adapter plus phone coexistence and fallback | Measured radio/freshness/resource limits; public capacity stays one until qualified |
| Product hardening | Phone/adapter/vehicle matrix, accessibility, performance, production release trust and Play publication | [Quality gates](development/quality.md), reproducible artifacts and explicit release authorization |
| Future scope | Safe code clearing, broader manufacturer packs, live phone telemetry, optional sensors | Documented protocol and complete protected path before advertising support |

## Scope and priorities

Android and the standalone gauge are active. iOS is deferred. Everyday driving,
thermal/off-road use, performance and diagnostics remain product goals, but new
signals need source-specific support evidence. No exhaustive identifier sweep or
synthetic placeholder becomes a supported vehicle value.

Normal configuration uses one source per profile. Dual-adapter scaffolding does
not satisfy the simultaneous-operation gate. Use the existing Transmission profile;
avoid introducing a second experimental workspace or competing settings store.

## Acceptance and maintenance

A change records requirement, behavior, protocol impact, meaningful tests and any
remaining physical check. Commit and push completed work under `AGENTS.md`; merge
through reviewed PRs. Hardware-pending development work stays explicitly bounded
and does not promote public capabilities. Firmware publication is a separate action.
Keep current status in one place and retire completed planning lists.
