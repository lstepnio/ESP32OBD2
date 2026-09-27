# Active document readback on paired hardware

Observed on 2026-09-26 with the USB-connected ESP32-S3-Touch-LCD-1.28 and the paired Pixel 10 Pro over Wi-Fi ADB. The gauge had no OBD adapter attached.

The firmware and Android debug APK built successfully. Before flashing, a USB read of the two OTA metadata sectors showed sequence 3 in `ESP_OTA_IMG_VALID` state for `ota_0` and sequence 4 in `ESP_OTA_IMG_ABORTED` state for `ota_1`. The new `0.2.0-dev.2` application image was written only to active `ota_0` at `0x60000`; esptool verified the flash hash and reset the gauge. This left NVS, OTA metadata, and both configuration slots untouched. The APK was installed with `adb install -r`.

After the reset, the app discovered the gauge through the public capability read. On the already bonded owner link, **Verify saved configuration document** completed without a new pairing prompt. Android reconstructed the complete 2,712-byte document in bounded owner reads and verified SHA-256 against the digest returned by the gauge. The Device screen showed revision 2, profile ID `default`, three definitions, three pages, and one alert. Its status displayed digest prefix `6689a8922875`, matching the previously recorded active status prefix. This is a physical readback of the committed document after reboot, not live PID or adapter evidence.

The readback does not validate concurrent configuration commits during a read, maximum-size transfer timing, an unbonded caller, or a vehicle session. Public `configWrite` and `ota` remain false. No code clear was attempted.
