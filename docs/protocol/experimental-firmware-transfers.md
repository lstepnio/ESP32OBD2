# Development owner protocol 0

This is the implemented development protocol-0 path, not a general production
configuration contract. Owner writes use characteristic `6f1a0002`; protected
reads use `6f1a0003`. Android configuration/readback and signed App/Wi-Fi updates
have recorded evidence. Public `configWrite` and `ota` stay false. Current prepared `cfg:7`
includes bindings (`cfg:3`), local page actions (`cfg:4`), expanded catalog decimals
and TCM numeric/gear alerts, exact CEL presentation filters (`cfg:6`) and ordered action pages (`cfg:7`). `va:1` identifies per-definition ECU routing and `da:1`
source-index reads. Older `cfg:2`/`experimentalNumericConfig:true` are legacy variants.
Negotiate these markers before writes; the later extension sections below define
current semantics without changing the existing command layouts.
See [current state](../current-state.md) for physical limits and
[adapter bindings](adapter-bindings-v1.md) for routing.

Configuration/update transfer commands begin with an opcode and little-endian `u32 sequence`. Transfer commands then include a nonzero little-endian `u32 transferId`. The ATT write confirms queueing only. Read state until its sequence and last opcode match the command, then inspect result and accepted offset. `STATUS` reopens extended read mode after reconnect without replacing the prior operation result. A legacy quick-selection write returns reads to the legacy eight-byte state. Only one configuration or update transfer can own the flash operation gate at a time.

ATT long reads return one immutable full characteristic value from the access
callback. ESP-IDF 5.4.1 NimBLE applies the Read Blob offset after the callback;
application code must not slice the value by that offset again. Application-level
configuration/document chunk offsets remain separate and unchanged. The shared
`companion_gatt_read_value` helper enforces this for every companion read response.

The owner client reads the protected state characteristic before its first write on each GATT connection. This lets Android restore encryption for the saved bond. With a custom document active, the default state read returns the 64-byte configuration status; with built-in readings it returns the legacy eight-byte state. Unauthenticated reads remain rejected. A transient public discovery disconnect is retried once on the same discovered device.
For a cached long-response `INVALID_OFFSET` (ATT 7) on older firmware, the client may
select the bounded configuration STATUS (`17`, sequence 1) once, then retry the owner
read within the same operation budget. This is an encrypted/authenticated owner
command and reads status only. It never changes pairing, configuration, faults or
firmware; any further offset error or invalid response fails closed.

| Config opcode | Payload after opcode + sequence | Effect |
| --- | --- | --- |
| `10` BEGIN | transferId, baseRevision, totalLength, all `u32` | Reserve metadata for 1..65536 bytes; compare active document revision |
| `11` DIGEST | transferId `u32`, partIndex `u8` 0..3, eight SHA-256 bytes | Supply digest in four fragments |
| `12` START | transferId `u32` | Erase inactive config slot and start sequential write |
| `13` CHUNK | transferId, offset `u32`, 1..160 bytes | Append bytes or confirm an identical retry |
| `14` VERIFY | transferId `u32` | Check complete SHA-256 and versioned document schema |
| `15` COMMIT | transferId `u32` | Recheck schema and executable subset, write and read back atomic commit marker; reboot after five seconds |
| `16` ABORT | transferId `u32` | Discard staged transfer, keep active generation |
| `17` STATUS | none | Resume status reads after reconnect |

State version `03` has 64 bytes: phase at 1, result at 2, last opcode at 3, sequence at 4, transfer ID at 8, accepted offset at 12, total length at 16, active document revision at 20, base revision at 24, received digest-part mask at 28, and active SHA-256 at 32. `u32` fields are little endian. Phases: 0 idle, 1 metadata, 2 receiving, 3 verified, 4 applied. Results: 0 success, 2 bad request, 3 conflict, 4 storage error, 5 invalid document, 6 unsupported runtime feature. A transfer expires after ten minutes without an owner command. A disconnect alone does not discard its in-RAM offset; reboot discards an incomplete transfer while retaining the prior committed slot.

