# Companion BLE protocol, draft v1

**Draft v1, not implemented.** Freeze only after the dual-link vertical slice. M1 exposes an experimental public capability characteristic at UUID `6f1a0001-9e3b-4f45-a714-69c9d23b6c00`. The integration branch adds the bounded protocol 0 quick-selection operation described below. Full configuration writes and OTA remain disabled.

## Implemented protocol 0 quick selection

The capability JSON advertises `quickSelect: true`, `configWrite: false` and `ota: false`. The app must check this flag. This operation does not enable custom PIDs, threshold writes, diagnostics, OTA, or arbitrary OBD requests.

The device uses LE Secure Connections, authenticated passkey entry, encryption and bonding. A gauge long press opens a 120-second association window. The six-digit passkey appears on its LCD and Android shows the system pairing prompt. The first authenticated bonded phone identity is stored in NVS as owner. Protected GATT access also checks that identity. A 12-second physical hold erases the owner association and bond, then restarts the gauge. This reset is intentionally local.

Owner adoption requires a passkey event on the same connection within that physical window. A previously bonded phone that reconnects after owner reset cannot silently reclaim ownership during the next window. If bond deletion fails, the owner association is still cleared and old encrypted links remain unauthorized; the user may need to forget stale bonds on the phone before a fresh pairing.

Protocol 0 uses two extra characteristics under the service UUID below:

| Prefix | Access | Value |
| --- | --- | --- |
| `6f1a0002` | Authenticated write with response | Exactly two bytes: opcode `01`, built-in reading index `00`..`04` |
| `6f1a0003` | Authenticated read | Four bytes: version `01`, applied index, little-endian volatile session revision `u16` |

Indices are RPM, speed, engine load, coolant temperature, and fuel level. A successful ATT write means the bounded request entered the firmware queue. Android reads state after the write and reports success only when the applied index matches. Firmware saves the choice in existing NVS before publishing it. The session revision resets on reboot and is not a durable config revision. A retry of the same index is safe. A failed or ambiguous readback must not be displayed as applied.

This is a development slice requiring phone pairing and LCD review before a production security claim. The pairing UI, Android system dialog behavior, bond recovery, and simultaneous OBD-adapter compatibility need hardware evidence. Runtime Secure Connections-only policy may exclude adapters that require legacy pairing; verify actual adapters before relying on dual-link operation.

