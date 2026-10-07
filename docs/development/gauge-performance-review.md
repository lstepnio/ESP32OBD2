# Gauge touch and firmware performance review

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

## Scope and observations

The reported delay occurs with the phone app closed. The USB console identified the bench gauge as `0.2.0-dev.24`, ELF prefix `24d277bce`, with configuration revision 17, two pages and no alerts. This is an identity observation, not a measured touch latency.

The review uses the dev.24 firmware as its baseline. No vehicle requests, simulated readings, capability changes, pairing changes or update-protocol changes are part of this work.

## Source audit

| Path | Finding | Change or decision |
| --- | --- | --- |
| Touch dispatch | Clicks waited for the 50 ms UI timer, then entered a second command queue | Normal touch callbacks run directly on the LVGL task. Calibration keeps deferred dispatch because it deletes screen objects. |
| Page selection | Each tap wrote NVS before changing the display. Rapid taps could enqueue the same next-page index while the earlier write was pending. | Change the visible page immediately. Save only the last selected page after 750 ms without another tap. |
| Storage contention | Browsing could write flash while a configuration or firmware transfer was active | Defer local page persistence until the transfer gate is idle. Phone commands retain their existing save and confirmation order. |
| Status rendering | Identical alert and diagnostic summaries reapplied styles and labels every 100 ms | Compare semantic fields and update widgets only on a change. Preserve an active alert while switching pages. |
| Page renderer | Dual-page content could wait for the next 50 ms data timer | Prepare the selected renderer immediately, on the LVGL task. First drawing remains on that task. |
| Logging | Every decoded value and page change generated an info log | Move those messages to debug and restore the application task's normal info level. |
| Compiler | Firmware used `-Og` | Build with `-O2`; keep assertions and the existing CPU, bus clocks and stack allocation. IDF no longer selects its debug-only task wrapper in this configuration. |
| Touch driver | The pinned LVGL port uses the CST816S interrupt to wake its task, but leaves the controller's standby defaults unchanged | Keep event-driven input; dev.26 disables automatic standby and redundant gesture interrupts, with readback verification. |
| Display buffers | One 10-row buffer; a second DMA buffer could overlap drawing and SPI transfer | Defer until heap and frame measurements justify the extra allocation, including during Wi-Fi OTA. |
| Scheduling | LVGL priority 4, application/OBD workers priority 5 | Keep priorities and affinity until measurements demonstrate contention. |

Only the last-viewed-page cursor is saved later. Power loss inside the settling window can restore the previously persisted page. Dashboard configuration, signed-update checks, ownership, and confirmed-running rules are unchanged. Storage failure leaves the selection pending for a bounded retry; a tap arriving during a save cannot be lost.

## Timing and verification

`CONFIG_EGAUGE_UI_PERFORMANCE_LOG` is an optional bench setting, disabled by default. After physical taps, it reports a 30-second summary of click-dispatch-to-render-submission mean/max and LVGL stack headroom. It does not generate input or vehicle data. The measurement excludes finger-down-to-release time, touch-controller detection before the LVGL callback, and final LCD scanout. Several taps before a single rendered frame count as one frame, timed from the earliest pending click.

Source-level validation:

- Host tests with address/undefined-behavior sanitizers cover a 100-tap burst, taps during persistence, failed saves, and millisecond-counter wraparound.
- The ESP-IDF 5.4.1 build validates the pinned LVGL 9.2.2 / port 2.7.2 integration.
- `tools/validate.py` checks schemas, examples, signed decode vectors and documentation links.

Physical acceptance still requires page changes with the phone closed and open, quick successive taps, reboot after the save interval, normal pairing/long-hold behavior, and stable display/stack headroom. An OTA and concurrent vehicle workload are separate performance checks. No latency improvement percentage is claimed from source inspection or compilation.

## Physical bench image, 2026-09-29

The dev.24 running slot was identified from valid OTA metadata as `ota_0` at `0x60000`. A complete 3 MB recovery copy was read before writing. During that read the user observed a black screen and no BLE connection, which is expected in the USB bootloader; the user confirmed that the display returned when dev.24 restarted. The reliable serial rate for this session was 115200 baud.

The application-only dev.25 test image is built from commit `57be6f9` with `CONFIG_EGAUGE_UI_PERFORMANCE_LOG=y` in a separate bench configuration. The tracked default leaves that instrumentation disabled.

