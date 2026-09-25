# USB migration to the 16 MB gauge layout

Status: implementation procedure for a development board. This changes the boot partition table and cannot be delivered through the current factory application. Keep a verified USB backup until the new image, saved selection, and owner bond have been checked on hardware.

## Layout and boot selection

[`partitions.csv`](../../firmware/gauge/partitions.csv) keeps NVS at `0x9000` for `0x6000` bytes and PHY at `0xf000`. It adds erased OTA metadata at `0x10000`, two 128 KiB configuration slots at `0x12000` and `0x32000`, and 3 MiB OTA application slots at `0x60000` and `0x360000`. The old 1 MiB factory image at `0x10000` overlaps the new data partitions and part of `ota_0`; it cannot remain bootable during migration. In ESP-IDF 5.4.1, the bootloader selects `ota_0` when both OTA metadata entries are erased and no factory partition exists. The bootloader then initializes the OTA sequence. This behavior is specific to the reviewed IDF version and must be checked again before changing the SDK.

## Procedure

1. Identify the USB port and confirm the chip reports 16 MB. Stop any serial monitor. Build the firmware with the custom partition table. Check that the app fits `ota_0` and the generated table has the offsets above.
2. Read `0x0` through `0xfffff` into a private local backup under `firmware/gauge/build/migration-backup/`. This contains NimBLE bond material and the owner identity, so keep it out of Git and restrict file permissions. Record its SHA-256 and decode its partition table to confirm it is the old single-app layout. Do not proceed if the read is incomplete.
3. Write the new app binary to `0x60000` and require esptool's readback hash verification. From this point until the new table is installed, an interrupted migration requires USB recovery from the backup.
4. Erase `0x10000` through `0x5ffff` to clear old factory bytes from OTA metadata and both configuration slots. Do not erase NVS at `0x9000`.
5. Write the generated custom partition table to `0x8000` and require readback hash verification. Reset the board. Do not use `idf.py flash` for the first migration because its default flash argument order does not protect the old table while the new app is written.
6. Capture the boot log. Confirm the table lists `ota_0`, `ota_1`, `config_a`, and `config_b`, that the bootloader starts `ota_0`, and that the LCD runs. Read the protected saved state from the already bonded phone. Compare the saved reading and durable revision from before migration; no new PIN should be necessary. Read back NVS and compare it with the corresponding backup slice. ESP-IDF or NimBLE may legitimately append entries during startup, so investigate changed regions rather than requiring a whole-partition hash match.

The command sequence uses the ESP-IDF 5.4.1 Python environment and its `esptool.py`:

```sh
python esptool.py --chip esp32s3 -p "$PORT" write_flash 0x60000 build/esp32-idf-project.bin
python esptool.py --chip esp32s3 -p "$PORT" erase_region 0x10000 0x50000
python esptool.py --chip esp32s3 -p "$PORT" write_flash 0x8000 build/partition_table/partition-table.bin
```

Run these only after the backup and offset checks. The backup can restore the old bootloader, partition table, NVS, and factory image with `write_flash 0x0 pre-migration-first-meg.bin` over USB. If the migration succeeded and the gauge later changed its NVS data, restoring this older backup also restores the older owner, bond, and reading state. The backup should therefore be treated as a recovery point, not a routine downgrade method.

## Scope after migration

The partition layout reserves space for two configuration generations and a second application slot. It does not by itself implement staged writes, semantic validation, atomic activation, rollback, OTA updates, or vehicle diagnostics. Firmware must continue to advertise `configWrite: false` and `ota: false` until those paths are implemented and verified.
