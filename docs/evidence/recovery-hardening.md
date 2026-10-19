# Recovery audit and qualification

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

Historical audit begun for development dev.44; dated physical evidence follows.
Installed firmware and physical results belong in [current state](../current-state.md).
The standing implementation policy is [interaction recovery](../architecture/interaction-recovery.md).

## Audit findings and changes

| Boundary | Finding | Change and evidence needed |
| --- | --- | --- |
| Android Wi-Fi | Blocking stream IO was on the IO dispatcher but cancellation did not directly close the socket. Per-read timeouts restart after each fragment; a trickle peer could prolong the lease. Connect exceptions could leave a socket open. | Shared `socketIo` closes on cancellation/failure, joins its child, and applies one total budget: connect 12 s, exchange 20 s. Successful exchanges retain the socket. Real JVM socket fixtures cover silent cancellation, trickle deadline and reuse/failure. |
| Android Wi-Fi callbacks | Network loss could occur before the connecting socket was published; callbacks from a retired request could affect a later one. | Publish before connect, reject retired callbacks, publish the selected network before resuming, verify it still exists after connect, and unregister a request that finishes registering after cancellation. Requires real Android network-loss qualification. |
| Foreground reads | A healthy or backed-off schedule could delay settings/source refresh after resume. The outer timeout handler could classify caller cancellation as a retry. | Reset each independent schedule on foreground entry; preserve separate failure scopes. Check caller activity before treating a timeout as retryable. Native resume tests require observations newer than the actual resume time. |
| Firmware socket reads/writes | Each `recv`/`send` could wait 65 s, renewed per fragment. Old clients could occupy the only server across network close/reopen. Accept relied on a receive timeout instead of an explicit readiness wait. | One 20 s fragment budget and 250 ms syscall slices. Session generations invalidate old clients across station loss and rapid reopen. `select` bounds listener readiness; only server task closes descriptors. Command waits/batches check session generation. Real production-C socket fixtures cover silence, trickle, send backpressure, peer closure, cancellation and wraparound. |
| Firmware OTA reservation | An interrupted Wi-Fi transfer could retain the transfer gate for the ten-minute inactivity timeout, pausing adapter polling. | Queue entries and transfer state carry the Wi-Fi generation. Worker rejects retired requests and releases an unactivated reservation after session loss; authenticated OTA socket loss retires its generation too. Activating phase 4 and independent BLE transfers are preserved. Host phase/session-policy tests plus new physical interruption checks required. Matching authenticated OTA departure also closes the maintenance network to clear its separate adapter pause; late older-session signals are ignored. |
| Firmware Wi-Fi state | Startup failure called BLE pause cleanup while holding Wi-Fi state lock. | Move cleanup outside the state lock. Keep published snapshots short and subsystem calls separate. |

Firmware retains a 65 s idle allowance before the first frame byte, allowing the
existing debug activation pause of up to 60 s. Once the first byte arrives, all
remaining header, tag and payload fragments share 20 s. Responses have their own
20 s send budget. A closed/rotated session is checked every syscall slice, rather
than waiting for the idle allowance. Flash command completion retains its existing
60 s worker budget; an already admitted write may finish after transport loss.
Readback must resolve its outcome. Unactivated Wi-Fi transfer reservations release
on the OTA worker's next iteration (normally within its one-second queue wait,
after any in-flight flash work). Phase 4 cannot be automatically abandoned.
Retired Wi-Fi requests are rejected before processing. Only an authenticated OTA
participant's socket close invalidates its generation; read-only and rejected
security probes cannot cancel a transfer. BLE-only transfers retain their existing
inactivity timeout independently. The Wi-Fi worker also closes the departed OTA
participant's matching maintenance session, clearing the separate BLE adapter
pause. A late departure signal cannot close a newer session with a different ID.
Both holds must release before adapter polling resumes; a transfer-gate-only fix
would still leave polling paused. Pre-first-frame abandonment, where no OTA
participant exists, retains explicit App cleanup and the session expiry fallback
and needs its own physical cancellation/consent-loss qualification. These bounds describe code, not measured ESP32
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

Use the [OTA matrix](../development/ota-recovery-matrix.md) for exact interruption procedures and
[dual-adapter matrix](../development/dual-adapter-recovery.md) for two-source cases. Serial resets
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

### Read-only Pixel recovery command

After installing the built debug and instrumentation APKs with data-preserving
replacement, enable the awake helper, then run the opt-in native harness:

```sh
adb shell am instrument -w -r \
  -e class com.lstepnio.egauge.AutomaticConnectionJourneyTest \
  -e allowGaugeRead true \
  -e toggleBluetooth true \
  -e checkGaugePickerRecovery true \
  -e expectedFirmwareVersion '<exact installed version>' \
  -e expectedFirmwareElf '<verified hosted ELF hash>' \
  com.lstepnio.egauge.test/androidx.test.runner.AndroidJUnitRunner
```

