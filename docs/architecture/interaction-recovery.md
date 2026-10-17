# Automatic refresh and resilient interactions

These are standing implementation rules for Android and ESP32 changes. Apply them
alongside [firmware ownership](firmware-runtime.md), [multi-adapter design](multi-adapter.md)
and the [quality gates](../development/quality.md). They are requirements, not a
claim that every hardware failure scenario has already been qualified.

## Consumer experience

- Use one `ConnectionStatusWidget` across Gauge, Car, Settings and subordinate pages.
  `LocalConnectionStatus` supplies the shared header slot; pages do not derive their
  own connection truth. The pill opens the connection summary, retaining update notices.
- Keep phone-to-gauge, adapter identity and vehicle/source status separate. A gauge
  being reachable does not prove an adapter or controller is responding. Never
  label stale, simulated, another-profile or another-source evidence as connected.
- Default to one adapter. Show the active source's faults, meaningful codes and
  limitations. Secondary-source placeholders and transport details do not belong in
  the normal single-adapter flow. Category coverage is available on demand; full
  device/protocol data remains in Expert.
- Read settings and saved-adapter/fault status automatically after authenticated
  connection. Ordinary users should not need Read, Check or Refresh to see settings
  or restore a known connection. First-time identity selection, pairing, applying
  setup, editing settings and installing firmware remain explicit actions.
- Foreground polling is cancellable, read-only and uses the existing operation lease.
  A user operation waits for automatic transport cleanup. Backgrounding stops reads;
  it does not cancel a user write. Never retry configuration, ownership changes,
  code clearing or update installation implicitly.
- Poll successful settings and vehicle reads about every 20 seconds. Failed reads
  back off through 2, 5, 10, 20 and 30 seconds; successful reads reset the schedule.
  Reset scope on a new gauge, profile, binding, source or configuration generation.
  Freshness always uses actual successful read time, not scheduled intent.
- Preserve last checked data through temporary loss, label its freshness and disable
  settings edits until current values are read. Settings failures and vehicle failures
  have their own retry schedules and must not turn a successful gauge check into a
  false phone-link failure.

## Durable local changes

Use the coupled `LocalSetupStore` transaction for profiles and gauge assignments.
Serialize writes on IO and publish UI state only after the commit acknowledges
success. An admitted commit finishes before releasing its writer lease; cancellation
while waiting never admits a write. Preserve malformed/newer source documents.
Failed save keeps the prior visible model and blocks setup changes until reopening
and review; rollback of the platform memory map is best-effort, not a guarantee
against storage hardware failure. Journal cleanup failure must not reverse a
protected, verified gauge outcome. Read-only page navigation needs no durable write.

Use typed pairing/read errors and cancellation-preserving `suspendResult` for optional
suspending work. Limit noncancellable cleanup to an explicit deadline. Reconcile
unknown/corrupt update evidence only from authenticated healthy running identity.

## Recovery implementation patterns

- Give each external operation one monotonic budget. A fragmented response, a retry
  or a cleanup step must not silently renew that budget. Use a single clock sample
  for remaining time and explicit bounded cleanup sub-budgets.
- Cancellation must reach the underlying resource. Moving blocking work to an IO
  thread does not make it cancellable: close its socket to unblock streams, join the
  child, then release the operation lease. `SocketIo.kt` is the Android pattern;
  firmware `wifi_bulk_io.c` uses short syscall slices with one total IO budget.
- A single task owns firmware socket close. Network stop, expiry and reopen
  invalidate a session generation; old clients and queued batches check it before
  further admission. Retired Android callbacks cannot modify the current request.
- Resume immediately schedules settings and each configured source for readback.
  Success resets the relevant backoff. Scope changes invalidate old completions;
  repeated foreground signals do not create duplicate workers.
- Optimize the successful path with existing connection reuse and bounded batches.
  Do not lower deadlines without measurements of healthy and failure paths. Record
  time to fresh authenticated state and time to released resources separately.
- Keep routine loss/retry in the universal status widget, without recurring dialogs
  or toasts. Explain a persistent failure once when an explicit user action really
  is needed. Automatic discovery and protected reads can recover silently;
  ambiguous writes must retain their outcome until identity/readback resolves it.
- Log diagnostic causes and timings for development without exposing session keys,
  private adapter identities or raw transport errors in normal user messages.

See [recovery audit and qualification](../evidence/recovery-hardening.md) for the
current application of these patterns and the remaining physical gates.

