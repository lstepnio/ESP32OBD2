# Paired control hardware review

Status: On 2026-09-25, the integration image was uploaded to the USB gauge with flash hashes verified, and the debug APK was installed on a Pixel 10 Pro. The Pixel discovered the gauge, read protocol 0 capabilities, completed LE Secure Connections passkey bonding, and confirmed authenticated built-in selections by state readback, including after a gauge reboot. No OBD adapter was present.

## Pairing diagnosis and observed fix, 2026-09-25

Initial attempts showed PAIR READY and a phone link, but no six-digit code. Android reported `SMP_RSP_TIMEOUT` after about 30 seconds. Inspection of the bundled ESP-IDF 5.4.1 NimBLE `ble_sm_pair_req_rx()` found that the `ble_hs_cfg.sm_sc_only` branch validates a valid Secure Connections request without filling or transmitting a pairing response. This is consistent with the timeout. The firmware now leaves that runtime flag off, disables legacy pairing at build time, sets security level 4, and retains bonding, MITM, Secure Connections, and display-only passkey settings. The stack rejects peers lacking Secure Connections when legacy pairing is compiled out.

After flashing this change, the gauge displayed a six-digit code and Android showed its system PIN entry dialog. The user entered the displayed code. Android's Bluetooth manager reported `BOND_BONDED`, `pairingAlgorithm:SC`, and `pairingVariant:PASSKEY_ENTRY`. The app confirmed built-in reading 5 of 5 (fuel), and the firmware logged the saved fuel selection. A later phone GATT connection, without another physical pairing window or passkey, confirmed built-in reading 3 of 5 (engine load); firmware logged PID `0x04` saved. The app reports success only after its protected state readback.

The first reboot check exposed a separate persistence defect: NimBLE's `CONFIG_BT_NIMBLE_NVS_PERSIST` was disabled, so the gauge lost its bond keys while its owner association remained in NVS. Android then requested pairing again. We enabled that option, flashed the new image, used the gauge's 12-second owner reset, and removed the stale eGauge bond in Android settings. After a fresh passkey bond, a gauge reboot and another authenticated selection succeeded without a new pairing prompt. This verifies bond and owner persistence on the tested hardware. The guarded stale-bond recovery handler and other security cases below remain untested.

The protected version 2 state read subsequently reported saved Coolant at durable legacy revision 8. After a gauge reset, the phone read revision 8 again. Later app interactions advanced the saved selection to Engine load at revision 9. The 16 MB USB partition migration then booted from `ota_0` at `0x60000`. The already bonded Pixel completed another protected saved-state read without a new passkey. That read reported RPM at revision 12 after intervening app interactions, so it verifies continued owner access rather than an unchanged selection across the migration. The app keeps the phone draft separate from this saved gauge state. An immediate reconnect after a selection occasionally failed to refresh; the app now clears the stale saved snapshot and offers a separate retry.

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
| Reboot persistence | Restart gauge after a confirmed selection, then select again from the same bonded phone | Observed: no new passkey prompt; authenticated selection and readback succeeded. Independent display confirmation of the preexisting reading is still needed |

Record Android version, app commit, firmware commit, observed passkey layout, GATT errors, selection before/after, and whether any pairing prompt uses a weaker method. A successful build or flash hash does not satisfy these cases. Do not interpret a public capability read as owner authentication.

## Next configuration increment

The partition layout and low-level two-slot storage module now exist, but the semantic validator, BLE transfer, app Apply flow, trial activation, and readback are still pending. Add a full-document `config.get` and versioned staged configuration writes with base revision, complete hash, schema validation, semantic checks, atomic generation activation, and post-commit readback. The current two-byte quick-selection command is intentionally outside the full configuration document. Its acceptance does not authorize alert thresholds, custom PIDs, DTC clearing, or OTA.
