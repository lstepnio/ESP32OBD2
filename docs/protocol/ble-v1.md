# Companion BLE protocol, draft v1

The general v1 envelope remains a design. Current development firmware uses protocol
0 and bounded owner-protected extensions. Android has exercised configuration and
App/Wi-Fi updates; Jeep source-specific telemetry has recorded evidence. Public
`configWrite` and `ota` remain disabled. [Current status](../current-state.md) owns
physical qualification limits; [experimental commands](experimental-firmware-transfers.md)
and [adapter bindings](adapter-bindings-v1.md) define the implemented bytes.

## Implemented protocol 0 quick selection

See the maintained [development owner protocol](experimental-firmware-transfers.md#legacy-selection-pairing-and-display-settings)
for legacy selection, pairing and display settings. This document owns the proposed
public v1 envelope below, not a second copy of implemented wire layouts.

## Proposed v1 discovery, ownership, and capabilities

The remaining v1 envelope/operation sections are design targets, not implemented
command names. Use the protocol-0 documents above for current clients.

Advertise the custom service and a short device name, without VIN or owner information. Physical long-press opens a 120-second association window. Use LE Secure Connections with authenticated passkey entry (fresh six digits displayed on gauge, entered in Android), encryption and bonding. Disable silent Just Works fallback for owner operations. A local confirm step identifies the physical gauge. Existing owner can revoke a phone; physical owner reset erases bonds and invalidates sessions. Define recovery without depending on a lost phone.

Service UUID: `6f1a0000-9e3b-4f45-a714-69c9d23b6c00`. Characteristics share the same suffix and replace `6f1a0000` with:

| Prefix | Name | GATT properties | Authorization |
| --- | --- | --- | --- |
| `6f1a0001` | Capabilities | Read | Public minimal protocol/board ID; no secrets |
| `6f1a0002` | Control | Write with response | Encrypted authenticated owner |
| `6f1a0003` | Events | Indicate | Encrypted authenticated owner |
| `6f1a0004` | Telemetry | Notify | Encrypted authenticated owner |
| `6f1a0005` | Bulk | Write without response / Notify | Encrypted authenticated owner, active transfer |

Capabilities report protocol major/minor, schema versions, board/revision, firmware version/build, supported renderers/services/decoder operators, limits, actual maximum frame size, maxAdapterLinks, simultaneousAdapterLinks, OTA availability, bulkTransports, config revision and optional feature flags (DTC_CLEAR, ALERT_RULES). An omitted boolean capability means false. Standard Device Information can be exposed separately. App gates unavailable features instead of assuming a firmware version implies a capability.

## Proposed v1 framing and message envelope

Works at ATT MTU 23; request larger MTU but use negotiated actual value. Every characteristic value starts with a 12-byte little-endian header: version u8=1; flags u8 (START=1, END=2, ACK=4, reserved bits zero); stream u16; message ID u32; fragment index u16; total fragments u16. Data length <= MTU-3-12. Require count 1..8192 and negotiated maximum; START only at index 0, END only at count-1. Bind assembly to connection generation + characteristic + stream + ID. Duplicate identical fragments are idempotent, conflicting duplicates abort; out-of-order fragments return expected index. Bound assembled command to 4096 bytes, metadata to 4096, and assembly timeout to 5 seconds. Bulk uses independently framed small chunks; never allocate an entire firmware image in RAM.

Control/event payloads are CBOR maps with string keys: `protocol=1`, `requestId` u32, `sessionId` boot-random identifier, `op` string, `body` map. Responses include `status` and typed error details. Unknown required fields/major version fail. Stable op names below are draft; examples and generated codecs must eventually share protocol vectors in Kotlin/C. CBOR representation does not replace the JSON import/config schemas.

Serialize control requests, wait for semantic result beyond the ATT write acknowledgment. Indications acknowledge radio delivery, not durable execution. Retries reuse requestId and idempotency token; maintain a bounded replay journal, and expose operation state after reboot. Events include event sequence and boot/session ID so the app notices gaps and reconciles.

## Proposed v1 operations

| Group | Operations | Semantics |
| --- | --- | --- |
| Device | `device.get`, `adapter.scan`, `adapter.select`, `device.owner.revoke` | Source-scoped selected adapter identity/profile, scan candidates bounded and paginated |
| Configuration | `config.get`, `config.begin`, `config.chunk`, `config.validate`, `config.commit`, `config.status`, `config.abort` | See transaction below |
| Discovery | `discovery.start`, `discovery.status`, `discovery.pause`, `discovery.cancel`, `discovery.results` | Job ID, sourceIds, scope, continuation cursor, per-ECU evidence |
| Telemetry | `telemetry.subscribe`, `telemetry.unsubscribe` | Negotiated PID/rate budget; dropped samples allowed with sequence/age |
| Diagnostics | `dtc.read`, `dtc.clear.prepare`, `dtc.clear.confirm`, `dtc.operation` | Fresh, scoped consent and reconciliation; see diagnostics spec |
| Hardware | `hardware.capacity.get` | Authenticated read-only runtime capacity and initialized-subsystem snapshot; see [hardware probe](hardware-probe.md) |
| Alerts | `alerts.list`, `alerts.acknowledge` | Rule configuration is part of config transaction; acknowledgment does not modify threshold |
| Update | `ota.begin`, `ota.status`, `ota.finish`, `ota.abort`, `ota.activate` | Manifest-bound stream; boot confirmation is separate |

## Proposed v1 config transaction

Android keeps a draft tied to `baseRevision`. Begin supplies total JSON UTF-8 length, SHA-256 and schema version. Device rejects >64 KiB or incompatible version before allocation. Chunks use accepted offset and bounded credits. Commit is allowed only after complete hash, schema, semantic references, poll budget, alert units, renderer limits and device capability validation. Stage a new generation, read it back, atomically switch active generation, then emit `APPLIED` with new revision and content hash. A repeat token returns the same result. Revision mismatch returns `CONFLICT` with active revision; app offers reload/rebase, never silent overwrite. Disconnect before commit leaves active config intact; stage expires after 10 minutes. After ambiguous commit, query revision/hash.

The original 24 KiB NVS partition could not hold a staged 64 KiB document. The development board has been migrated to the custom dual-generation [configuration storage](../architecture/config-storage.md) layout, and the protected development validation/transfer/activation/recovery path is implemented. This proposed public v1 transaction is not that path; keep `configWrite: false` until its full production qualification passes.

The app's full Apply state is explicit:

| State | Entered when | Allowed next step |
| --- | --- | --- |
| Local draft | User edits or restores a profile | Run local and negotiated capability preflight |
| Blocked | Schema, source evidence, revision, or firmware capability is missing | Keep draft; show the exact blocker |
| Staging | `config.begin` returns a transfer ID | Send bounded chunks from the accepted offset; cancel safely |
| Validating | All bytes are accepted | Wait for typed firmware validation result |
| Committing | Validated document is submitted with an idempotency token | Wait for semantic result; do not infer success from ATT acknowledgment |
| Result unknown | Link drops or response times out after commit | Reconnect as owner, query revision and digest, reconcile |
| Applied | Device reports matching revision and digest | Mark the local revision as applied |
| Conflict or rolled back | Device reports newer base or fallback generation | Retain local draft and last known active document; offer reload/rebase |

The app may resume staging only when transfer ID, owner, schema, length, hash, and base revision all match the firmware's current staging record. Any mismatch starts a new explicit transaction after the old stage is aborted or expires. This avoids treating a partial upload as a complete configuration.

## Proposed v1 bulk flow control

Negotiate a 1 KiB initial chunk ceiling and a credit window of 1..8 chunks. An accepted chunk reports transfer ID, next contiguous offset and rolling progress. Sender stops when credits are exhausted; do not confuse Android write-without-response return with device flash acceptance. Retransmit from device's accepted offset, validate chunk CRC32 and complete SHA-256; CRC is corruption detection, not authentication. On disconnect resume only via the authenticated owner and matching artifact ID/hash. After device reboot the v1 OTA design restarts transfer, as documented in the OTA spec.

## Proposed v1 error vocabulary

`UNAUTHORIZED`, `UNSUPPORTED_VERSION`, `UNSUPPORTED_CAPABILITY`, `INVALID_SCHEMA`, `INVALID_REFERENCE`, `OUT_OF_RANGE`, `CONFLICT`, `BUSY`, `RESOURCE_LIMIT`, `OFFSET_MISMATCH`, `HASH_MISMATCH`, `SIGNATURE_INVALID`, `WRONG_BOARD`, `TIMEOUT`, `ADAPTER_LOST`, `CANCELLED`, `RESULT_UNKNOWN`. Include field paths and retryability where applicable. Keep transport failure separate from vehicle rejection. Rate-limit malformed and unauthorized requests; bounded logs omit credentials and sensitive identifiers.

All adapter/discovery operations identify `sourceId`; telemetry includes source + ECU identity. DTC clear tokens bind exactly one source and the displayed ECU scope. Cross-source bulk clear is excluded from v1. See [multi-adapter contract](../architecture/multi-adapter.md).

Wi-Fi bulk transfer uses the same operation/hash/offset semantics with an exclusive writer lease. BLE framing is transport-specific and is not tunneled blindly over TLS. Endpoint discovery and device-identity pinning occur over the owned BLE link. See [hardware transport design](../architecture/hardware-and-transports.md).

## Development adapter selection extension

The [adapter binding contract](adapter-bindings-v1.md) and
[development owner protocol](experimental-firmware-transfers.md) own implemented
configuration/discovery/routing markers. Public capability promotion remains gated
by [current qualification](../current-state.md).
