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


## 2026-10-18: dev.58 240 MHz bench trial

Owner approved publishing/installing dev.58 through App/Wi-Fi. Quality CI and the
signed development workflow passed for source `555971e52427473bc26ff0b50d1023d22d0be4c3`.
The release tag is `dev-v0.2.0-dev.58`, catalog generation 39; downloaded catalog
and bundle signatures, hashes, board/layout and app descriptor were independently
verified against the pinned public key. Image size is 1,615,856 bytes; signed ELF
is `bcb2db99ab7d61cf45e5fce42fc5a4d5bfa9ce5ecca380ee39d9eeeca19123a9`.

CPU configuration changes from 160 to 240 MHz, with dynamic power management still
disabled and flash/PSRAM/peripheral clocks unchanged. The candidate also includes
the pending unchecked-caption removal and wider glyph/arc envelope. These changes
make it a combined candidate, not a controlled CPU-only graphics benchmark.
The ESP-IDF boot log directly reports 160,000,000 Hz on dev.57 and 240,000,000 Hz
on dev.58. Local build/memory gate, six focused host tests and repository validation
passed; linked internal static use was 163,898 bytes against the 196,608-byte gate.

`WifiFirmwareJourneyTest` passed in **104.265 seconds**, with an installation
journey of **72,905 ms** and no artificial activation pause. It observed the
ordinary hosted signed App/Wi-Fi path, exact healthy alternate image (OTA state 2),
cleared update journal and retained revision 51/digest/settings/phone profiles.
The receiver during this transfer was still dev.57 at 160 MHz, so its timing is
not evidence of improved upload throughput on the 240 MHz receiver.

Same-session maintenance comparisons with the same Pixel/App:

| Installed image | Two-cycle test duration | Protected AP opens | Result |
| --- | --- | --- | --- |
| dev.57, 160 MHz | 44.904 seconds | 1,837 / 1,936 ms | Both authenticated Wi-Fi status/close cycles passed |
| dev.58, 240 MHz | 35.323 seconds | 1,607 / 1,634 ms | Both passed; unchanged boot nonce and saved state |

Two samples per setting do not establish a reliable speedup. Radio/Android timing
and connection preparation contribute to these end-to-end measurements. No reboot,
recorded allocation failure or queue drop occurred in either maintenance test.
During the exercised dev.58 paths, sampled internal heap minimum was 97,323 bytes
and the smallest reported largest block was 31,744 bytes. Minimum sampled
application-stack headroom across maintenance/showcase was 1,536 bytes. These are path-specific
observations, not added RAM capacity or OOM immunity.

The first all-frame showcase attempt stopped before completion. The owner confirmed
touching the gauge, consistent with its intended local exit behavior; that attempt
is not counted as a passing all-frame run. Owner separately reported clear screens,
no bezel clipping/corruption, a normal page and working swipes. The untouched repeat
passed in **176.307 seconds**, observing all 21 distinct frame indices 0..20.
App-owned stop and actual protected readback confirmed revision 51/digest/settings
and phone profiles preserved. This protocol observation is distinct from the owner
visual/touch confirmation.

Loaded ECM/TCM/phone soak, enclosure power/thermal measurements and a full upload
received at 240 MHz remain unqualified. No public capability flag was enabled.

## 2026-10-18 dev.59 indoor recovery and memory qualification

The owner requested indoor recovery/memory testing and a roadmap refresh. The
installed App APK matched the previously qualified dev.51 hash
`3f05a494b5dc60fbffde3cdd7f09feadbd853819b646813e806b5079c670313e`.
Firmware was signed dev.59, source `4932860`, ELF
`a4a68d78ad5275e9f9d0c20b8dfc8fafcb7405fcb4195b1faad3267a3ad5ab64`,
healthy OTA state 2 at 240 MHz, dashboard revision 51. Both adapters were absent;
their retry activity does not qualify connected ECM/TCM traffic or radio load.
Raw serial logs, phone preference backups and device identifiers remain private.

