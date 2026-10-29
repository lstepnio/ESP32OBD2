# Dev.75 combined indoor delivery

> Historical evidence. [Current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-28, America/Denver. The owner authorized a combined PR #122/#123
release, App installation and App/Wi-Fi firmware delivery. This is indoor evidence,
not loaded Jeep qualification.

## Source and artifact identity

PR #122 merged at `e166fa02822a6842d20d4c2b1ac83886eeb1a801`; its four CI lanes
passed on `3e17eaab352d8be458484c64d406d429d451e0e6` (run `40802262074`).
PR #123 integrated it and merged at `b3f679f7f257e252337332f2f41d6c1beb16a82e`.
The merge tree equals final checked source `62d1e4b0ece1e3d038d423382e61f5e50cba4508`;
all four lanes passed (run `40808347360`). Integration checks included 333 debug / 307
release JVM tests, 94 managed UI tests, host/native checks and the firmware matrix.

The App and instrumentation APKs were replaced without clearing data. The pulled
installed App matched SHA-256
`b0142c38ce37a8569d3cfaa9ed9c7f430bde97d62f1a7689765f6c45b0cc99c0`.
Its development label remains dev.53/versionCode 53 despite the updated source.
Actual Car/history/settings screens were reviewed without loading fixture profiles.
Previous activity, grouped warning history and explicit missing-code detail replace
misleading episode totals; retained code detail and pinned-report reachability follow
the [shared presentation contract](../architecture/alerts-and-diagnostics.md#app-fault-and-history-presentation).

Signed [dev.75](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.75)
was published by successful run `40809534118`, generation 56. All five release assets
were downloaded and verified: signatures, freshness, catalog compatibility, descriptor,
strict bundle inventory, evidence inventory hashes and application parity.

- Image length: 1,686,592 bytes.
- Image SHA-256: `b762939ecff84c13ef2e2e48eadfb3896cd4893ca57c933e7b2244cbceb136c3`.
- ELF: `a03ef73e96df58eadfba846f27104aaad3ce8cf17e0856fe62d43d5d0379bbfd`.

Existing parked-idle and orientation candidate options remain enabled in this
owner-supervised candidate. Public qualification flags and ABS/TCS execution remain
off. Numeric RPM drafts remain phone-only; neither variable controller targets nor
Winch throttle-above-floor behavior is enabled by this installation.

## Successful installation and final checks

The full signed App/Wi-Fi installation completed in 74.822 seconds. Sampled stages
were preparing at 58 ms, sending at 19.142 s, verifying at 61.020 s, restarting at
62.119 s and healthy confirmation at 74.806 s. These are operation-stage samples,
not exact wire throughput. Exact dev.75 ELF, healthy OTA state 2, alternate slot
`0x360000` and cleared update journal were confirmed. No USB firmware write occurred.

Two subsequent Wi-Fi open/authenticated-read/close cycles passed, with protected
opening at 1,572 / 1,650 ms, distinct sessions and unchanged boot/image/setup.
The complete automatic recovery recheck passed in 65.050 seconds: initial protected
opening 8,387 ms, resume 4,334 ms, Bluetooth off/on recovery 12,787 ms, Add gauge
cancellation recovery and a hardware-capacity-to-fresh-firmware read chain.
Wi-Fi mode 255 and a cleared initialized bit confirmed maintenance cleanup.
Final protected diagnostic/configuration/action readback passed in 15.417 seconds.
Both adapter sources were absent indoors; no ECU commands or configuration sends
were performed. Idle and ABS/TCS remained Unavailable with operation 0.

Configuration remained revision 65, digest
`5fe79e9b78e95211fc12d7459140b3a63ecd4426573ed3a967c59b35e8fdcec8`.
Display settings retained brightness 100%, Auto desired, manual fallback 3, Imperial,
15-second cycling and Smart. Private logical comparisons preserved schema-19 profiles,
associations, settings, all 40 raw events, 33 episodes, 17 context configurations and
zero reports. Only the expected reboot synchronization cursor changed; its scope and
drop counts did not. No history was deleted or restored.

The owner separately confirmed: “Clean display and navigation work.” The requested
check covered touch wake, normal swipes, three-up action entry and down-to-return.
Original phone notification policy and timeout/charging-wake settings were restored
and verified; the serial observer stopped. The App was left on Gauge.

## Interrupted attempts and evidence limits

Failed attempts remain recorded and are not counted as passes:

- Initial owner authentication failed before Wi-Fi; fresh protected dev.74 readback
  then passed with no image activation.
- One Android temporary-network request returned unavailable. No consent dialog was
  observed, so this does not establish a user denial.
- A sending attempt ended with “Socket closed”; serial recorded a power-on reset
  and old-image boot around the interruption. The owner was unsure whether power was
  moved. Cause and timing attribution remain uncertain; no OOM cause is established.
  Old-image readback and two Wi-Fi lifecycle checks passed before a fresh upload.
- The first post-update automatic journey passed opening/resume/Bluetooth checks but
  timed out after picker cancellation. The complete affected recheck then passed.
  Source review found no deterministic defect; retain this as intermittent recovery
  follow-up rather than claiming it fixed or broadly qualified.

A concurrent external UI snapshot failed as tooling and was discarded; it supplies
no product evidence. Retained serial samples showed no allocation failures or
panic/corrupt-heap/canary markers. Final internal free/largest block was
115,723 / 31,744 bytes, observed minimum-ever internal free 90,031 bytes and main
stack margin 1,640 bytes. These bounded indoor observations do not prove OOM immunity
or explain every reset across the long session.
Private logs, hashes, readbacks and preservation checks remain ignored under
`artifacts/device-rollout-dev75/`.

## Remaining qualification

Follow the [parked session plan](../development/jeep-action-qualification.md#next-session-configurable-idle-and-pedal-investigation)
for actual variable-target encoding/ranges, pedal behavior, Stop/restoration and
thermal/fallback protection. Loaded foreground sample spacing/smoothing, sibling
ECM/TCM independence and any recurring idle-Unknown recovery still need the Jeep.
The [Auto checkpoint](../architecture/hardware-and-transports.md#automatic-display-orientation)
is installed, but a settled new checkpoint followed by cold boot and full four-angle
touch alignment are not proven by the first OTA boot.
Website impact: defer new idle/performance claims; current App/circular-gauge captures
can be reviewed in the normal daily publication window. No website was published here.
