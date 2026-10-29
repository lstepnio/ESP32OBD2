#pragma once
#include <stdbool.h>
#include <stdint.h>

typedef enum
{
    READING_PRESENT_DIRECT,
    READING_PRESENT_CONTINUOUS,
    READING_PRESENT_RPM
} reading_presentation_kind_t;

/* Two visible-metric instances only. These values never enter the sample cache. */
typedef struct
{
    double   origin, target, numeric;
    uint32_t started_ms, numeric_ms, generation;
    bool     initialized;
} reading_presentation_t;

double reading_presentation_step(reading_presentation_t     *state,
                                 reading_presentation_kind_t kind,
                                 bool                        available,
                                 double                      raw,
                                 uint32_t                    generation,
                                 uint32_t                    now_ms,
                                 bool                        immediate,
                                 double                     *numeric);
