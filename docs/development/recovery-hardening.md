# Recovery audit and qualification

Updated 2026-10-07. Source candidate firmware dev.44 and Android dev.40.
Installed firmware and physical results belong in [current state](../current-state.md).
The standing implementation policy is [interaction recovery](../architecture/interaction-recovery.md).

## Audit findings and changes

| Boundary | Finding | Change and evidence needed |
| --- | --- | --- |
| Android Wi-Fi | Blocking stream IO was on the IO dispatcher but cancellation did not directly close the socket. Per-read timeouts restart after each fragment; a trickle peer could prolong the lease. Connect exceptions could leave a socket open. | Shared `socketIo` closes on cancellation/failure, joins its child, and applies one total budget: connect 12 s, exchange 20 s. Successful exchanges retain the socket. Real JVM socket fixtures cover silent cancellation, trickle deadline and reuse/failure. |
| Android Wi-Fi callbacks | Network loss could occur before the connecting socket was published; callbacks from a retired request could affect a later one. | Publish before connect, reject retired callbacks, publish the selected network before resuming, verify it still exists after connect. Requires real Android network-loss qualification. |
| Foreground reads | A healthy or backed-off schedule could delay settings/source refresh after resume. The outer timeout handler could classify caller cancellation as a retry. | Reset each independent schedule on foreground entry; preserve separate failure scopes. Check caller activity before treating a timeout as retryable. Native resume tests require observations newer than the actual resume time. |
| Firmware socket reads/writes | Each `recv`/`send` could wait 65 s, renewed per fragment. Old clients could occupy the only server across network close/reopen. Accept relied on a receive timeout instead of an explicit readiness wait. | One 20 s fragment budget and 250 ms syscall slices. Session generations invalidate old clients across station loss and rapid reopen. `select` bounds listener readiness; only server task closes descriptors. Command waits/batches check session generation. Real production-C socket fixtures cover silence, trickle, send backpressure, peer closure, cancellation and wraparound. |
| Firmware Wi-Fi state | Startup failure called BLE pause cleanup while holding Wi-Fi state lock. | Move cleanup outside the state lock. Keep published snapshots short and subsystem calls separate. |

Firmware retains a 65 s idle allowance before the first frame byte, allowing the
existing debug activation pause of up to 60 s. Once the first byte arrives, all
remaining header, tag and payload fragments share 20 s. Responses have their own
20 s send budget. A closed/rotated session is checked every syscall slice, rather
than waiting for the idle allowance. Flash command completion retains its existing
60 s worker budget; an already admitted write may finish after transport loss.
Readback must resolve its outcome. These bounds describe code, not measured ESP32
recovery latency under every radio/flash workload.

## Existing architecture retained

- `ForegroundConnectionController` and `OperationCoordinator`: automatic reads
  yield to explicit user work and wait for transport cleanup; backgrounding stops
  reads, not an in-progress user transaction.
- Settings, ECM and TCM retry scopes remain independent. Successful reads reset
  backoff; failed reads preserve last checked values with honest freshness.
- OBD workers already use independent source generations, bounded response waits,
  overflow rejection, unavailable rendering, 500 ms to 8 s reconnect backoff,
  and poll scheduling without catch-up bursts. Coexistence remains unqualified.
- Config/OTA workers retain exclusive flash ownership, cached status snapshots,
  atomic configuration slots, signed image verification and boot-health confirmation.
  Worker-owned locks around flash are not acquired by public status readers.
- OTA recovery journal and protected readback resolve running image identity.
  No new implicit setup, pairing, vehicle-control or firmware writes are introduced.
- No protocol bytes, public capability flags, normal UI controls or dialogs change.

## Verification order

| Stage | Cases | Pass criteria |
| --- | --- | --- |
| Offline regression | Socket silence/trickle/backpressure; cancel; fragment success; peer close; expiry and wraparound; independent schedule reset; existing parser/config/storage negatives | Deadline terminates; child/descriptors released; successful socket reusable; no synthetic success; existing fixtures pass |
| Pixel + current gauge | Open, background/resume, Bluetooth off/on, cancel Add gauge; no Connect/Refresh tap | Fresh authenticated running/config/settings observations, same gauge/profile/hash/preferences, automatic retry, no writes |
| Signed candidate App/Wi-Fi | Install dev.44, verify hosted identity and health; firmware display/swipes | Warm App confirms actual candidate; setup/settings retained; owner confirms physical behavior |
| Transport loss | Close session while client silent or sending partial header; immediately reopen; cancel Android pending IO; repeat | Old client cannot block/reuse new session; prompt cleanup; no leaked sockets/callbacks; fresh protected reads succeed |
| Transfer interruption | Early/middle/late, verified-before-activation, trial boot; app termination and Wi-Fi loss | Observed valid running image/config; durable outcome reconciled automatically; no blind replay or percentage-based success |
| Vehicle and power | One source missing; independent ECM/TCM unplug/reconnect; brownout/rail power during save/update; prolonged soak | Healthy sibling continues; stale readings clear; committed state survives or documented rollback; touch responsive; resource usage stabilizes |

Use the [OTA matrix](ota-recovery-matrix.md) for exact interruption procedures and
[dual-adapter matrix](dual-adapter-recovery.md) for two-source cases. Serial resets
are not rail-power tests. One available adapter cannot qualify simultaneous links.
Signed publication requires explicit owner release authorization; local builds are
not eligible App update artifacts.

## Remaining audit boundaries

This increment does not qualify bond loss/re-pairing, all Android network callback
orders, all Wi-Fi initialization failures, flash stalls, lock contention under load,
trial-window power loss, real brownouts or prolonged dual-radio soak. Initialization
allocation cleanup and lock ordering across other subsystems need targeted fixtures
before wider changes. Do not replace bounded conservative recovery with shorter
arbitrary timeouts or classify an uncertain write as failed without readback.

## Reference guidance

- [Kotlin cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html): cancellation is cooperative; cleanup and operation ownership must be explicit.
- [Kotlin runInterruptible](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-interruptible.html): thread interruption is appropriate for interruptible blocking APIs. Socket stream cancellation here explicitly closes the resource instead.
- [ESP-IDF 5.4.1 lwIP guide](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-guides/lwip.html): supported BSD socket options, `select`, error handling and thread ownership constraints.

## Verification recorded for this increment

- 106 Android JVM tests passed, including real socket read/write cancellation,
  trickle deadline and successful reuse/failure invalidation; schedule reset tests
  preserve independent settings/child backoff. Debug and instrumentation APKs
  built; Android lint passed.
- 74 host tests passed, including production-C socket fixtures under address and
  undefined-behavior sanitizers. ESP-IDF 5.4.1 built source dev.44 successfully.
- Repository schemas/examples/rejection vectors, documentation links and whitespace
  validation passed. Wire formats/public flags and UI styling are unchanged.
- Pixel was detected during the initial audit, then disconnected before the awake
  helper could start. No app installation or native hardware test occurred in this
  increment, and no phone wake settings changed. App dev.40 and firmware dev.44
  remain candidates. Last installed/confirmed pair remains App dev.39 + signed
  firmware dev.43; its earlier observations do not qualify these changes.
- Native recovery harness now requires fresh observations after the actual resume
  time and records resume/Bluetooth recovery durations. Run it with explicit
  read-only opt-in on the paired Pixel before claiming physical automatic recovery.
