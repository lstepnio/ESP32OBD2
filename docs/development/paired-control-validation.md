# Paired control hardware review

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

Status: On 2026-09-25, the integration image was uploaded to the USB gauge with flash hashes verified, and the debug APK was installed on a Pixel 10 Pro. The Pixel discovered the gauge, read protocol 0 capabilities, completed LE Secure Connections passkey bonding, and confirmed authenticated built-in selections by state readback, including after a gauge reboot. It also saved and read back a 90-degree display rotation through the owner link; the user confirmed the display and touch looked correct. No OBD adapter was present.

## Pairing diagnosis and observed fix, 2026-09-25

Initial attempts showed PAIR READY and a phone link, but no six-digit code. Android reported `SMP_RSP_TIMEOUT` after about 30 seconds. Inspection of the bundled ESP-IDF 5.4.1 NimBLE `ble_sm_pair_req_rx()` found that the `ble_hs_cfg.sm_sc_only` branch validates a valid Secure Connections request without filling or transmitting a pairing response. This is consistent with the timeout. The firmware now leaves that runtime flag off, disables legacy pairing at build time, sets security level 4, and retains bonding, MITM, Secure Connections, and display-only passkey settings. The stack rejects peers lacking Secure Connections when legacy pairing is compiled out.

After flashing this change, the gauge displayed a six-digit code and Android showed its system PIN entry dialog. The user entered the displayed code. Android's Bluetooth manager reported `BOND_BONDED`, `pairingAlgorithm:SC`, and `pairingVariant:PASSKEY_ENTRY`. The app confirmed built-in reading 5 of 5 (fuel), and the firmware logged the saved fuel selection. A later phone GATT connection, without another physical pairing window or passkey, confirmed built-in reading 3 of 5 (engine load); firmware logged PID `0x04` saved. The app reports success only after its protected state readback.

The first reboot check exposed a separate persistence defect: NimBLE's `CONFIG_BT_NIMBLE_NVS_PERSIST` was disabled, so the gauge lost its bond keys while its owner association remained in NVS. Android then requested pairing again. We enabled that option, flashed the new image, used the gauge's 12-second owner reset, and removed the stale eGauge bond in Android settings. After a fresh passkey bond, a gauge reboot and another authenticated selection succeeded without a new pairing prompt. This verifies bond and owner persistence on the tested hardware. The guarded stale-bond recovery handler and other security cases below remain untested.

The protected version 2 state read subsequently reported saved Coolant at durable legacy revision 8. After a gauge reset, the phone read revision 8 again. Later app interactions advanced the saved selection to Engine load at revision 9. The 16 MB USB partition migration then booted from `ota_0` at `0x60000`. The already bonded Pixel completed another protected saved-state read without a new passkey. That read reported RPM at revision 12 after intervening app interactions, so it verifies continued owner access rather than an unchanged selection across the migration. The app keeps the phone draft separate from this saved gauge state. An immediate reconnect after a selection occasionally failed to refresh; the app now clears the stale saved snapshot and offers a separate retry.

After enabling 2 MB PSRAM and adding a revision-checked rotation opcode to protocol 0, the Pixel saved 90 degrees and read it back at legacy revision 26. Following a gauge reset, it read 90 degrees again without pairing. The user reported that the LCD looked correctly rotated and touch selected one reading per tap. The revision was 41 on that later read; the intervening legacy saves have not been attributed. Do not treat that revision jump as evidence of a rotation fault or of correct write frequency without a serial trace during the interval.

After the document validator image was flashed, the Pixel read RPM, 90 degrees, and legacy revision 58 through the existing owner bond. An idle serial observation for 30 seconds showed adapter connection timeouts because no adapter was present, but no configuration saves. A following authenticated phone read still reported RPM, 90 degrees, and revision 58. This rules out continuous autonomous legacy writes during that observed interval; it does not attribute the earlier revision changes.

## Pairing startup update, 2026-09-28

Implemented in source: ownership is restored before the first UI frame. A gauge with no saved owner starts on the pairing view and opens a 120-second association window during BLE initialization. Expiry keeps the exclusive pairing view visible with **HOLD TO PAIR**; long press reopens the window. A saved owner starts on the selected gauge page, including when that phone is disconnected.

All gauge renderers, reading labels, units, alerts, and diagnostic badges share a hidden parent while pairing is visible. Incoming samples, alert changes, diagnostic updates, and page changes cannot reveal those children. Short taps during pairing do not select or persist hidden gauge pages. Successful owner persistence restores the gauge view. Public capability flags and passkey authentication requirements are unchanged.