**Legacy compatibility, cfg:2 only:** Configuration capability version 2 supports one ECM source using headerless functional Mode 01 replies, one-byte PID identifiers, one to eight ordered pages, and the decoder's first 1..4 payload bytes. Numeric, arc, bar, and trend pages reference one PID; dual pages reference exactly two distinct PIDs. It requires metric units, brightness 80, reduced motion false, and alert snooze zero because those settings have no runtime handler yet. The schema's `7E8` response ID is constrained at compile time but cannot be confirmed from a headerless adapter reply; no ECU attribution claim should be made from this path. Mode 22, physical routing, TCM sources, renderer-specific style options, and ECU-specific responses cannot be committed yet. A full schema-valid document may therefore return result 6 at COMMIT. The active generation is loaded at boot; firmware falls back to its built-in readings if a selected generation cannot compile. Polling uses per-definition intervals. Configured thresholds run locally with hysteresis, entry and clear dwell, and a warning or critical badge; the remaining attention/acknowledgment design is pending. When a custom document is active, the public legacy quick-selection and saved-state flags become false because those operations represent only the built-in profile.

| Update opcode | Payload after opcode + sequence | Effect |
| --- | --- | --- |
| `20` BEGIN | transferId, imageLength, boardTag `0x31534745`, all `u32` | Reserve inactive OTA slot |
| `21` DIGEST | transferId `u32`, partIndex `u8` 0..3, eight SHA-256 bytes | Supply image digest |
| `28` SIGNATURE | transferId `u32`, partIndex `u8` 0..8, DER signature length `u8`, 1..8 signature bytes | Supply a P-256 release signature in fragments |
| `22` START | transferId `u32` | Erase inactive OTA slot |
| `23` CHUNK | transferId, offset `u32`, 1..160 bytes | Append or confirm identical retry |
| `24` VERIFY | transferId `u32` | Verify full readback SHA-256 and ESP image format |
| `25` ACTIVATE | transferId `u32` | Set OTA boot partition and restart after five seconds |
| `26` ABORT | transferId `u32` | Stop incomplete update |
| `27` STATUS | none | Resume status reads |

Update state version `04` has 56 bytes: phase/result/opcode/sequence/transferId/acceptedOffset/totalLength at the same positions as config; digest-part mask at 20, signature length at 21, signature-part mask at 22, and expected SHA-256 at 24. Phases: 0 idle, 1 metadata, 2 receiving, 3 verified, 4 activating. Results: 0 success, 2 invalid image/request, 3 conflict, 4 flash error, 5 unsupported board/length. The signed bytes are little-endian board tag `0x31534745`, little-endian image length, then the 32 raw image SHA-256 bytes. Verify uses ECDSA P-256 with SHA-256 and the pinned development public key in `firmware/gauge/main/certs`. Bootloader rollback is enabled. A trial image is confirmed after display, touch, BLE and task startup. No OBD adapter is required for boot health.

**Trust boundary:** Update activation requires the authenticated owner bond, full-image SHA-256, and a P-256 signature from the pinned development key. Published development releases use protected GitHub signing material and the [release runbook](../development/firmware-release-runbook.md). Earlier local bench keys are historical inputs, never a substitute for the protected publication workflow. Development trust is not production provisioning. This design does not yet enforce downgrade prevention, hardware secure boot, or encrypted NVS. Keep `ota:false` until power interruption, production signing and recovery policy are exercised. A 23-byte ATT MTU allows only seven bytes per chunk; negotiated larger MTUs are important for a practical update time.

The Android development install path now imports the signed package, checks its SHA-256, signature, board tag and ESP app descriptor, then uses the owner link to send metadata and sequential chunks. It checks the accepted offset after every chunk, asks the gauge to verify the complete image, and only then sends ACTIVATE. After reboot it requires a different running partition, `ESP_OTA_IMG_VALID`, and an ELF SHA-256 matching the descriptor inside the signed image. The transfer runs in Android ViewModel scope so Activity recreation does not cancel it; the app keeps the screen awake while foreground. Process death or a lost session still requires a fresh attempt, which aborts a prior incomplete transfer. This is an experimental development path, not a general `ota:true` capability.

