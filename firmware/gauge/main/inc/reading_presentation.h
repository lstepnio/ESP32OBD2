#pragma once
#include <stdbool.h>
#include <stdint.h>
#include <math.h>

/* Causal display-only easing, with no prediction or sample/history mutation.
 * A 100 ms ease-out catches the measured target quickly, then settles exactly.
 * Observation age pays down the window rather than adding another full window
 * when the UI consumes a delayed sample. The next UI frame completes settling. */
#define READING_PRESENTATION_SETTLE_MS 100U

typedef struct
{
    double   from, target;
    uint32_t observed_at_ms, last_observed_at_ms, continuity;
    bool     initialized;
} reading_presentation_t;

static inline double reading_presentation_current(const reading_presentation_t *state, uint32_t now)
{
    uint32_t age = now - state->observed_at_ms;
    if (age >= READING_PRESENTATION_SETTLE_MS)
        return state->target;
    double remaining = 1.0 - (double)age / READING_PRESENTATION_SETTLE_MS;
    double value     = state->from + (state->target - state->from) * (1.0 - remaining * remaining * remaining);
    return fmin(fmax(state->from, state->target), fmax(fmin(state->from, state->target), value));
}

static inline double reading_presentation_value(reading_presentation_t *state,
                                                bool                    available,
                                                double                  measured,
                                                uint32_t                observed_at_ms,
                                                uint32_t                continuity,
                                                uint32_t                now)
{
    if (!available || !isfinite(measured))
    {
        *state = (reading_presentation_t){0};
        return measured;
    }
    if (!state->initialized || state->continuity != continuity)
    {
        *state = (reading_presentation_t){.from                = measured,
                                          .target              = measured,
                                          .observed_at_ms      = observed_at_ms,
                                          .last_observed_at_ms = observed_at_ms,
                                          .continuity          = continuity,
                                          .initialized         = true};
        return measured;
    }
    state->last_observed_at_ms = observed_at_ms;
    if (state->target != measured)
    {
        state->from           = reading_presentation_current(state, now);
        state->target         = measured;
        state->observed_at_ms = observed_at_ms;
    }
    return reading_presentation_current(state, now);
}
