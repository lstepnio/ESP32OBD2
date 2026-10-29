# Dev.76 owner brake candidate delivery

> Historical delivery evidence. [Current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-29. The owner explicitly requested enabled, best-effort ABS/TCS
and Burnout actions and App/firmware delivery. This is indoor device evidence;
actual brake behavior remains parked Jeep qualification.

## Exact source and artifacts

PR [#126](https://github.com/lstepnio/ESP32OBD2/pull/126) is an unmerged candidate.
Source `4abfc6069dc1400ba0c41775243b073028acb797` passed all four quality lanes
([run 40924670915](https://github.com/lstepnio/ESP32OBD2/actions/runs/40924670915)).
Local checks included 340 debug / 314 release JVM tests, 182 host and 14 review-server
tests, ASan/UBSan worker/identity replays, real-LVGL normal/fallback/parked checks,
reference firmware/memory checks and focused managed Android UI tests, including
compact/200% text and adding an unavailable supervised action while indoors.

App dev.54/code54 was replacement-installed on the Pixel. Its pulled APK matched
SHA-256 `9a9fecef3f501e142a141da91718dde91670225a8755957a7dd3f8955049435f`; the existing signing identity and App data were retained.

Signed [dev.76](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.76)
was published by [run 40926785961](https://github.com/lstepnio/ESP32OBD2/actions/runs/40926785961),
catalog generation 57, from the same source. All five assets were downloaded and
verified: catalog freshness/compatibility/signature, strict bundle inventory/image
signature, descriptor, build-evidence signature/inventory and application parity.
The signed sdkconfig includes parked-idle, owner brake and orientation candidate
switches; public qualification is unchanged.

- Image length: 1,694,400 bytes.
- Image SHA-256: `9ed308ed739f3e07c6e5d31c6a00e386c26b789ac31609389c8acc03b8bfe6f8`.
- ELF: `e58e8e316f6bd9d2834fb8ee0f0a2a318611a4f1975b6c7a82a5da10e40438ae`.

## Installed and preserved

The ordinary hosted App/Wi-Fi path completed in 74.110 seconds, with sending
observed at 16.035 seconds. Exact post-boot ELF, alternate OTA slot, health 2 and a
cleared update journal passed. Revision 65, its digest, display settings and phone
profiles were unchanged by firmware installation.

Two further maintenance opens/closes passed (1,793 and 1,653 ms to protected open),
with authenticated Wi-Fi reads and unchanged image/settings. Automatic foreground
connection/resume, hardware read chain and both cached diagnostic endpoints passed.
Observed memory snapshots reported zero allocation failures; the post-install
Wi-Fi window reached 89,843 bytes minimum internal free and 31,744-byte largest
block. This short indoor window is not a loaded or long-duration memory guarantee.

Burnout was explicitly added through the App and sent to the gauge. One initial
configuration attempt ended unresolved; the App refreshed gauge truth, and a
subsequent reviewed send succeeded. Revision 66 is confirmed running with no trial.
Only `actions` and `baseRevision` differ from the original configuration: the menu
is Air up, Winch, Disable ABS + TCS, Burnout. The three-up/five-second gesture,
Air up 300 s, Winch 60 s, five reading pages, bindings, alert, four CEL filters and
display settings are unchanged. Protected readback found all three command kinds
inactive with operation 0; both brake statuses carry owner-supervised/local-Start
context. The phone action-page open/edit/back/dismiss/readback check passed without
changing setup or issuing a vehicle command. Private backup comparison preserved
the complete saved profile and previous alert/configuration history; one revision-66
configuration context was appended and the synchronization cursor adopted the new
boot. Original phone notification policy and Android wake settings were restored.

## Remaining evidence

The [owner candidate contract](../architecture/vehicle-actions.md#explicit-owner-supervised-brake-candidate)
owns admission, physical Start, hold/release and manual-confirmation policy. Both
brake features are enabled for the matched Jeep; ordinary qualification masks are
not substituted with invented controller proofs. Follow the [parked checks](../development/jeep-action-qualification.md#brake-actions-follow-up)
for actual effects, release/reset timing, link loss, controller expiry and touch
behavior. No new physical-gauge observation or brake actuation was supplied in
this indoor run. Public claims should not treat these checks as vehicle qualification.
