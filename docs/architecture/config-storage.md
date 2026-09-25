# Configuration storage and apply boundary

Status: proposed for the first full configuration transfer. The paired protocol 0 quick selection uses the existing small NVS `config_t` blob and does not implement this design.

## Capacity evidence

The checked-in ESP-IDF 5.4.1 configuration selects a 16 MB flash size and the default single-app partition table. That table allocates 0x6000 bytes (24 KiB) to NVS and 1 MiB to the factory application. The current app image is about 954 KiB. The [configuration contract](../../contracts/config.schema.json) and [BLE transaction draft](../protocol/ble-v1.md) allow a 64 KiB JSON document. A 64 KiB staged document plus an active generation cannot fit safely in the existing NVS partition. The existing app slot also leaves little growth room.

## Migration layout

Use one reviewed USB migration to a custom partition table before enabling full configuration transfer or OTA. Preserve a small NVS partition for bonds, owner identity, calibration, and active-generation metadata. Reserve two dedicated configuration data partitions, at least 128 KiB each, and two OTA application slots sized against a measured release image plus growth margin. Leave room for future logs/catalog cache only after exact flash geometry and erase-block alignment are confirmed. The migration must define whether old NVS settings and bonds are copied or intentionally reset; the app must explain any required re-pairing.

The config partitions hold bounded bytes, not executable code. Each generation has a header with magic, schema version, document length, SHA-256, generation number, and commit marker. The device writes only the inactive slot. The active slot remains untouched until the inactive slot passes length, digest, schema, semantic, budget, and capability validation.

## Atomic apply

1. `config.begin` checks authenticated owner, `baseRevision`, declared schema, length at most 64 KiB, and expected SHA-256. It reserves an inactive generation and returns a transfer ID and accepted offset.
2. `config.chunk` accepts only the next bounded offset. A disconnect leaves the active generation unchanged; a resumed owner must present the same transfer ID and hash before continuing.
3. `config.validate` checks the complete digest and every referenced source, PID definition, renderer, unit, threshold, and rate. No custom request becomes active merely because it is syntactically valid.
4. `config.commit` writes and rereads the inactive generation, records its commit marker, then changes the active pointer. Acknowledgment includes new revision and digest. Repeating a request ID returns the original outcome.
5. Boot selects the last valid committed generation. If active metadata is damaged, inspect both generation headers and digests, choose the newest valid committed generation, and report recovery to the app. Never boot an unvalidated staged document.

Firmware must keep the previous generation until the new one has booted and rendered successfully. Mark the newly selected generation as trial and clear that marker only after scheduler and display initialization complete; repeated early boots or validation failure choose the previous valid generation and report rollback. A trial marker must never cause a loop between generations. If commit succeeds but the BLE response is lost, Android queries revision and digest instead of sending a second blind commit. If the base revision changed, Android retains its local draft and offers reload/rebase. The app never labels a draft as applied based on an ATT write acknowledgment.

## Gate before implementation

Confirm actual flash geometry on the exact board, image size on the release toolchain, partition offsets/alignment, NVS owner/bond migration behavior, and power-loss recovery. Keep full `configWrite` capability false until this layout and transaction are implemented and exercised. The quick-selection path remains explicitly separate so no 64 KiB document is promised by the current firmware.

Android must map legacy local profile UUIDs to schema-conforming `vehicleProfileId` values without silently renaming the user's local profile or losing its draft. New local IDs already use a `vehicle-` prefix. This mapping belongs in the explicit configuration export layer, with a stable persisted association before full Apply is enabled.
