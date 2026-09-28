# Display color quality correction

Recorded 2026-09-27 for the Waveshare ESP32-S3-Touch-LCD-1.28.

## Physical evidence before the correction

The gauge showed a washed blue-gray background, strong cyan and pink fringes around the arc, and uneven-looking antialiased edges. Black and white pixels were mostly recognizable while chromatic pixels were not. This observation came from the physical gauge and is distinct from simulator or source-level behavior.

## Root causes

Two independent format mismatches affected the panel:

1. The GC9A01 SPI panel consumes each RGB565 pixel most-significant byte first. LVGL stores the 16-bit value in the ESP32-S3's native little-endian order. The display port did not enable byte swapping, so the panel decoded the two bytes in reverse order. Pure black and white remain similar after reversal, which hid the transport problem, while colored and antialiased pixels became unrelated colors.
2. The physical board requires the GC9A01 driver to use BGR element order. With byte swapping corrected but RGB element order selected, the calibration page displayed intended red as blue and intended blue as amber. Cyan and yellow were reversed as well.

Display inversion remains enabled as required by the board.

## Implemented correction

- Enable `swap_bytes` on the LVGL SPI display path.
- Select BGR element order in the GC9A01 panel configuration.
- Drive the GPIO2 backlight with 20 kHz LEDC PWM instead of a permanent full-output GPIO level.
- Apply the configuration's validated 80% brightness at startup.
- Replace pure white and saturated accents with a lower-bloom night palette.
- Reduce the main arc width from 10 to 7 pixels and use rounded ends for a cleaner 240-pixel circular presentation.

The current configuration contract still accepts brightness 80 only. General app-controlled brightness remains gated until the physical panel's usable range and low-light behavior are characterized.

## Installation and runtime evidence

PR #24 passed the contract, documentation, Android and firmware jobs. The protected release workflow published signed catalog generation 12 and `0.2.0-dev.20`. A clean GitHub download passed catalog and image-signature verification, and its image descriptor reported the expected version.

The owner-authenticated Pixel discovered the compatible release, downloaded and verified it, transferred it through the automatic private Wi-Fi path, and reported the new image healthy at `ota_0` offset `0x60000`. A subsequent serial reboot confirmed:

- application version `0.2.0-dev.20` and the expected ELF SHA-256 prefix;
- confirmed boot from `ota_0`;
- retained configuration revision 4 with five PIDs, five pages and one alert;
- initialized display, CST816S touch and BLE;
- GPIO2 backlight PWM applied at 80%.

These are hosted-update and runtime observations for `0.2.0-dev.20`.

## Physical calibration evidence

A temporary USB calibration build presented five touch-cycled pages on the physical gauge. The first pass exposed the remaining red/blue channel reversal. After changing the panel element order to BGR, a second set of photos recorded on 2026-09-28 showed:

- correct red, green and blue blocks across the upper half;
- correct cyan, magenta and yellow blocks across the lower half;
- ordered black, near-black, middle gray, light gray and near-white bands;
- complete reference rings at radii 96, 104, 112 and 118;
- a six-character `16,383` value fitting cleanly in the 32 px compact font;
- the production-style RPM page rendering teal progress, blue track, near-white value text and gray secondary text as intended.

The camera still records pixel-grid moire and colored fringes around some bright text, especially at an angle. Those artifacts vary with camera position and do not appear in the solid-color channel test. They are treated as camera and panel optics evidence rather than another RGB transport error.

The reusable calibration mode is `CONFIG_EGAUGE_DISPLAY_CALIBRATION`. It must remain disabled in release firmware because it intentionally skips BLE and OBD startup. The measured geometry and typography rules are recorded in [Round display UI guidelines](../design/round-display-ui-guidelines.md).

## Normal firmware restoration

After calibration, the default-off calibration option and BGR correction were built as normal firmware `0.2.0-dev.21`. The application-only image was written over USB to `ota_0` at `0x60000`, and a separate `esptool verify_flash` operation matched its digest. The serial boot log then confirmed:

- application version `0.2.0-dev.21` from `ota_0`;
- retained configuration revision 4 with five PIDs, five pages and one alert;
- successful 240 x 240 display and CST816S touch initialization;
- 80% PWM backlight application;
- BLE initialization and normal RX/TX task startup;
- no calibration-mode warning.

The six-color mapping, neutral bands, boundary rings and typography are physical observations from the temporary calibration build. The normal-runtime startup list is serial evidence. A final physical photo of the normal build is useful for presentation review but is not needed to establish the corrected channel mapping.

## Hosted artifact verification

PR #26 passed the contract, documentation, Android and firmware jobs and was merged. The protected development release workflow then published `0.2.0-dev.21` as catalog generation 13. A clean GitHub download was checked independently from the workflow:

- catalog signature verified with the pinned development public key;
- catalog generation and release version matched 13 and `0.2.0-dev.21`;
- bundle size and SHA-256 matched the signed catalog;
- bundle contained only `metadata.json` and `firmware.bin`;
- image length and SHA-256 matched the bundle metadata;
- ESP application descriptor reported `0.2.0-dev.21`;
- image signature verified with the pinned development public key.

The verified hosted image SHA-256 is `0516abab1baaaa12cd8f5355302b1ef1ab8405ea0e0461e857911c8aa78a921f`. The bundle SHA-256 is `8b9cc577ef654939c6bdd59807d8ccab04fbb0e48a4b339045c4ae3fdc06888f`.

That exact downloaded `firmware.bin` was written over USB to `ota_0` and passed a separate flash digest comparison. Its subsequent serial boot reported application version `0.2.0-dev.21`, ELF SHA-256 prefix `2afea7191`, retained configuration revision 4, successful display and touch initialization, 80% backlight and normal RX/TX task startup.
