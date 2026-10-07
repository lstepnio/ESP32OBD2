#pragma once
#include <stddef.h>
#include <stdint.h>

/* Observed 7E9 / 22 5503 full-byte values. Do not discard unknown high bits.
 * P/R/N/1 were compared on the vehicle; 2..8 retain the published interpretation. */
static inline const char *transmission_gear_label(int32_t value)
{
    static const char *const forward[] = {"1", "2", "3", "4", "5", "6", "7", "8"};
    if (value >= 1 && value <= 8) return forward[value - 1];
    if (value == 0) return "N";
    if (value == 11) return "R";
    if (value == 13) return "P";
    return NULL;
}
