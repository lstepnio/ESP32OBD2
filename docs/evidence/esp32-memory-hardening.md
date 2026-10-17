# ESP32 memory audit and hardening

> Historical evidence. Current support is in [current state](../current-state.md);
> remaining qualification belongs in [the backlog](../backlog.md). This report
> records this session and is not a replacement for the maintained memory policy.

Date: 2026-10-16. Firmware dev.55 is the comparison baseline. Dev.56 introduces
memory placement/lifecycle hardening; dev.57 adds measured application-stack
headroom. This report separates source/link measurements, host fault injection,
serial observations and physical bench qualification.

## Findings

The board is the Waveshare ESP32-S3-Touch-LCD-1.28 with ESP32-S3R2: 512 KiB SRAM,
2 MiB QSPI PSRAM and 16 MiB flash. The vendor [board specification](https://www.waveshare.com/wiki/ESP32-S3-Touch-LCD-1.28)
and pinned [ESP-IDF memory guidance](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-guides/performance/ram-usage.html)
agree with the configured target and device boot report. Flash and external RAM
cannot replace required internal allocations. Runtime PSRAM uses QSPI at 40 MHz.

The dev.55 ELF reserved a **65,536-byte LVGL TLSF arena in internal BSS**. Its
board draw buffer was only 4,800 bytes; the large pool holds CPU-side UI data.
Configuration trees/runtime and Wi-Fi's two 8,448-byte frame buffers were already
allocated explicitly in PSRAM. Blindly moving stacks or reducing the three required
BLE links would add risk without addressing the largest misplaced allocation.

The private read-only USB baseline captured the dev.55 ELF prefix `f79a039c3`,
internal free **53,275 bytes**, largest block **31,744**, minimum **47,499** and
PSRAM free/minimum **2,045,572**. The application stack margin was **892 bytes**;
logged queue drops were zero. These are indoor samples with unavailable-adapter
retries, not a dual-adapter load/OTA soak.

Earlier evidence records a real dev.48 vendor beacon allocation panic, controlled
dev.50 refusal at 13,279 internal bytes and dev.51/52 successful Wi-Fi repair.
The dev.55 rollout also encountered ATT 0x11 from a busy nonblocking status mutex,
then an independent service-discovery timeout. These are distinct failures; a
Bluetooth error alone does not establish OOM. See [recovery investigation](gatt-long-read-recovery.md)
and [dev.55 installation](gauge-performance-review.md#2026-10-16-dev55-appwi-fi-installation).

## Changes and static comparison

LVGL keeps its 64 KiB bounded allocator and now uses its supported large-RAM
attribute hook, injected through project CMake, to place that arena in external
BSS. No managed vendor source was edited. ESP-IDF's supported external-BSS option
also relocates 19,336 bytes of SDK network/host data. Mandatory PSRAM boot checks
and the SDK's S3 PSRAM atomic workaround remain. The SPI draw buffer is explicitly
internal DMA-capable. Stacks, Wi-Fi buffer counts and NimBLE link capacity stay.

| Linked section | Baseline bytes | Candidate bytes |
| --- | ---: | ---: |
| Internal BSS | 105,000 | 20,216 |
| Internal initialized data | 23,348 | 23,396 |
| IRAM code | 119,187 | 119,259 |
| IRAM vectors | 1,027 | 1,027 |
| Combined internal static | 248,562 | 163,898 |
| External BSS | 0 | 84,872 |

Net internal static reduction is **84,664 bytes, about 82.7 KiB**. This is a linked
image result, not a measured runtime-free gain. New telemetry accounts for the
small internal overhead. The post-link guard requires the external 64 KiB arena,
an IRAM failure hook and combined internal static use no greater than 192 KiB.
It also runs during signed release builds, preventing a vendor/config change from
silently moving the arena back into SRAM.

Wi-Fi startup has separate internal 8-bit free/largest admission floors: 64/16 KiB
before initializing a new driver and 32/8 KiB before AP start. These are sampled
floors, not reserved heap or proof against all vendor/private-pool failures.
SDK initialization failures unwind driver/netif state; fixtures cover injected
initialization failures and normal/idempotent close, not every possible SDK
stop/deinitialization failure.
Both mandatory PSRAM frame buffers and both workers are created transactionally;
any partial creation frees buffers, tasks, queue and mutex. A failed optional
maintenance service no longer aborts the gauge or advertises unavailable Wi-Fi.

Wi-Fi status now copies a published 112-byte snapshot under a short critical
section rather than taking the worker mutex. Reads recompute expiry and scrub
expired credentials. Allocation-failure diagnostics use only an IRAM hook and
small internal counters; formatting/heap queries occur later in task context.
Lifecycle logs distinguish internal 8-bit, DMA and PSRAM free/largest values.
Workers sample their own stack watermarks once per 64 progress reports. No new
tasks, watchdog resets, exception swallowing or user prompts were added.

## Verification and limits

The ESP-IDF 5.4.1 build and linked-memory gate passed. Address/undefined-behavior
sanitized fixtures passed for all six Wi-Fi service creation failure points,
existing seven SDK initialization failures/retry/idempotent close, status expiry/
wrap/scrubbing, failure-hook side effects, stack watermark minima and admission
boundaries. Memory-guard negatives reject internal/missing/duplicate/oversized pools
and excess combined static use. Production LVGL host renders passed all twenty-one
showcase frames and existing preemption/restore/glyph cases. The Android test APK
compiled with the bounded 1..30 maintenance-cycle option.

These offline checks alone do not establish physical qualification. Publication
uses separate owner authorization and green CI; the authorized outcomes follow.
The [qualification runbook](../development/memory-qualification.md) owns repeated
AP lifecycle, signed full upload, memory/stack measurements, physical display/touch
and loaded dual-source soak. Public capability flags remain
disabled; no main merge, USB flashing, vehicle mutation or owner/bond reset occurred.


## Authorized dev.56 publication and installation

All jobs in [quality run 38997376649](https://github.com/lstepnio/ESP32OBD2/actions/runs/38997376649)
passed for `90347ce`. The owner explicitly authorized publication, App/Wi-Fi
installation and repeated memory tests. Protected [release run 38997833006](https://github.com/lstepnio/ESP32OBD2/actions/runs/38997833006)
published immutable dev.56 from exact source `90347ceb3f41a094086c4a387a7a5d4172ac6dba`,
using catalog generation 37. Independent verification passed for both signatures,
package/image hashes and sizes, board/layout/protocol, version and signed ELF
`782875f89d3307b1a8e25147f068c15b70a4e5555bc99c2360b1693974a9dec1`.

`WifiFirmwareJourneyTest` passed in 109.889 seconds. The update portion took
71,890 ms, with sending at 16,039 ms, verifying at 55,839 ms and restart at 57,301 ms.
The exact image ran in the alternate slot with health 2, cleared recovery journal
and unchanged revision 50/digest, display settings and phone profiles. The owner
confirmed normal physical pages and working swipes. The 30-cycle result with
private serial capture is documented below.

The incoming transfer was executed by the previous dev.55 runtime. To exercise
full reception/flash/signature verification while the optimized PSRAM layout is
running, dev.57 uses a new firmware version identity. During
ongoing dev.56 cycles, the new diagnostics recorded an application-stack minimum
of 552 bytes. Dev.57 therefore also increases `app_tick` from 3,072 to 4,096 bytes,
spending 1 KiB of recovered internal heap for a measured safety margin. All other
task stacks and memory placement/lifecycle logic remain unchanged. The owner
separately authorized its publication and installation. Reinstalling/bypassing the
identical-image guard is not a qualification method.


## Dev.56 thirty-cycle bench result

`WifiMaintenanceJourneyTest` completed all thirty requested cycles in **284.124
seconds**, with fresh session identities, authenticated Wi-Fi OTA/config reads,
protected closes, unchanged boot nonce, retained exact dev.56 ELF/health 2 and
unchanged revision 50/digest/settings. The owner separately confirmed physical
normal pages and working swipes after installation. No ECU write occurred.

| Metric | Captured dev.56 range/result |
| --- | --- |
| Internal 8-bit free at network ready | 99,559..100,019 bytes |
| Internal 8-bit free after close | 123,611..123,875 bytes |
| Smallest largest internal 8-bit block | 31,744 bytes |
| Minimum internal 8-bit heap recorded | 98,091 bytes |
| PSRAM free at network-ready samples, minimum | 1,942,432 bytes |
| Allocation failure counter | 0 throughout captured lifecycle samples |
| Queue drops | No nonzero reported worker drops |
| Reset during the thirty-cycle test | None; unchanged protocol boot nonce |

After-close internal samples varied by only 264 bytes, within the 2 KiB investigation
threshold, with no accumulating loss demonstrated over these thirty cycles. Largest
block stayed 31,744 bytes. This is a short indoor lifecycle test with unavailable
adapter workers, not an hours-long leak proof or loaded three-link/vehicle soak.
The old baseline's legacy internal-capability aggregate and the new explicit 8-bit
measurement are not an exact identical-capability A/B comparison. The linked
84,664-byte static reduction is independent evidence of the allocation correction.

Sampled stack minima were application 552, UI 2,960, primary adapter 952, child
adapter 992, configuration 4,256, OTA 3,036, Wi-Fi server 6,040 and command worker
2,560 bytes. The application margin is too close to the 512-byte bench floor for
future alert paths. Dev.57 raises only that stack by 1 KiB; no task stack is reduced.
The following qualification separates a full upload received by dev.56 from
post-dev.57 stack observations. Loaded recovery qualification remains open.


Prepared dev.57 source `8af369b` passed local IDF/link-budget checks and all jobs in
[quality run 38998481809](https://github.com/lstepnio/ESP32OBD2/actions/runs/38998481809).
Its only firmware changes from dev.56 are application-stack 3,072 to 4,096 bytes
and version identity. A separate App-test-only option compiles successfully and
exposes the existing bounded debug pre-activation pause for memory observation;
it defaults to zero and resets in `finally`. The firmware release source is pinned
at `codex/memory-dev57-candidate`; separate owner approval was received.

## Authorized dev.57 publication and full-transfer qualification

The owner authorized publication/installation and the one-minute pre-activation
measurement pause. Protected [release run 38998977297](https://github.com/lstepnio/ESP32OBD2/actions/runs/38998977297)
published immutable [dev.57](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.57)
from exact source `8af369b0b48e6696d13a6342bce8661e2c04b614`, catalog generation 38.
Independent catalog/bundle signature, size/hash, board/layout/protocol and app
descriptor verification passed. The signed application is 1,616,160 bytes with ELF
`dacbcb6d4fb4f458f925ed8020748ddf1dc429a4f3267a308efe129441de7cfc`.

The first installation attempt stopped in authenticated Bluetooth preparation:
the protected session timed out after 20 seconds before any Wi-Fi startup/sending
stage. The fixture ended in 39.447 seconds. A fresh-App retry could not reach its
initial ready condition within 90 seconds (91.156 seconds overall). The Pixel
showed Searching, Android reported its gauge link disconnected, and the last
firmware phone-connect event had no corresponding disconnect in the captured
interval. Gauge task/adapter retry progress continued, with zero allocation
failures and approximately 133 KB internal 8-bit free in nearby periodic samples.
These facts do not identify the Bluetooth failure's root cause or establish OOM.
Both failed attempts are retained privately. No firmware sending occurred; a
gauge power-cycle was requested before further App/Wi-Fi qualification.

After the owner restarted the gauge, the third attempt was externally force-stopped
by another shell command at 21:39:18, just after a successful 1,044 ms authenticated
preflight. Android exit information identifies USER REQUESTED / FORCE STOP; this was
not an App runtime crash. A duplicate active chat was using the same devices and
worktree. The owner authorized coordination and that chat paused its device work.
The fourth attempt then failed on an initial owner read with Android GATT 133 in
20.034 seconds, again before Wi-Fi/image sending. The general Android status is
defined as `GATT_ERROR = 0x85` in [AOSP](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/cdb1c6dca4/system/stack/include/gatt_api.h);
it does not identify an ESP32 allocation failure.

Source review found that protected session cleanup requested disconnect and
immediately closed GATT without awaiting the platform callback. The App now retires
callbacks, awaits disconnect for at most 1.5 seconds under bounded cancellation-safe
cleanup, and retains the shared lease until that cleanup finishes. Initial owner
reads retry transient 133 alongside existing authentication/encryption/resource
statuses within four reads; only a successful protected frame authenticates the
session, and no transaction write is replayed. Focused JVM cases cover delayed or
absent disconnect callbacks, cancellation, synchronous cleanup failure, transient
owner proof, retry exhaustion, malformed/denied proof and the one-time legacy reset.
This closes a source-level handover gap; the original field failure's precise
cause is not inferred from the change. Ten focused JVM cases, App/test APK builds,
lint and all jobs in [quality run 39000168256](https://github.com/lstepnio/ESP32OBD2/actions/runs/39000168256)
passed for `ad302d4`. The App was installed without data clearing. Its first
attempt still exhausted initial owner-read retries before sending. A Pixel
Bluetooth off/on reset then restored the path; no bonds or saved data were removed.
Successful installation after that reset does not prove the original handover
failure is eliminated. Broader automatic recovery/soak remains a separate gate.

### Completed optimized-receiver transfer

The next `WifiFirmwareJourneyTest` passed in **166.884 seconds**. The installation
portion took **132,638 ms**, including the requested **60,000 ms** debug pause
after verification; excluding that pause it took 72,638 ms. Sending began at
16,608 ms, verifying at 56,606 ms and running-image confirmation at 117,961 ms.
These are sampled App stages, not wire-only throughput. App diagnostics measured
1,123 ms authenticated preflight, 2,905 ms total through gauge AP startup, 9,924 ms
Android network acquisition and 2,546 ms flash preparation.

The receiver was optimized dev.56. Its pause before activation captured the
post-upload/signature-verification heap minimum before reboot:

| Receiver measurement | Captured result |
| --- | ---: |
| Internal 8-bit free at Wi-Fi ready | 100,151 bytes |
| Minimum internal 8-bit heap through upload/verification | 94,991 bytes |
| Smallest largest internal 8-bit block | 31,744 bytes |
| Lowest periodic sampled internal free | 98,763 bytes |
| Lowest periodic sampled PSRAM free | 1,940,252 bytes |
| Internal free after Wi-Fi close, before activation | 123,791 bytes |
| Allocation failures / reported queue drops | 0 / 0 |
| OTA worker sampled stack margin | 1,868 bytes |

Application stack observations reached 488 bytes on this dev.56 boot, reinforcing
the already prepared 1 KiB correction in dev.57. UI/primary/child/configuration/
Wi-Fi server/command sampled stack minima were 2,976/1,000/992/4,256/7,304/2,576
bytes. The OTA worker's 2,485 ms maximum progress gap is consistent with the
measured bounded flash preparation, not evidence of a stalled task. Stage samples
and the registered hook do not detect every possible vendor private-pool failure.

The App verified exact signed dev.57 ELF, alternate OTA slot, health 2, cleared
recovery journal and unchanged revision **51**, digest, display settings and phone
profiles. Revision 51 was already present before this test; the installation did
not restore the earlier revision 50 snapshot. USB boot logs show the retained
owner association and healthy confirmation after UI/BLE/application progress.
No USB flashing, ECU write, capability promotion or main merge occurred.

### Dev.57 maintenance and graphics checks

Two fresh protected maintenance cycles passed in **35.106 seconds**, with opens
of 1,589 and 1,951 ms, authenticated Wi-Fi OTA/config reads, protected close,
unchanged boot nonce and retained exact dev.57 image/health 2/revision 51/settings.
Network-ready internal free was 98,711..98,995 bytes; both closes recovered to
122,711 bytes with a 31,744-byte largest block. Recorded internal minimum was
97,243 bytes and allocation failures remained zero. The measured application
stack minimum reached 1,512 bytes during subsequent rendering/health logging.
The complete twenty-one-frame protected showcase and owner display/touch check
are being collected separately from these maintenance results.

The first all-frame observer attempt timed out after 220 seconds (231.055 seconds
overall), because it sampled App state through the ordinary approximately
twenty-second Settings cadence while frames advance every eight seconds. Its
`finally` stop completed. This was an observer error, not a demonstrated rendering
or memory failure. The fixture now isolates foreground polling, directly observes
protected showcase state, preserves App-owned start/stop, checks actual document/
settings readback after stop and restores foreground polling in `finally`.

A subsequent isolated-observer start did not reach active playback in 30 seconds
(40.385 seconds overall). Source review identified a foreground cancellation
handover race exposed by the new suspending transport cleanup: `collectLatest`
could clear the automatic-job reference while its child was still releasing the
shared lease. A deterministic JVM regression reproduced the lost-owner gap before
the fix. The controller now awaits that child's cleanup before clearing its
reference, so a simultaneous user operation can join the same still-owned job.
This addresses the demonstrated ownership race without establishing it as the
cause of every earlier GATT failure.
All eighteen focused foreground/owner/cleanup JVM cases, both APK builds and lint
passed after the fix; the new regression failed against the previous controller.
All jobs in [quality run 39001134847](https://github.com/lstepnio/ESP32OBD2/actions/runs/39001134847)
passed for `d393490`. Its App/test APKs were installed without data clearing.

An external Maps experiment then replaced the production APK while the next
showcase fixture was starting. Android recorded PACKAGE UPDATED at 22:04:25;
that interrupted attempt is not an App runtime crash or a valid showcase result.
The experiment subsequently restored the exact local `d393490` production APK,
verified by SHA-256 equality, and finished in a separate sandbox package. It was
left installed. The shared phone wake helper was restarted for the remaining
qualification, and USB capture was extended. A POWERON reset was observed when
that capture reopened; it occurred between the completed maintenance checks and
the final graphics attempt, not inside the two-cycle unchanged-boot assertion.
The logs alone do not establish the reset's cause. The gauge retained its owner
and signed dev.57 identity. Final graphics/physical results follow separately.

### Final graphics and session teardown

`DisplayShowcaseJourneyTest` passed in **221.939 seconds** using App source
`d393490`. Protected reads observed every distinct frame index **0..20** on the
physical gauge. App-owned stop completed, followed by actual protected document/
settings readback confirming revision 51/digest/settings and retained phone
profiles. This is protocol/render-path qualification, not camera capture or owner
approval of every pixel, unit, rotation and daylight condition.

Across captured dev.57 maintenance/rendering boots, minimum sampled stack margins
were application **1,512**, UI **2,496**, primary adapter **968**, child **992**,
configuration **4,256**, OTA **3,036**, Wi-Fi server **6,040** and command **2,576**
bytes. The final graphics boot alone recorded application 1,544 and UI 2,528 bytes.
All captured allocation counters and reported worker queue drops stayed zero.
These are exercised-path minima, not proof of every possible call path; adapters
were unavailable indoors and the full upload's signature work ran on dev.56.

The read-only USB logger was closed and normal eGauge was reopened. The shared
debug-awake record had already been removed by another session when final stop
was attempted; status confirmed the helper inactive, screen timeout 60,000 ms and
charging-wake value 15. No missing original values were guessed or overwritten.
On 2026-10-16 the owner separately confirmed dev.57 shows a normal page with clean
text and working swipes. This confirmation does not cover the subsequent unchecked-
caption removal or expanded display envelope, which have not been installed. Loaded
ECM/TCM/phone soak, unattended Bluetooth recovery and interrupted maintenance/OTA
remain open; no OOM-immunity or production-capability claim is made.