Owner opcode `30` with a four-byte sequence switches the protected state read to a 32-byte diagnostic snapshot, version `05`. Flag bits at byte 1 indicate fresh MIL evidence (bit 0), MIL on (bit 1), and fresh Mode 03/07/0A category evidence (bits 2/3/4). Byte 2 is the reported Mode 01 PID 01 DTC count. Bytes 3..5 count nonzero DTC pairs in confirmed/pending/permanent replies. Bytes 6..11 are the first raw two-byte DTC in each category, big endian; zero means none. Little-endian observation timestamps at 12, 16 and 20, MIL timestamp at 24, and current monotonic milliseconds at 28 let the caller calculate age. The firmware rejects malformed, ambiguous or multi-responder ELM text. Read-only diagnostics do not authorize Mode 04 clearing. The legacy snapshot is Engine-only and partial. Current command `39` / version `0F` carries full source-scoped lists; its single authoritative layout is in [diagnostics](diagnostics-and-alerts.md).

Owner opcode `31` with a four-byte sequence switches the protected state read to a 60-byte running image identity, version `06`. Byte 1 is the ESP-IDF OTA image state (`0` new, `1` pending verify, `2` valid, `3` invalid, `4` aborted, `255` unavailable). Byte 2 is the running partition subtype. Bytes 4..7 are the little-endian secure version, bytes 8..39 the running app's ELF SHA-256, bytes 40..55 the first 16 bytes of its version string, and bytes 56..59 the little-endian running partition address. The Android app can display these after an authenticated read. The ELF hash identifies the booted build, but differs from the signed update package's raw binary SHA-256. The installer compares the signed package's ELF hash with this protected running identity before reporting success. A valid prior image means the trial was not confirmed; proving the inactive slot's aborted state requires an OTA metadata read.

Owner opcode `32` with a little-endian `u32 sequence` and `u32 document offset` selects a protected active-document read. State version `07` has a 52-byte header followed by up to 128 document bytes. Byte 1 is `1` when a custom document is active and `0` for built-in readings. Bytes 2..3 and 17..19 are reserved zero. Bytes 4..7 hold active revision, 8..11 total document length, 12..15 returned offset, byte 16 returned byte count, and 20..51 the committed SHA-256. All integers are little endian. With no custom document, offset zero returns only the 52-byte header and flag zero. Out-of-range offsets fail. Each request reads a bounded piece from the active slot; if revision or digest changes while that piece is read, the read fails. The client must verify stable metadata across all pieces and hash the complete reconstructed document before presenting it as a saved configuration. This readback is experimental owner functionality and does not enable general `configWrite` or establish any vehicle PID support.

Owner opcode `33` with a four-byte sequence selects protected runtime configuration identity version `08`. The response is 44 bytes. Byte 1 bit 0 means a custom configuration is running; bit 1 means the runtime rejected the newest stored generation and loaded the previous committed executable generation; bit 2 means the running generation is in its boot-health trial. Bytes 2..3 are reserved zero. Bytes 4..7 hold the little-endian running revision, bytes 8..11 hold the newest durable revision observed before any recovery selection, and bytes 12..43 hold the running revision's SHA-256. When bit 0 is clear, the firmware is using built-in readings and the running revision and digest are zero; the durable revision may still identify a rejected document. This identity is distinct from the active-document read: version 7 reports the current store base used for the next revision, while version 8 reports what the current boot actually compiled and whether a newer durable generation was rejected.

## Dual-source development configuration, dev.41

The existing schema-2 config transaction can atomically carry a bound ECM plus TCM.
No config/update opcode or packet layout changes. Capability `da:1` gates the debug
app's combined send and [source-specific reads](adapter-bindings-v1.md).
Existing stored/running hash, trial/fallback and uncertain-outcome rules apply to
the entire configuration. This extension does not promote public write, OTA or
simultaneous radio capacity flags.

## Local page actions, development `cfg:4`

`cfg:4` includes the `cfg:3` transport and schema-2 adapter/config features and
adds optional `actions` to a schema-2 document. It uses the existing atomic transfer,
validation, running revision/hash and protected document readback. No new opcode or
vehicle control service is introduced. Schema 1 forbids `actions`; omitting it
preserves older configurations. New Android may save a local draft while offline
but must require `cfg:4` before sending a nonempty action list. Older Android that
rejects unknown `cfg` versions requires the matching App update.

The list permits zero or one object with exactly these fields:

