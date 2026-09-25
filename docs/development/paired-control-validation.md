# Paired control hardware review

Status: Android phone is available. On 2026-09-25, integration images were uploaded to the USB gauge with flash hashes verified, and the debug APK was installed on a Pixel 10 Pro. The Pixel discovered the gauge and read protocol 0 capabilities with `quickSelect: true`, `configWrite: false`, and `ota: false`. This is public discovery evidence only. Pairing, owner authorization, control write, and applied-state readback have not passed.

## Live pairing blocker, 2026-09-25

The user confirmed PAIR READY on the gauge during several attempts. The gauge serial monitor logged the physical owner window opening and the phone link connecting. It did not show a passkey, and the user confirmed no six-digit code appeared. Android's Bluetooth manager reported `SMP_RSP_TIMEOUT` about 30 seconds after initiating bonding, with no bond stored. The app did not confirm a selection. Both explicit `createBond()` before GATT connection and a GATT-first protected state read reached this failure. Temporarily pausing OBD adapter scans during the pairing window did not change it and was removed. A guarded handler for NimBLE's repeat-pairing event was added because that stack otherwise silently ignores a new request for an already bonded peer, but no repeat-pairing event was observed in these attempts; it is not evidence of the root cause.

Next capture NimBLE SMP or controller trace around the pairing request, verify whether the gauge receives that SMP PDU, and determine why it sends no pairing response. Preserve authenticated, physical-window-only pairing while diagnosing. Do not mark quick selection as validated from the public capability read or from a bond attempt alone.

## Preparation

1. Keep the gauge powered by USB and open the latest Android debug APK.
2. In Device, read gauge capabilities. Require protocol major 0 and `quickSelect: true` before showing the paired action. If multiple gauges are nearby, identify the one chosen by the current first-match discovery behavior before continuing. The control action uses that same discovered Bluetooth device for the current app session.
3. In Design, select a built-in example such as Speed. TCM's synthetic input-speed example is intentionally unsupported by quick select.
4. Keep any vehicle adapter disconnected for the first review. Pairing should not require vehicle data.

## Owner and control cases

| Case | Action | Expected evidence |
| --- | --- | --- |
| First owner | Long press gauge, then tap Set preview reading on gauge | Round LCD shows a six-digit code; Android system pairing prompt accepts it; app reports success only after authenticated state readback; gauge shows the chosen built-in label |
| Window expiry | Long press gauge without pairing and wait more than 120 seconds | PAIR READY clears from the LCD and a new owner cannot bond until another physical long press |
| Without physical window | Forget Android bond and try a fresh pairing without a gauge long press | New pairing fails and no selected reading changes |
| Reconnect | Close app, reopen, choose another built-in reading | Previously bonded phone reconnects without a new passkey; applied label and app confirmation agree |
| Rejected input | Attempt an unsupported index or malformed control payload using an authenticated development client | GATT rejects it, saved selection is unchanged |
| Lost phone | Hold gauge touch for 12 seconds, forget eGauge in Android Bluetooth settings, pair again | Owner bond is removed, gauge restarts, and a new physical pairing window allows association |
| Stale bond after reset | Reconnect an old bonded phone before entering a fresh passkey | It cannot regain owner status or change a reading; a fresh physical passkey exchange is required |
| Reboot persistence | Power-cycle gauge after a confirmed selection | Chosen built-in reading survives; volatile session revision may restart |

Record Android version, app commit, firmware commit, observed passkey layout, GATT errors, selection before/after, and whether any pairing prompt uses a weaker method. A successful build or flash hash does not satisfy these cases. Do not interpret a public capability read as owner authentication.

## Next configuration increment

Once the paired path passes, add `config.get` and versioned staged configuration writes with base revision, complete hash, schema validation, semantic checks, atomic generation activation, and post-commit readback. The current two-byte quick-selection command is intentionally outside the full configuration document. Its acceptance does not authorize alert thresholds, custom PIDs, DTC clearing, or OTA.
