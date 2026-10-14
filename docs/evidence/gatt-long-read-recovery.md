# GATT long-read recovery investigation

> Historical evidence. This dated review records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Observed 2026-10-14 during second-adapter setup. Source candidate: dev.49.

## Phone, gauge and vehicle observations

The owner confirmed original adapter in ECM, new adapter in TCM, ignition ON/RUN
and other OBD Apps closed. The Pixel discovered and saved the distinct second
adapter under the existing transmission child. Private saved-state comparison
confirmed unchanged primary binding and both dashboard drafts. Dual mode was enabled
on the phone; no gauge configuration was sent before connection recovery failed.
The owner reported live engine RPM and responsive swipes on the physical gauge.

App restart, Pixel Bluetooth off/on, gauge restart and removal of only the ECM
adapter did not produce a stable protected connection. Mac advertising checks alone
were inconclusive: a connected peripheral stops advertising. Subsequent USB logs
showed repeated Phone link connected/disconnected events and healthy UI/config
worker progress. Thus a frozen display or permanently absent radio was not established.
The owner returned indoors with gauge and Pixel on USB before simultaneous vehicle
qualification. Engine-link timeout logs with its adapter absent are expected absence,
not evidence of a deadlock.

Pixel debug logs identified protected reads and owner-state reads failing with
GATT 7. Installed ESP-IDF 5.4.1 NimBLE `ble_gatts_val_access` calls the application
callback, then calculates `full_value_length - offset` and copies from that offset.
The existing companion callbacks already appended a sliced tail, applying the
same offset twice. A 360-byte event response at negotiated MTU 256 reaches offset
255 with only 105 bytes appended and fails INVALID_OFFSET. The default SDK preferred
MTU is 256. This affects long status/capability/readback values, not OBD decoder bytes.

## Corrective boundary

All 21 companion response paths use one full-value append helper. Bounded immutable
snapshots remain cached across positive-offset continuations. The native host owns
ATT slicing; application configuration/document offsets remain unchanged. Offset
past end and allocation/length failure retain their error semantics. Wire payloads,
owner protection, configuration schema and public capability flags are unchanged.

Android performs at most one encrypted/authenticated read-only STATUS command when
an owner read receives ATT 7 on older firmware. It then requires a recognized protected
frame before proceeding. Authentication and unrelated failures never use the reset;
repeated offset failure fails closed. The existing operation deadline still applies.
This permits the field App/Wi-Fi repair path without USB flashing or bond deletion.

## Verification and deployment boundary

- Sanitized production C helper reconstructs values of 4, 10, 56, 140, 248, 360 and
  512 bytes at MTUs 23, 185, 256 and 517. Exact-end, past-end and append-failure cases
  pass, and the former 360/256 double-offset failure is reproduced.
- All 89 host tests passed. Android build/lint and 179 JVM tests passed, including
  one-reset/read-only opcode policy and rejection of unrelated/authentication failures.
- ESP-IDF 5.4.1 dev.49 firmware build passed. No firmware was installed over USB.
- Updated dev.49 debug App installed with data preserved. It restored the saved
  gauge review/send flow; the universal pill reports Car reconnecting with adapters
  absent instead of losing the phone/gauge management path. Exact App/FW qualification
  remains separate from offline checks.
- Signed dev.49 firmware publication/installation requires explicit owner authorization,
  green CI, verified signed assets, App/Wi-Fi transfer and exact post-boot identity/health.
  Firmware remains dev.48 until that path is completed. The gauge still has revision 49
  and the previous single transport; two bindings are saved on the phone pending send.

After firmware repair, send the complete dual setup and require protected stored/running
revision/hash and cleared trial. Confirm physical display/swipes separately. Live ECM/TCM
reads, independent loss/recovery and soak remain QUAL-04; no public qualification increase.
Private logs, identifiers and preference backups remain ignored artifacts.

## Authorized signed release