```json
{"type":"jumpPage","pageId":"page.rpm","gesture":"up","count":3,"windowMs":5000}
```

`pageId` must reference a page in the same document. `count` is an integer 2..5;
`windowMs` is an integer 2000..10000 divisible by 1000. The target resolves to a
compiled page index, including explicitly prefixed child pages. Unknown types,
extra fields, invalid references and multiple bindings fail staging validation.
Firmware and Android both reject vehicle control types, raw commands and ambiguous
bindings. The first and final completed strokes must be at most `windowMs` apart;
recognition uses monotonic elapsed time and a five-second completion cooldown.

Bindings are persisted only as configuration. Partial sequences/cooldown are volatile,
boot never replays an action, and local selection uses the existing bounded page-save
policy. This development extension establishes local page navigation only; it does
not advertise idle/ABS support or qualify physical gesture usability.


## Whole-vehicle definition routing, source dev.45

Compact capability `va:1` enables standard and the existing captured enhanced
transmission definitions on one primary transport, plus mixed-controller Dual pages.
Each PID supplies its service and expected ECU; optional child routing preserves
all vehicle pages. Configuration schema remains 2. Apps must require this capability
before sending the new payload to older firmware. Compact `qs` aliases `quickSelect`
to keep the discovery capability frame within its 255-byte read boundary. Public
link/configuration/update qualification flags remain unchanged.

## Expanded local alerts, development `cfg:5`

`cfg:5` retains `cfg:4` operations and bounds and adds decimal-preserving rendering,
numeric enhanced-PID alerts and gear `equals` conditions. Config schema 2 accepts
`equals` only for a gear definition, valid integer position codes 0..8/11/13 and
zero hysteresis. Both numeric comparators retain existing release/dwell behavior.
The App requires this capability before sending new catalog entries or TCM alerts.
Install the compatible App first; older Apps reject an unknown capability version.
No BLE opcode, protocol-major, public OTA/config-write flag or link qualification
changes. See [catalog maintenance and evidence](../development/reading-catalog-alerts.md).

### Development OTA status polling

A protected mutation is sent once, then only its status is polled. Android uses
5/10/20/40 ms initial pauses followed by 75 ms pauses for Bluetooth OTA replies.
The four initial pauses total one previous 75 ms pause; three extra reads retain
the existing nominal maximum pause budget (6 seconds for 80 legacy attempts,
45 seconds for 600). The complete operation deadline and cancellation remain
unchanged. Stale sequence/opcode replies never certify completion; no early poll
replays a firmware write. Wi-Fi bulk framing and response handling are unchanged.

## Temporary display showcase

Dev.54 adds `sc:1` to document-mode development capabilities only. The legacy
capability frame remains within its 255-byte budget and does not advertise this tool.
Public configuration and OTA capability flags remain disabled. No config schema,
saved settings or diagnostic wire packet changes are introduced.

Authenticated owner command `64` is seven bytes: enabled (0/1), interval seconds
(5..30 when enabled, zero allowed for stop), then nonzero request sequence `u32`.
It queues one nonblocking UI operation. Duplicate sequences do not restart playback
or extend its deadline. Command `65` is five bytes with a `u32` read selector and
selects the protected twenty-byte version `15` (decimal 21) status response:

| Offset | Meaning |
| --- | --- |
| 0 | Tag 21 |
| 1 | Active, 0 or 1 |
| 2 | Result: 0 applied, 1 refused |
| 3 | Zero-based frame index |
| 4..7 | Acknowledged request sequence, little endian |
| 8 | Frame count (currently 21; reader bounds 1..64) |
| 9 | Interval seconds |
| 10 | Schema 1 |
| 11 | Reserved zero |
| 12..15 | Remaining duration ms, little endian, maximum 600000; zero when inactive |
| 16..19 | Reserved zero |

Reader validates structure, identity and bounds. Command acceptance does not prove
physical display success. The connection owner recognizes this protected frame for
encrypted readback; disconnect resets the extended-state selector as before. The
client sends a mutation once and polls for its sequence; uncertain writes are not
replayed. UI-only lifecycle and preemption rules are in the [showcase guide](../development/display-showcase.md).

## Exact CEL code filters, development `cfg:6`

