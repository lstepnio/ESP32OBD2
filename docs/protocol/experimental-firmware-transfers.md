# Experimental owner transfer on protocol 0

This document records firmware code on `feat/owned-gauge-control`, not a released Android contract. The bonded owner must have an encrypted, authenticated BLE connection. All writes use existing characteristic `6f1a0002`; reads use `6f1a0003`. This avoids a GATT database change for the paired Pixel. The Android app does not yet drive these transactions. Public `configWrite` and `ota` remain `false` until phone integration and live transfer evidence exist.

Every command begins with an opcode and little-endian `u32 sequence`. Transfer commands then include a nonzero little-endian `u32 transferId`. The ATT write confirms queueing only. Read state until its sequence and last opcode match the command, then inspect result and accepted offset. `STATUS` reopens extended read mode after reconnect without replacing the prior operation result. A legacy quick-selection write returns reads to the legacy eight-byte state. Only one configuration or update transfer can own the flash operation gate at a time.

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

The current executable subset is one ECM source using headerless functional Mode 01 replies from a single responder `7E8`, one-byte PID identifiers, one numeric value per numeric page (up to five pages), and the decoder's first 1..4 payload bytes. It requires metric units, brightness 80, reduced motion false, and alert snooze zero because those settings have no runtime handler yet. Mode 22, physical routing, TCM sources, dual pages, other renderers and ECU-specific responses cannot be committed yet. A full schema-valid document may therefore return result 6 at COMMIT. The active generation is loaded at boot; firmware falls back to its built-in readings if a selected generation cannot compile. Polling uses per-definition intervals. Configured thresholds run locally with hysteresis, entry and clear dwell, and a warning or critical badge; the remaining attention/acknowledgment design is pending. When a custom document is active, the public legacy quick-selection and saved-state flags become false because those operations represent only the built-in profile.

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

**Trust boundary:** Update activation requires the authenticated owner bond, full-image SHA-256, and a P-256 signature from the pinned development key. The private development key is generated locally outside the repository at `~/.config/egauge/dev-update-key.pem`; it is not a production release key and must be backed up or deliberately rotated before distributing updates. This design does not yet enforce downgrade prevention, hardware secure boot, or encrypted NVS. Keep `ota:false` until Android delivery, power interruption, rollback, and recovery are exercised. A 23-byte ATT MTU allows only seven bytes per chunk; negotiated larger MTUs are important for a practical update time.
