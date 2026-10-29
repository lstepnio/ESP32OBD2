# Dev.72 combined indoor rollout

> Historical evidence. This dated rollout records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-28. This is device evidence, not loaded Jeep qualification.

## Source and signed release

PR #114 merged as `705b784`; PR #113 merged as `9ae16b1` after all four quality
checks passed on source `e64e8bc` (run `40741156076`). The two initial UI attempts
failed different modal-editor interactions. Final source retains actual taps and
exact saved-value assertions with keyboard/destination synchronization; the focused
14 action/idle and four Alert tests passed, followed by the full CI UI lane.

Signed [dev.72](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.72)
consumes generation 53. Release run `40742950892` built the exact green source;
merged/source trees match. Catalog/image signatures, expiry, board/layout/protocol,
image descriptor and tag commit were verified before installation. Image length is
1,679,632 bytes; ELF is
`74a5062dad4d3f22d706e3fadc05a795737d4f55e9230b119e702c98e430e955`.
Parked-idle and owner-supervised orientation candidate options are retained;
ordinary qualification/public flags and ABS/TCS execution remain off.

## Hardware checks

The installed App remains dev.53. Only the matched instrumentation APK was updated.
The ordinary hosted signed App/Wi-Fi journey passed (106.702 s test, 73.819 s install
journey), confirming the exact healthy alternate image and cleared update journal.
Five maintenance cycles passed in 63.761 s: protected opens 1,589/1,610/1,633/1,609/
1,766 ms, authenticated Wi-Fi status/close, distinct sessions and unchanged boot nonce.
Automatic connection/resume/Bluetooth/picker/hardware-chain recovery passed in
69.455 s; Wi-Fi mode 255 and cleared initialized-feature bit prove driver deinit.

Protected before/after diagnostic-cache reads passed in 22.374/15.293 s. Revision 65,
exact running JSON/digest, five pages, ECM/child-TCM, four filters, actions, alerts and
display settings were retained. Preferences remain manual 270 degrees, brightness
100, Imperial, ten-second cycling and Smart On. Phone profiles, associations,
presentation and permission preferences compare equal. Phone alerts are restored
On; debug wake restoration is inactive with the original 60,000 ms / charging 15.

The durable idle operation remains Unknown/operation 1 while both adapters are
absent; its replay fence is restored to 1 rather than reset. No ECU control, clearing,
filter edit, configuration rewrite or bond reset was performed. All current endpoint
categories were NotChecked/disconnected, so this read cannot attribute the earlier
wrench photo or prove faults cleared. The four actual rules remain TCM-scoped U0121,
U0140, P1DCA and P1DF3.

The owner confirmed clean display, tap wake and normal/action navigation on dev.72,
and observed Check idle / Status unknown while disconnected. That is preserved
uncertainty, not a claim of currently raised idle or a failed installation.

## Resource window and remaining gates

After exact protected confirmation, 28 lifecycle samples across maintenance/recovery
showed minimum sampled internal free 91,959 bytes, minimum-ever counter 90,375,
largest internal block 31,744 and PSRAM free 1,935,328. Observed worker stack minima:
App 1,448; UI 3,548; primary/child adapter 760/752; config 4,264; OTA 3,028;
Wi-Fi server/command 6,032/2,584; sensor 1,680; main 1,016 bytes. Allocation failures,
queue drops and fatal markers were zero in this window. This is not OOM immunity or
loaded adapter/control-stack qualification. Private backups, logs and identities
remain ignored under the task's device-rollout artifacts.

The [parked procedure](../development/jeep-action-qualification.md) still needs actual
inactive-routine acceptance after restart, fresh settled-RPM reconciliation, control
Stop/thermal/fallback timing, and live filtered/unfiltered CEL startup behavior.
Four-position Auto orientation and explicit showcase hold-exit wire proof retain
their separate physical gates. Source/release branches are removed; immutable assets
and private backups are retained. Website review: no new marketing claim or visual
asset is needed for these corrective changes.