The owner authorized publication and App/Wi-Fi installation of dev.49. All three
quality jobs passed for `5864046`; release workflow `38627758933` published the
immutable [development prerelease](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.49)
from that exact commit, generation 31. Downloaded catalog and image signatures
verified against the App public key, alongside board/layout/version, descriptor,
size and digest checks. Image size: 1,534,592 bytes. Expected ELF SHA-256:
`e5fcda4f66721dc405a9add3c26e996caac21cb786849c833fcf6194e9585530`.

Publication alone is not device installation. Post-boot image/health, whole dual
configuration and physical touch remain separate gates.

## Wi-Fi installation blocker

Authorized App/Wi-Fi attempts reached a protected dev.48 boot preflight but did
not transfer an image. The App reported an interrupted link (GATT 133); fresh USB
capture established a second failure at maintenance startup: repeated vendor
`alloc eb len=752 type=4 fail` messages followed by LoadProhibited and a CPU reset.
The next boot remained dev.48 with revision 49. Earlier event-sync ATT 7 logs
persisted on that older firmware; they are distinct from this startup crash.

The existing Wi-Fi defaults allocate ten static RX buffers, 32 dynamic RX/TX
buffers and 32 short management buffers without preferring PSRAM. The observed
pre-start internal free/largest block were approximately 58 KB / 31 KB while
about 2 MB PSRAM remained free. Dev.50 preparation enables the SDK PSRAM-first
Wi-Fi/LwIP allocator, four static RX and four static TX buffers, eight dynamic
RX and cached TX buffers, a matching four-frame Block Ack window and six short
management buffers for the single maintenance client. Eligible allocations use
PSRAM; DMA requirements remain internal. Startup additionally refuses to enable
the AP below 16 KB internal free / 4 KB largest block and stops partial startup
on SDK error. These conservative admission floors need physical measurements;
they are not a proof of immunity to all vendor allocation failures.

Dev.49 publication succeeded, but App/Wi-Fi installation remains blocked on
running dev.48. The owner explicitly authorized signed App/Bluetooth recovery on 2026-10-14. It uses the
existing protected transfer, update journal and exact post-boot check; no USB
flash, pairing deletion or configuration change is needed. The opt-in recovery
instrumentation is skipped unless an owner authorizes installation and supplies
an exact signed bundle/version/ELF. Ordinary update UI continues using Wi-Fi.
Dev.50 publication needs its own approval and green checks. Local App build/lint,
179 JVM tests and recovery instrumentation compilation passed; dev.50 firmware
build, sanitized core fixtures (including exhaustion/fragmentation admission cases),
repository validation and diff checks passed. Physical recovery is recorded below
only after the exact running-image check completes.

## Authorized App/Bluetooth recovery and dual send

The owner explicitly approved App/Bluetooth recovery after the Wi-Fi crash was
identified. The dev.49 image from `5864046`, generation 31, installed through the
App's existing protected Bluetooth OTA path with the development recovery App
from `f12edb1`. The opt-in preservation test passed in 776.084 seconds, including
transfer, reboot, fresh protected image read and automatic settings reload. Running
ELF matched `e5fcda4f66721dc405a9add3c26e996caac21cb786849c833fcf6194e9585530`,
OTA health was 2, the update journal cleared, and revision 49/digest, display
settings and phone profiles were unchanged. No USB flash was performed.

The whole saved vehicle dashboard was then reviewed and sent once through the
ordinary App flow. Protected stored/running revision **50** matched digest
`21ce4c932ae9ec32c978cf3ff1570fc9fdec93988a3674555c5a7198ad046cab`,
with trial cleared, two distinct ECM/TCM bindings, three mixed-reading pages and
one coolant alert. Private pre/post preference comparison also confirmed unchanged
profile and presentation documents. The read-only automatic connection journey
passed in 17.219 seconds: opening confirmation 10.357 seconds, resume refresh
2.775 seconds, hardware snapshot followed by fresh protected firmware identity,
unchanged configuration/settings and no mutations. No Bluetooth toggle or picker
recovery case was invoked in that fixture.

