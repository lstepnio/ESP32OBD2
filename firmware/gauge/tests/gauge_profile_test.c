#include "gauge_presentation.h"
#include <assert.h>
#include <stddef.h>
#include <stdio.h>

static gauge_presentation_t profile;
const gauge_presentation_t *gauge_presentation(void)
{
    return &profile;
}

int main(void)
{
    static const int resource;
    profile = (gauge_presentation_t){.surface  = &gauge_reference_surface,
                                     .title    = (const struct lv_font_t *)&resource,
                                     .compact  = (const struct lv_font_t *)&resource,
                                     .large    = (const struct lv_font_t *)&resource,
                                     .dual     = (const struct lv_font_t *)&resource,
                                     .subtitle = (const struct lv_font_t *)&resource,
                                     .unit     = (const struct lv_font_t *)&resource};
    static const lv_point_precise_t point;
    for (unsigned i = 0; i < GAUGE_SYMBOL_COUNT; i++)
        profile.symbols[i] = &point;
    assert(gauge_presentation_matches(&gauge_reference_surface));
    for (int i = -500; i <= 500; i++)
    {
        assert(gauge_presentation_px(i) == i);
        assert(gauge_presentation_x(i) == i);
        assert(gauge_presentation_y(i) == i);
        assert(gauge_presentation_design_x(i) == i);
        assert(gauge_presentation_design_y(i) == i);
    }
    assert(!gauge_presentation_matches(NULL));
    gauge_surface_t wrong = gauge_reference_surface;
    wrong.content_inset++;
    assert(!gauge_presentation_matches(&wrong));
    profile.title = NULL;
    assert(!gauge_presentation_matches(&gauge_reference_surface));
    profile.title                          = (const struct lv_font_t *)&resource;
    profile.symbols[GAUGE_SYMBOL_ARROW_UP] = NULL;
    assert(!gauge_presentation_matches(&gauge_reference_surface));
    profile.symbols[GAUGE_SYMBOL_ARROW_UP] = &point;
    gauge_surface_t large                  = {480, 480, GAUGE_SURFACE_ROUND, 16, 8};
    profile.surface                        = &large;
    assert(gauge_presentation_matches(&large));
    assert(gauge_presentation_px(11) == 22);
    assert(gauge_presentation_x(120) == 240);
    assert(gauge_presentation_design_y(64) == 32);
    assert(gauge_presentation_fits(240, 16, 240, 16));
    assert(!gauge_presentation_fits(240, 15, 240, 16));
    large = (gauge_surface_t){641, 481, GAUGE_SURFACE_RECTANGLE, 16, 8};
    assert(gauge_presentation_matches(&large));
    assert(gauge_presentation_px(240) == 481);
    assert(gauge_presentation_x(0) == 80);
    assert(gauge_presentation_x(120) == 321);
    assert(gauge_presentation_y(120) == 241);
    assert(gauge_presentation_px(-7) == -gauge_presentation_px(7));
    for (int i = 0; i <= 240; i++)
    {
        assert(gauge_presentation_design_x(gauge_presentation_x(i)) == i);
        assert(gauge_presentation_design_y(gauge_presentation_y(i)) == i);
    }
    large.width = 0;
    assert(!gauge_presentation_matches(&large));
    puts("PASS: native profile matching, resources, reference parity and centered geometry");
}
