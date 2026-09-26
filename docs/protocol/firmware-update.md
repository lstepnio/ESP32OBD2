# Firmware updates and recovery, draft 0.1

The development gauge now runs the two-slot layout and a rollback-enabled bootloader. Firmware has an experimental owner-only BLE transfer and activation path described in [experimental firmware transfers](experimental-firmware-transfers.md). A paired Pixel completed a [signed development BLE update](../development/update-live-validation.md), a [changed-build retry after phone interruption](../development/update-recovery-validation.md), and a fresh delivery after a gauge reset during transfer. Owner identity reads confirmed the expected valid image after each attempt. An isolated unconfirmed trial was marked aborted, the previous valid image booted, and Android reported the fallback. Production signing, downgrade prevention and physical power-loss recovery remain outstanding. Public `ota` remains false. BLE updates do not change the bootloader or partition table.

For local integration work, `python3 tools/sign_dev_update.py firmware/gauge/build/esp32-idf-project.bin --bundle /tmp/egauge-dev-update.zip` creates a development ZIP with exactly `metadata.json` and `firmware.bin`. The metadata carries board tag, image length, SHA-256, and a DER P-256 signature over the firmware transfer's 40 signed bytes. The private key remains outside Git. The Android development installer imports this package and separately verifies its image and signature; creating the package alone does not activate an update. It is not a production release manifest.

## Proposed 16 MiB layout

| Partition | Offset | Size | Purpose |
| --- | --- | --- | --- |
| nvs | 0x9000 | 0x6000 | Settings/bonds metadata |
| phy_init | 0xF000 | 0x1000 | Radio |
| otadata | 0x10000 | 0x2000 | Redundant boot selection |
| config_a | 0x12000 | 0x20000 | Configuration generation A |
| config_b | 0x32000 | 0x20000 | Configuration generation B |
| reserved | 0x52000 | 0xE000 | Alignment |
| ota_0 | 0x60000 | 0x300000 | App A, 3 MiB |
| ota_1 | 0x360000 | 0x300000 | App B, 3 MiB |

The remaining flash is unassigned. This table is installed on the development gauge and is defined by [partitions.csv](../../firmware/gauge/partitions.csv). App slots are 64 KiB aligned. Configuration commit uses its own validated two-generation flash store.

## Artifact trust

Release bundle should include exact board ID/revision, chip target, image size/hash, monotonically increasing release sequence, semantic version, protocol/config compatibility ranges, minimum bootloader, partition-layout ID, release notes hash, channel and signing key ID. See [manifest schema](../../contracts/release-manifest.schema.json). The experimental firmware path currently verifies a pinned P-256 signature over its board tag, image size and SHA-256. A production release should sign canonical UTF-8 JSON (RFC 8785/JCS) bytes as detached metadata, and both phone and gauge should validate metadata signature, board/partition compatibility, size and SHA-256. ESP-IDF signed-app verification would provide an independent image check. Specify and validate the ESP secure-image key format separately from the metadata key, rather than assuming those signatures are interchangeable.

Keep private signing keys outside the repo/ordinary CI; a protected release job receives narrow signing access. Plan key rotation with an old-key-signed trust-set change before revocation. Reject normal version downgrades; an explicit recovery policy can select the last known valid slot. No eFuse anti-rollback or secure-boot provisioning during prototype setup. Application verification without secure boot does not protect against hostile replacement through physical flashing.

## State machine

`idle -> preflight -> receiving -> verifying -> ready -> activating -> pending_boot -> confirmed`

Failure branches: `receiving -> interrupted -> receiving`; `verifying -> rejected`; `pending_boot -> rollback`. Progress displayed during receiving is acknowledged bytes/image length, then a separate verifying/restarting status. Never show “updated” before reconnect confirms the new build and healthy boot state.

1. Download verified bundle on phone. User starts update while stationary with stable power. An ESP32 board battery ADC does not prove vehicle supply stability; ask for stable power if no reliable observation exists.
2. Enter maintenance, stop OBD polling/discovery and DTC operations, show a static gauge update screen. Do not evaluate old samples as fresh alerts during maintenance.
3. Device validates manifest and free inactive slot, starts OTA write, accepts offset/CRC checked chunks with credits. Bound retry windows. The current boot slot remains selected until full verification.
4. On phone disconnect in the same device boot, retain OTA handle/offset for 10 minutes; rejoin only the same hash/owner. After timeout abort and restart. On power loss or device reboot during transfer, safely boot the old image and restart the transfer from zero. Durable cross-reboot resume is explicitly deferred.
5. Verify full image/hash/signatures; `ota.finish` only stages. `ota.activate` sets next slot and reboots. Persist operation/artifact ID for reconciliation.
6. New image checks configuration readability, UI task responsiveness and companion service startup within a bounded 30-second boot-health window, then confirms valid. Adapter/car availability is not a boot-health requirement. Enable IDF rollback. A failed or interrupted trial boot returns to the previous valid slot; phone reports rollback with reason.

Use the documented [ESP-IDF OTA and rollback APIs](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-reference/system/ota.html). Cross-version config compatibility and our operation journal are application responsibilities.

## Recovery and release acceptance

Power interruption before/after every erase/write/boot-selection boundary must retain a bootable image. Verify rejected wrong-board, truncated, altered-signature and altered-metadata bundles. Phone process death reconciles operation state, not replays activation. Keep USB recovery instructions and a known compatible production image available. Rollback must read the previous compatible config generation; migration cannot destroy that generation during trial boot. Failed update evidence appears in the app and gauge, never hides behind a spinner.

## Hardware-aware transport extension

BLE provides association/control and a universal update path; Wi-Fi is designed as a negotiated faster bulk transport sharing the same operation state, trust checks and recovery semantics. Two-adapter radio coexistence, board sensors, PSRAM, USB and power management are covered in the [hardware and transport strategy](../architecture/hardware-and-transports.md). Preferred production update transport remains subject to WIFI-001/RADIO-001 measurements.
