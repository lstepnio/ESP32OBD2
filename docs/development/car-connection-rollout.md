# Car connection and settings rollout

Recorded 2026-10-07. This record separates device observations from fixture tests.

## Installed development candidate

The signed [0.2.0-dev.40 development release](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.40)
was built from `b20d673ddac313a121d0e6a223f614364276fcb0`, catalog generation 23.
Catalog and bundle signatures, image size and image/descriptor hashes were independently
checked against the pinned development key before installation.

- Image size: 1,509,488 bytes.
- Image SHA-256: `d939dd02a5466117831a98b0e1f06b7fb1f675fba69e6f8242e2cf3548896f94`.
- ELF SHA-256: `f9eeb9ce11a1d7358cd70128526de5ba57603bd34fcb40bfcf840ce783abf6cb`.

The physical Pixel installed through the hosted signed App/private-Wi-Fi flow.
The durable app journal matched the exact image and ELF. The app showed “Update installed”
and confirmed the running image. A protected hardware test asserted the exact version,
ELF hash and healthy OTA state 2. The owner separately confirmed a normal physical
reading page and working swipes. USB serial observation was read-only.

The old `dev.37-tcm` version label prevented the standard comparison from offering
`dev.40`. The app now normalizes only that known legacy family for availability ordering;
all signed image identity and boot checks remain exact. One protected configuration
read disconnected before update transfer; no write was started by that failed read.

## Verification

- Android: 78 unit tests, debug and instrumentation builds, and lint passed.
- Isolated Pixel UI: eight tests passed, including all 76 native screenshot comparisons,
  vehicle hierarchy and shared connection widget cases. These use fixture data.
- Firmware: 72 offline host tests, including production C sanitizer fault injection,
  and the ESP-IDF 5.4.1 build passed. Firmware source matches the published candidate.
- Repository contract/document checks passed.
- Physical post-update automatic connection test: one test passed in 46.424 seconds.
  Protected confirmation on opening took 3,571 ms. Open/resume settings reads,
  Bluetooth off/on recovery and cancelling Add gauge required no reconnect tap.
  Gauge identity, configuration revision 29/hash, display settings and all vehicle
  profile contents were retained within the test. It invoked no configuration,
  vehicle or firmware writes. Boot identity was asserted before reconnect operations;
  later reconnect clears cached boot identity until a new protected read.

Actual settings readback showed brightness 100%, orientation 270°, Metric and
saved-page cycling off. Saved unit choice was preserved; regional defaults apply
only when no valid choice exists. Documentation screenshots are in-memory UI examples,
not live vehicle readings. Private backups, identifiers, raw captures and logs remain
under ignored local artifacts, outside Git.

## Measured update startup

One real transfer recorded authenticated preflight at 752 ms, gauge Wi-Fi startup
at 1,548 ms cumulative, Android Wi-Fi availability after a further 9,981 ms,
socket connection at 170 ms and first response at 11,914 ms cumulative.
Flash preparation then took 2,358 ms. Android network joining accounted for most
of this observed startup delay; this is one measurement, not a performance guarantee.

## Remaining qualification

Live Car faults, two physical adapters/multiple gauges, queue saturation, long soak,
configuration/OTA interruption, power loss and alternate phones remain separate gates.
No vehicle or OBD-adapter exploration was performed during this rollout.
The app represents a primary ECM plus optional Expert TCM child and per-gauge contexts;
firmware still supports one active adapter/source per gauge. Public `configWrite:false`,
`ota:false` and link capacity one remain unchanged. See [remaining work](../roadmap.md).
