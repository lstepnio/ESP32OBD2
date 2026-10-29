#include "reading_presentation.h"
#include <math.h>

static double analog_at(const reading_presentation_t *s, uint32_t now)
{
    uint32_t elapsed = now - s->started_ms;
    if (elapsed >= 150)
        return s->target;
    double t = elapsed / 150.0;
    /* Monotone ease-out between measured endpoints, never prediction. */
    double value = s->origin + (s->target - s->origin) * t * (2 - t);
    return fmin(fmax(s->origin, s->target), fmax(fmin(s->origin, s->target), value));
}

double reading_presentation_step(reading_presentation_t     *s,
                                 reading_presentation_kind_t kind,
                                 bool                        available,
                                 double                      raw,
                                 uint32_t                    generation,
                                 uint32_t                    observed_at_ms,
                                 uint32_t                    continuity,
                                 uint32_t                    now,
                                 bool                        immediate,
                                 double                     *numeric)
{
    if (!available || !isfinite(raw))
    {
        *s       = (reading_presentation_t){0};
        *numeric = 0;
        return 0;
    }
    bool reset = !s->initialized || s->generation != generation || s->continuity != continuity || immediate ||
                 kind == READING_PRESENT_DIRECT || raw == 0;
    if (reset)
        *s = (reading_presentation_t){.origin              = raw,
                                      .target              = raw,
                                      .numeric             = raw,
                                      .started_ms          = observed_at_ms,
                                      .numeric_ms          = now,
                                      .generation          = generation,
                                      .continuity          = continuity,
                                      .last_observed_at_ms = observed_at_ms,
                                      .initialized         = true};
    else if (raw != s->target)
    {
        s->origin     = analog_at(s, now);
        s->target     = raw;
        s->started_ms = observed_at_ms;
    }
    s->last_observed_at_ms = observed_at_ms;
    if (!reset && kind == READING_PRESENT_RPM)
    {
        /* Quantization is presentation-only. Hysteresis prevents bin-edge chatter;
         * engine-off, invalidation and alert transitions bypass the cadence. */
        if (now - s->numeric_ms >= 250 && fabs(raw - s->numeric) >= 17.5)
        {
            s->numeric    = round(raw / 25.0) * 25.0;
            s->numeric_ms = now;
        }
    }
    else
        s->numeric = raw;
    *numeric = s->numeric;
    return kind == READING_PRESENT_DIRECT ? raw : analog_at(s, now);
}
