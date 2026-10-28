#pragma once
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include "gauge_gear_colors.h"

/* Observed 7E9 / 22 5503 full-byte values. Do not discard unknown high bits.
 * P/R/N/1 were compared on the vehicle; 2..8 retain the published interpretation. */
static inline const char *transmission_gear_label(int32_t value)
{
    static const char *const forward[] = {"1", "2", "3", "4", "5", "6", "7", "8"};
    if (value >= 1 && value <= 8)
        return forward[value - 1];
    if (value == 0)
        return "N";
    if (value == 11)
        return "R";
    if (value == 13)
        return "P";
    return NULL;
}

/* Project palette: category meaning supplements the actual label; it does not
 * assert a universal per-gear industry standard or infer D from an actual gear. */
static inline uint32_t transmission_gear_color(const char *label)
{
    if (!label)
        return GAUGE_GEAR_UNKNOWN;
    if (!strcmp(label, "P"))
        return GAUGE_GEAR_PARK;
    if (!strcmp(label, "N"))
        return GAUGE_GEAR_NEUTRAL;
    if (!strcmp(label, "R"))
        return GAUGE_GEAR_REVERSE;
    if (!strcmp(label, "D") || (label[0] >= '1' && label[0] <= '8' && !label[1]))
        return GAUGE_GEAR_DRIVE;
    return GAUGE_GEAR_UNKNOWN;
}
