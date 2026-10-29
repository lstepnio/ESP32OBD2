#pragma once
#include <stdbool.h>
#include <stdint.h>

typedef enum
{
    READING_PRESENT_DIRECT,
    READING_PRESENT_CONTINUOUS,
    READING_PRESENT_RPM
} reading_presentation_kind_t;

/* Analog settles 150 ms after the target observation, paid down by sample age.
 * Two visible-metric instances only. These values never enter the sample cache. */
typedef struct
{
    double   origin, target, numeric;
    uint32_t started_ms, numeric_ms, generation, continuity, last_observed_at_ms;
    bool     initialized;
} reading_presentation_t;

/* observed_at_ms retains the sample's monotonic age. continuity changes after
 * unavailable/source-reset boundaries even when no UI frame saw the outage.
 * numeric is a separate presentation output; RPM cadence uses display time. */
double reading_presentation_step(reading_presentation_t     *state,
                                 reading_presentation_kind_t kind,
                                 bool                        available,
                                 double                      raw,
                                 uint32_t                    generation,
                                 uint32_t                    observed_at_ms,
                                 uint32_t                    continuity,
                                 uint32_t                    now_ms,
                                 bool                        immediate,
                                 double                     *numeric);