Prepared dev.55 retains cfg:5 operations and adds optional schema-2
`diagnosticFilters`, a maximum sixteen-element array of objects containing
`controller` (`ecm` or `tcm`) and `code` (uppercase `[PCBU][0-3][0-9A-F]{3}`, excluding
P0000). Exact controller/code pairs must be unique. Unknown fields, wildcard codes,
invalid controllers and legacy schema-1 injection are rejected. Omission and an
empty array mean no filters. Rules are carried in the existing atomic authenticated-owner
configuration transaction and readback; no new opcode or diagnostic packet layout
is introduced. Runtime capability `cfg:6` gates sending nonempty filters; older
firmware rejects this property. Public configuration/OTA flags remain false.

The raw version-15 diagnostic packets, MIL bit, reported count and event journal
are unchanged. Presentation suppression follows the [shared framework policy](../architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters),
including complete fresh checks and MIL-count reconciliation. Engine/Transmission
identity is independent of which adapter transports the replies. A filter never
sends an ECU clear command and never changes the vehicle's own lamp.

## Legacy selection, pairing and display settings

The authenticated display-settings extension advertises `ds: 1` beginning with dev.28 after physical qualification of the protected path on dev.27. Dev.27 implements the path without advertising the public flag. The owner writes `35 01 00 00 00` to the protected control characteristic and reads the protected state characteristic. State version `0A` is eight bytes: version, rotation `0`..`3`, brightness `5`..`100`, reserved zero, and little-endian `u32` revision. To save, the owner writes opcode `36`, rotation, brightness, then the revision it just read. Firmware validates bounds, queues the command, saves a versioned NVS record, applies rotation and backlight, then advances the revision. The app reports success only after a read confirms the requested values and the next revision. Default values come from the active document or built-in gauge; a saved owner adjustment survives restart and document updates. The extension uses the existing protected characteristics and requires no GATT database change.

Display settings version `ds: 2` extends the same eight-byte state to version `0B`. Byte 3 is the display units enum (`0` metric, `1` imperial); the other fields retain their offsets. Opcode `37` writes rotation, brightness, units, and the little-endian `u32` revision last read, for eight bytes total. Firmware rejects invalid values and stale revisions. The existing opcode `36` remains supported and preserves units. The saved NVS display record migrates version 1 to version 2 without changing rotation, brightness, or revision. Built-in and document readings remain canonical metric values; the gauge converts Celsius and kilometers per hour for display, and the app converts previews and alert editing at the presentation boundary. No configuration document or GATT database change is required.

Display settings version `ds: 3` adds saved-page cycling. State version `0C` is ten bytes: version, rotation, brightness, units, little-endian `u32` revision, then little-endian `u16` cycle interval in seconds. The interval accepts `0` (Off) or `5`, `10`, `15`, `30`, and `60`. Opcode `38` writes rotation, brightness, units, little-endian `u16` interval, then little-endian `u32` expected revision, for ten bytes total. Older opcodes `36` and `37` preserve the cycle interval. Existing NVS records migrate to a disabled interval without changing their display values or revision. The gauge advances only when more than one saved page exists, resets the interval after a manual page change or owner selection, and pauses during a transfer. Automatic page changes are runtime only; they do not repeatedly write flash. The app verifies the saved interval by reading the next revision. This extension uses the existing protected characteristics and requires no GATT database or configuration document change.

### Smart page cycling settings

Configured dashboards add optional capability `sp:1`; `ds:3`, legacy capability
flags and legacy state/opcodes retain their meanings. The document JSON omits
`savedStateRead:false` and `displayRotationWrite:false`, whose supported decoders
already default to false, to stay below the qualified 255-byte read boundary.
The legacy built-in-page path does not advertise Smart.

An authenticated owner writes `68 01 00 00 00` to request Smart display settings.
The protected state is 12 bytes: `17` (decimal23), rotation, brightness, units,
little-endian u32 settings revision, little-endian u16 cycle interval, Smart Boolean
`0`/`1`, and policy version `1`. Reject other lengths, flags, policy versions or
existing display field bounds. `0` interval makes Smart inactive without discarding
its saved preference. This is distinct from Auto physical display orientation.

