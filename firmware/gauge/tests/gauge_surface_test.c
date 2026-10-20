#include <assert.h>
#include <limits.h>
#include <stddef.h>
#include <stdint.h>
#include "gauge_surface.h"

/* Baseline before extraction: preserve every reference point and sampled glyph box. */
static bool legacy_fits(int left, int top, int right, int bottom)
{
    if (left > right || top > bottom || left < 0 || top < 0 || right > 240 || bottom > 240)
        return false;
    int xs[2] = {left - 120, right - 120}, ys[2] = {top - 120, bottom - 120};
    for (unsigned x = 0; x < 2; ++x)
        for (unsigned y = 0; y < 2; ++y)
            if (xs[x] * xs[x] + ys[y] * ys[y] > 112 * 112)
                return false;
    return true;
}

int main(void)
{
    assert(gauge_surface_valid(&gauge_reference_surface));
    assert(gauge_surface_uses_reference_templates(&gauge_reference_surface));
    assert(!gauge_surface_uses_reference_templates(NULL));
    for (int x = -1; x <= 241; ++x)
        for (int y = -1; y <= 241; ++y)
            assert(gauge_surface_fits(&gauge_reference_surface, x, y, x, y) == legacy_fits(x, y, x, y));
    uint32_t seed = 7;
    for (unsigned sample = 0; sample < 100000; ++sample)
    {
        int bounds[4];
        for (unsigned i = 0; i < 4; ++i)
        {
            seed      = seed * 1664525U + 1013904223U;
            bounds[i] = (int)(seed % 260U) - 10;
        }
        assert(gauge_surface_fits(&gauge_reference_surface, bounds[0], bounds[1], bounds[2], bounds[3]) ==
               legacy_fits(bounds[0], bounds[1], bounds[2], bounds[3]));
    }
    gauge_surface_t rectangle = {800, 480, GAUGE_SURFACE_RECTANGLE, 8, 4};
    assert(!gauge_surface_uses_reference_templates(&rectangle));
    assert(gauge_surface_fits(&rectangle, 8, 8, 792, 472));
    assert(!gauge_surface_fits(&rectangle, 7, 8, 792, 472));
    assert(!gauge_surface_fits(&rectangle, 8, 8, 793, 472));
    gauge_surface_t round = {466, 466, GAUGE_SURFACE_ROUND, 8, 4};
    assert(!gauge_surface_uses_reference_templates(&round));
    assert(gauge_surface_fits(&round, 233, 8, 233, 8));
    assert(!gauge_surface_fits(&round, 8, 8, 8, 8));
    round.width = round.height = 467;
    assert(gauge_surface_fits(&round, 233, 9, 233, 9));
    assert(!gauge_surface_fits(&round, 233, 8, 233, 8));
    round.width = round.height = UINT16_MAX;
    assert(gauge_surface_fits(&round, 32767, 32767, 32768, 32768));
    assert(!gauge_surface_fits(&round, 8, 8, 8, 8));
    assert(!gauge_surface_fits(&round, INT_MIN, INT_MIN, INT_MAX, INT_MAX));
    assert(!gauge_surface_valid(NULL));
    gauge_surface_t invalid[] = {
        {0, 240, GAUGE_SURFACE_ROUND, 8, 4},     {240, 480, GAUGE_SURFACE_ROUND, 8, 4}, {240, 240, 99, 8, 4},
        {240, 240, GAUGE_SURFACE_ROUND, 120, 4}, {240, 240, GAUGE_SURFACE_ROUND, 4, 8},
    };
    for (unsigned i = 0; i < sizeof(invalid) / sizeof(invalid[0]); ++i)
        assert(!gauge_surface_valid(&invalid[i]));
    return 0;
}
