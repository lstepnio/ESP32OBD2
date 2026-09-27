# Firmware core hardening validation

Date: 2026-09-26. Branch: `feat/owned-gauge-control`.

This record separates implementation evidence from live hardware evidence.

## Completed checks

| Check | Result |
| --- | --- |
| Clean ESP-IDF 5.4.1 firmware build with application `-Wall -Wextra -Wshadow -Werror` | Passed. Image version `0.2.0-dev.3`, size `0x103370`, with 66 percent of the smallest app partition free. Binary SHA-256: `10a28a5c5d600d6cfc510e27e5c7f33702bfe152f424b600a75d3b474e8a78b0`. |
| Core pure-C fixture with AddressSanitizer and UndefinedBehaviorSanitizer | Passed. Covers JSON depth, delimiter pairing and escaped text; signed decoding; immediate/delayed alert transitions, hysteresis, stale state, priority, time wrap; scheduler fairness, background budget, limits, and time wrap; configuration trial, confirmation, rejection, stale journal, and recovery decisions. |
| ELM response fixture with AddressSanitizer and UndefinedBehaviorSanitizer | Passed. |
| Python schema/contracts/document links/color parity validator | Passed: 3 schemas, 6 examples, 15 rejection cases, signed decode vector, 36 document link sets, and color token parity. |
| Android debug APK and unit tests | Passed with the local Android SDK. The owner client accepts protected runtime identity version 8 and can request opcode `0x33`. |
| Source whitespace validation | `git diff --check` passed. |

## Gauge and Pixel regression

The final application image was written only to the active `ota_0` partition at `0x60000`; esptool verified the written hash. NVS, config slots, OTA metadata, bootloader, partition table, and `ota_1` were not written. The serial boot log confirmed:

- version `0.2.0-dev.3`, ESP-IDF 5.4.1, 16 MB flash, and 2 MB PSRAM memory test success;
- boot from `ota_0` at `0x60000`;
- preserved and compiled configuration revision 2 with three PIDs, three pages, and one alert;
- display, touch, LVGL, BLE controller, and OBD worker startup without reset or abort;
- at 121.8 seconds, `app_tick` stack watermark 1,000 bytes, minimum internal heap 138,827 bytes, minimum PSRAM heap 2,070,804 bytes, 16 free alert queue entries, and zero dropped alert samples.

The rebuilt debug APK was installed on the bonded Pixel 10 Pro. It discovered the hardened gauge and completed an owner-authenticated protected status read showing active revision 2, SHA-256 prefix `6689a8922875`, idle transfer phase, and result 0. The final APK SHA-256 is `b79443cd0e98c068f91d254ad0d0b712002d184089813fac3d5bef4a881ae79a`. The final client compiles and requests runtime identity version 8 as part of the active-status action, but that added presentation step was not observed after the phone locked.

## Implemented fault boundaries

- Established ECM loss now clears stale presentation/diagnostics and re-enters bounded connection setup.
- GATT adapter readiness requires characteristic properties, discovered CCCD, confirmed subscription, and bounded ELM initialization.
- Config and OTA status reads use immutable snapshots and fixed activation deadlines. Status polling cannot defer restart.
- Post-commit config abort and post-activation OTA abort are rejected. The maintenance gate remains held until restart.
- Touch and phone selection changes enter one application command path. UI values carry PID identity.
- Alert freshness and companion expiry run from an independent 100 ms task.
- Runtime parsing rejects JSON deeper than 16 containers before cJSON, rejects mismatched containers, and rejects reserved PID 01.
- Boot can fall back to the previous committed executable config and reports stored versus running identity separately.
- Configuration commits journal a persistent trial before the slot marker. The first boot marks it attempted; health progress confirms it; reset-before-confirmation selects the previous committed generation on the next boot.
- OTA trial confirmation requires sustained application progress in addition to successful subsystem startup.
- Owner NVS persistence runs outside the NimBLE GAP callback and fails closed.

## Evidence still requiring hardware

The following are validation gates, not unfinished software claims:

- Confirm touch, all rotations, circular geometry, runtime-identity presentation, config trial rejection under physical reset, config activation, and OTA regression on the flashed build. Startup, owner reconnect, and protected config status are observed.
- Measure target stack watermarks, heap minima, callback latency, render latency, flash latency, and queue pressure under transfers.
- Run controlled USB power interruptions during config writes and OTA phases.
- With actual adapters, capture GATT maps and initialization replies, then test single-adapter disconnect/reconnect and request timing.
- With both adapters, test three simultaneous BLE links, per-source isolation, throughput, memory pressure, and the TCM scheduling design.
- DTC clearing remains intentionally absent until explicit user confirmation, adapter/vehicle preconditions, and ambiguous completion behavior are implemented and tested.

Public `configWrite`, `ota`, simultaneous-adapter, live PID, and DTC-clear capabilities remain disabled or unclaimed until their applicable evidence is recorded.
