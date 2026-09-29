# Gauge touch and firmware performance review

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
| Touch driver | The pinned LVGL port already uses the CST816S interrupt to wake its task | Keep this working event-driven path; faster polling would add work without removing the identified queue delays. |
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

Primary references: [LVGL 9.2 display buffers](https://lvgl.io/docs/open/9.2/porting/display), [ESP-IDF performance guidance](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-guides/performance/speed.html), and the pinned component sources under `firmware/gauge/managed_components/` after dependency resolution.
