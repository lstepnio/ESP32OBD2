# Gauge fonts

`fonts.csv` is the inventory; generated C is checked in so normal builds need no
font tooling. Keep the six sizes used by the production UI. Noto Sans retains its
[SIL OFL notice](../../../../third_party/NotoSans-OFL.txt).

From the repository root, with Node/npm available:

```sh
firmware/gauge/scripts/mkfonts.sh --check
firmware/gauge/scripts/mkfonts.sh --write
```

The script uses lv_font_conv 1.5.3, stages every output before replacing the working
set, and preserves the original files on conversion failure. The converter arguments
retain the ASCII/degree character set (plus the middle dot in label fonts), 4 bpp and uncompressed format used by the UI.
`--check` detects both generated drift and extra/missing sizes. A font change also
requires production UI rendering and circular glyph checks through the firmware test target.
