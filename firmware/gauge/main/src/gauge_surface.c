#include "gauge_surface.h"
#include <stddef.h>

const gauge_surface_t gauge_reference_surface = {
    .width            = GAUGE_REFERENCE_WIDTH,
    .height           = GAUGE_REFERENCE_HEIGHT,
    .shape            = GAUGE_SURFACE_ROUND,
    .content_inset    = GAUGE_REFERENCE_CONTENT_INSET,
    .decoration_inset = GAUGE_REFERENCE_DECORATION_INSET,
};

bool gauge_surface_valid(const gauge_surface_t *surface)
{
    if (surface == NULL || surface->width == 0 || surface->height == 0 ||
        (surface->shape != GAUGE_SURFACE_ROUND && surface->shape != GAUGE_SURFACE_RECTANGLE))
        return false;
    if (surface->shape == GAUGE_SURFACE_ROUND && surface->width != surface->height)
        return false;
    uint16_t shorter = surface->width < surface->height ? surface->width : surface->height;
    return surface->content_inset >= surface->decoration_inset && (uint32_t)surface->content_inset * 2U < shorter;
}

bool gauge_surface_uses_reference_templates(const gauge_surface_t *surface)
{
    return gauge_surface_valid(surface) && surface->width == GAUGE_REFERENCE_WIDTH &&
           surface->height == GAUGE_REFERENCE_HEIGHT && surface->shape == GAUGE_SURFACE_ROUND &&
           surface->content_inset == GAUGE_REFERENCE_CONTENT_INSET &&
           surface->decoration_inset == GAUGE_REFERENCE_DECORATION_INSET;
}

bool gauge_surface_fits(const gauge_surface_t *surface, int left, int top, int right, int bottom)
{
    if (!gauge_surface_valid(surface) || left > right || top > bottom || left < surface->content_inset ||
        top < surface->content_inset || right > surface->width - surface->content_inset ||
        bottom > surface->height - surface->content_inset)
        return false;
    if (surface->shape == GAUGE_SURFACE_RECTANGLE)
        return true;

    /* Doubled coordinates retain half-pixel centers for odd native dimensions. */
    int64_t xs[2]  = {(int64_t)left * 2 - surface->width, (int64_t)right * 2 - surface->width};
    int64_t ys[2]  = {(int64_t)top * 2 - surface->height, (int64_t)bottom * 2 - surface->height};
    int64_t radius = surface->width - (int64_t)surface->content_inset * 2;
    for (unsigned x = 0; x < 2; ++x)
        for (unsigned y = 0; y < 2; ++y)
            if (xs[x] * xs[x] + ys[y] * ys[y] > radius * radius)
                return false;
    return true;
}
