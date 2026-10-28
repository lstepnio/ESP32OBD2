#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define TREND_CAPACITY 60
#define TREND_WIDTH 148
#define TREND_HEIGHT 36
#define TREND_MASK_BYTES (TREND_WIDTH * TREND_HEIGHT)

/* Display geometry only: interpolate through samples without introducing extrema. */
float trend_curve_value(const float *samples, size_t count, float position);
/* A bounded A8 mask, 3 px rounded stroke, fixed 30-second horizontal scale. */
bool trend_curve_mask(uint8_t mask[TREND_MASK_BYTES], const float *samples, size_t count);