| Image identity | Value |
| --- | --- |
| Version | `0.2.0-dev.25` |
| Image size | 1,492,016 bytes |
| Binary SHA-256 | `f89296ccdd06b7ef42b7cd18154826450f8a1f594e82c8c2b0338c01e3e16c21` |
| ELF SHA-256 | `2bbd0fb591d22900aad276707a32614b145c2af0867d2f8cfbf18e008c9998e7` |

The USB write and independent `verify_flash` both matched the image digest. Startup reported dev.25, the matching ELF prefix, configuration revision 17, two pages, no alerts, and successful display/touch initialization. NVS, configuration partitions, bootloader and OTA metadata were not written. This was a USB bench installation, not a signed OTA qualification.

**Physical user observation:** normal display, little or no improvement in touch response with the phone app closed. The first pass has not resolved the reported problem.

**Device instrumentation:** one frame measured 53,794 microseconds from click callback to drawing submission. A later four-frame group averaged 66,268 microseconds, maximum 81,643. LVGL stack low-water was 2,988 bytes; application stack 1,308 bytes, internal heap minimum 81,027 bytes. These are small samples, exclude touch detection and final panel completion, and do not establish an improvement over dev.24.

## Second touch-input experiment: dev.26

The pinned CST816S driver resets and reads the controller but leaves its input registers at their defaults. Waveshare documents automatic standby after two seconds and a slower scan rate in standby. This is a hypothesis for idle-to-touch delay, not an established root cause.

The BSP now sets and reads back `DisAutoSleep` (`0xFE`) to `1`, and `IrqCtl` (`0xFA`) to `0x60` for touch/state-change interrupts. LVGL still handles short and long presses, with no page change on finger-down. Startup logs record original and applied register values. Tuning failure logs a warning and does not prevent display/BLE startup. Driver dependencies, display buffers, clocks and renderer settings are unchanged from dev.25.

This trades controller standby savings for dynamic scanning while powered (datasheet typical dynamic current 1.6 mA versus 6 microamps in standby). Future device sleep support must explicitly revisit this choice. Bench instrumentation adds recognized press/release/click/hold counts, without per-event logging or artificial input.

The bench image was built from `f33c16b` with performance logging enabled. Its 1,492,528-byte binary SHA-256 is `923fe2d329139ded5e20a0270aab09add93b9fb1883e47a7b3dbaf5c3e849950`; ELF SHA-256 is `26af65461f004e7c8d5757fd84e334b7bfe2af0215fd77d08cc5721a12a7d226`. Application-only USB write and independent flash verification matched. Startup reported dev.26, the matching ELF, retained revision 17/two pages, and successful display/touch initialization.

**Hardware register observation:** `0xFE` changed from `0x00` to `0x01`; `0xFA` changed from `0x70` to `0x60`. This board already enabled touch/change interrupts, so waiting exclusively for completed hardware gestures was not its problem. The second setting removes additional gesture interrupts; disabled standby is the main input-path change under evaluation. Neither successful initialization nor register readback proves normal visible display or improved physical touch response.

**Physical user observation:** after testing a tap following five seconds idle and several quick taps with the phone app closed, the user reported "Normal display, touch is better." This supports keeping the second change; it does not quantify end-to-end latency or independently isolate the two register changes.

**Device instrumentation:** five recognized presses, five releases, five clicks and five rendered frames; no holds in this sample. Click-to-render-submission averaged 65,571 microseconds, maximum 84,135, with 2,988 bytes LVGL stack low-water. Drawing time is similar to dev.25 despite better perceived response, which points toward touch detection as the useful improvement. The controller-to-finger timing was not measured directly and there is no claimed percentage reduction.

Both default and timing-enabled dev.26 ESP-IDF builds passed, as did repository validation and optimized sanitizer host fixtures. No simulated touch or vehicle data was used. Long holds, phone-open contention, Wi-Fi OTA memory headroom, sustained vehicle load and broader device variants remain unqualified by this bench check. The installed image retains timing instrumentation for further observation.

Primary touch references: [CST816S register description](https://files.waveshare.com/wiki/common/CST816S_register_declaration.pdf), [CST816S datasheet](https://files.waveshare.com/wiki/common/CST816S_Datasheet_EN.pdf).

Primary references: [LVGL 9.2 display buffers](https://lvgl.io/docs/open/9.2/porting/display), [ESP-IDF performance guidance](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-guides/performance/speed.html), and the pinned component sources under `firmware/gauge/managed_components/` after dependency resolution.
