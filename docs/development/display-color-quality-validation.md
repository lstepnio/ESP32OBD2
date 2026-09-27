# Display color quality correction

Recorded 2026-09-27 for the Waveshare ESP32-S3-Touch-LCD-1.28.

## Physical evidence before the correction

The gauge showed a washed blue-gray background, strong cyan and pink fringes around the arc, and uneven-looking antialiased edges. Black and white pixels were mostly recognizable while chromatic pixels were not. This observation came from the physical gauge and is distinct from simulator or source-level behavior.

## Root cause

The GC9A01 SPI panel consumes each RGB565 pixel most-significant byte first. LVGL stores the 16-bit value in the ESP32-S3's native little-endian order. The display port did not enable byte swapping, so the panel decoded the two bytes in reverse order. Pure black and white remain similar after reversal, which hid the transport problem, while colored and antialiased pixels became unrelated colors.

The board configuration already matched the vendor example in two other important respects: RGB element order and display inversion. Those settings were retained.

## Implemented correction

- Enable `swap_bytes` on the LVGL SPI display path.
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

These are hosted-update and runtime observations. Physical color and uniformity confirmation still requires comparing the corrected page on the panel. Until that check is complete, the display-quality result is not recorded as physically validated.

If the new image still shows incorrect primary colors, the next diagnostic is a full-screen red, green, blue, gray and gradient test page. Panel gamma or initialization tuning should be based on that evidence rather than changing RGB order or inversion speculatively.
