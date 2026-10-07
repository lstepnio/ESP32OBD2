# Firmware runtime ownership and recovery

Updated 2026-10-06. Implemented development architecture. Public capability flags remain gated by hardware evidence.

## Task and resource inventory

| Owner | Priority | Stack | Bounded input | Blocking boundary |
| --- | ---: | ---: | --- | --- |
| ESP main application owner | IDF default | 3,584 bytes | phone command queue: 4 | Applies selection and rotation commands. Persistence is serialized at the application boundary. |
| `app_tick` | 5 | 3,072 bytes | alert sample queue: 16 | Ticks companion expiry, alert freshness, and UI snapshots every 100 ms. It does not wait for an adapter transaction. |
| `obd_task` | 5 | 4,096 bytes | one ELM request, RX bytes: 512 | Owns ECM connect, initialization, request/response, and bounded reconnect backoff. |
| `tcm_link` | 5 | 4,096 bytes | one ELM request, RX bytes: 512 | Owns the optional second adapter lifecycle. TCM polling remains unavailable without a configured adapter and vehicle contract. |
| `cfg_transfer` | 4 | 6,144 bytes | request queue: 4 | Owns config erase, write, hash, validation, and commit. Publishes a short immutable status snapshot. |
| `ota_transfer` | 4 | 6,144 bytes | request queue: 4 | Owns inactive-slot writes, verification, signature checks, and activation. Publishes a short immutable status snapshot. |
| `owner_store` | 4 | 3,072 bytes | owner-save queue: 1 | Moves NVS bond-owner persistence outside the NimBLE callback. Authorization remains closed until persistence succeeds. |
| `boot_health` | 5 | 3,072 bytes | progress counter | Confirms configuration and OTA trials only after essential startup and at least five seconds of application tick progress. |
| NimBLE host | 5 | 4,096 bytes | manager result queue: 1 per link | GAP/GATT callbacks validate, copy, enqueue, or copy a published snapshot. No JSON validation or flash operation runs in an application GATT callback. |
| LVGL owner | component managed | component configured | value: 1, touch: 4, pairing/alert/diagnostics: 1 each | Owns widgets and touch detection. Touch produces an application command. Numeric updates include PID identity and late samples are rejected. |

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
writer mutex; writer admission is bounded to 250 ms. Adapter status uses a short
critical section. Diagnostics, adapter discovery and Wi-Fi status return a retryable
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

The pure scheduler supports 32 runtime PIDs and unsigned wrap-safe millisecond deadlines. Normal PIDs use round-robin selection among due definitions. One background job is allowed only after ten normal jobs. MIL is considered every 10 seconds; confirmed, pending, and permanent DTC categories are considered every 30 seconds. A completed job is accounted at the supplied current time, which avoids catch-up bursts.

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

The normal runtime selects one active Engine or Transmission source. Engine uses
bounded Mode 01 definitions; Transmission permits the captured Mode 22 temperature
and current-gear definitions. Five renderers, ordered pages, canonical-unit alerts
and independent persisted display settings are implemented within compiler bounds.
Transmission gear uses Numeric/Dual and its profile currently excludes alerts.
PID 01 is reserved for MIL diagnostics. Schema-valid unsupported features are rejected
before commit. Legacy second-link scaffolding is not simultaneous telemetry support.