The implementation follows the ESP-IDF NimBLE security settings and Android's system-managed bonding API. See [Espressif's security option reference](https://docs.espressif.com/projects/esp-idf/en/v5.4/esp32s3/api-reference/kconfig.html) and [Android `BluetoothDevice.createBond`](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createBond()). Those references describe platform behavior; they do not prove this exact phone/gauge exchange until observed.

In v1, firmware is peripheral to the Android central and central to the OBD adapter. One authorized phone session initially. A bonded device identity, not a changing BLE MAC address, identifies the gauge.

## Discovery, ownership, and capabilities

Advertise the custom service and a short device name, without VIN or owner information. Physical long-press opens a 120-second association window. Use LE Secure Connections with authenticated passkey entry (fresh six digits displayed on gauge, entered in Android), encryption and bonding. Disable silent Just Works fallback for owner operations. A local confirm step identifies the physical gauge. Existing owner can revoke a phone; physical owner reset erases bonds and invalidates sessions. Define recovery without depending on a lost phone.

Service UUID: `6f1a0000-9e3b-4f45-a714-69c9d23b6c00`. Characteristics share the same suffix and replace `6f1a0000` with:

| Prefix | Name | GATT properties | Authorization |
| --- | --- | --- | --- |
| `6f1a0001` | Capabilities | Read | Public minimal protocol/board ID; no secrets |
| `6f1a0002` | Control | Write with response | Encrypted authenticated owner |
| `6f1a0003` | Events | Indicate | Encrypted authenticated owner |
| `6f1a0004` | Telemetry | Notify | Encrypted authenticated owner |
| `6f1a0005` | Bulk | Write without response / Notify | Encrypted authenticated owner, active transfer |

Capabilities report protocol major/minor, schema versions, board/revision, firmware version/build, supported renderers/services/decoder operators, limits, actual maximum frame size, maxAdapterLinks, simultaneousAdapterLinks, OTA availability, bulkTransports, config revision and optional feature flags (DTC_CLEAR, ALERT_RULES). Standard Device Information can be exposed separately. App gates unavailable features instead of assuming a firmware version implies a capability.

## Framing and message envelope

Works at ATT MTU 23; request larger MTU but use negotiated actual value. Every characteristic value starts with a 12-byte little-endian header: version u8=1; flags u8 (START=1, END=2, ACK=4, reserved bits zero); stream u16; message ID u32; fragment index u16; total fragments u16. Data length <= MTU-3-12. Require count 1..8192 and negotiated maximum; START only at index 0, END only at count-1. Bind assembly to connection generation + characteristic + stream + ID. Duplicate identical fragments are idempotent, conflicting duplicates abort; out-of-order fragments return expected index. Bound assembled command to 4096 bytes, metadata to 4096, and assembly timeout to 5 seconds. Bulk uses independently framed small chunks; never allocate an entire firmware image in RAM.

Control/event payloads are CBOR maps with string keys: `protocol=1`, `requestId` u32, `sessionId` boot-random identifier, `op` string, `body` map. Responses include `status` and typed error details. Unknown required fields/major version fail. Stable op names below are draft; examples and generated codecs must eventually share protocol vectors in Kotlin/C. CBOR representation does not replace the JSON import/config schemas.

Serialize control requests, wait for semantic result beyond the ATT write acknowledgment. Indications acknowledge radio delivery, not durable execution. Retries reuse requestId and idempotency token; maintain a bounded replay journal, and expose operation state after reboot. Events include event sequence and boot/session ID so the app notices gaps and reconciles.

## Operations

| Group | Operations | Semantics |
| --- | --- | --- |
| Device | `device.get`, `adapter.scan`, `adapter.select`, `device.owner.revoke` | Source-scoped selected adapter identity/profile, scan candidates bounded and paginated |
| Configuration | `config.get`, `config.begin`, `config.chunk`, `config.validate`, `config.commit`, `config.status`, `config.abort` | See transaction below |
| Discovery | `discovery.start`, `discovery.status`, `discovery.pause`, `discovery.cancel`, `discovery.results` | Job ID, sourceIds, scope, continuation cursor, per-ECU evidence |
| Telemetry | `telemetry.subscribe`, `telemetry.unsubscribe` | Negotiated PID/rate budget; dropped samples allowed with sequence/age |
| Diagnostics | `dtc.read`, `dtc.clear.prepare`, `dtc.clear.confirm`, `dtc.operation` | Fresh, scoped consent and reconciliation; see diagnostics spec |
| Alerts | `alerts.list`, `alerts.acknowledge` | Rule configuration is part of config transaction; acknowledgment does not modify threshold |
| Update | `ota.begin`, `ota.status`, `ota.finish`, `ota.abort`, `ota.activate` | Manifest-bound stream; boot confirmation is separate |

## Config transaction

Android keeps a draft tied to `baseRevision`. Begin supplies total JSON UTF-8 length, SHA-256 and schema version. Device rejects >64 KiB or incompatible version before allocation. Chunks use accepted offset and bounded credits. Commit is allowed only after complete hash, schema, semantic references, poll budget, alert units, renderer limits and device capability validation. Stage a new generation, read it back, atomically switch active generation, then emit `APPLIED` with new revision and content hash. A repeat token returns the same result. Revision mismatch returns `CONFLICT` with active revision; app offers reload/rebase, never silent overwrite. Disconnect before commit leaves active config intact; stage expires after 10 minutes. After ambiguous commit, query revision/hash.

The existing 24 KiB NVS partition cannot hold this staged 64 KiB document. [Configuration storage](../architecture/config-storage.md) defines the required custom partition migration and dual-generation apply path. Keep `configWrite: false` until that migration and recovery behavior are implemented.

## Bulk flow control

Negotiate a 1 KiB initial chunk ceiling and a credit window of 1..8 chunks. An accepted chunk reports transfer ID, next contiguous offset and rolling progress. Sender stops when credits are exhausted; do not confuse Android write-without-response return with device flash acceptance. Retransmit from device's accepted offset, validate chunk CRC32 and complete SHA-256; CRC is corruption detection, not authentication. On disconnect resume only via the authenticated owner and matching artifact ID/hash. After device reboot the v1 OTA design restarts transfer, as documented in the OTA spec.

## Error vocabulary

`UNAUTHORIZED`, `UNSUPPORTED_VERSION`, `UNSUPPORTED_CAPABILITY`, `INVALID_SCHEMA`, `INVALID_REFERENCE`, `OUT_OF_RANGE`, `CONFLICT`, `BUSY`, `RESOURCE_LIMIT`, `OFFSET_MISMATCH`, `HASH_MISMATCH`, `SIGNATURE_INVALID`, `WRONG_BOARD`, `TIMEOUT`, `ADAPTER_LOST`, `CANCELLED`, `RESULT_UNKNOWN`. Include field paths and retryability where applicable. Keep transport failure separate from vehicle rejection. Rate-limit malformed and unauthorized requests; bounded logs omit credentials and sensitive identifiers.

All adapter/discovery operations identify `sourceId`; telemetry includes source + ECU identity. DTC clear tokens bind exactly one source and the displayed ECU scope. Cross-source bulk clear is excluded from v1. See [multi-adapter contract](../architecture/multi-adapter.md).

Wi-Fi bulk transfer uses the same operation/hash/offset semantics with an exclusive writer lease. BLE framing is transport-specific and is not tunneled blindly over TLS. Endpoint discovery and device-identity pinning occur over the owned BLE link. See [hardware transport design](../architecture/hardware-and-transports.md).
