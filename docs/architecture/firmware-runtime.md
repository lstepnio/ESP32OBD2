# Firmware runtime ownership and recovery

Updated 2026-10-27. Implemented development architecture. Public capability flags remain gated by hardware evidence.

## Task and resource inventory

| Owner | Priority | Stack | Bounded input | Blocking boundary |
| --- | ---: | ---: | --- | --- |
| ESP main application owner | IDF default | 3,584 bytes | phone command queue: 4 | Applies selection and rotation commands. Persistence is serialized at the application boundary. |
| `app_tick` | 5 | 4,096 bytes | alert sample queue: 16 | Ticks companion expiry, alert freshness, and UI snapshots every 100 ms. It does not wait for an adapter transaction. |
| `obd_task` | 5 | 4,096 bytes | one ELM request, RX bytes: 512 | Owns ECM connect, initialization, request/response, and bounded reconnect backoff. |
| `tcm_link` | 5 | 4,096 bytes | one ELM request, RX bytes: 512 | Owns the optional second adapter lifecycle. TCM polling remains unavailable without a configured adapter and vehicle contract. |
| `cfg_transfer` | 4 | 6,144 bytes | request queue: 4 | Owns config erase, write, hash, validation, and commit. Publishes a short immutable status snapshot. |
| `ota_transfer` | 4 | 6,144 bytes | request queue: 4 | Owns inactive-slot writes, verification, signature checks, and activation. Publishes a short immutable status snapshot. |
| `owner_store` | 4 | 3,072 bytes | owner-save queue: 1 | Moves NVS bond-owner persistence outside the NimBLE callback. Authorization remains closed until persistence succeeds. |
| `boot_health` | 5 | 3,072 bytes | progress counter | Confirms configuration and OTA trials only after essential startup and at least five seconds of application tick progress. |
| `wifi_control` | 4 | 5,120 bytes | owner command queue: 2 | Owns AP lifecycle, checked initialization and driver/netif cleanup. |
| `wifi_bulk` | 4 | 8,192 bytes | one client, two 8,448-byte PSRAM frame buffers | Owns bounded socket IO; invalidated generations cannot write to a later transfer. |
| NimBLE host | 5 | 4,096 bytes | manager result queue: 1 per link | GAP/GATT callbacks validate, copy, enqueue, or copy a published snapshot. No JSON validation or flash operation runs in an application GATT callback. |
| LVGL owner | component managed | component configured | latest readings: 32, touch: 4, pairing/alert/diagnostics: 1 each | Owns widgets and touch detection. Touch produces an application command. Numeric updates retain source, PID, revision, session and original age. |

FreeRTOS stack canaries are enabled. The application health log reports the `app_tick` stack watermark, minimum internal heap, minimum PSRAM heap, alert-queue space, and dropped alert events once per minute. These are diagnostics, not completed soak evidence.

## Ownership and lock rules

1. The application owner is the only writer for selected page, rotation, and active runtime selection.
2. The LVGL task owns LVGL objects. Other tasks publish bounded values through UI mailboxes.
3. One adapter worker owns each adapter transaction stream. Only one ELM request is outstanding per adapter.
4. Config and OTA workers share one transfer gate. A successful commit or activation retains the gate until restart.
5. GATT status reads copy an immutable snapshot under a short critical section. They do not take a transfer mutex.
6. Store metadata is copied while holding `store_lock`; partition reads, hashes, erase, JSON validation, and runtime compilation happen after releasing that lock.
7. No subsystem lock is held while acquiring another subsystem lock. Queue admission from BLE callbacks is nonblocking.

Display settings snapshots use a copy-only critical section independent of the NVS
writer mutex; writer admission is bounded to 250 ms. Adapter and Wi-Fi status use
short copy-only critical sections. Wi-Fi GATT reads never acquire the maintenance
worker mutex and recompute expiry/scrub expired credentials on the copied snapshot.
Diagnostics and adapter discovery return a retryable
failure when their short snapshot lock is busy. GATT rejects a missing snapshot
rather than returning an empty packet. Adapter discovery callbacks do not wait for
its worker mutex. ELM prompt waits use one clock observation for deadline arithmetic,
and stale RX drains have a fixed item budget.

