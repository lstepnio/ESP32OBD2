# Development adapter binding contract

Status: implemented development slice. Protocol major remains 0. Public `configWrite`, `ota`, and simultaneous-adapter verification remain false. Current private markers include `cfg:5`, `ad:1`, `va:1` and `da:1`; older versions remain compatibility variants. Live vehicle qualification is separate from parser replay and bench simulation.

## Durable selection

Configuration schema 2 adds an optional `adapter` object to each source:

```json
{
  "id": "primary",
  "label": "Vehicle adapter",
  "role": "ecm",
  "adapter": {
    "id": "adapter-fixture",
    "address": "C0:00:00:00:00:01",
    "addressType": "random",
    "driver": "elm-18f0-v1"
  }
}
```

The example address is synthetic. Adapter identity, BLE address type, driver, vehicle profile, pages, and alerts travel in the same owner-authenticated stage/commit document. Existing hash readback, two reserved slots, trial boot, and previous-generation recovery apply. Changing a binding does not bypass document validation. Duplicate adapter IDs or addresses across sources are rejected. The runtime executes one primary transport or a distinct primary/child pair. `va:1` places request/responder identity on each definition; one transport can carry common Mode 01 and captured Mode 22 queries. `cfg:5` supports the shared catalog, mixed-controller Dual pages and TCM numeric/gear alerts. Captured enhanced requests remain restricted to reviewed definitions, including 2204FE/225503 and 7E1/7E9. Compilation rejects undefined sources and duplicate physical query identities. Public two-link qualification remains pending.

