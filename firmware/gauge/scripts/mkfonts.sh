#!/usr/bin/env bash

CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ_DIR="$(dirname "$SCRIPT_DIR")"
CSV_PATH="assets/fonts/fonts.csv"
CSV_DIR="assets/fonts"

if ! command -v lv_font_conv >/dev/null 2>&1; then
    echo -e "${RED}Error: lv_font_conv not found in PATH.${NC}"
    exit 1
fi

if [[ ! -f "$PROJ_DIR/$CSV_PATH" ]]; then
    echo -e "${RED}Error: Fonts config file not found at $PROJ_DIR/$CSV_PATH${NC}"
    exit 1
fi

cd "$PROJ_DIR" || exit 1

if ! (rm -rf main/fonts && mkdir -p main/fonts); then
    echo -e "${RED}Error: Failed to recreate main/fonts directory.${NC}"
    exit 1
fi

while IFS=, read -r INPUT_FONT OUTPUT_PREFIX SIZES; do
    IFS=',' read -ra FIELDS <<< "$INPUT_FONT,$OUTPUT_PREFIX,$SIZES"
    INPUT_FONT="${FIELDS[0]}"
    OUTPUT_PREFIX="${FIELDS[1]}"
    for ((i=2; i<${#FIELDS[@]}; i++)); do
        SIZE="${FIELDS[i]}"
        INPUT_PATH="$CSV_DIR/$INPUT_FONT"
        OUTPUT_PATH="main/fonts/${OUTPUT_PREFIX}_${SIZE}.c"
        echo -e "${CYAN}[${OUTPUT_PREFIX}_$SIZE] $INPUT_PATH -> $OUTPUT_PATH${NC}"
        lv_font_conv \
            --font "$INPUT_PATH" \
            -r 0x20-0x7F,0xB0 \
            --size "$SIZE" \
            --format lvgl \
            --bpp 4 \
            --no-compress \
            -o "$OUTPUT_PATH"
    done
done < "$CSV_PATH"

# Invalidate the cmake to force a full rebuild, next time we run `idf build``
touch main/CMakeLists.txt
