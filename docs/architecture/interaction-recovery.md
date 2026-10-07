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

See [recovery audit and qualification](../development/recovery-hardening.md) for the
current application of these patterns and the remaining physical gates.

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
