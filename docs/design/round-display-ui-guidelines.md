# Round display UI guidelines

These rules apply to the 240 x 240 GC9A01 panel on the Waveshare ESP32-S3-Touch-LCD-1.28. They come from physical calibration photos recorded on 2026-09-28, not from the browser prototype or a square framebuffer preview.

## Panel contract

- Logical size: 240 x 240 pixels.
- Circle center: `(120, 120)`.
- Pixel transport: RGB565 with LVGL byte swapping enabled.
- Panel element order: BGR.
- Display inversion: enabled by the board initialization sequence.
- Reference backlight: 80% using 20 kHz PWM.

Changing any transport setting requires repeating the six-color calibration page on physical hardware. A black-and-white page cannot prove correct RGB565 transport because byte and channel errors can leave black and white looking plausible.

## Circular zones

| Radius | Use | Physical result |
| ---: | --- | --- |
| 0 to 96 px | Primary reading and critical status | Conservative region with the strongest bezel clearance |
| 96 to 104 px | Essential-content limit | Complete and clearly visible around the tested panel |
| 104 to 112 px | Decorative graphics only | Visible, but increasingly affected by viewing angle and bezel masking |
| 112 to 118 px | Calibration and edge references | Too close to the physical edge for meaningful content |
| Greater than 118 px | Avoid | Outside the reliable circular presentation area |

Essential text, icons, alert meaning and touch affordances must fit inside radius 104. Rings, ticks and nonessential progress decoration may extend to radius 112. Do not place state meaning solely in the outer decorative band.

For a row centered at vertical coordinate `y`, the available chord within radius `r` is:

```text
width = 2 * sqrt(r^2 - (y - 120)^2)
```

Measure the full glyph box, including ascenders and descenders, against the narrowest chord it crosses. A rectangular width that fits at the screen center can be clipped near the top or bottom of the circle.

## Production gauge layout

The physically verified primary layout uses:

| Element | Top coordinate | Maximum width | Font |
| --- | ---: | ---: | --- |
| Short display label | 48 px | 160 px | 16 px subtitle |
| Primary value | 78 px | 176 px | 64 px title or 32 px compact |
| Unit | 158 px | 156 px | 24 px unit |

Use the 64 px title font only when the measured text fits the 176 px value box. Four digits such as `2840` fit in the physical test. Longer values must switch to the 32 px compact font; `16,383` was physically verified. If the compact font still does not fit, use the 16 px subtitle font and keep the complete value visible. Never wrap, scroll or silently truncate a primary numeric value.

Use short gauge labels such as `ENGINE RPM`, `COOLANT` and `INPUT SPEED`. Preserve the full descriptive name in the Android app and accessibility text. Units remain centered under the reading.

The physically verified baseline arc was 198 px in diameter with a 7 px stroke. Firmware `0.2.0-dev.29` uses a 218 px diameter and 11 px stroke. Its outer radius is 109 px, within the previously observed decorative band, while the essential reading and alert text remain inside radius 104. Keep rounded ends and avoid using the arc color as the only alert signal. The enlarged arc still needs physical review at normal viewing angles.

## Physically verified baseline palette

| Role | Hex | Notes |
| --- | --- | --- |
| Background | `#05080A` | Near-black to reduce backlight haze |
| Primary text | `#E4EAED` | Near-white to reduce bloom |
| Secondary text | `#9CAAB2` | Labels and units |
| Track | `#202A30` | Inactive arc and bar |
| Accent | `#26B895` | Normal active data |
| Warning | `#E0A63A` | Attention state |
| Critical | `#E75A5A` | Critical alert state |

Color must reinforce text or shape. Warning and critical states require a readable label because color perception and sunlight conditions vary.

Firmware `0.2.0-dev.29` uses a stronger palette for the track and active data: track `#29343A`, normal `#00D6A0`, warning `#FFB000`, and critical `#FF4D5A`. These are source-level choices pending physical display review. The background and text colors remain as physically verified above.

The next firmware palette increases saturation after the dev.30 gauge appeared washed out in physical review: track `#1B2A30`, normal `#00FF70`, warning `#FF8A00`, and critical `#FF1744`. Android previews and semantic colors use matching shared tokens. Physical review of the new image is still required.

The build, signed release, and Pixel update evidence for this change are recorded in [Arc visual refresh validation](../evidence/arc-visual-refresh-validation.md).

## Physical review checklist

For a new renderer or a layout change:

1. Check the design against the radius-104 essential-content circle.
2. Exercise missing, stale, negative, minimum and maximum values.
3. Exercise the longest label, unit and formatted numeric value.
4. Confirm one tap produces one page change and long press remains distinct.
5. Photograph the display head-on and at normal driver viewing angles in daylight and low light.
6. Separate camera moire and reflections from artifacts visible to the eye.
7. Record the firmware version, rotation, brightness and physical observation in the validation document.

The optional calibration build provides color, neutral, ring, typography and production-reference pages. Enable `CONFIG_EGAUGE_DISPLAY_CALIBRATION` only for a USB bench build. It disables the BLE and OBD runtime by design and must be off in distributed images.

## Prepared dev.54 compositor and Round 3 feedback

This implementation is prepared for device review; the earlier physical calibration
remains the color/geometry baseline, not proof of the new screens.

One compositor places unboxed CEL/count at y42, reading content in the middle,
condition/direction at y194 and Demo or page position at y213. Single numeric
centers are 87/126/169. The owner's Round 3 correction uses 75/114/157 for Numeric
conditions without CEL, raising the reading block by twelve pixels. CEL combinations
retain their approved geometry. Bar uses 70/108/147 with a six-pixel scale at174;
Trend uses 72/102/132 above its chart; Dual labels72/135 and values97/160.

Measure actual production glyph ink, not character count or advance width alone.
Complete values fit fonts64/48/40/32, step down only while the page/unit context is
unchanged, and show an explicit unavailable-layout state if none fits. Labels may
use bounded ellipsis; values must never be silently chopped. Unsupported units stay
explicit. RPM labels suppress a duplicate rpm unit; selected/target/actual gear and
input/output shaft roles remain distinct. Units follow the owner's saved setting.
Do not rename an unqualified controller temperature to ATF.

Top fault counts represent retrieved distinct codes across fresh categories per
controller. The same code on different controllers counts separately. Unknown counts
use a question mark; partial coverage uses a plus. MIL-reported count is a separate
quantity. Diagnostic and lower-condition projections reuse the shared event owner;
noncritical MIL does not obscure a simultaneous threshold warning. Critical diagnostic
attention can still take over. Missing readings are dashes and trend gaps stay gaps.

Critical attention and pairing use separate full-screen compositions. The showcase
is temporary presentation state, described in the [development guide](../development/display-showcase.md).
Production software captures and regression checks use `firmware/gauge/tests/ui_host`.
Future fault inspection, provider focus, new maintenance screens and custom enum
metadata remain design work; not all review mocks are implemented in dev.54.
