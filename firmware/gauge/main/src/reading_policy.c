#include "reading_policy.h"
#include <string.h>
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
            policy.interval_ms    = 5000;
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
