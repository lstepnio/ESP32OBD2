# Authenticated hardware capacity snapshot

Status: implemented in source as a read-only protocol-0 owner operation. Physical gauge and Pixel evidence must be recorded after the firmware and app are installed.

## Purpose

The snapshot lets the companion app inspect actual runtime capacity without parsing logs or inferring hardware from a firmware version. It reports live memory availability, low watermarks, flash size, processor details, uptime, reset reason, Wi-Fi initialization state, and initialized board subsystems.

The public capability characteristic advertises `hardwareCapacity: 1` so older firmware remains safely distinguishable. Detailed hardware state is available only through the encrypted, authenticated owner control and state characteristics.

## Request

Android writes five bytes to the protected control characteristic:

| Offset | Size | Value |
| --- | --- | --- |
| 0 | 1 | Opcode `0x34` |
| 1 | 4 | Request sequence, little endian; currently informational and set to 1 |

The next protected state read returns one immutable 56-byte snapshot. Reading does not initialize a subsystem, change radio mode, sample the battery ADC, or communicate with the IMU.

## Version 10 response

All multibyte integers are unsigned and little endian.

| Offset | Size | Field |
| --- | --- | --- |
| 0 | 1 | Response version, `10` |
| 1 | 1 | Snapshot schema, `1` |
| 2 | 1 | ESP-IDF chip model identifier |
| 3 | 1 | Processor core count |
| 4 | 2 | Chip revision |
| 6 | 2 | Configured CPU frequency in MHz |
| 8 | 1 | ESP-IDF reset reason |
| 9 | 1 | ESP-IDF Wi-Fi mode, or `255` when the stack is not initialized |
| 10 | 2 | Reserved, zero |
| 12 | 4 | Features declared by this board design |
| 16 | 4 | Features initialized and observed in this boot |
| 20 | 4 | Physical flash bytes |
| 24 | 4 | Total internal heap bytes |
| 28 | 4 | Current free internal heap bytes |
| 32 | 4 | Minimum free internal heap bytes since boot |
| 36 | 4 | Largest free internal heap block bytes |
| 40 | 4 | Total PSRAM heap bytes |
| 44 | 4 | Current free PSRAM heap bytes |
| 48 | 4 | Minimum free PSRAM heap bytes since boot |
| 52 | 4 | Uptime seconds, wrapping after approximately 136 years |

Feature bits are Wi-Fi `0`, BLE `1`, PSRAM `2`, display `3`, touch `4`, backlight `5`, IMU `6`, battery ADC `7`, expansion connector `8`, and USB-to-UART `9`.

## Interpretation boundaries

Declared means the reviewed Waveshare board design provides the device or interface. Initialized means firmware observed or successfully initialized it during this boot. Neither field proves product qualification.

PSRAM presence comes from the runtime heap and ESP-IDF PSRAM state. Display, touch, and backlight initialization are marked only after their startup calls return. BLE readiness is sampled when the response is created. Wi-Fi reports initialized only when ESP-IDF returns a current mode.

IMU and battery ADC bits are declaration only in this version. A later probe must verify the QMI8658 identity on the shared I2C bus and implement calibrated, filtered ADC sampling before the app presents either as measured health. GPIO1 must not be described as vehicle voltage.

## Android behavior

Opening technical details triggers one snapshot read when a gauge is selected. The refresh button performs another read. The app validates response size, version, feature masks, processor bounds, flash bounds, and memory relationships before displaying the values. It does not poll continuously because maintenance reads should not compete with adapter traffic.
