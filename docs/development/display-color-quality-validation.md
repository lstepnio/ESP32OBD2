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

## Evidence status

Source review and an ESP-IDF 5.4.1 firmware build confirm the corrected transport path compiles for the target. Physical confirmation requires installing the GitHub-hosted development image and comparing the same gauge page on the panel. Until that check is complete, the correction is not recorded as physically validated.

If the new image still shows incorrect primary colors, the next diagnostic is a full-screen red, green, blue, gray and gradient test page. Panel gamma or initialization tuning should be based on that evidence rather than changing RGB order or inversion speculatively.
