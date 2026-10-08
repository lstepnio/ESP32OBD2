# Offline adapter integration

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

## Implemented

Android can find adapters through the owned gauge, save a validated binding per vehicle profile, and send the binding together with pages and alerts in one configuration transaction. Schema 1 retains legacy discovery. Schema 2 supports an explicit primary source binding and treats an unbound source as unconfigured. Stored phone profiles migrate without inventing bindings.

Firmware exposes bounded adapter discovery and source status through private `cfg3` / `ad1` extensions. Public capability flags remain disabled. Explicit ECU attribution accepts captured CAN single-frame replies and rejects malformed, ambiguous, truncated, and unsupported multi-frame responses. Engine diagnostic reads are attributed to ECU 7E8. Missing support-map evidence remains unknown.

The trace build accepts a separate bench driver and marks the display SIMULATED. Production builds reject that driver. Sanitized engine and transmission fixtures exercise the production parser; they do not prove live vehicle behavior. Simultaneous adapter qualification and enhanced transmission definitions remain follow-up work.

## Notification failure diagnosis

The bench simulator reproduced the Session A symptom: ATT subscription and command writes succeeded, but no adapter reply reached the gauge application. The installed ESP-IDF 5.4.1 NimBLE `ble_att_svr_rx_notify` explicitly discards unencrypted notifications when the global `BLE_SM_LVL` is at least 2. The project had configured level 4, affecting the unencrypted Vgate connection as well as the phone.

After loading the corrected image through Android/Wi-Fi, the same Mac peripheral completed initialization and received repeated Mode 01 polling commands for RPM, coolant, and speed. This is physical gauge BLE transport against a simulated adapter, not live Jeep verification.

The global level is now 1 so adapter notifications reach the client. The companion control and state attributes still require authentication, and the application authorization gate still requires encryption, authentication, a bond, and the saved owner's identity. Pairing MITM and Secure Connections settings remain enforced for the phone. Contract validation guards these protections and the adapter-compatible setting.

ATT command writes wait for completion, and disconnected operations are retired using a generation token so an old callback cannot block or complete a new connection's command. Wi-Fi maintenance pauses background adapter discovery and adapter links. Android observes standard Service Changed events, confirms the expected healthy boot identity, and reconciles uncertain updates after reconnecting.

## Verification boundaries

- Host tests cover captured response parsing, malformed inputs, profile migration, binding validation, production rejection of the bench driver, stale alert invalidation, and early old-image observations during OTA reboot.
- Pixel observations are recorded separately from firmware USB traces and physical display observations.
- The Vgate must still be retested after this change. Finding a specific failure mechanism and reproducing it on the bench does not qualify the real adapter or prove a live reading on the physical LCD.

## Next vehicle session

Use the Jeep profile, bind the Vgate to the engine source, and compare a responding reading with an independent instrument. Confirm binding and page persistence across restart; unplug and reconnect the adapter to verify unavailable state and recovery. Transmission tests remain separate and must not label engine ECU data as transmission data. A second adapter is required for simultaneous load qualification.

## Local bench observations

- Pixel: per-profile adapter selection, source check showing a connected bench simulator, and explicit example-reading wording were observed in the installed app. The update recovery journal was empty after reconnection in the final app build.
- Gauge USB trace: `0.2.0-dev.34lab` booted with the saved bench configuration; initialization completed; captured RPM payload `0BE0`, coolant `53`, and speed `00` were accepted by the production parser. After stopping the simulator, a disconnect was recorded; restarting it produced another `link_ready` with a new connection generation and resumed accepted replies.
- Physical LCD: confirmation was requested separately. USB acceptance does not prove the visible value or its SIMULATED label.
- Installed lab image SHA-256: `5dded1629d38cb720f90554f09af65637f155c37e23f74f5a1ab8e6a6c5fe4cc` (1,504,736 bytes). Loaded through Android/Wi-Fi. Production build excludes the bench driver.
- Wi-Fi timing from the bench update: owner preflight 161 ms, gauge startup 936 ms total, Android association 8,933 ms, socket connect 14 ms, first response 9,908 ms total, flash preparation 2,273 ms. Android association is the dominant measured preparation delay in this run; comparison under controlled conditions is still required before claiming an improvement.
- Verification: production and lab firmware builds, Android assemble/unit tests/lint, 14 capture/bench Python tests, sanitizer parser/configuration/core tests, and contract/document checks pass.

The final bench USB recording replayed 432 completed transactions, with 420 accepted by the production parser. The recording is classified as simulated because its trace contains `source_simulated`. The Jeep profile was restored and its unbound schema 2 setup was confirmed healthy as revision 23. Find and select the Vgate in the next vehicle session before sending that binding.