Opcode `69` writes eleven bytes: opcode, rotation, brightness, units, LE16 interval,
Smart Boolean, LE32 expected revision. Admission validates bounds/revision and queues
without blocking. The application owner uses the existing display writer; confirmed
success requires an exact next revision and all requested fields in extended readback.
A lost acknowledgement is an uncertain outcome until fresh readback, never permission
to replay. The new read/write leaves state in this extended mode for polling.

Older write opcodes preserve the Smart preference on new firmware and leave their
readback format unchanged. Capability absence means the new App retains ordinary
cycling controls. NVS version/revision sidecars, interruption and downgrade semantics
are owned by the [Smart cycling contract](../architecture/smart-page-cycling.md#settings-and-compatibility).
No GATT database, saved configuration document or public capability-flag change is
required. Existing owner authentication and operation serialization apply.

The device uses LE Secure Connections, authenticated passkey entry, encryption and bonding. An unowned gauge starts on an exclusive pairing screen and automatically opens a 120-second association window. When the window expires, the screen shows **HOLD TO OPEN PAIRING** and a gauge long press opens another window. While the window is open, the screen says **OPEN APP TO PAIR GAUGE**. Readings, renderer graphics, alerts, and diagnostics stay hidden throughout pairing. The six-digit passkey appears on its LCD and Android shows the system pairing prompt. The first authenticated bonded phone identity is stored in NVS as owner, which closes pairing and restores the selected gauge page. Subsequent boots with a saved owner start on that page even when the phone is disconnected. Protected GATT access also checks the owner identity. A 12-second physical hold now opens a 30-second confirmation screen; a tap confirms erasing the owner association and bond, then restarts the gauge. This reset is intentionally local.

Owner adoption requires a passkey event on the same connection within the active association window. A previously bonded phone that reconnects after owner reset cannot silently reclaim ownership during the next window. If bond deletion fails, the owner association is still cleared and old encrypted links remain unauthorized; the user may need to forget stale bonds on the phone before a fresh pairing.

The public read-only pairing status characteristic uses UUID `6f1a0004-9e3b-4f45-a714-69c9d23b6c00`. It returns four bytes: version `01`, state (`00` closed and unowned, `01` ready, `02` passkey displayed, `03` owner saved), and remaining association-window seconds as little-endian `u16`. It contains no passkey or owner identity. Clients treat the characteristic as optional for older firmware. Android starts bonding only from the explicit Pair gauge action; ordinary protected reads and controls never initiate a bond. The advertised name includes a six-character suffix from the BLE identity address. The LCD repeats this short ID during pairing so a user can match a nearby gauge to the app when several are present.

Protocol 0 uses two extra characteristics under the service UUID below:

| Prefix | Access | Value |
| --- | --- | --- |
| `6f1a0002` | Authenticated write with response | Opcode `01`: two bytes with built-in reading index `00`..`04`. Opcode `02`: six bytes with rotation enum `00`..`03` and little-endian `u32` base legacy revision. |
| `6f1a0003` | Authenticated read | Legacy version `01`: four bytes with applied index and volatile session revision `u16`. Current version `02`: eight bytes with applied index, saved index, display rotation enum `00`..`03`, and little-endian durable legacy revision `u32` |

Indices are RPM, speed, engine load, coolant temperature, and fuel level. A successful ATT write means the bounded request entered the firmware queue. Android reads state after the write and reports success only when the applied index matches. Firmware saves the choice in existing NVS before publishing it. The old session revision resets on reboot. The version `02` legacy revision persists in NVS, starts at zero for an older saved selection, and increments on each successful legacy save. It is not a v1 full-document revision. A retry of the same index is safe. A failed or ambiguous readback must not be displayed as applied. Android shows the saved snapshot separately from the phone's design draft. Version `02` reuses the existing characteristic so bonded Android devices with a cached GATT database can still read it after an app update.

Rotation writes require the owner and the revision read immediately before the request. Firmware discards a queued rotation if a local touch or another command changed that revision first. Android confirms the requested saved rotation and a higher revision after the write.

Pairing/retention, adapter coexistence and production trust require separate evidence.
[Current state](../current-state.md) owns the observed results and unresolved limits.

The current development image has neither secure boot nor NVS encryption enabled. Bond and owner records therefore need a production storage-protection decision before distributing trusted update or vehicle-changing operations. Development owner writes include bounded configuration, display settings and signed
updates. Arbitrary vehicle actions are not implemented; Engine clearing and readback
exist behind a default-off gate in the [diagnostic contract](diagnostics-and-alerts.md). See [Espressif's ESP32-S3 security guide](https://docs.espressif.com/projects/esp-idf/en/v5.4/esp32s3/security/security.html).

The implementation follows the ESP-IDF NimBLE security settings and Android's system-managed bonding API. See [Espressif's security option reference](https://docs.espressif.com/projects/esp-idf/en/v5.4/esp32s3/api-reference/kconfig.html) and [Android `BluetoothDevice.createBond`](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createBond()). Those references describe platform behavior; they do not prove this exact phone/gauge exchange until observed.

In v1, firmware is peripheral to the Android central and central to the OBD adapter. One authorized phone session initially. A bonded device identity, not a changing BLE MAC address, identifies the gauge.

**Proposed, not implemented:** the general v1 sections below describe future contracts,
not the protocol-0 operations above. Capability flags do not enable them.

## Offline temporary-idle inner codec

Implemented as an **offline codec and policy foundation only** in `temporary_idle.c`
and `IdleCommandProtocol`. No owner BLE opcode, status version, execution capability
or vehicle driver is registered. These bytes cannot execute through the installed
firmware. Authentication, running identity and source-worker admission remain
required integration work under [ADR-013](../architecture/vehicle-actions.md#first-vehicle-command-temporary-high-idle).

The 24-byte inner request uses little-endian integers and exact length/version.
The same [Start vector](../../contracts/examples/temporary-idle-start.hex) is consumed
by native C and Kotlin tests; there is no outer opcode in this vector.

| Offset | Size | Field |
| --- | --- | --- |
| 0 | 1 | Inner version, 1 |
| 1 | 1 | Start 1, Stop 2, Status 3 |
| 2 | 1 | Start preset: Air up 1 or Winch 2; otherwise zero |
| 3 | 1 | Reserved/source, must be zero; no child-controller target |
| 4 | 4 | Nonzero operation ID; Start IDs increase during the policy instance lifetime, without wrap |
| 8 | 4 | Nonzero running configuration revision |
| 12 | 4 | Nonzero primary link generation |
| 16 | 2 | Start target RPM, structural envelope 400..3000; otherwise zero |
| 18 | 2 | Start duration seconds, 10..600; otherwise zero |
| 20 | 4 | Reserved, zero |

These structural bounds are not supported PCM limits. The independently qualified
controller definition further restricts RPM/step, duration, IO/refresh and known ECU
lease timeout before any Start intent is admitted. A packet cannot grant qualification.
The codec does not define status replies or freeze the future authenticated envelope.
The worker policy rejects duplicate/old Starts and overlapping operations, isolates
completion tickets, and marks lost/late/unverified results Unknown. A repeated explicit
release from Unknown requires a definition-declared idempotent release; context loss
prevents traffic. Verified reconciliation is required before another Start.

## Shared vehicle commands v1

**Implemented development interface; ordinary builds keep live controller qualification disabled.**
The explicit [parked idle candidate](../architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate)
uses the same wire with local-only Start and a separate temporary admission policy.
Document-mode capability `vc:1` identifies the typed interface and actions-menu
configuration extension. It does not establish a qualified car or enable vehicle
writes. Legacy built-in mode does not advertise it. Public BLE capability flags
remain unchanged. The older numeric temporary-idle inner codec above is an offline
fixture contract, not an alternative live request path.

Authenticated control `0x66` contains exactly one 24-byte inner request. `0x67` +
nonzero read sequence u32 + kind u8 selects a 32-byte protected snapshot. The kind
also selects the response after a command. There is no arbitrary diagnostic text,
child-adapter selector, fourth-button control or numeric RPM field.

| Inner offset | Size | Field |
| --- | --- | --- |
| 0 | 1 | Version 1 |
| 1 | 1 | Kind: captured idle 1, combined ABS/TCS 2 |
| 2 | 1 | Start 1 or Stop/Return 2 |
| 3 | 1 | Idle Start preset: Air up 1 or Winch 2; otherwise zero |
| 4 | 4 | Nonzero operation; Start exceeds the gauge's last admitted operation |
| 8 | 4 | Nonzero running configuration revision |
| 12 | 4 | Nonzero primary link generation |
| 16 | 4 | Current nonzero gauge command boot nonce |
| 20 | 2 | Idle Start duration seconds 10..600; otherwise zero |
| 22 | 2 | Reserved, zero |

All integers are little-endian. Stop addresses the existing operation. Requests
with retired boot/revision/link, unsupported kind, invalid fields, unavailable
qualification or a full bounded queue are refused. ATT success means queued, not
vehicle success. Both App and physical menu use the same queue and worker owner.
The [captured-idle request vector](../../contracts/examples/vehicle-command-idle-start.hex)
is checked by native and Kotlin tests.

| Status offset | Size | Field |
| --- | --- | --- |
| 0 | 1 | Protected status tag 22 |
| 1 | 1 | Kind 1 or 2 |
| 2 | 1 | Unavailable 0, Ready 1, Starting 2, Active 3, Stopping 4, Verifying 5, Restored 6, Restart required 7, Rejected 8, Unknown 9 |
| 3 | 1 | OK 0, Unqualified 1, Invalid 2, Busy 3, Context 4, Guard 5, Timeout 6, Response 7, Storage 8, Coolant timer 9 (idle only) |
| 4 | 4 | Operation, zero before any operation |
| 8 | 4 | Current running revision |
| 12 | 4 | Current primary link generation |
| 16 | 4 | Current boot nonce |
| 20 | 4 | Last event sequence for this kind |
| 24 | 2 | Remaining idle seconds, otherwise zero |
| 26 | 1 | Idle preset 1/2, otherwise zero |
| 27 | 1 | Bit 0: execution enabled with usable journal (qualification or the explicit parked idle candidate); bit 1: ABS/TCS restart required; idle-only bit 2: latched timed fallback, bit 3: physical-gauge Start required, bit 4: coolant unavailable; bits 5..7 zero |
| 28 | 4 | Global last admitted Start operation; no wrap |

Idle fallback flags report the [coolant policy](../architecture/vehicle-actions.md#coolant-protection-for-raised-idle).
Remaining seconds reflects the earlier deadline, rounded up. The physical-confirmation
origin exists only inside the firmware queue; reserved request bytes remain zero
and cannot authorize a missing-coolant Start. Older decoders refuse extended
statuses rather than offering an unprotected Start. No public capability is promoted.
The parked candidate always sets bit 3 for idle, even with fresh coolant; the App
can retain monitoring/Stop without an APK update and cannot bypass local Start.

An uncertain write is sent once and reconciled by bounded status reads. Status
must match kind, boot, revision, link and operation. Foreground reads retry with
bounded backoff; they never replay Start/Return. A lost or cancelled phone session
does not stop autonomous gauge timing. Firmware restart preserves uncertainty and
never resumes a Start. A positive ABS/TCS Return remains Restart required until
independent same-vehicle reset/state evidence is verified.

The schema-2 configuration action `vehicleActions` has `gesture:"up"`, `count`
2..5, `windowMs` 2000..10000 in whole seconds, and `airUpSeconds` / `winchSeconds`
10..600. It has no `pageId`. Existing `jumpPage` actions retain their shape.
`vc:1` identifies command admission. `cfg:7` additionally identifies ordered action
pages, represented as an optional `pages` array of unique curated IDs `airUp`,
`winch`, `absTcs` (0..3 entries). The App negotiates `cfg:7` before sending the
collection. Omitted `pages` migrates the old menu to all three in that order;
explicit `[]` means no configured action pages. `jumpPage` cannot contain `pages`.
Unknown/duplicate IDs, extra keys and invalid timers fail before installation.

A completed gesture opens the branch, never a control write. Selecting Start
requires a named confirmation; Stop is direct. Navigation and button releases are
mutually exclusive. Reading cycling pauses in the branch and resumes with a full
interval after returning. Existing command bytes/status, qualification masks and
public capability flags are unchanged. The UI receives one packed atomic snapshot
per kind; no new transport or polling worker is added.