The firmware still contains `portMAX_DELAY` waits inside owning workers and
initialization paths. Apply the [interaction policy](interaction-recovery.md) to
new or modified paths; these remaining waits are not proof of exhaustive liveness
qualification.

## Adapter lifecycle

| State | Entry | Exit or recovery |
| --- | --- | --- |
| disconnected | startup, link loss, timeout resync failure, RX overflow | Scan/connect with 500 ms retry, doubling to 8 seconds. Clear displayed and diagnostic freshness. |
| GATT discovered | service and characteristics found with required properties | Discover descriptors and locate the notification CCCD. Missing properties or descriptor fails the session. |
| subscribed | CCCD write callback succeeds | Run bounded adapter initialization. Subscription submission alone is not readiness. |
| adapter ready | The selected adapter profile completes bounded initialization, header setup, prompt recovery and standard discovery | Begin scheduled OBD requests. Any initialization failure disconnects and re-enters backoff. |
| vehicle responding | one strict single-responder reply decodes successfully | Samples carry the selected PID identity and timestamp. Timeout or malformed replies do not create data. |

The active source requests headered replies and validates the responding ECU.
TCM uses 7E1/7E9; Engine evidence is attributed to 7E8. See `obd_adapter_profile.c`
and `ble_obd.c` for exact commands/budgets. Jeep captures and reconnect observations
are scoped evidence, not general adapter compatibility.

## Polling, diagnostics, and alerts

The UI retains one fixed-capacity latest-value mailbox for all configured readings.
Page selection immediately renders matching fresh entries without flushing them or
renewing their original monotonic timestamps. Short critical sections only scan or
copy the 32 slots; they never call LVGL or wait for transport. Source loss, pause,
maintenance and revision/session changes invalidate hidden entries as well as the
visible page. Live transport checks also reject a retired source before its worker
observes the disconnect. Alert/control freshness remains independent. Initial
warning widgets stay hidden until the first truthful status render.

The pure scheduler supports 32 runtime PIDs with wrap-safe monotonic milliseconds.
Selection uses freshness urgency, then priority and deadline, with an independent
round-robin slot every ninth ordinary request. Completion accounts actual elapsed
time. Successful scoped command-monitoring samples also satisfy ordinary polling
cadence, so display/alerts can reuse them without another adapter request. The
control owner retains its independent strict guard reads and original deadlines.

Before MIL/code work, reserve the full request timeout, prompt margin and upcoming
command guard/identity work; refresh each at-risk eligible reading once. If the
window remains impossible, admit bounded diagnostic progress and count the
freshness overrun instead of starving faults indefinitely. Background work retains
a two-second spacing floor, 60-second MIL, 60-second stored/pending and 120-second
permanent cadence, with per-reading/category failure backoff. A changed MIL or
reported count accelerates category refresh, including when the prior same-session
MIL has aged beyond raw freshness. Before a code-list job has its first valid or
definitively unsupported answer, transient failures become eligible again
after 2, 4, 8, 16, 32, 64 and 128 seconds, then cap at 240 seconds. These are retry
eligibility intervals, still subject to normal-reading reservation and background
spacing. They avoid applying a 60/120-second steady cadence to an initializing ECU.
MIL checks retain their 60-second minimum even after an unanswered first attempt.
An explicit unsupported reply exits the code-list startup path, as does a successful reply;
later failures keep the existing steady-state backoff. Changed MIL/count evidence
restarts category acquisition without inheriting an old failure penalty.
The worker yields 20 ms between transactions; every read still waits for a verified
prompt or bounded recovery.

