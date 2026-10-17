# ESP32 memory audit and prepared hardening

> Historical evidence. Current support is in [current state](../current-state.md);
> remaining qualification belongs in [the backlog](../backlog.md). This report
> records this session and is not a replacement for the maintained memory policy.

Date: 2026-10-16. Firmware dev.55 is the installed baseline; dev.56 is the prepared
candidate. This report distinguishes source/link measurements, host fault injection,
serial baseline observations and future physical qualification.

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
Partial driver startup and close retain checked deinitialization/netif cleanup.
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

The candidate is not installed or physically qualified by these results. Publication
requires separate owner authorization and green CI. The [qualification runbook](../development/memory-qualification.md)
owns the next repeated AP lifecycle, signed full upload, memory/stack measurements,
physical display/touch and loaded dual-source soak. Public capability flags remain
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
confirmed normal physical pages and working swipes. The 30-cycle maintenance test
then started with private serial capture; its final result is not inferred here.

The incoming transfer was executed by the previous dev.55 runtime. To exercise
full reception/flash/signature verification while the optimized PSRAM layout is
running, a **version-only dev.57** candidate is prepared. It changes only firmware
version identity, preserves the same memory implementation and requires separate
release authorization. Reinstalling/bypassing the identical-image guard is not a
qualification method.
