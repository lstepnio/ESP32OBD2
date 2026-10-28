#pragma once
#include <stdbool.h>
#include <stdint.h>

typedef struct
{
    const char *name, *labels[2], *units[2];
    uint8_t     renderer, metrics, arc_metric_index;
    double      values[2], minimum, maximum;
    bool        present[2], mil, count_known, attention, last_seen;
    uint8_t     faults, severity;
    const char *condition;
} display_showcase_frame_t;
typedef struct
{
    bool     active;
    uint8_t  index, seconds, result;
    uint32_t sequence, started, changed;
} display_showcase_t;
enum
{
    SHOWCASE_APPLIED = 0,
    SHOWCASE_REFUSED = 1
};
#define DISPLAY_SHOWCASE_DURATION_MS 600000U
unsigned                        display_showcase_count(void);
const display_showcase_frame_t *display_showcase_frame(unsigned index);
bool                            display_showcase_apply(display_showcase_t *state,
                                                       bool                enabled,
                                                       uint8_t             seconds,
                                                       uint32_t            sequence,
                                                       uint32_t            now,
                                                       bool                vehicle_ready,
                                                       bool                maintenance,
                                                       bool                attention);
bool                            display_showcase_tick(display_showcase_t *state, uint32_t now, bool interrupt);
void                            display_showcase_next(display_showcase_t *state, uint32_t now);