The gauge/Pixel were indoors without vehicle adapters. Missing-source states are
expected; simultaneous radio operation, sibling continuity and live values were
not qualified. The owner subsequently confirmed the dev.49 normal page and responsive swipes.
The dev.50 Wi-Fi memory repair remains uninstalled and requires separate release
approval; all three GitHub checks passed for `f12edb1` in run `38630911056`.

## Authorized dev.50 rollout

The owner separately approved publishing and installing dev.50 through App/Bluetooth,
then checking Wi-Fi startup. Workflow `38633276160` published the immutable
[development prerelease](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.50)
from exact approved commit `f12edb1`, generation 32. A temporary release branch kept
workflow dispatch on that reviewed commit and was removed after publication; the
release tag and primary development branch remain. Downloaded catalog and image
signatures verified against the App public key, alongside board/layout/version,
size and digest checks. Image size: 1,535,136 bytes. Expected ELF SHA-256:
`e3d951da08830a29ed056c3c536160eccefce53db0c24725185f36cf53830b23`.

The recovery App now makes a best-effort high-priority Bluetooth connection request
only for the bounded signed BLE transfer and requests balanced timing in cleanup.
Refusal is not a failure gate. Normal Wi-Fi selection and authentication remain
unchanged. Android build/lint, 179 JVM tests and both recovery/maintenance fixture
compilations passed. Installation and Wi-Fi qualification are recorded separately
below after physical execution; publication is not an installed-image claim.

## Dev.50 device outcome and next memory refinement

Owner-authorized dev.50 App/Bluetooth installation passed its preservation test in
913.182 seconds: exact signed ELF, OTA health 2, revision 50/digest, display settings,
profiles and cleared journal. Android accepted the high-priority request with interval
12, latency 1; this run did not demonstrate a speed improvement over the prior run.
The runs used different active single/dual runtime configurations and included a
foreground interruption, so this is not an isolated timing benchmark.

The physical Wi-Fi fixture failed at protected OPEN with result 3. USB logs showed
PSRAM preference and the expected bounded four RX/four TX/eight cached/eight dynamic
RX/six management buffers, then a controlled admission refusal at **13,279 bytes
internal free / 7,168 largest block**, below the 16 KB total floor. There was no
beacon allocation panic. Minute logs afterward showed internal minimum 13,151 bytes,
PSRAM minimum 2,024,064 bytes, continued App/UI/config/OTA/Wi-Fi worker progress and
both missing-adapter retry workers; UI maximum gap was 115 ms. Source absence is
expected indoors. This proves controlled refusal, not a usable maintenance network.

Dev.51 preparation removes another approximately 6.4 KB of static DMA buffers by
using two RX/two TX and a matching two-frame RX Block Ack window, retaining the
16 KB / 4 KB admission floors. The maintenance worker now deinitializes the driver,
unregisters its handlers and destroys its AP interface on failed startup or close
before resuming adapters. Shared event-loop/netif runtime is retained. Checked
netif create/attach primitives replace the SDK convenience function's assertions.
Sanitized production lifecycle fixtures inject netif allocation/attach, default and
custom event registration and driver-init failures, verify resource release, retry
and idempotent close. The firmware builds with ESP-IDF 5.4.1. New publication and
installation require separate owner release authorization and green checks.

After the refusal, the physical read-only automatic connection journey passed in
17.476 seconds, including resume refresh, hardware-to-firmware read chain, healthy
dev.50 identity and unchanged revision 50/digest/settings. Thus the controlled
network failure did not strand protected App/gauge management. All 90 host tests,
the dev.51 firmware build, repository validation and diff checks passed locally;
Android test fixtures compiled with the boot-nonce reset check. CI and release
approval remain the next gates for dev.51.
