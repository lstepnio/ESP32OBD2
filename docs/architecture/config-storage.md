# Configuration storage and apply boundary

Status: proposed for the first full configuration transfer. The paired protocol 0 quick selection uses the existing small NVS `config_t` blob and does not implement this design.

## Capacity evidence

On 2026-09-25, `esptool.py flash_id` on the USB-connected ESP32-S3 reported a 16 MB flash chip (manufacturer `20`, device `4018`). A read of the board's partition table at `0x8000`, decoded with ESP-IDF 5.4.1 `gen_esp32part.py`, showed NVS at `0x9000` for 24 KiB, `phy_init` at `0xf000` for 4 KiB, and a factory app at `0x10000` for 1 MiB. The first 3072 bytes of the board read matched the local build's partition binary; the remaining bytes in the 4096-byte read were erased padding. The current app image is about 954 KiB. The [configuration contract](../../contracts/config.schema.json) and [BLE transaction draft](../protocol/ble-v1.md) allow a 64 KiB JSON document. A 64 KiB staged document plus an active generation cannot fit safely in the existing NVS partition. The existing app slot also leaves little growth room.

## Migration layout

Use one reviewed USB migration to a custom partition table before enabling full configuration transfer or OTA. The [16 MB partition candidate](partitions-16mb-proposal.csv) keeps the current NVS and PHY offsets, reserves two 128 KiB configuration data partitions, and allocates two 3 MiB OTA application slots. The candidate's first OTA slot starts at `0x60000`; the old factory app starts at `0x10000`, where the candidate places OTA metadata and configuration storage. This is a migration layout, not a partition table to activate through an ordinary app update. Its unused upper flash remains unassigned until requirements for logs or catalog cache are measured. Preserving NVS bytes at their current offset may retain owner and bond records, but the migration must explicitly decide whether keeping them is safe and verify behavior on a backed-up development unit; otherwise perform an intentional owner reset and explain re-pairing in the app.

The candidate parses and passes ESP-IDF 5.4.1 `gen_esp32part.py --flash-size 16MB` verification. That confirms bounds and alignment only. It does not prove boot selection, OTA rollback, bond preservation, configuration power-loss handling, or an in-place migration.

The config partitions hold bounded bytes, not executable code. Each generation has a header with magic, schema version, document length, SHA-256, generation number, and commit marker. The device writes only the inactive slot. The active slot remains untouched until the inactive slot passes length, digest, schema, semantic, budget, and capability validation.

## Atomic apply

1. `config.begin` checks authenticated owner, `baseRevision`, declared schema, length at most 64 KiB, and expected SHA-256. It reserves an inactive generation and returns a transfer ID and accepted offset.
2. `config.chunk` accepts only the next bounded offset. A disconnect leaves the active generation unchanged; a resumed owner must present the same transfer ID and hash before continuing.
3. `config.validate` checks the complete digest and every referenced source, PID definition, renderer, unit, threshold, and rate. No custom request becomes active merely because it is syntactically valid.
4. `config.commit` writes and rereads the inactive generation, records its commit marker, then changes the active pointer. Acknowledgment includes new revision and digest. Repeating a request ID returns the original outcome.
5. Boot selects the last valid committed generation. If active metadata is damaged, inspect both generation headers and digests, choose the newest valid committed generation, and report recovery to the app. Never boot an unvalidated staged document.

Firmware must keep the previous generation until the new one has booted and rendered successfully. Mark the newly selected generation as trial and clear that marker only after scheduler and display initialization complete; repeated early boots or validation failure choose the previous valid generation and report rollback. A trial marker must never cause a loop between generations. If commit succeeds but the BLE response is lost, Android queries revision and digest instead of sending a second blind commit. If the base revision changed, Android retains its local draft and offers reload/rebase. The app never labels a draft as applied based on an ATT write acknowledgment.

## Gate before implementation

The flash capacity and current on-device table are now observed. Before activating the candidate, review the slot sizes against a measured release image, define the bootable image and `otadata` initialization sequence, specify whether the existing NVS owner/bond records survive or are intentionally reset, and define power-loss recovery. This migration requires an explicit USB flash procedure because the current factory app occupies the region where the candidate places OTA metadata and configuration data. Keep full `configWrite` capability false until the layout and transaction are implemented and exercised. The quick-selection path remains explicitly separate so no 64 KiB document is promised by the current firmware.

Android must map legacy local profile UUIDs to schema-conforming `vehicleProfileId` values without silently renaming the user's local profile or losing its draft. New local IDs already use a `vehicle-` prefix. This mapping belongs in the explicit configuration export layer, with a stable persisted association before full Apply is enabled.
