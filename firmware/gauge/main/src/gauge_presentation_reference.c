#include "gauge_presentation.h"
#include "lvgl.h"

#define GAUGE_SYMBOL_PX(n) (n)
#include "gauge_symbol_paths.inc"

extern const lv_font_t notosans_semibold_64;
extern const lv_font_t notosans_semibold_32;
extern const lv_font_t notosans_semibold_48;
extern const lv_font_t notosans_semibold_40;
extern const lv_font_t notosans_medium_16;
extern const lv_font_t notosans_medium_24;

const gauge_presentation_t *gauge_presentation(void)
{
    static const gauge_presentation_t profile = {
        .surface  = &gauge_reference_surface,
        .title    = &notosans_semibold_64,
        .compact  = &notosans_semibold_32,
        .large    = &notosans_semibold_48,
        .dual     = &notosans_semibold_40,
        .subtitle = &notosans_medium_16,
        .unit     = &notosans_medium_24,
        .symbols  = GAUGE_SYMBOL_PATHS,
    };
    return &profile;
}
