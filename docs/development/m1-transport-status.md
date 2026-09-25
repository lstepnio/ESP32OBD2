# M1 transport increment

Status: implementation branch `feat/m1-transport-core`, 2026-09-25. This is a narrow response-integrity and display-freshness increment, not completion of M1.

## Implemented

- A bounded ELM ASCII assembler receives all bytes in a GATT notification chain and waits for the `>` prompt before decoding. It tolerates split, coalesced and echoed Mode 01 responses.
- Invalid hex, adapter errors, absent data, overflow and multiple matching replies reject the reading. The current decoder accepts only headerless Mode 01 replies from one responder. Header-bearing CAN lines, Mode 03/07/0A diagnostics and multi-frame payloads require a subsequent parser increment.
- A response timeout keeps the old transaction outstanding until its prompt arrives. Five consecutive waits without that prompt terminate the BLE link for a clean reconnect. Queue overflow also terminates the link. The callback context remains allocated while asynchronous NimBLE events can arrive.
- A displayed numeric value expires after 1.5 seconds without a new reading. Switching the selected PID clears the previous value immediately.
- Service discovery now checks that both TX and RX characteristic handles were found before it reports success.

## Evidence and open limits

The ESP-IDF 5.4.1 firmware build and host parser fixtures with address and undefined-behavior sanitizers pass locally. CI runs both gates. The branch was flashed over USB to `/dev/cu.usbmodem5C931582021` on 2026-09-25. Esptool verified both written images. Serial output showed the UI handling a PID touch event and a BLE service discovery timeout. A matching `18F0` adapter was not observed during this check, so vehicle readings and reconnect behavior remain unverified. The current BLE manager is still a singleton and chooses the first adapter advertising service `18F0`; it cannot represent separate ECM and TCM links or a phone link. It still assumes the notification CCCD is immediately after the RX value handle. Per-responder attribution, explicit adapter selection, robust GATT descriptor discovery and the companion service are next.

The round display uses `...` for a stale or unavailable value. A later UI increment will distinguish stale, searching, and unavailable states visually while preserving the no-old-number rule.

## Hardware validation sequence

1. Flash this branch over USB and confirm the display and touch still start.
2. With one known `18F0` adapter, check that RPM and coolant update, disappear after link loss, and recover after reconnect. Record the adapter make, firmware and GATT map.
3. Capture response fragments, timeouts and BLE disconnect events, with any VIN or identifying vehicle data removed.
4. Measure two adapter links plus a phone peripheral session while LVGL renders. If coexistence or freshness is inadequate, record the radio and memory measurements and choose the documented fallback in [multi-adapter architecture](../architecture/multi-adapter.md).

No DTC clearing or vehicle command is added by this increment.
