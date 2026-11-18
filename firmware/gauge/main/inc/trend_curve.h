#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define TREND_CAPACITY 60
#define TREND_WINDOW_MS 30000U
#define TREND_INTERVAL_MS 500U
#define TREND_WIDTH 148
#define TREND_HEIGHT 36
#define TREND_MASK_BYTES (TREND_WIDTH * TREND_HEIGHT)

/* Display geometry only: interpolate through samples without introducing extrema. */
float trend_curve_value(const float *samples, size_t count, float position);
/* A bounded A8 mask, 3 px rounded stroke, fixed 30-second horizontal scale. */
bool trend_curve_mask(uint8_t mask[TREND_MASK_BYTES], const float *samples, size_t count);

typedef struct
{
    uint32_t at_ms;
    float    value;
} trend_sample_t;

typedef struct
{
    trend_sample_t samples[TREND_CAPACITY];
    uint32_t       revision;
    uint8_t        count, next;
} trend_history_t;

void   trend_history_clear(trend_history_t *history);
void   trend_history_record(trend_history_t *history, uint32_t at_ms, double value, uint32_t gap_ms);
size_t trend_history_copy(const trend_history_t *history, trend_sample_t out[TREND_CAPACITY]);
/* positions are chart-grid x coordinates in [0, 144], ordered oldest first. */
bool trend_curve_mask_positions(uint8_t      mask[TREND_MASK_BYTES],
                                const float *samples,
                                const float *positions,
                                size_t       count);
void trend_scale(const trend_sample_t *samples, size_t count, double minimum_span, float *scaled);
