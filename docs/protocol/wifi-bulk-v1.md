# Experimental authenticated Wi-Fi bulk transport

Status: enabled in the development build after live Pixel and gauge qualification on 2026-09-26. The capability remains explicitly experimental while session-expiry, physical power-loss, and precisely timed activation interruption cases are completed.

## User experience

The user never enters a Wi-Fi network name or password. An authenticated owner operation over BLE asks the gauge to create a temporary WPA2 maintenance network. The gauge generates the network password and a separate 256-bit application session key, and returns both only through the encrypted and authenticated owner characteristic. Android uses `WifiNetworkSpecifier` to request that network and binds only the bulk socket to it. Android may show its standard one-time network consent sheet. Other phone traffic retains its normal route.

The gauge allows one Wi-Fi station, expires the session after ten minutes, and stops the access point on close, expiry, or reboot. Network credentials and session keys are generated in RAM and are never persisted or logged.

## BLE negotiation

Commands use the existing authenticated control characteristic. Every command contains an opcode and little-endian `u32 sequence`.

| Opcode | Meaning |
| --- | --- |
| `40` | Open a new temporary maintenance network and rotate all credentials |
| `42` | Close the network and erase the in-memory session |

Protected state version `09` is 112 bytes. Phase, result, opcode and sequence occupy bytes 1 through 7. Bytes 8 through 11 contain the IPv4 address, 12 through 13 the little-endian TCP port, 16 through 19 the session ID, 20 through 51 the AES-256 key, and 52 through 55 the remaining session lifetime in seconds. Byte 56 is the SSID length and byte 57 is the password length. The SSID starts at byte 58 and the password at byte 90. Secret fields are zero unless phase 2 is ready.

Public capabilities advertise `wifiBulk: experimental-softap-aead-v2` in the current qualified development build. Android retains the v1 single-command sender for older gauges. Discovery alone never exposes credentials or authorizes the transport.

## Encrypted frames

The TCP listener uses port 7331. Each frame starts with a 16-byte authenticated header:

| Offset | Field |
| --- | --- |
| 0 | ASCII `EGW1` |
| 4 | little-endian session ID |
| 8 | strictly increasing little-endian frame sequence |
| 12 | kind: 1 configuration, 2 OTA, 3 status, 4 OTA batch |
| 13 | response error flag; zero in requests |
| 14 | little-endian ciphertext length |

The header is followed by a 16-byte GCM tag and the ciphertext. AES-256-GCM uses the BLE-delivered session key. Its 12-byte nonce contains the session ID, frame sequence and direction byte. Request direction is zero and response direction is one. The complete header is additional authenticated data. Firmware rejects an expired session, wrong ID, repeated or decreasing frame sequence, invalid tag, oversized payload, unknown kind, and commands outside the existing configuration and OTA ranges.

Kinds 1 and 2 carry one unchanged transfer command. Firmware queues it in the same configuration or OTA worker and returns the existing status structure after the matching transfer sequence completes.

Kind 4 reduces network and AEAD round trips without creating another update state machine. Its plaintext starts with a command count from 1 through 8. Each entry is a little-endian `u16` command length followed by one complete existing OTA chunk command. Firmware caps the frame at 8,448 bytes, validates the entire structure and every opcode before executing the first entry, then passes each command through the original OTA worker in order. Every worker response must match its command and report success. The response is the executed command count followed by the final 56-byte OTA status. Android verifies the count, final sequence, opcode, result, and accepted offset.

The larger request and plaintext buffers are allocated once from PSRAM. The OTA queue remains bounded to one original command, so batching does not multiply worker memory or bypass the existing transfer gate. This preserves the SHA-256, signed image, per-chunk offset, one-writer, activation and rollback behavior.

## Operational boundaries

- Wi-Fi is off by default and intended for maintenance only.
- OTA transfer pauses ECM polling through the existing transfer gate.
- Android requests at most one local network and releases it when the operation closes.
- Application-layer AEAD protects the local socket independently of WPA2.
- BLE remains the ownership bootstrap, capability source and post-reboot health channel.
- Live encrypted transfer, signed activation, alternate-partition boot, health confirmation and BLE readback are recorded in [Wi-Fi bulk validation](../development/wifi-bulk-validation.md).
- A live owner-authenticated check rejected wrong-session, wrong-key, and replay frames; see [Wi-Fi transport security validation](../development/wifi-transport-security-validation.md).
- Session expiry, active attack testing, physical power-loss interruption, and recovery still require live qualification before removing the experimental label.
