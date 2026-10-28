#include "reading_policy.h"
#include <string.h>
#include <math.h>
typedef enum
{
    READING_CUSTOM,
    READING_FAST,
    READING_THERMAL,
    READING_SLOW
} reading_class_t;
static const struct
{
    const char     *id;
    uint8_t         service;
    uint16_t        pid;
    uint32_t        poll_ms, stale_ms;
    reading_class_t kind;
    float           stability_epsilon;
    uint32_t        stability_window_ms;
} catalog[] = {
#include "reading_policy_catalog.inc"
};
poll_pid_policy_t reading_policy(const char *id,
                                 uint8_t     service,
                                 uint16_t    pid,
                                 uint32_t    poll_ms,
                                 uint32_t    stale_ms,
                                 bool        visible,
                                 bool        next_page,
                                 bool        alert)
{
    poll_pid_policy_t policy = {poll_ms, stale_ms, visible ? 2 : next_page ? 1 : 0};
    for (unsigned i = 0; id && i < sizeof(catalog) / sizeof(catalog[0]); ++i)
    {
        if (catalog[i].service != service || catalog[i].pid != pid || strcmp(catalog[i].id, id) ||
            catalog[i].poll_ms != poll_ms || catalog[i].stale_ms != stale_ms)
            continue; /* Explicit custom rate/age overrides remain authoritative. */
        switch (catalog[i].kind)
        {
        case READING_FAST:
            policy.interval_ms    = visible ? 250 : next_page ? 500 : 1000;
            policy.display_age_ms = 3000;
            break;
        case READING_THERMAL:
            policy.interval_ms    = 1500;
            policy.display_age_ms = 8000;
            break;
        case READING_SLOW:
            policy.interval_ms    = visible ? 2000 : next_page ? 2500 : 5000;
            policy.display_age_ms = 20000;
            break;
        default:
            break;
        }
        break;
    }
    /* A configured alert retains its original sampling requirement, independent
     * of display class or visibility. Idle guards have their own faster owner. */
    if (alert && policy.interval_ms > poll_ms)
        policy.interval_ms = poll_ms;
    return policy;
}

static unsigned catalog_slot(const char *id, uint8_t service, uint16_t pid, uint32_t poll_ms, uint32_t stale_ms)
{
    for (unsigned i = 0; id && i < sizeof(catalog) / sizeof(catalog[0]); ++i)
        if (catalog[i].service == service && catalog[i].pid == pid && !strcmp(catalog[i].id, id) &&
            catalog[i].poll_ms == poll_ms && catalog[i].stale_ms == stale_ms)
            return i + 1;
    return 0;
}
void reading_stability_reset(reading_stability_t *state)
{
    if (state)
        *state = (reading_stability_t){0};
}
void reading_stability_observe(reading_stability_t *state,
                               const char          *id,
                               uint8_t              service,
                               uint16_t             pid,
                               uint32_t             poll_ms,
                               uint32_t             stale_ms,
                               float                value,
                               uint32_t             at,
                               uint32_t             generation)
{
    if (!state)
        return;
    unsigned slot = catalog_slot(id, service, pid, poll_ms, stale_ms);
    if (!slot || !catalog[slot - 1].stability_window_ms || !isfinite(value))
    {
        reading_stability_reset(state);
        return;
    }
    uint32_t max_gap = catalog[slot - 1].kind == READING_THERMAL ? 8000 : 20000;
    bool     restart = !state->initialized || state->catalog_slot != slot || state->generation != generation ||
                       (uint32_t)(at - state->observed_at_ms) > max_gap ||
                       fabsf(value - state->anchor) > catalog[slot - 1].stability_epsilon;
    if (restart)
    {
        state->anchor          = value;
        state->stable_since_ms = at;
    }
    state->observed_at_ms = at;
    state->generation     = generation;
    state->catalog_slot   = slot;
    state->initialized    = true;
}
poll_pid_policy_t reading_policy_adaptive(const char                *id,
                                          uint8_t                    service,
                                          uint16_t                   pid,
                                          uint32_t                   poll_ms,
                                          uint32_t                   stale_ms,
                                          bool                       visible,
                                          bool                       next_page,
                                          bool                       alert,
                                          const reading_stability_t *state,
                                          uint32_t                   now,
                                          uint32_t                   generation)
{
    poll_pid_policy_t policy = reading_policy(id, service, pid, poll_ms, stale_ms, visible, next_page, alert);
    unsigned          slot   = catalog_slot(id, service, pid, poll_ms, stale_ms);
    if (!visible && !next_page && state && state->initialized && slot && state->catalog_slot == slot &&
        state->generation == generation && catalog[slot - 1].stability_window_ms &&
        (uint32_t)(now - state->observed_at_ms) <= policy.display_age_ms &&
        (uint32_t)(now - state->stable_since_ms) >= catalog[slot - 1].stability_window_ms)
    {
        uint32_t interval = catalog[slot - 1].kind == READING_THERMAL ? 3000 : 10000;
        /* A hidden adaptive sample must occur within half its existing display
         * age. Never alter display validity or the alert/control sampling owner. */
        if (interval > policy.display_age_ms / 2)
            interval = policy.display_age_ms / 2;
        if (interval > policy.interval_ms)
            policy.interval_ms = interval;
    }
    if (alert && policy.interval_ms > poll_ms)
        policy.interval_ms = poll_ms;
    return policy;
}
