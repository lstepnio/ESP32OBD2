# M1 transport increment

Status: implementation branch `feat/m1-transport-core`, 2026-09-25. Response assembly, display freshness, and two independently owned central-link contexts are implemented. M1 remains incomplete.

## Implemented

- A bounded ELM ASCII assembler receives all bytes in a GATT notification chain and waits for the `>` prompt before decoding. It tolerates split, coalesced and echoed Mode 01 responses.
- Invalid hex, adapter errors, absent data, overflow and multiple matching replies reject the reading. The current decoder accepts only headerless Mode 01 replies from one responder. Header-bearing CAN lines, Mode 03/07/0A diagnostics and multi-frame payloads require a subsequent parser increment.
- A response timeout keeps the old transaction outstanding until its prompt arrives. Five consecutive waits without that prompt terminate the BLE link for a clean reconnect. Queue overflow also terminates the link. The callback context remains allocated while asynchronous NimBLE events can arrive.
- A displayed numeric value expires after 1.5 seconds without a new reading. Switching the selected PID clears the previous value immediately.
- Service discovery now checks that both TX and RX characteristic handles were found before it reports success.
- ECM and TCM have separate stable BLE connection handles, GATT characteristic handles, RX queues, transaction assemblers, connection generations, and polling tasks. One scan mutex serializes discovery on the shared radio.
- A read-only phone discovery endpoint advertises the planned service UUID and name `eGauge`. Its public capability characteristic reports experimental protocol 0 and explicitly disables configuration writes and OTA. It contains no VIN, adapter address, telemetry, or owner data.
- An ECM MAC may be set for deterministic binding; a blank ECM MAC keeps first-compatible discovery, excluding the configured TCM address. TCM requires an explicit, different MAC and otherwise stays disabled. The TCM task currently holds the link for coexistence measurement and sends no PID requests until a TCM catalog/profile is installed.

## Evidence and open limits

The ESP-IDF 5.4.1 firmware build and host parser fixtures with address and undefined-behavior sanitizers pass locally. CI runs both gates. The branch was flashed over USB to `/dev/cu.usbmodem5C931582021` on 2026-09-25. Esptool verified both written images. Serial output showed the UI handling a PID touch event and service discovery retries. After the two-context refactor, the default empty-MAC firmware was flashed again and an ECM discovery retry was observed without a crash. A matching `18F0` adapter was not observed during these checks, so live readings, second-link coexistence and reconnect behavior remain unverified. The read-only phone peripheral service was observed from macOS using Bleak 3.0.2: the scanner found the advertised UUID, connected, and read valid capability JSON while ECM discovery was running. Two consecutive probe runs after the latest flash confirmed that advertising resumes after the client disconnects. Android client behavior and a phone alongside two adapters remain unverified. GATT discovery still assumes the notification CCCD is immediately after the RX value handle. Header-bearing CAN replies, per-responder attribution, TCM PID polling, descriptor discovery, and the authenticated companion control service are next.

The round display uses `...` for a stale or unavailable value. A later UI increment will distinguish stale, searching, and unavailable states visually while preserving the no-old-number rule.

## Hardware validation sequence

1. Flash this branch over USB and confirm the display and touch still start. Default empty-MAC image has been flashed; startup serial is observed, while a fresh physical touch check remains useful.
2. With one known `18F0` adapter, check that RPM and coolant update, disappear after link loss, and recover after reconnect. Record the adapter make, firmware and GATT map.
3. Capture response fragments, timeouts and BLE disconnect events, with any VIN or identifying vehicle data removed.
4. Set `EGAUGE_TCM_ADAPTER_MAC` to the second adapter's observed BLE address in a local build. With both adapters present, measure two links while LVGL renders. Phone peripheral coexistence requires the later companion service. If coexistence or freshness is inadequate, record the radio and memory measurements and choose the documented fallback in [multi-adapter architecture](../architecture/multi-adapter.md).

No DTC clearing or vehicle command is added by this increment.
