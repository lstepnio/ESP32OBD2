#include "gauge_presentation.h"

bool gauge_presentation_matches(const gauge_surface_t *surface)
{
    const gauge_presentation_t *p = gauge_presentation();
    if (!p || !gauge_surface_valid(surface) || !gauge_surface_valid(p->surface) || !p->title || !p->compact ||
        !p->large || !p->dual || !p->subtitle || !p->unit)
        return false;
    for (unsigned i = 0; i < GAUGE_SYMBOL_COUNT; i++)
        if (!p->symbols[i])
            return false;
    const gauge_surface_t *s = p->surface;
    return surface->width == s->width && surface->height == s->height && surface->shape == s->shape &&
           surface->content_inset == s->content_inset && surface->decoration_inset == s->decoration_inset;
}

int gauge_presentation_px(int design_pixels)
{
    const gauge_surface_t *s     = gauge_presentation()->surface;
    int                    side  = s->width < s->height ? s->width : s->height;
    int64_t                value = (int64_t)design_pixels * side;
    return (int)((value + (value < 0 ? -120 : 120)) / 240);
}

int gauge_presentation_x(int design_x)
{
    const gauge_surface_t *s    = gauge_presentation()->surface;
    int                    side = s->width < s->height ? s->width : s->height;
    return (s->width - side) / 2 + gauge_presentation_px(design_x);
}

int gauge_presentation_y(int design_y)
{
    const gauge_surface_t *s    = gauge_presentation()->surface;
    int                    side = s->width < s->height ? s->width : s->height;
    return (s->height - side) / 2 + gauge_presentation_px(design_y);
}

static int design_position(int native, int dimension)
{
    const gauge_surface_t *s     = gauge_presentation()->surface;
    int                    side  = s->width < s->height ? s->width : s->height;
    int64_t                value = ((int64_t)native - (dimension - side) / 2) * 240;
    return (int)((value + (value < 0 ? -side / 2 : side / 2)) / side);
}

int gauge_presentation_design_x(int native_x)
{
    return design_position(native_x, gauge_presentation()->surface->width);
}

int gauge_presentation_design_y(int native_y)
{
    return design_position(native_y, gauge_presentation()->surface->height);
}

bool gauge_presentation_fits(int left, int top, int right, int bottom)
{
    return gauge_surface_fits(gauge_presentation()->surface, left, top, right, bottom);
}
