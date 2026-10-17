# Memory and maintenance qualification

Use this after changes to memory placement, stacks, radio buffers, graphics or
maintenance lifecycle. A successful link does not establish runtime stability.
Preserve owner, bonds, vehicle dashboard and display settings. Normal installation
uses a separately authorized signed App/Wi-Fi development release.

## Offline gates

1. Build with pinned ESP-IDF 5.4.1. The post-link memory guard must pass. Retain its
   JSON section report and compare against the previous candidate.
2. Run `python3 -m unittest discover -s tools/tests -p 'test_firmware_memory.py'`,
   `test_wifi_driver_lifecycle.py`, `test_obd_worker_health.py` and the existing core
   policy fixtures. These inject queue/buffer/task and SDK lifecycle failures,
   exercise snapshot expiry/wrap, and verify hook side effects and memory budgets.
3. Build/run the production LVGL host fixture. Compile the scoped Android journey
   APK. These checks do not prove physical PSRAM/cache or display behavior.

## Paired bench session

Start/stop the debug-awake helper with the selected Pixel serial. Keep logs and
preference backups under ignored `artifacts/`; never publish device identifiers,
Wi-Fi credentials, keys or raw vehicle captures. Serial capture is read-only.

1. Verify exact installed signed version, ELF, health 2, saved configuration
   revision/digest, settings and phone profiles. Record current free/largest and
   minimum internal/PSRAM values. Note whether adapters are actually connected.
2. Run `WifiMaintenanceJourneyTest` with `allowMaintenanceWifi=true`, exact
   `expectedFirmwareVersion`/`expectedFirmwareElf`, and `maintenanceCycles=30`.
   Its existing two-cycle default remains available for a short check; accepted
   range is 1..30 and the outer budget scales with the requested count. Keep phone
   monitoring off and the App visible with its ordinary automatic polling disabled
   by the isolated fixture. It checks a fresh session, authenticated OTA/config
   Wi-Fi reads, protected close, unchanged boot nonce and unchanged saved state.
3. Capture `memory` lifecycle logs throughout. Compare idle memory after the first
   warm-up cycle to later closes at comparable App/radio states. Check for allocation
   failures, a shrinking largest block, cumulative unreleased memory and gaps/drops.
   Investigate a sustained drop exceeding 2 KiB after warm-up instead of accepting
   it as normal. Variable connection allocations must be compared at the same phase.
4. Run a full hosted signed upload of a different authorized image using
   `WifiFirmwareJourneyTest`; exact image, health, alternate slot, cleared journal
   and setup preservation must pass. A network-open test alone is insufficient.
5. During active AP/transfer, require at least 32 KiB internal free and an 8 KiB
   largest internal 8-bit block in captured samples, no ordinary allocation failures,
   no reboot and continued essential task progress. Check sampled worker stack
   margins above 512 bytes, including signature verification and rendering. A failed
   margin requires investigation or code/stack correction, not hiding the metric.
6. Obtain owner physical normal-page/swipe confirmation. Exercise the 21-frame
   display showcase with metric/imperial/rotation cases while the new arena is in
   PSRAM; software screenshots are not physical cache/display qualification.

Sampling misses transient lows; allocation failure hooks, heap minima and stack
watermarks complement stage snapshots. The static budget and admission checks do
not reserve RAM or prove immunity to vendor bugs. Do not deliberately exhaust a
customer device's heap or erase its bonds/settings to manufacture negative cases.

## Loaded session

Use the [dual-adapter matrix](dual-adapter-recovery.md), separately recording one-
and two-adapter load plus phone management. Run the two-hour soak, source absence/
return, phone reconnect and maintenance pause/resume cases. Measure per-source
freshness/rates, worker gaps, queue drops, internal/DMA/PSRAM minima and largest
blocks, stack margins and reset reason. Do not generalize bench results to live
three-link coexistence or interrupted OTA. Update current state/backlog only for
the exact qualified gates.
