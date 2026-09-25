# Paired control hardware review

Status: pending Android phone access. The integration image has been uploaded to the USB gauge and both source targets build. None of the pairing or control cases below has been observed yet.

## Preparation

1. Keep the gauge powered by USB and open the latest Android debug APK.
2. In Device, read gauge capabilities. Require protocol major 0 and `quickSelect: true` before showing the paired action.
3. In Design, select a built-in example such as Speed. TCM's synthetic input-speed example is intentionally unsupported by quick select.
4. Keep any vehicle adapter disconnected for the first review. Pairing should not require vehicle data.

## Owner and control cases

| Case | Action | Expected evidence |
| --- | --- | --- |
| First owner | Long press gauge, then tap Set preview reading on gauge | Round LCD shows a six-digit code; Android system pairing prompt accepts it; app reports success only after authenticated state readback; gauge shows the chosen built-in label |
| Without physical window | Forget Android bond and try a fresh pairing without a gauge long press | New pairing fails and no selected reading changes |
| Reconnect | Close app, reopen, choose another built-in reading | Previously bonded phone reconnects without a new passkey; applied label and app confirmation agree |
| Rejected input | Attempt an unsupported index or malformed control payload using an authenticated development client | GATT rejects it, saved selection is unchanged |
| Lost phone | Hold gauge touch for 12 seconds, forget eGauge in Android Bluetooth settings, pair again | Owner bond is removed, gauge restarts, and a new physical pairing window allows association |
| Stale bond after reset | Reconnect an old bonded phone before entering a fresh passkey | It cannot regain owner status or change a reading; a fresh physical passkey exchange is required |
| Reboot persistence | Power-cycle gauge after a confirmed selection | Chosen built-in reading survives; volatile session revision may restart |

Record Android version, app commit, firmware commit, observed passkey layout, GATT errors, selection before/after, and whether any pairing prompt uses a weaker method. A successful build or flash hash does not satisfy these cases. Do not interpret a public capability read as owner authentication.

## Next configuration increment

Once the paired path passes, add `config.get` and versioned staged configuration writes with base revision, complete hash, schema validation, semantic checks, atomic NVS activation, and post-commit readback. The current two-byte quick-selection command is intentionally outside the full configuration document. Its acceptance does not authorize alert thresholds, custom PIDs, DTC clearing, or OTA.
