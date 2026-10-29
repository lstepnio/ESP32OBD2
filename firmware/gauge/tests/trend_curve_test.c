#include "trend_curve.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
int main(void)
{
    float values[] = {15, 15.2f, 15, 30, 0, 0};
    for (size_t i = 0; i < 6; i++)
        assert(fabsf(trend_curve_value(values, 6, i) - values[i]) < .0001f);
    for (size_t i = 0; i < 5; i++)
    {
        float previous = values[i];
        for (unsigned j = 0; j <= 100; j++)
        {
            float y = trend_curve_value(values, 6, i + j / 100.f);
            assert(y >= fminf(values[i], values[i + 1]) - .0001f);
            assert(y <= fmaxf(values[i], values[i + 1]) + .0001f);
            assert(values[i + 1] >= values[i] ? y >= previous - .0001f : y <= previous + .0001f);
            previous = y;
        }
    }
    assert(isnan(trend_curve_value(NULL, 0, 0)));
    assert(isnan(trend_curve_value(values, 6, NAN)));
    assert(trend_curve_value(values, 1, 0) == values[0]);
    uint8_t mask[TREND_MASK_BYTES];
    float   constant[TREND_CAPACITY];
    for (size_t i = 0; i < TREND_CAPACITY; i++)
        constant[i] = 15.25f;
    assert(trend_curve_mask(mask, constant, TREND_CAPACITY));
    assert(mask[17 * TREND_WIDTH + 70] == 255);
    assert(mask[19 * TREND_WIDTH + 70] > 0 && mask[19 * TREND_WIDTH + 70] < 255);
    assert(!mask[22 * TREND_WIDTH + 70]);
    assert(trend_curve_mask(mask, constant, 2));
    assert(!mask[17 * TREND_WIDTH + 70]); /* Short history stays at the right, not stretched. */
    values[2] = NAN;
    assert(!trend_curve_mask(mask, values, 6));
    for (size_t i = 0; i < TREND_MASK_BYTES; i++)
        assert(!mask[i]);
    assert(!trend_curve_mask(mask, NULL, 0));
    assert(!trend_curve_mask(mask, constant, TREND_CAPACITY + 1));
    puts("PASS: trend anchors, extrema, fractional edge coverage, history scale and invalid-data gaps");
}