Two fixed scheduler records live in PSRAM, owned by their respective workers.
Minute logs record requests, reused guard samples, maximum ordinary/diagnostic
request duration, preflight deferrals, forced admissions and missed display budgets.
These are bounded counters, not another sample history.
The App tick also logs diagnostic presentation only when its endpoint state changes:
usable MIL, reported/visible/filtered counts, scan completeness and suppression.
This separates a real reported warning from pending filter classification without
adding an on-screen loading warning or an unbounded history.
Rate classes and display retention belong to the
[catalog contract](../development/reading-catalog-alerts.md#display-and-polling-classes).
A timeout without a completed reply lets a last-good displayed value age normally;
explicit NO DATA, unsupported/malformed replies and source loss invalidate it.
Alert/control failure handling is unchanged. The [qualification plan](../development/gauge-continuity-plan.md)
separates offline evidence from remaining physical tests.

### Page cycling

`page_cycle_step` owns the interval and pause reason independently of polling.
Off, a single page, missing owner, maintenance, touch/gesture, action page, critical
attention and showcase are explicit states. Routine command-worker ownership and
phone reads do not reset the timer. Resume, manual navigation and interval changes
start a full interval, with no catch-up burst. A lost touch event cancels gesture
recognition without dispatching a command; impossible held states have a bounded
recovery. Reasons are logged only when changed and stay out of the normal UI.


Alert evaluation uses the decoded `double` before display range conversion. Zero dwell applies on the first qualifying sample. Delayed trigger and clear use unsigned elapsed time, hysteresis, and per-rule priority. An active alert remains visible when its source becomes stale and is marked unavailable. The 100 ms application tick drives freshness even while an adapter request is waiting.

## Configuration states and recovery

| State | Allowed operations | Recovery and retry rule |
| --- | --- | --- |
| idle | `BEGIN`, `STATUS` | `STATUS` is read-only and does not extend a deadline. |
| metadata | digest parts, `START`, `ABORT`, `STATUS` | Exact opcode, sequence, and transfer ID retries return the published result without repeating the side effect. |
| receiving | sequential `CHUNK`, identical accepted-chunk retry, `VERIFY`, `ABORT`, `STATUS` | Conflicting offsets or bytes fail. Inactivity aborts only an incomplete transfer. |
| verified | `COMMIT`, `ABORT`, `STATUS` | Commit validates the base revision again before selecting the inactive slot. |
| applied | `STATUS` | Abort and new transfers are rejected. Restart uses a fixed deadline that status reads cannot postpone. |

The store retains two committed generations. Commit journals the candidate revision before writing its durable marker. Boot selects the newest schema-valid generation, then the runtime compiler selects the newest executable generation. The first boot marks a new revision attempted. Five seconds of UI, BLE, and application progress confirms it. A reset before confirmation or a compile failure rejects it, selects the previous committed generation as the store/runtime base, and preserves that generation during the next transfer. Protected runtime identity reports the exact revision and hash in use, whether fallback occurred, and whether a trial is still unconfirmed.

## OTA states and trial health

OTA uses the same retry and immutable-status rules. Metadata and signature fragments precede erase. Chunks are sequential and identical retries are idempotent. Verification reads the complete inactive image, checks SHA-256, the ESP image format, board tag, and the pinned development signature. Activation selects the boot partition and starts a fixed five-second restart deadline. Abort and a second maintenance operation are rejected after activation.

A pending image is confirmed only after display, touch, BLE companion startup, and 50 successful 100 ms application ticks. No phone or vehicle adapter is required. Bootloader rollback remains the recovery path when that evidence is absent.

## Fixed limits

| Resource | Limit |
| --- | ---: |
| Staged configuration | 65,536 bytes |
| Runtime PIDs | 32 |
| Runtime pages | 8 |
| Runtime alerts | 32 |
| JSON nesting before cJSON | 16 |
| Active document read chunk | 128 bytes |
| Config/OTA request | 176 bytes |
| ELM response | 512 bytes |
| Simultaneous NimBLE connections configured | 3 |

One whole vehicle dashboard compiles onto one primary adapter by default, or an
explicitly configured primary/child pair. Each physical source has an independent
poll worker, scheduler, parser, reconnect backoff and diagnostic session; shared
discovery consumes one bounded deadline. Source loss invalidates only its samples
and displayed values. Queued samples carry source generation. The shared catalog
contains 53 bounded Mode 01 readings and captured Mode 22 temperature/current gear.
Only selected pages and alerts are polled. Mixed-controller Dual pages and TCM
alerts are supported by development cfg:5. Arbitrary enhanced definitions remain
unsupported. PID 01 is reserved for MIL diagnostics. The configuration owns both
bindings; three-link radio behavior and sustained rates remain unqualified.

## Worker progress and memory evidence

`worker_health.c` holds eight fixed atomic records: application, UI, primary adapter,
child adapter, configuration, OTA, Wi-Fi server and Wi-Fi command. Owning loops
publish progress, including adapter maintenance pauses and idle queue waits.
Nonblocking queue rejection increments the relevant drop count. The existing
minute health log includes progress age/count, maximum loop gap, admission drops,
current free and largest allocatable internal/PSRAM blocks, existing heap minima,
application/UI stack margins and alert queue/drop counts.

The prepared dev.57 increases the application task from 3,072 to 4,096 bytes
after dev.56 bench telemetry measured only 552 bytes of remaining stack. This
uses 1 KiB of the recovered internal headroom; other task stacks are unchanged.

The ESP-IDF `main` task is separate from that application worker. Dev.67's opt-in
orientation candidate overflowed it during early startup, after touch initialization
and before sensor-worker startup. The synchronous early-logo LVGL refresh is on this
boot path; the corrupted backtrace does not prove the exact overflowing callee.
The replacement configures `CONFIG_ESP_MAIN_TASK_STACK_SIZE=4608`, up from 3584.
IDF 5.4.1 adds 512 bytes for non-nano libc logging, so the actual allocation changes
from 4096 to 5120 bytes, costing 1 KiB of internal heap. A compile-time lower bound
checks the resolved SDK configuration for every image. Boot and candidate periodic
logs measure the main watermark separately from `app_tick` and the sensor worker.
Passing compilation or the static ELF budget cannot qualify these dynamic stacks;
record real boot/maintenance margins before accepting the replacement.

Dev.68 confirmed 1104 bytes of main startup headroom, then exhausted the independent
2 KiB orientation-worker stack. Its stack pointer crossed the static stack boundary
into the adjacent task record. The exact call is unproven; IDF I2C NACK/timeout paths
can invoke nested newlib formatting even without logging in the worker itself. The
replacement uses one 4096-byte constant for static storage and task creation, keeps
it aligned/internal, and adds 2 KiB to the existing static memory admission budget.
The ELF gate checks the stack and task record placement and rejects undersized or
misaligned storage. Healthy sampling alone does not qualify bus-error stack margins.

Each worker additionally samples its own stack watermark once per 64 progress
reports, starting with its first report. The log distinguishes an unobserved margin
from a real zero margin. `memory_health.c` logs internal **8-bit** free/largest/minimum,
internal DMA free/largest, PSRAM free/largest and allocation-failure count/last
size/capabilities at boot, each Wi-Fi lifecycle boundary and the minute interval.
The IRAM allocation hook updates three internal counters under a tiny copy-only
critical section. It never allocates, logs, queries a heap, takes a mutex or performs
IO. Logging occurs after releasing that section. It records failed allocation API
calls after registration, not every SDK private-pool failure or historical boot OOM.

These are approximate diagnostics, not synchronized transaction snapshots or an
automatic reset policy. An unavailable adapter is normal recovery work, not a
stalled task. No metric feeds a watchdog to disguise a stall. Firmware build and
sanitized host fixtures qualify arithmetic and bounds only; physical soak,
interruption and memory/rate measurements remain pending. See
[offline hardening](../evidence/offline-hardening.md).

## Memory placement and admission budget

The Waveshare ESP32-S3R2 has 512 KiB on-chip SRAM, 2 MiB QSPI PSRAM and 16 MiB
flash. Flash/PSRAM capacity does not establish internal RAM availability. DMA,
cache-disabled operations and normal task stacks have stricter requirements.
Follow the pinned [ESP-IDF 5.4.1 memory guidance](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-guides/performance/ram-usage.html)
and [external RAM constraints](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-guides/external-ram.html).

- LVGL retains its bounded 64 KiB TLSF arena. `lvgl_memory.h` places that CPU-only
  arena in external BSS through LVGL's supported attribute hook. No managed vendor
  source is patched. PSRAM initialization remains mandatory. The IDF external-BSS
  option also relocates SDK-supported network/host data; keep the S3 PSRAM atomic
  workaround enabled. PSRAM stays QSPI at the qualified 40 MHz setting.
- The 240 × 10 × RGB565 LCD draw buffer is explicitly DMA-capable and internal.
  Do not place DMA descriptors, ISR state or flash-writing task stacks in PSRAM.
  Configuration trees/runtime and the two 8,448-byte Wi-Fi frame buffers already
  use explicit PSRAM allocation without falling back into the internal radio heap.
- `check_firmware_memory.py` runs after linking in ordinary and signed-release
  builds. It requires the 64 KiB LVGL arena in external BSS, the failure hook in
  IRAM and at most **192 KiB** combined `.dram0.data`, `.dram0.bss`, `.iram0.text`
  and `.iram0.vectors`. This is a reviewed static budget, not a runtime-free guarantee.
  Pool/budget changes require a new measured budget and matching negative tests.
- Wi-Fi admission requires at least **64 KiB internal 8-bit free / 16 KiB largest**
  before initializing a new driver and **32 KiB / 8 KiB** after initialization,
  before enabling the AP. PSRAM is never substituted for those floors. The existing
  two static RX/two static TX buffers, eight dynamic RX/cache TX buffers and PSRAM-
  preferred eligible Wi-Fi/LwIP allocation remain. These floors need physical
  qualification and cannot prevent all vendor private-pool allocation failures.
- Allocate mandatory frame buffers before starting workers. Partial buffer/task/
  queue creation releases its resources. A failed optional Wi-Fi service does not
  abort the whole gauge: retain UI/owner/BLE management and omit `wifiBulk` from
  capabilities if service initialization failed for that boot. No silent runtime task restart
  or firmware transport switch is introduced by the qualification fixtures.
- Deinitialize the driver, unregister AP handlers and destroy the AP netif after
  close/start failure. Keep the shared event loop/netif runtime. Do not reduce
  stack sizes or connection capacity merely to make a startup sample fit. Measure
  worst-case signing/verification, rendering, three BLE links and Wi-Fi first.

See [memory audit evidence](../evidence/esp32-memory-hardening.md) and the
[repeatable qualification procedure](../development/memory-qualification.md).

## Display evolution ownership

[Display experience](display-experience.md) owns the ten-page/layout/waiting rules.
The application initializes the board/logo after NVS preferences and before heavy
runtime/radio setup. The existing UI owner maintains one retained logo tree, reads
scoped observations and consumes its waking touch. No additional render task or
bitmap/framebuffer is allocated. Sampling stability is a fixed 640-byte maximum
PSRAM array in the existing adapter workers. [Sampling metadata and limits](../development/reading-catalog-alerts.md#display-and-polling-classes)
never relax control/alert freshness. Smart's ten-page state is 3,448 bytes plus a
256-byte alert projection, guarded by the existing 4 KiB bound.

The [orientation candidate](hardware-and-transports.md#automatic-display-orientation)
uses an optional low-priority worker with a 2 KiB internal stack, snapshot ownership
and existing board I2C, disabled in ordinary images. Rendering applies display/touch
transforms and acknowledges only success; no sensor I/O belongs in callbacks.
Static linked placement and bounded native LVGL checks are necessary but do not
replace loaded Wi-Fi/BLE/dual-source stack, fragmentation and failure qualification.
