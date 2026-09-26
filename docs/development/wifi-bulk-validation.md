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

The Pixel sometimes rejected the first `WifiNetworkSpecifier` request after associating without completing the WPA handshake. Retrying from the app succeeded without entering credentials. This Android consent and association behavior needs a clearer retry state before release.

## Boundaries still open

This evidence validates the successful encrypted transport and signed activation path. It does not validate a wrong application key, replayed frame, expired session, power loss during transfer, automatic resumption, or concurrent vehicle traffic. Those cases retain the experimental capability label. Live OBD polling and dual-adapter coexistence were not exercised.