## Android read scheduling and hosted HTTP

Gauge health, settings and each adapter have independent `VehiclePollSchedule`
instances. Healthy reads run at 20 seconds; scope changes and foreground resume
make checks due. Adapter failure ticks do not repeat a still-current protected
gauge check. Nominal 2/5/10/20/30-second retries receive 80..100% jitter, retain a
one-second minimum and cap at 30 seconds. User reads still preempt and await
automatic cleanup before acquiring the shared lease. An unexpected platform
exception records a failure and backs off instead of terminating the foreground loop.

Protected `GaugeConfigTransferClient` sessions retire discovery/read callbacks before
cleanup. `closeGattSession` requests disconnect and waits for the platform callback
for at most 1.5 seconds before closing the registered client, including cancellation.
The shared operation lease stays held through that cleanup. The initial owner read
can retry authentication/encryption/resource pressure and Android GATT error 133
at most four times; every successful handshake still requires a successful protected
status frame. Only the existing one-time authenticated status-selector reset is
permitted. Transaction commands, failed writes and uncertain mutations are never
replayed by this handshake policy. Other BLE client paths retain their current
cleanup and need separate qualification; this is not an app-wide disconnect guarantee.

`resourceIo` closes blocking resources on cancellation and awaits the IO child.
`socketIo` and `boundedHttpDownload` share it. Hosted responses have a 45-second
total budget plus byte/connect/read bounds; the whole catalog search has a
120-second budget. Candidate iteration rethrows cancellation. Manual checks and
downloads mark busy before launch and cancel/join the automatic HTTP job before
handover. Online failures do not invalidate an independently checked gauge.
Signatures, compatibility, generation/conflict protection and reviewed installation
remain mandatory. Background HTTP failure uses the existing 15-minute retry;
a successful check waits six hours. No failed mutation is automatically replayed.

[Product optimization evidence](../evidence/product-optimization.md) separates
unit/HTTP fixtures, rendered UI and physical owner readback from hardware recovery.

## Multiple adapters

Each required link has a stable ID, source title and independent status. The pill's
aggregate is connected only when the gauge and **every configured required link**
are current and ready. A healthy ECM must not conceal a lost TCM. Zero required
links, example links and incomplete setup cannot produce all-connected status.
One lost source must not stop retries, data or alerts for another healthy source.

`ConnectionLinkUi` and the widget accept multiple links. Vehicles may have an
optional transmission child in local storage; current firmware readback exposes
one or two selected sources through the gated development path. The app supplies
independent links, read schedules and failure state; one healthy adapter never makes
the whole connection pill healthy when its required sibling is unavailable. See
[vehicle connections](vehicle-connections.md) for the hierarchy and per-gauge contexts.
Do not fabricate a second link or advertise simultaneous adapters from UI support.
Complete source-specific firmware snapshots, bindings and coexistence validation
before extending the projection or promoting `maxAdapterLinks` above one.

## ESP32 concurrency and recovery

- Keep GAP/GATT callbacks and LVGL/touch paths bounded. Validate and copy/enqueue;
  do not wait for network replies, scan completion, flash writes or JSON processing.
- NimBLE GATT read callbacks append the complete immutable characteristic value.
  The host applies the ATT blob offset; never subtract it in both layers. Test long
  responses at the minimum MTU and the installed controller MTU, including exact-end
  and invalid offsets and allocation failure. Application document offsets are separate.
- Published snapshots use short copy-only critical sections or nonblocking lock
  attempts. Contention returns a retryable failure, never an empty healthy packet.
  A critical section contains no flash, network, blocking queue, logging or other
  subsystem lock acquisition.
- Serialize writers under an explicit owner. Settings readers observe the last
  committed snapshot while persistence runs; failed commits leave it unchanged.
  Bound writer lock acquisition and reject stale revisions before writing.
- Every external response wait has a monotonic deadline. Calculate remaining time
  from one clock observation; test exact expiry and tick rollover. Repeated queue
  draining has an explicit item budget, even when a producer continuously refills it.
- On silence, RX overflow, malformed data or generation change, fail closed, expire
  freshness and resynchronize at the prompt or reconnect. Never attribute late
  bytes to the next request, source or profile. Queue admission is bounded and
  reports saturation. Recovery must eventually release locks, handles and ownership.
- Use bounded reconnect backoff. UI and touch continue independently of adapter
  failures. Update/config workers own flash operations and transfer exclusion;
  status reads must not wait behind those workers. An uncertain write requires
  readback and identity proof rather than blind replay or a success message.