An absent adapter in schema 2 means unbound: no automatic connection to a nearby adapter. Schema 1 remains readable with its legacy discovery behavior. The Android codec reads profile schemas 1..12 without inventing missing bindings; [storage](../architecture/android-runtime.md#storage-and-recovery) owns migration and atomic assignment details. Selections stay separate for each of its eight vehicle profiles. A local selection is a draft until gauge save and running hash readback succeed.

`elm-18f0-v1` requires service 18F0, write characteristic 2AF1, notify characteristic 2AF0, suitable discovered properties, and a subscribed CCCD. An advertised name or service only establishes a candidate. Address selection is appropriate for the observed Vgate; adapters that rotate addresses need a stronger identity mechanism before qualification.

`elm-bench-v1` uses service `6f1a1000-9e3b-4f45-a714-69c9d23b6c00` with the same characteristic UUIDs. It is accepted for runtime use only with `CONFIG_EGAUGE_OBD_TRACE=y`. The gauge displays SIMULATED, the app labels the source as a bench simulator, and vehicle fault checks are blocked. Normal builds reject this driver. Legacy discovery cannot accidentally connect to this separate service.

## Owner commands

All requests use the existing protected control characteristic. They do not add GATT characteristics. Commands are one opcode plus a little-endian u32 sequence.

| Opcode | Action | Result |
| --- | --- | --- |
| 50 | Start one bounded eight-second adapter scan | Selects status kind 13; worker performs scanning |
| 51 | Select the current scan snapshot | Status kind 13 |
| 52 | Select primary source status | Status kind 14 |

A user scan preempts a background connection attempt, keeps established adapter links, and uses the shared scan lock. Wi-Fi maintenance prevents adapter discovery. Four unique candidate addresses are retained; additional candidates do not expand the buffer. No credentials or candidate addresses are exposed by a public characteristic.

Kind 13 is 140 bytes:

| Offset | Field |
| --- | --- |
| 0 | Kind 13 |
| 1 | Phase: 0 idle, 1 scanning, 2 complete, 3 failed |
| 2 | BLE manager result code |
| 3 | Reserved zero |
| 4..7 | Request sequence, u32 LE |
| 8 | Candidate count, 0..4 |
| 9..11 | Reserved zero |
| 12..139 | Four 32-byte entries: address in NimBLE byte order (6), address type (1, public 0/random 1), driver (1, real 1/bench 2), bounded ASCII name (24) |

Kind 14 is 160 bytes:

| Offset | Field |
| --- | --- |
| 0 | Kind 14 |
| 1 | Phase: 0 unbound, 1 waiting, 2 connecting, 3 initializing, 4 adapter ready, 5 failed, 6 Wi-Fi maintenance |
| 2 | Last setup result, phase-dependent |
| 3 | Flags: bit 0 explicitly bound, bit 1 simulated |
| 4..7 | Connection generation, u32 LE |
| 8..11 | Age of last support reply in milliseconds, u32 LE; meaningful only with a known map |
| 12..15 | ECU responder, u32 LE |
| 16..79 | Vehicle profile ID, ASCII zero padded |
| 80..111 | Source ID, ASCII zero padded |
| 112..117 | Reserved zero |
| 118 | Bound address type |
| 119 | Driver ID |
| 120 | Known support-map bit mask, bases 00 through E0 |
| 121..152 | Eight four-byte Mode 01 maps, wire byte order |
| 153..156 | Uptime in milliseconds, u32 LE |
| 157..159 | Reserved zero |

Ready means ELM initialization completed, not that all selected readings are responding. Support maps establish advertised capability for the named ECU and current connection generation. Timeout leaves evidence unknown. They never establish live values, physical vehicle identity, or independent measurement accuracy. Maps are cleared on disconnect/generation changes; simulated observations remain simulated. Android adapter snapshots expire after 15 seconds and fault snapshots after 30 seconds.

## Transport and upgrade recovery

ATT writes wait for their completion callback, with a two-second bound. A write timeout retires the connection. Write-without-response is used only when that is the discovered characteristic's available write property. CCCD write acknowledgement, raw readback, connection parameter updates, and write completion are locally traceable. CCCD enablement retains Bluetooth's standard byte order; the Mac's observed readback difference is not yet a proven vendor defect.

The pairing status characteristic is appended after capabilities, control, and state to preserve the established handles. Firmware announces Service Changed once per host startup. Android treats a Service Changed callback as an interrupted transaction and reconnects before further commands. A write with an unknown outcome still requires durable readback. No hidden Android cache-refresh API is used in production.

Adapter scanning and connections stop while the temporary Wi-Fi maintenance network is open, including phone association before image transfer begins. Established values and fault freshness are invalidated. Adapter retries resume after the network closes or startup fails. This removes radio contention as one possible cause of the measured startup delay; it does not prove the Vgate failure or every Wi-Fi delay is resolved.

## Unencrypted adapter replies and owner protection

ESP-IDF 5.4.1 filters incoming ATT notifications from unencrypted links when its global NimBLE security level is 2 or higher. The gauge uses global level 1 for the adapter transport. Companion control/state attributes require authentication, and their handlers independently require an encrypted, authenticated, bonded saved-owner identity. Phone pairing still requests bonding, MITM protection, and Secure Connections. Adapter transport access does not grant companion owner access.

An uncertain Android configuration result can be reconciled after reconnecting only when the saved document and healthy runtime match the exact attempted revision and payload digest. A subsequent draft edit is not substituted for that attempted payload. OTA reconciliation similarly requires the expected healthy image identity.

## Development source selection, da:1

Compact capability `da:1` advertises the owner development source-selection path,
not verified public simultaneous capacity. Android release builds cannot enable the
experimental both-adapter option. Public `maxAdapterLinks` stays one.

- `53 sequence:u32 sourceIndex:u8` selects a source-specific adapter snapshot.
- `3A sequence:u32 sourceIndex:u8` selects its full diagnostic snapshot.

Source index is its array position in the exact confirmed configuration, 0 or 1.
Only configured indices are accepted. Existing `52` and `39` read source zero and
retain their packet layouts (v14, 160 bytes; v15, 248 bytes). Each selection clears
the cached long-read snapshot. Unsupported commands, bad indices and malformed
responses never fall back to another source. The Android client derives indices
from unique configured role entries and validates vehicle/source/revision on reads.
