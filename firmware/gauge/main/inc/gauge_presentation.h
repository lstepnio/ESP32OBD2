#pragma once

#include "gauge_surface.h"
#include "misc/lv_area.h"

struct lv_font_t;

typedef enum
{
    GAUGE_SYMBOL_ENGINE_OUTLINE,
    GAUGE_SYMBOL_ENGINE_PIPE,
    GAUGE_SYMBOL_ENGINE_JOIN,
    GAUGE_SYMBOL_WRENCH_HANDLE,
    GAUGE_SYMBOL_WRENCH_HEAD,
    GAUGE_SYMBOL_WARNING_OUTLINE,
    GAUGE_SYMBOL_CRITICAL_OUTLINE,
    GAUGE_SYMBOL_WARNING_STEM,
    GAUGE_SYMBOL_WARNING_DOT,
    GAUGE_SYMBOL_ARROW_UP,
    GAUGE_SYMBOL_ARROW_DOWN,
    GAUGE_SYMBOL_COUNT,
} gauge_symbol_id_t;

/* Immutable native-pixel resources. One profile is linked for the whole image. */
typedef struct
{
    const gauge_surface_t    *surface;
    const struct lv_font_t   *title;
    const struct lv_font_t   *compact;
    const struct lv_font_t   *large;
    const struct lv_font_t   *dual;
    const struct lv_font_t   *subtitle;
    const struct lv_font_t   *unit;
    const lv_point_precise_t *symbols[GAUGE_SYMBOL_COUNT];
} gauge_presentation_t;

const gauge_presentation_t *gauge_presentation(void);
bool                        gauge_presentation_matches(const gauge_surface_t *surface);

/* The shared composition uses a centered square design grid. Only geometry is
 * converted; glyphs are rasterized at their native font size, never transformed. */
int  gauge_presentation_px(int design_pixels);
int  gauge_presentation_x(int design_x);
int  gauge_presentation_y(int design_y);
int  gauge_presentation_design_x(int native_x);
int  gauge_presentation_design_y(int native_y);
bool gauge_presentation_fits(int left, int top, int right, int bottom);
