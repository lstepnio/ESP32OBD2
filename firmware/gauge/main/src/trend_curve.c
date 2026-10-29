#include "trend_curve.h"
#include <math.h>
#include <string.h>

static float clamp(float value, float minimum, float maximum)
{
    return fminf(maximum, fmaxf(minimum, value));
}
static float tangent(const float *v, size_t count, size_t i)
{
    if (!i)
        return v[1] - v[0];
    if (i == count - 1)
        return v[i] - v[i - 1];
    float before = v[i] - v[i - 1], after = v[i + 1] - v[i];
    return before * after <= 0 ? 0 : 2 * before * after / (before + after);
}
float trend_curve_value(const float *samples, size_t count, float position)
{
    if (!samples || !count || count > TREND_CAPACITY || !isfinite(position))
        return NAN;
    position = clamp(position, 0, (float)count - 1);
    size_t i = (size_t)position;
    if (i == count - 1)
        return samples[i];
    float t = position - i, t2 = t * t, t3 = t2 * t;
    float a = samples[i], b = samples[i + 1];
    float value = (2 * t3 - 3 * t2 + 1) * a + (t3 - 2 * t2 + t) * tangent(samples, count, i) + (-2 * t3 + 3 * t2) * b +
                  (t3 - t2) * tangent(samples, count, i + 1);
    return isfinite(value) ? clamp(value, fminf(a, b), fmaxf(a, b)) : NAN;
}
static void segment(uint8_t *mask, float x0, float y0, float x1, float y1)
{
    int   left = (int)floorf(fminf(x0, x1) - 2), right = (int)ceilf(fmaxf(x0, x1) + 2);
    int   top = (int)floorf(fminf(y0, y1) - 2), bottom = (int)ceilf(fmaxf(y0, y1) + 2);
    float dx = x1 - x0, dy = y1 - y0, length = dx * dx + dy * dy;
    for (int y = top < 0 ? 0 : top; y <= bottom && y < TREND_HEIGHT; y++)
        for (int x = left < 0 ? 0 : left; x <= right && x < TREND_WIDTH; x++)
        {
            float   t = length > 0 ? clamp(((x - x0) * dx + (y - y0) * dy) / length, 0, 1) : 0;
            float   a = x - x0 - t * dx, b = y - y0 - t * dy;
            uint8_t opacity = (uint8_t)lroundf(255 * clamp(2 - sqrtf(a * a + b * b), 0, 1));
            size_t  at      = (size_t)y * TREND_WIDTH + x;
            if (opacity > mask[at])
                mask[at] = opacity;
        }
}
bool trend_curve_mask(uint8_t mask[TREND_MASK_BYTES], const float *samples, size_t count)
{
    if (!mask)
        return false;
    memset(mask, 0, TREND_MASK_BYTES);
    if (!samples || count < 2 || count > TREND_CAPACITY)
        return false;
    for (size_t i = 0; i < count; i++)
        if (!isfinite(samples[i]) || samples[i] < 0 || samples[i] > 30)
            return false;
    float span = 144.f * (count - 1) / (TREND_CAPACITY - 1), start = 146 - span;
    float previous_x = start, previous_y = 2 + samples[0];
    for (size_t interval = 0; interval < count - 1; interval++)
        for (unsigned step = 1; step <= 5; step++)
        {
            float position = interval + (float)step / 5;
            float x        = start + position * span / (count - 1);
            float y        = 2 + trend_curve_value(samples, count, position);
            segment(mask, previous_x, previous_y, x, y);
            previous_x = x;
            previous_y = y;
        }
    return true;
}
