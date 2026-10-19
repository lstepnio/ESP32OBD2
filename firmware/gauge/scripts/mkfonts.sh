#!/usr/bin/env bash
set -euo pipefail

PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---write}"
[[ "$MODE" == --write || "$MODE" == --check ]] || { echo 'Usage: mkfonts.sh [--check|--write]' >&2; exit 2; }
CONVERTER_VERSION=1.5.3
convert() {
    if [[ -n "${FONT_CONVERTER:-}" ]]; then
        "$FONT_CONVERTER" "$@"
    else
        npm exec --yes --package="lv_font_conv@$CONVERTER_VERSION" -- lv_font_conv "$@"
    fi
}
[[ "$(convert --version)" == "$CONVERTER_VERSION" ]] || { echo "Expected lv_font_conv $CONVERTER_VERSION" >&2; exit 1; }

# Stage on the same filesystem. Failed conversion never removes working fonts.
STAGE=$(mktemp -d "$PROJ_DIR/.fonts.XXXXXX")
cleanup() {
    result=$?
    if [[ -d "$STAGE/original" && ! -d "$PROJ_DIR/main/fonts" ]]; then
        mv "$STAGE/original" "$PROJ_DIR/main/fonts"
    fi
    rm -rf "$STAGE"
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$STAGE/main/fonts"
ln -s "$PROJ_DIR/assets" "$STAGE/assets"
cd "$STAGE"
while IFS=, read -r input prefix sizes; do
    [[ -n "$input" && -n "$prefix" && -n "$sizes" ]] || { echo 'Invalid font inventory' >&2; exit 1; }
    # Labels retain their middle-dot glyph; value fonts keep the smaller original set.
    ranges=0x20-0x7F,0xB0
    [[ "$prefix" != notosans_medium ]] || ranges+=,0xB7
    IFS=, read -r -a dimensions <<< "$sizes"
    for size in "${dimensions[@]}"; do
        [[ "$prefix" =~ ^[a-z_]+$ && "$size" =~ ^[1-9][0-9]*$ && "$input" != */* ]] || exit 1
        convert --font "assets/fonts/$input" -r "$ranges" --size "$size" \
            --format lvgl --bpp 4 --no-compress -o "main/fonts/${prefix}_${size}.c"
        test -s "main/fonts/${prefix}_${size}.c"
    done
done < "$PROJ_DIR/assets/fonts/fonts.csv"
# The converter adds a blank EOF line; retain the checked-in newline convention.
python3 - "$STAGE/main/fonts" <<'PYTHON'
from pathlib import Path
import sys
for path in Path(sys.argv[1]).glob("*.c"):
    path.write_bytes(path.read_bytes().rstrip(b"\n") + b"\n")
PYTHON
if [[ "$MODE" == --check ]]; then
    diff -qr "$PROJ_DIR/main/fonts" main/fonts
else
    mv "$PROJ_DIR/main/fonts" "$STAGE/original"
    mv main/fonts "$PROJ_DIR/main/fonts"
fi
