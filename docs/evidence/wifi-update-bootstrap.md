# Wi-Fi update bootstrap reconciliation

> Historical evidence. Reconciliation source and test record for 2026-10-28.
> [Current state](../current-state.md) owns installed facts; [backlog](../backlog.md)
> owns remaining qualification. Historical captures below are not new physical results.

Current source uses one owner-authenticated BLE session for protected running-image
identity and AP startup, with a shared 90-second operation budget. An already-running
image or failed identity check never starts Wi-Fi. An uncertain/cancelled AP start
closes the GATT session before bounded AP cleanup and preserves the original error;
it is never automatically replayed. Network loss/cancellation still belongs to the
existing private transport and operation lease owners. Current CONNECTING_WIFI and
first-response/flash preparation stages supersede the older standalone prepare call;
image progress starts only after those transport steps. Post-boot identity, signatures,
journal/settings invalidation and exact healthy-image confirmation stay unchanged.

`WifiUpdateBootstrapTest` exercises production orchestration with fake protected
sessions: exact ordering, already-running rejection, failed identity, uncertain startup,
cleanup failure and cancellation before/after AP request. These tests do not exercise
real Android GATT or prove a measured startup speedup. Current phone/AP cancellation,
reconnect and timing qualification remain separate physical gates.

The following original PR #41 observations and images are retained as historical
2026-09-29 evidence, not current installed state or a new qualification result. The
older protocol/release details describe that run only. The source branch remains
available for original context.

## Android startup optimization physical check

The Android update path now reads the running firmware identity and opens the temporary Wi-Fi network in one owner-authenticated BLE connection. Previously it closed the first GATT connection and repeated the MTU exchange, service discovery, and owner check before opening Wi-Fi. Android now reports transfer progress only after the private network and TCP socket are ready. The signed image verification, owner association, private-network credentials, and post-restart running-image confirmation are unchanged.

On 2026-09-29, the owner-bonded Pixel 10 Pro used the optimized debug app and USB debugging to discover signed GitHub prerelease [`dev-v0.2.0-dev.24`](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.24). The new firmware differs from the known-good dev.23 source only in `firmware/gauge/version.txt`. The GitHub workflow built and signed it. A clean download independently passed catalog and image signature verification, catalog generation 16, bundle size and SHA-256 checks, and the embedded dev.24 version check before installation.

A Pixel screen recording at one-frame-per-second resolution showed these approximate UI timings from the Install tap:

| Event | Elapsed |
| --- | ---: |
| Install tapped, GitHub download started | 0 s |
| App displayed Sending to gauge, after the private network and TCP socket were ready | 17 s |
| App displayed 100% sent | 56 s |
| App displayed Checking your gauge | 57 s |
| App displayed Update installed after confirmed running-image readback | 71 s |

The hosted download, BLE owner check, temporary AP startup, Android association, and TCP connection are included in the first 17 seconds. Their individual timestamps and the first accepted OTA byte were not captured. This single run establishes a current tap-to-transfer-ready and tap-to-confirmation observation. The earlier approximately 65-second run started from a locally loaded signed bundle, used different firmware, and did not isolate startup stages, so it is not a valid controlled speedup comparison.

The Android app read back version `0.2.0-dev.24`, valid OTA state `2`, partition `0x60000`, and ELF SHA-256 `24d277bce7d2e13fd8a727ad8fd7657c652e65fc371fcd07f9a018ffa7d3da24`, equal to the hosted image descriptor. Device data showed stored and running configuration revision 15, matching SHA-256 values, two definitions, two pages, zero alerts, and no configuration trial flag. This is physical Pixel-to-gauge evidence. The user separately confirmed that the physical display renders normally and page taps work. No live OBD telemetry or code clearing was attempted.

A fresh GitHub check after installation reported that dev.24 was up to date and did not offer the equal version for installation.

The first APK exposed a presentation bug after the confirmed update: the screen still showed the previously read dev.23 version and an Install action until the user checked the installed version again. The follow-up Android change carries the already authenticated post-restart identity into screen state and clears the downloaded selection. It passed build, unit tests, Android test compilation, lint, and repository checks. No second OTA was performed solely to retest this UI cleanup.

Pixel captures: [sending after network setup](assets/wifi-dev24/wifi-dev24-sending.png), [confirmed update with stale selection before the UI fix](assets/wifi-dev24/wifi-dev24-confirmed.png), and [dev.24 current after a fresh signed-release check](assets/wifi-dev24/wifi-dev24-current.png).