The 2026-09-25 physical observations above predate this update. The startup and exclusive pairing behavior have not yet been flashed or observed on the physical gauge or phone. The cases below describe the current acceptance expectations.

Software validation: the ESP-IDF 5.4.1 firmware build passed, as did the repository contract and documentation validator and the whitespace check. Source review confirmed that LVGL 9.2.2 suppresses child rendering when a parent is hidden. These checks do not establish physical startup, touch, or phone-pairing behavior.

### Hosted dev.22 attempt and startup correction

The signed `0.2.0-dev.22` release (catalog generation 14) passed independent catalog and image-signature verification. The owner-paired Pixel downloaded it from GitHub and sent the complete image over the gauge's temporary Wi-Fi network. Serial evidence showed the new image booting from `ota_1` at `0x360000`, then a `main` task stack overflow during UI initialization. The trial image automatically rolled back to `0.2.0-dev.21` at `0x60000`. Configuration revision 9, four PIDs, five pages, and one alert were reported before the update attempt and after rollback.

Dev.22 is withdrawn. The startup path called `lv_refr_now()` while still on the main task's stack. The corrected dev.23 source leaves the first draw to the LVGL task after releasing its port lock, retaining the hidden gauge parent before any draw. Physical validation of that correction is pending its GitHub-to-app installation.

## Preparation

1. Keep the gauge powered by USB and open the latest Android debug APK.
2. In Device, read gauge capabilities. Require protocol major 0 and `quickSelect: true` before showing the paired action. If multiple gauges are nearby, identify the one chosen by the current first-match discovery behavior before continuing. The control action uses that same discovered Bluetooth device for the current app session.
3. In Design, select a built-in example such as Speed. TCM's synthetic input-speed example is intentionally unsupported by quick select.
4. Keep any vehicle adapter disconnected for the first review. Pairing should not require vehicle data.

## Owner and control cases

| Case | Action | Expected evidence |
| --- | --- | --- |
| First owner | Boot an unowned gauge, then tap Set preview reading on gauge within 120 seconds | PAIR READY appears without a long press or a gauge-page flash; round LCD then shows only the six-digit pairing prompt; Android accepts the code; saved ownership restores the gauge view |
| Window expiry | Leave an unowned gauge without pairing for more than 120 seconds | HOLD TO PAIR remains on the LCD with every gauge widget hidden; a new owner cannot bond until another physical long press |
| Closed window | Let the startup window expire, then try a fresh pairing without a gauge long press | New pairing fails and no selected reading changes |
| Exclusive pairing | While ready, displaying a passkey, and waiting after expiry, exercise all five renderers and deliver samples, alerts, and diagnostic updates | Only pairing content is visible; no numbers, units, arcs, bars, trends, dual values, alert banners, or diagnostic badges appear |
| Pairing touch | Tap during pairing, then long press after expiry | Short taps do not change or save the hidden page; long press returns to PAIR READY |
| Owned startup | Boot a gauge with a saved owner while the phone is disconnected | Selected gauge page appears without opening a new association window |
| Reconnect | Close app, reopen, choose another built-in reading | Previously bonded phone reconnects without a new passkey; applied label and app confirmation agree |
| Rejected input | Attempt an unsupported index or malformed control payload using an authenticated development client | GATT rejects it, saved selection is unchanged |
| Lost phone | Hold gauge touch for 12 seconds, forget eGauge in Android Bluetooth settings, pair again | Owner bond is removed, gauge restarts on PAIR READY, and its startup pairing window allows association |
| Stale bond after reset | Reconnect an old bonded phone before entering a fresh passkey | It cannot regain owner status or change a reading; a fresh physical passkey exchange is required |
| Reboot persistence | Restart gauge after a confirmed selection, then select again from the same bonded phone | Observed: no new passkey prompt; authenticated selection and readback succeeded. Independent display confirmation of the preexisting reading is still needed |
| Rotation persistence | Select 90 degrees on the phone and restart the gauge | Observed: authenticated readback reported 90 degrees before and after restart, and the user confirmed the rotated display and touch behavior |

Record Android version, app commit, firmware commit, observed passkey layout, GATT errors, selection before/after, and whether any pairing prompt uses a weaker method. A successful build or flash hash does not satisfy these cases. Do not interpret a public capability read as owner authentication.

## Next configuration increment

This 2026-09-25 note predates the restricted numeric document transfer, complete protected readback, and persistent configuration trial added later. Current implementation and remaining gates are tracked in [configuration storage](../architecture/config-storage.md) and [core hardening validation](firmware-core-hardening-validation.md). The two-byte quick-selection command remains outside the full configuration document. Its acceptance does not authorize alert thresholds, custom PIDs, DTC clearing, or OTA.