Record the external `automatic-connection/result.txt` and full active document
privately, compare saved preferences before/after, and restore the awake helper.
The harness temporarily toggles phone Bluetooth, restores it in `finally`, never
sends configuration or firmware, and cannot establish physical display/touch or
vehicle-adapter recovery from phone observations alone.

Publication attempt 37654830879 was cancelled before release creation when final
review identified the separate maintenance-network adapter pause. Candidate dev.44
was amended to release both holds; publication requires checks on the amended
commit. No published version or asset was changed.


### Signed dev.44 candidate publication, 2026-10-07

- Final source commit `79965f2a11acc2bd9cc6170aa2243db49148c04d` passed all hosted quality jobs in run 37656161279.
- Owner-authorized protected release run 37657094780 published [dev.44](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.44), catalog generation 26, targeting that exact commit. The earlier cancelled run created no release.
- Independently downloaded catalog/bundle passed both signatures against the App-pinned public key, compatibility, descriptor, size and hash checks. Image: 1,516,720 bytes; SHA-256 `04f943bc97a9cf8917c0b93e16faf1fd8f35fcf68fc97fc4226148155e7583d9`; ELF SHA-256 `325bc9bc2c1606ddb0947138e8214ecbd7bce1d1babec467c4a27f98c4d7a6ce`.
- Publication does not establish installation or physical recovery. Pixel remains disconnected; installed App dev.39 and signed firmware dev.43 are unchanged. App dev.40 installation, App/Wi-Fi dev.44 transfer, exact running readback and physical interruption tests remain pending. Public capabilities remain unchanged.


### Physical Pixel and signed dev.44 qualification, 2026-10-07

- Installed App dev.40 with data-preserving replacement. Read-only test on signed dev.43 passed: opening 9.613 s, resume 3.238 s, Bluetooth off/on recovery 10.645 s and Add-gauge cancellation recovery. Configuration revision 42, full digest, profiles and settings retained.
- First update attempt stopped before firmware transfer on a successful-GATT owner read whose frame shape was rejected; the App incorrectly suggested pairing. The exact rejected frame was not logged, so its identity is not established. Inspection found the supported 56-byte version-10 hardware snapshot missing from the owner-read shape gate.
- Checked the gauge and explicitly retried the signed candidate through normal App/Wi-Fi. Android network selection took 9.882 s; first Wi-Fi response arrived 12.794 s after preflight began; flash preparation took 2.251 s. Warm App showed Update installed after restart. No USB flash or reset was used.
- Protected post-update read confirmed signed dev.44 ELF `325bc9bc2c1606ddb0947138e8214ecbd7bce1d1babec467c4a27f98c4d7a6ce`, OTA health 2 and unchanged revision 42/digest. Opening 7.222 s, resume 2.771 s, Bluetooth off/on recovery 10.973 s and Add-gauge cancellation recovery passed.
- Owner separately confirmed a normal physical gauge page and working swipes after dev.44 installation.
- App dev.41 adds the missing hardware frame shape to the shared owner-read gate and logs GATT status, frame version and length for rejected reads without dumping payloads. Known/unknown/truncated frame fixtures passed; 108 JVM tests, debug/test builds, lint and repository validation passed. Protocol bytes unchanged.
- Installed App dev.41 and reran protected opening/resume plus hardware snapshot followed by a new protected firmware read: passed in 13.690 s overall, opening 6.299 s and resume 2.790 s. Exact dev.44/health/configuration/settings retained. Preferences for presentation, profiles and gauge associations matched the before-session copies byte for byte. Phone wake settings restored.
- These checks do not qualify interrupted OTA reservation/network cleanup, power removal, pre-first-frame abandonment or simultaneous ECM/TCM. Those physical gates remain pending. The owner has one adapter.


### App dev.43 reported connection stall, 2026-10-07

- Pixel logs captured repeated 12-second protected-read timeouts after successful
  service discovery. Later sessions resumed successfully before the instrumentation
  rerun. This is a transient transport stall; its initiating cause is not established.
- Read-only opening/resume qualification passed: protected confirmation in 6.310 s,
  fresh resume/settings in 2.746 s, signed dev.44 identity and OTA health 2, unchanged
  configuration revision 42/digest and display settings. Bluetooth toggling and
  hardware restart were not exercised in this check.
- The normal App's Connections sheet separately confirmed “Your gauge is ready”
  and a retrying transmission adapter. The aggregate “Car reconnecting” indicator
  was not a phone-to-gauge failure at that point. No transport patch, configuration
  send or firmware installation was performed. Phone wake settings were restored.
- Retain the captured failure for a future stalled-link reproduction; a subsequent
  successful readback does not prove the original timeout cause was corrected.