- Review every new `portMAX_DELAY`, nested lock and retry loop. An idle worker waiting
  for its own queue can wait indefinitely if it holds no other resource and has no
  shutdown obligation. An external-response wait or callback cannot.

## Required failure evidence

For changed boundaries, test silence, late replies, disconnect/reconnect, malformed
or fragmented responses, queue/lock contention, background/resume, user-write
preemption, update interruption and stale/wrong-source data as applicable. Include
failed persistence, revision conflict, deadline expiry and tick rollover when those
paths change. Multiple-link work additionally tests one adapter failing while the
other remains healthy. Record resource recovery and touch responsiveness in hardware
soaks. A passing host test or build does not establish physical liveness or immunity
to every possible deadlock.

## Evidence for this change

- Android unit tests cover backoff/reset, freshness, saved identity matching and a
  mixed ECM/TCM aggregate. Native UI tests cover the shared widget, concise Car and
  settings without a manual read tap.
- [Car example](../design/car-connections/car-example.png) and
  [Settings example](../design/car-connections/settings-example.png) were rendered
  on an isolated emulator with in-memory data. These are simulated native examples,
  not Pixel observations or live vehicle results.
- Production C fault-injection tests cover diagnostics read contention, display read
  during commit, failed commit, stale revision, writer contention and clock expiry.
- Physical foreground/background, adapter-loss and settings persistence checks with
  this exact build, flood testing and prolonged coexistence soak remain required.

## Firmware health observation

Extend `worker_health` and the existing minute health log, rather than creating
unbounded diagnostic queues. Record owner-loop progress, admission drops and largest
allocatable blocks alongside free memory. Progress during missing-adapter retry is
healthy owner behavior. Measurements do not establish deadlock immunity or justify
reset loops; use them to qualify failure/soak scenarios on actual hardware.

## Wi-Fi maintenance memory budget

The one-phone maintenance AP shares internal RAM with BLE, display and workers.
Use the board's PSRAM-first Wi-Fi/LwIP allocator for eligible buffers, retain bounded
DMA buffers internally, and keep RX Block Ack window no larger than static RX count.
Do not inherit desktop-like throughput defaults without measuring startup headroom.
The current board budget is two static RX/TX, eight dynamic RX/cached TX and six
short management buffers. Check internal total and largest free block after driver
initialization, before AP startup; defer startup through the existing failed-status
and adapter-resume path when below the policy floors. These floors are conservative
admission checks, not a reservation or a vendor crash guarantee. The maintenance
worker retires its AP driver, handlers and DMA buffers on close or failed startup,
then resumes adapter workers. Keep the shared event loop/netif runtime; use checked
netif creation/attach instead of the SDK convenience constructor that asserts on
allocation failure. Record physical
startup, upload rate, post-start minimum/largest heap and repeated close/open before
calling the budget qualified. See [startup failure evidence](../evidence/gatt-long-read-recovery.md#wi-fi-installation-blocker).

Do not silently change an authorized Wi-Fi update into a Bluetooth/USB repair.
Development Bluetooth recovery requires explicit owner authorization and exact
signed bundle/version/ELF, then uses the existing serialized update owner, durable
journal and healthy image confirmation. Keep ordinary UI on the normal Wi-Fi path.


## Fresh pairing and build security

NimBLE's compile-time security level affects both peripheral phone pairing and
central adapter notifications. Keep `CONFIG_BT_NIMBLE_SM_LVL=0`: level 1 rejects
fresh pairing, while levels >=2 discard the Vgate's unencrypted notifications.
The neutral setting delegates phone security to authenticated GATT attributes,
MITM/bonding/Secure Connections requests and the encrypted/authenticated/bonded
saved-owner identity gate. Secure Connections stays enabled and legacy pairing stays
disabled. Do not weaken those per-service checks to accommodate adapter transports.
`ble_security_policy.h` rejects conflicting build configurations; CI compiles the
actual policy against checked-in and invalid settings. Keep the existing SC-only
runtime branch workaround for ESP-IDF 5.4.1 independently of the global level.

Existing-bond reconnect, protected reads and OTA do not qualify fresh pairing.
Record first pairing and absent/mismatched owner/bond recovery separately, then
verify the durable owner after restart. Never silently recreate ownership or erase
bonds from routine retry. An absent owner record is an unresolved persistence or
reset event until supported by evidence; do not infer its cause from a pairing screen.