### Thirty-cycle lifecycle baseline

`WifiMaintenanceJourneyTest` passed **30 cycles in 361.910 seconds**. Each cycle
opened a distinct protected session, authenticated OTA/config status over Wi-Fi,
closed it and checked unchanged image and boot nonce. Final authenticated readback
retained revision/digest and display settings. There was no firmware/configuration
send, ECU command or bond reset. The serial capture included an initial POWERON
boot; no further boot banner occurred during this baseline, and its protected boot
nonce remained unchanged. Do not infer an unexplained reset from the initial banner.

| Measurement | Observed |
| --- | --- |
| Complete memory samples through baseline/readback | 127 |
| Recorded allocation failures | 0 |
| Internal heap low watermark | 94,179 bytes |
| Lowest sampled current internal free | 97,899 bytes |
| Smallest sampled/lifecycle-close largest block | 31,744 bytes |
| First close / second warm close / final close free | 122,843 / 122,839 / 122,719 bytes |
| Close free range after first warm-up | 122,707..122,851 bytes (144-byte range) |
| Sampled minimum stack: App / UI | 1,512 / 2,976 bytes |
| Sampled minimum stack: primary / child adapter workers | 888 / 992 bytes |
| Sampled minimum stack: config / OTA | 4,256 / 3,036 bytes |
| Sampled minimum stack: Wi-Fi server / command | 6,040 / 2,576 bytes |

Recorded worker queue drops and alert drops remained zero. Every sampled stack
margin exceeded the runbook's 512-byte floor. This shows no accumulating close
loss in this bounded bench run, not leak freedom, thermal qualification, every
transient minimum or safety under connected dual-adapter load.

### Offline failure boundaries

Five memory-budget/allocation-hook/service-failure tests, one Wi-Fi driver lifecycle
failure/retry fixture, one worker-health fixture, one production Wi-Fi IO fixture
(silence/trickle/backpressure/close/session loss) and sanitized core firmware policy
fixtures passed. Four socket-cancellation/deadline, four bounded GATT cleanup and
six protected-owner recovery JVM tests passed. A further fourteen JVM cases covered
boot confirmation, automatic update policy and durable setup. These are injected
software cases, not physical flash/power interruption evidence.

### Deliberate phone Wi-Fi loss

The new opt-in `WifiMaintenanceRecoveryJourneyTest` passed in **33.839 seconds**
against the unchanged installed App/firmware. After an authenticated status read,
the fixture disabled phone Wi-Fi. The old socket raised `SocketException` in
**191 ms** rather than reporting success. Bounded cleanup restored Wi-Fi and
closed the protected session; a distinct fresh session authenticated both OTA and
configuration status and closed in **4,703 ms**. Exact image/health, boot nonce,
revision 51/digest and settings remained unchanged. Only the instrumentation APK
was updated; the installed product APK hash remained the recorded baseline.

