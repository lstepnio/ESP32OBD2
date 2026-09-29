# Authenticated Wi-Fi bulk validation

Observed on 2026-09-26 with the USB-connected ESP32-S3-Touch-LCD-1.28 and the owner-bonded Pixel 10 Pro. The phone ran the debug Android app over Wi-Fi ADB. No OBD adapter or vehicle was present.

## Successful signed OTA

The gauge advertised `wifiBulk: experimental-softap-aead-v1`, and the app rendered `Automatic private Wi-Fi`. The owner operation created a temporary WPA2 SoftAP at `192.168.4.1:7331`. Serial output showed the Pixel join, receive `192.168.4.2` from DHCP, and remain connected for the transfer. The companion BLE discovery link had disconnected before image data moved.

The app transferred the signed `0.2.0-dev.4` image and the gauge selected `ota_1`. Serial boot evidence showed:

- application partition `0x360000`
- version `0.2.0-dev.4`
- ELF SHA-256 `04fe292a82c75bcb43ee6003d24b0bee664878b58b04ca9fa746c280ef4d543f`
- saved configuration revision 2 with three PIDs, three pages and one alert retained
- trial image confirmed after UI, BLE and application progress

Android then read the same version, partition and hash over the owner BLE link and reported `Confirmed valid`.

## Throughput correction and second OTA

The first run used a 100 ms worker-status polling interval after every 1 KiB OTA chunk. It took about 319 seconds from access point ready to restart. The running firmware was updated to poll once per 10 ms scheduler tick, with the same 60 second command timeout, authenticated frames, 1 KiB offset checks, digest, signature and activation checks.

A second signed Wi-Fi OTA moved `0.2.0-dev.6` from `ota_0` to `ota_1`. Serial timing was:

| Event | Gauge uptime |
| --- | ---: |
| Access point ready | 48.010 s |
| Pixel joined | 57.530 s |
| Pixel left after transfer | 185.160 s |
| Restart initiated | 189.910 s |

The optimized access-point-ready-to-restart interval was about 142 seconds, a 56 percent reduction. The gauge booted `0.2.0-dev.6` from `0x360000`, reported ELF SHA-256 `79e442d59fc163582109c4807dfd89846535d53688f506265bea3cef5a357db1`, and confirmed the trial after UI, BLE and application health progress. Android independently reported 100 percent, the new partition, and the matching hash.

The Pixel sometimes rejected the first `WifiNetworkSpecifier` request after associating without completing the WPA handshake. Retrying from the app succeeded without entering credentials. The client now unregisters the failed callback and makes one bounded retry with a fresh network request, so this recovery no longer requires another install tap.

## Bounded batch qualification

Protocol v2 groups up to eight unchanged 1 KiB OTA chunk commands into one authenticated Wi-Fi frame. Firmware validates the complete batch before its first write and then processes every chunk through the existing OTA worker, including its transfer ID, command sequence, exact accepted offset, status result, digest, signature, activation and rollback checks. Two 8,448-byte frame buffers are allocated from PSRAM rather than the constrained internal heap.

The v2 bootstrap image `0.2.0-dev.7` was written to `ota_0` over USB while retaining NVS and both configuration slots. The updated Android app then transferred the signed `0.2.0-dev.8` image over the automatic private network. This run did not require a Wi-Fi password or a new Android consent interaction.

| Event | Gauge uptime |
| --- | ---: |
| Access point ready | 284.828 s |
| Pixel joined | 294.658 s |
| Image verification began | 333.858 s |
| Pixel left after transfer | 334.658 s |
| Restart initiated | 339.388 s |

Access-point-ready-to-restart was 54.6 seconds, 61.6 percent faster than the prior 142-second result and 82.9 percent faster than the original 319-second result. The station join-to-leave transfer interval was 40.0 seconds. The Android operation took about 65 seconds from tapping Install through confirmed health, including BLE setup, Wi-Fi association, image verification, reboot, and post-boot BLE confirmation.

The gauge booted `0.2.0-dev.8` from `ota_1` at `0x360000`, reported ELF SHA-256 `779cb7cb99521ab9602e2c7fe728fb8549e3ef82986b4a23008768e313451fe7`, retained configuration revision 2 with three PIDs, three pages and one alert, and confirmed the trial after UI, BLE and application progress. Android independently reported 100 percent, the same partition, and the matching hash. During the transfer the observed minimum free internal heap was 13,303 bytes and minimum free PSRAM was 2,060,808 bytes.

## GitHub-hosted path

The owner-bonded Pixel discovered and verified prerelease `dev-v0.2.0-dev.9` directly from GitHub Releases, then installed its signed bundle over the same private Wi-Fi transport. Serial output showed the phone join the temporary network, the OTA image verify, and the gauge restart into `ota_0` at `0x60000`. The running image reported version `0.2.0-dev.9`, ELF SHA-256 `1948f04c9088382b6d61aab42b13fbf435b30cf7669da24b142a54c707623d97`, retained configuration revision 2, and confirmed the trial after application progress. Android independently reported 100 percent and the matching partition and hash. A repeat GitHub check reported the installed version up to date and did not offer it again.

## Boundaries still open

This evidence validates the successful encrypted transport, bounded batch path and signed activation path. It does not validate a malformed authenticated batch on hardware, wrong application key, replayed frame, expired session, power loss during transfer, automatic resumption, or concurrent vehicle traffic. Those cases retain the experimental capability label. Live OBD polling and dual-adapter coexistence were not exercised.

## Android startup optimization pending physical timing

The Android update path now reads the running firmware identity and opens the temporary Wi-Fi network in one owner-authenticated BLE connection. Previously it closed the first GATT connection and repeated the MTU exchange, service discovery, and owner check before opening Wi-Fi. Android now reports transfer progress only after the private network and TCP socket are ready. The signed image verification, owner association, private-network credentials, and post-restart running-image confirmation are unchanged.

This is source-level and build validation only. The revised app has not yet performed a physical OTA, so startup time saved is unmeasured. On the next authorized hardware update, separately record Install tap, package download completion if applicable, private AP ready, Pixel association, first OTA byte, and confirmed running image. Compare tap-to-first-byte with the prior app on the same gauge and Pixel before claiming a measured speedup.
