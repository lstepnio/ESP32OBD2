# Dev.74 consolidated device delivery

> Historical evidence. This dated rollout records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-28 in America/Denver. This is indoor delivery evidence, not loaded
Jeep qualification.

## Source and installed artifacts

The owner requested the latest App/gauge baseline and explicitly authorized merging,
publishing and installing dev.74 after CI. PR #120 changes only firmware version
identity over consolidated main `c8ab21aaeac00a3197546169c690b86beb44dda1` from PR #119.
All four quality lanes passed on `1ba79411a94316e567014eb6fc956a99603c8f62`
(run `40789924754`); merge `6b904c7d0e5b96cb53316337606f1232b17d6089` has the same tree.

The latest debug App and instrumentation APK were built and installed with
replacement, preserving data. The installed base APK was pulled and matched SHA-256
`826aba59008b44a42b8b32aacc7fd3dc397f008686a0a61816d8d617b8f7848b`.
The App label remains dev.53/versionCode 53; Android source is the consolidated
main baseline, not the earlier installed source with the same version label.
No migration, new permission or experiment/provider activation was introduced.

Signed [dev.74](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.74)
consumes generation 55, published by run `40791350838` from the exact checked source.
All five assets were downloaded: catalog/image and evidence signatures, freshness,
board/layout/protocol, descriptor, inventory and application parity verified.
Image length is 1,683,936 bytes; ELF is
`912c87a01b481552f31c5ead597fac15d8f60760f39151fcc83e9e5611f8d445`.
Existing parked-idle and orientation candidate options are retained; normal
qualification/public flags and ABS/TCS execution remain off.

## Physical App/Wi-Fi path

Before installation, the updated App confirmed healthy dev.73, protected setup and
both cached diagnostic endpoints in 15.581 seconds. Two Wi-Fi open/read/close cycles
passed in 42.390 seconds, closing the prior USB rollout's deferred radio readback.
The production Car overview was visually reviewed without clipping; no fixture data
or configuration edits were loaded onto the owner phone.

The hosted signed update journey passed in 105.889 seconds; the observed installation
operation took 72.769 seconds. Stages were preparing at 50 ms, Wi-Fi sending at
16.659 s, verifying at 57.029 s, restarting at 58.104 s and healthy confirmation at
72.766 s. These are sampled operation-stage timings, not exact wire throughput.
The ordinary private Wi-Fi transport was observed. Alternate slot `0x60000`, exact
healthy ELF/state 2, cleared journal and unchanged configuration/settings/profiles
were confirmed. No USB firmware write was performed in this delivery.

The first subsequent maintenance check returned GATT 133 during a power-on reset.
The owner confirmed restarting/reconnecting the gauge at that time. It was not
counted as a pass or attributed to firmware. Only the interrupted post-installation
checks were repeated, with no second upload:

- Two Wi-Fi reopen/read/close cycles passed in 48.201 seconds. Protected opening took
  1,807 and 1,960 ms; the boot nonce stayed unchanged through both cycles.
- Automatic opening/resume/Bluetooth/picker/read-chain recovery passed in 70.458 s.
  Opening confirmation took 10,323 ms, resume 4,371 ms and Bluetooth recovery 12,984 ms.
  Wi-Fi mode 255 confirmed cleanup; protected hardware then fresh firmware reads passed.
- Final diagnostic/configuration/action readback passed in 24.116 seconds, confirming
  healthy dev.74, revision 65 and digest
  `5fe79e9b78e95211fc12d7459140b3a63ecd4426573ed3a967c59b35e8fdcec8`.

Both adapter sources were absent indoors. Retained idle operation 1 stayed Unknown;
this was not cleared to manufacture readiness. No vehicle controls, ECU clearing or
configuration sends were invoked.

## Preservation and resource observations

Private before/after comparisons retained the complete schema-19 profile collection,
associations, settings and alert policy. All 32 raw events, 30 episodes and 17 stored
context configurations remained exactly equal as logical rows; reports remained empty.
Only the synchronization cursor's boot/sequence changed. `AlertRepository.ingest`
allows a sequence reset on a changed alert-runtime boot; scope/drop count stayed equal.
The initial private comparison incorrectly demanded cursor equality across reboot;
its follow-up distinguishes that cursor from immutable history and from the independent
vehicle-command boot nonce. No history was restored, deleted or edited.

Serial observations after the owner restart retained zero allocation failures and
no panic/corrupt-heap/canary marker. The lowest recorded free-internal sample was
91,755 bytes; final internal free/largest was 115,891/31,744 bytes. Minimum-ever internal
free in that boot was 90,231 bytes. These observations cover this bounded indoor path,
not a long loaded/radio soak or OOM immunity.

The owner confirmed clean text, normal taps/swipes, three-up action entry and
Down-to-return. Original phone notifications and timeout/charging-wake preferences
were restored and verified; the serial observer stopped. The App was left available
for normal use. Private backups/captures remain ignored under
`artifacts/device-rollout-dev74/`. Website impact is the already prepared consolidated
artwork for a normal daily batch; no website publication was performed here.

## Remaining gate

At the Jeep, verify automatic uncertainty recovery from exact ECM identity, captured
session acknowledgement, inactive routine status and later settled RPM. Retain the
independent TCM link during that check. Loaded freshness, coolant/fallback/thermal
protection and commanded Start/Stop remain governed by the
[parked runbook](../development/jeep-action-qualification.md).
