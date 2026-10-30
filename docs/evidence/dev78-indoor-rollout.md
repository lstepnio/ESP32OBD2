# Dev.78 action and dual-link recovery delivery

> Historical evidence. [Current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-29. The owner approved signed dev.78 publication, App dev.55 and
App/Wi-Fi installation, and indoor checks without vehicle controls or clearing
recovery records. PR [#126](https://github.com/lstepnio/ESP32OBD2/pull/126) remains
unmerged. No main merge was requested for this delivery.

## Source and artifacts

Source `84083079e2e16ef50671404937abfc067c62be4b` passed all four lanes in
[quality run 40961455110](https://github.com/lstepnio/ESP32OBD2/actions/runs/40961455110).
Downloaded UI results include 28 action/command cases with zero failures or skips.
Local checks additionally include 185 host and 14 review-server tests, 341 JVM
cases, firmware normal/fallback/parked rendering and configuration fixtures,
Android lint, format and combined owner-candidate firmware/APK builds.

Signed [dev.78](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.78)
was published by [run 40989748872](https://github.com/lstepnio/ESP32OBD2/actions/runs/40989748872),
catalog generation 59, from the same source. Catalog freshness/compatibility and
signature, strict image bundle/signature, descriptor, build-evidence signature and
inventory, and exact build/application parity passed. Parked-idle, owner-brake and
orientation candidates remain selected; public qualification is unchanged.

- Image length: 1,700,272 bytes.
- Image SHA-256: `90eee1344c8a3b4da3c7e71fade4f55e8972a79d90eaaa961898d9b5ddcc08d1`.
- ELF: `c5fcb9fb56ab09b5240dd3fda9ed39378be273c94dd978a6fee453b4472928c3`.

App dev.55/code55 was replacement-installed after a private backup and matching
its certificate to the installed App. APK SHA-256:
`fcd018b263a7bf6b8279cd5dde19d76c6f285a0b75420b9ce85947dbd702355a`.
No App-data clearing or bond erasure was used.

## Device checks and preservation

Fresh pre-install readback found dev.77, configuration revision 66 and ABS/TCS
Unknown operation 1. Idle and Burnout were inactive with operation 0. This fresh
baseline supersedes older incident snapshots; no operation ID was assumed.
App replacement preserved the running configuration, display settings and command
records before firmware transfer.

The ordinary signed App/Wi-Fi installation completed in 74.512 seconds. Sending
was observed at 17.847 seconds, complete image verification at 58.679 seconds and
healthy running identity at 74.495 seconds. Exact post-boot ELF, alternate OTA slot,
health 2 and cleared update journal passed. Two further maintenance opens/closes
passed (1,589 and 1,627 ms to protected open), with authenticated reads and unchanged
boot identity. Automatic foreground connection/resume, hardware read chain and
protected cached diagnostic readback passed.

Raw running configuration remains revision 66 with SHA-256
`807623e49e2995cb50bad065979ed974b627503f4314bdff3b16db971697b153`; display
settings and all command operations/phases were unchanged. ABS/TCS remains Unknown
operation 1, restart required; Idle and Burnout remain inactive. No Start, Stop,
Restore, owner confirmation, ECU clearing or configuration write was sent.
The raw compatible configuration can still contain the old Burnout page; firmware
omits that normal menu entry without rewriting the configuration or erasing its
uncertainty. A later permitted App save can remove the raw entry.

Actual Pixel capture confirms only Air up, Winch and Disable ABS + TCS in normal
action pages, with no Burnout/Line Lock. Private before/after backup comparison
allows only that typed action-menu removal in the gauge association. Vehicle
profiles, all other association fields, display/connection/notification preferences,
90 alert events, 67 episodes and 18 configuration contexts were preserved. Both
SQLite copies passed integrity checks. Phone notifications and original Android
sleep/charging-wake policy were restored.

## Remaining qualification

The owner separately confirmed clean normal pages, working swipes/three-up entry
and navigation through only Air up, Winch and ABS/TCS, with no Burnout page. This
physical panel/navigation check is distinct from source rendering and phone
observation. No loaded adapter or vehicle-control check was performed indoors. The captured ABS Return
followed by ignition reset, durable receipt/recovery and actual braking/warnings
still require the [parked procedure](../development/jeep-action-qualification.md#brake-actions-follow-up).
Automatic swapped-port correction, inverse-map reconnect/reboot and loaded sibling
continuity require the [dual recovery matrix](../development/dual-adapter-recovery.md#swapped-port-check).
Do not interpret CI, request acknowledgement or owner confirmation as decoded
ABS-controller restoration proof. Burnout remains paused.

The Pixel's indoor ABS settings row still says “Not supported yet” when its ECM
context is unavailable; the candidate is enabled on the matched Jeep. That copy
needs the existing unavailable/reconnecting pattern, independently of control
admission. Website impact: the dynamic firmware catalog can select dev.78; no new
public brake or loaded-recovery qualification claim is justified.