This is a real radio-loss check of a read-only session, not an interrupted image
upload, activation or rollback test. The fixture is documented in
[memory qualification](../development/memory-qualification.md#indoor-interruption-checks-without-another-firmware-image).

### App lifecycle and phone Bluetooth recovery

`AutomaticConnectionJourneyTest` passed in **58.261 seconds** with phone Bluetooth
off/on, cancelled gauge selection and hardware-then-firmware reads enabled. Opening
reached protected confirmation in **7,664 ms**, resume refreshed protected state
and settings in **2,775 ms**, and Bluetooth return recovered in **12,251 ms** without
a reconnect tap. Revision/digest, display settings, gauge identity and profiles were
unchanged. No pairing reset or configuration/firmware write was invoked.

After an explicit App force-stop, a separate read-only invocation passed in
**12.813 seconds**. Its fresh process reached protected confirmation in **7,591 ms**
and refreshed after resume in **2,864 ms**, retaining the same state and healthy
image. This tests reconstruction after this process stop, not every Android/OEM
background-kill condition or device power loss. These isolated attempts do not
establish a recovery success rate or explain the earlier GATT 133 cause.

### Controlled gauge reset and foreground resume

An initial new reset fixture failed its final independent protected read with
`Could not queue protected gauge command`, after detecting the changed boot and
automatic state refresh. Source review found that its independent client was
outside the App coordinator while automatic polling remained active. That attempt
is not counted as a passing reset/readback qualification or as a diagnosed product
regression. The corrected observer pauses polling, drains the App's operation lease
through an ordinary read, then exclusively observes the protected boot nonce.

The corrected `GaugeRestartRecoveryJourneyTest` passed in **40.183 seconds**.
After its explicit ready checkpoint, the Mac closed its logger and invoked esptool
`run` with hard reset, without flash writes or erasure, then reopened read-only
logging. A changed protected boot nonce proved the new boot. Resuming foreground
polling in the same App automatically refreshed connection/settings in **3,501 ms**;
the entire checkpoint/reset/readback sequence took **29,464 ms**. Exact healthy
image, authenticated owner, revision 51/digest, settings and profiles were retained.
No reconnect tap or setup resend was used. The first and corrected attempts each
included one host-controlled serial-reset sequence; neither qualifies rail-power
loss, brownouts, long retention or physical display/touch behavior.

### Display sweep, preservation and teardown

`DisplayShowcaseJourneyTest` passed in **174.069 seconds**, observing all **21**
distinct protected frame indices 0..20, stopping playback and reading back the
unchanged dashboard/settings/profiles. This is protocol/runtime evidence; no new
owner pixel, touch or all-rotation confirmation was collected in this session.
The prior dev.59 physical footer/hold/swipe confirmation remains separate.

Final private comparisons found profiles, gauge associations and presentation
preferences byte-for-byte unchanged. Across the serial captures, recorded
allocation-failure and queue/alert-drop counters remained zero. After the corrected
reset, sampled showcase App stack minimum was **1,576 bytes**, internal low watermark
**131,751 bytes** and the smallest sampled largest block **36,864 bytes** without
an active Wi-Fi driver. These are a different lifecycle phase from the 30-cycle
Wi-Fi baseline and must not be combined as a like-for-like improvement claim.
Normal eGauge was reopened, the read-only logger closed and original phone wake
settings restored. No firmware was installed during these tests.

### Next interruption candidate preparation

Dev.60 changes only the firmware version identity from dev.59; test/runbook/roadmap
updates do not alter the product runtime. Its ESP-IDF 5.4.1 build and linked memory
guard passed locally: **163,898 bytes** reviewed internal static use against the
**196,608-byte** ceiling; the single **65,536-byte** LVGL arena remains in external
BSS, and the allocation hook remains in IRAM. The candidate is not signed, published
or installed by this preparation. QUAL-02 retains the separate release-approval
gate and controlled OTA reset/identity-reconciliation matrix. Bench socket loss
does not substitute for that work.

## 2026-10-18 dev.60 signed OTA interruption qualification

The owner separately approved merging PR #55 after CI, publishing dev.60 and
performing controlled App/Wi-Fi interruption/recovery tests. All three quality
jobs passed in [run 39199744683](https://github.com/lstepnio/ESP32OBD2/actions/runs/39199744683).
PR #55 merged as `860dc3f`; the protected
[release workflow](https://github.com/lstepnio/ESP32OBD2/actions/runs/39200239768)
built that exact commit and published signed development dev.60, generation **41**.
Both signatures, catalog expiry/board/layout/protocol, bundle size/hash and image
descriptor/version were verified against the pinned development public key before
any transfer. Image size is **1,615,904 bytes**, image SHA-256
`bb8591ba9c24f8671b8e5e84a81fe22a67c88e175a57bb59ee6cf5e5e3c30c8d`,
ELF SHA-256 `4da1e9bbf61af24e25fcb1c7e13db68c55bd64e72ff30575d5cc112606bbcef3`.
Firmware runtime logic is unchanged from dev.59; the version identity alone differs.

### Controlled transfer resets

The opt-in `WifiFirmwareInterruptionJourneyTest` used the ordinary App signed
GitHub download and Wi-Fi install owner. At acknowledged progress checkpoints,
the host invoked the existing esptool serial-reset sequence without flash writes
or erasure. Checkpoint percentages are App-observed acknowledged transfer progress,
not measurements of the exact flash instruction at the reset edge.

| Observed checkpoint | Whole instrumentation test | Automatic previous-image/settings refresh after failed outcome |
| --- | --- | --- |
| 10% | 66.045 s | 3,328 ms |
| 50% | 78.925 s | 3,318 ms |
| 90% | 96.824 s | 3,291 ms |
| Signed image verified, before activation | 153.568 s | 3,255 ms |

Each passing case reported a failed interruption rather than installation, then
automatically reconciled the exact previous signed dev.59 ELF, partition
**0x60000**, health **2**. Full protected dashboard/settings readback retained
revision **51**, digest and display settings; phone profiles were unchanged.
The durable journal cleared only after authenticated reconciliation. No candidate
activation, automatic firmware retry, bond reset or ECU command occurred in those
cases. The roughly 3.3-second refresh above starts after the operation's failure;
host reset checkpoint to final test completion for the three progress cases was
about **23 seconds**, including
transport loss detection, bounded cleanup and preservation reads. Do not present
the refresh-only value as total interruption latency.

The verified/preactivation case used the existing **60-second debug activation
pause**. Its checkpoint was reached at acknowledged 100% only after firmware
SHA-256/signature verification succeeded. Host reset before activation retained
the previous confirmed slot; the long test duration includes the intentional pause
and is not a normal transfer/recovery performance measurement.

An initial observer attempt incorrectly accepted the prior completed download
operation as terminal. It triggered no host reset and is excluded. The guard now
requires a new UPDATE operation ID. A separate preactivation attempt failed its
protected owner preflight (`Gauge owner link did not become authenticated`) before
the selected reset boundary; it is also excluded. Ordinary fresh protected reads
regained access without bond erasure or re-pairing. Its root cause is not established.
The hardware cleanup assertion was corrected against the existing hardware-probe
contract: mode **255** with Wi-Fi initialized bit clear means the driver is released;
mode **0** would mean an initialized driver in NULL mode. That assertion mismatch
is not a product memory failure. Private failed-attempt logs were retained.

### App process loss during transfer

At an acknowledged **50%** checkpoint the host force-stopped only eGauge, without
resetting the gauge. Instrumentation reported its expected process crash; that
termination is not counted as a passing recovery test. A private journal read
confirmed that the intended dev.60 ELF and `preparing` entry survived the stop.

An independent `AutomaticConnectionJourneyTest` invocation then passed in
**13.918 seconds**, with the exact previous dev.59 image/health and protected
configuration/settings confirmed. Opening took **7,617 ms** and resume refreshed
in **1,923 ms**. The journal was cleared after authenticated reconciliation.
The hardware chain required Wi-Fi mode **255** and initialized Wi-Fi bit clear,
confirming driver deinitialization after the abandoned OTA socket; sampled internal
free/largest were **122,959 / 31,744 bytes**. No protected close/reset command or
bond repair was used to make that assertion pass. Revision 51/digest/settings were
retained and no automatic update retry occurred. This validates the actual
process-stop and socket-abandonment path, not every OEM kill or expiry scenario.

### Final complete installation and post-update maintenance

`WifiFirmwareJourneyTest` passed in **157.689 seconds**, including hosted preparation
and download. The ordinary installation journey took **126,782 ms**, including the
intentional **60,000 ms** verification-to-activation pause for a minute health sample.
Sending was observed at **14,206 ms**, verification at **53,191 ms**, and checking the
running image at **114,207 ms**. The prior download's terminal `Update ready` sample
at 3 ms is not an installation result; the fixture now filters observations by a
new UPDATE operation ID, matching the interruption observer's generation guard.

Exact signed dev.60 ELF/version, alternate slot **0x360000**, healthy OTA state **2**,
cleared journal and unchanged revision 51/digest/settings/profiles passed. The
receiver's complete memory samples recorded **93,975 bytes** internal low watermark,
**31,744 bytes** smallest largest block and zero allocation failures/queue drops.
Sampled minimum stack bytes were App **1,576**, UI **2,992**, primary/child workers
**1,000 / 992**, config **4,256**, OTA **1,340**, Wi-Fi server **7,016** and Wi-Fi command
**2,576**. These meet the runbook's captured memory/stack floors. Server progress
age spans a live client session because its outer accept loop owns that health
marker; successful authenticated frame completion is not a frame-level gap trace.
Finer progress coverage remains under QUAL-08, not a deadlock-immunity claim.

Two subsequent dev.60 maintenance cycles passed in **39.733 seconds**, with protected
opens **1,813 / 1,626 ms**, authenticated Wi-Fi status, protected close and unchanged
boot nonce/image/setup. A separate final automatic App read chain passed in
**14.816 seconds**: opening **7,744 ms**, resume **2,795 ms**, exact dev.60/health 2,
revision/digest/settings and deinitialized Wi-Fi driver. Its hardware sample showed
internal free/largest **122,835 / 31,744 bytes**.

Final profile, gauge-association and presentation preference comparisons against
the original pre-session backup were byte-for-byte unchanged. Normal eGauge was
reopened, the logger closed and original phone wake settings restored. There was
no USB firmware flash, configuration resend, bond erasure or ECU operation.
No new physical dev.60 pixel/touch confirmation was collected. Controlled serial
resets do not qualify rail-power loss, trial-boot rollback, configuration-commit
interruption, connected ECM/TCM load, rates, long retention or thermal behavior.
One rejected owner preflight remains an unresolved observation despite successful
fresh-read recovery; these results do not establish a fleet success rate or OOM
immunity. No public capability or default-off clearing boundary was promoted.


## 2026-10-19 owner-reported display freeze

Owner reported a blank, nonresponsive gauge with only “Demo” visible; manual
reboot recovered operation. Owner subsequently confirmed no vehicle adapters were
connected and that the problem was noticed immediately before reboot. Time to
failure, showcase state, phone connection and specific hold/swipe response remain
unknown. No failure-time capture, running-image identity or heap measurement was
collected. The last recorded installed baseline is dev.60, not a freshly verified
incident identity.

Source inspection: the App-started showcase hides its footer in `render_status`.
A distinct `demo_lbl` remains for simulated runtime/alert provenance. It is created
visible before the first status pass, so the word alone neither proves showcase
playback nor identifies the failure cause. UI progress is recorded at entry to the
LVGL timer callback, not proof of a completed LCD flush. No evidence establishes
OOM, a watchdog event, stalled flush, or a lock cycle as the cause.

A 35-second USB serial read after the report retained a dev.60 power-on log,
successful UI/display/touch initialization, a phone link connection and both
adapter workers timing out and retrying. No panic or allocation-failure message
was present in that capture. It does not contain the original failure and does
not prove physical UI responsiveness or exclude an earlier memory failure.
The private capture is retained under `artifacts/display-freeze/`.

Prioritize normal operation with configured adapters absent for reproduction; the
report does not support treating simultaneous active ECM/TCM traffic as a required
trigger. Adapter retry activity remains possible and is not established as causal.

Next evidence: operating mode and uptime before failure, touch/hold response,
phone connection response, serial UI/application progress and memory/stack margins,
and display-flush completion. Capture before reboot when practical, without
erasing bonds/setup or forcing an update. Compare normal absent-adapter, connected
adapter and showcase paths only after reproducing or narrowing the incident.
Removing the label would not resolve an unresponsive display.
