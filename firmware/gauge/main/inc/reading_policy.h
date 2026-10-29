#pragma once
#include <stdbool.h>
#include <stdint.h>
#include "poll_scheduler.h"
#include "reading_presentation.h"
/* Catalog classes only affect display/scheduling. Unknown/custom timings retain
 * their explicit profile limits; no guard or alert freshness is widened. */
poll_pid_policy_t reading_policy(const char *id,
                                 uint8_t     service,
                                 uint16_t    pid,
                                 uint32_t    poll_ms,
                                 uint32_t    stale_ms,
                                 bool        visible,
                                 bool        next_page,
                                 bool        alert);

/* One fixed state per PID/source. No heap, queries, or display-age changes. */
typedef struct
{
    float    anchor;
    uint32_t stable_since_ms, observed_at_ms, generation;
    uint16_t catalog_slot; /* One-based identity, zero means unavailable. */
    bool     initialized;
} reading_stability_t;
void              reading_stability_reset(reading_stability_t *state);
void              reading_stability_observe(reading_stability_t *state,
                                            const char          *id,
                                            uint8_t              service,
                                            uint16_t             pid,
                                            uint32_t             poll_ms,
                                            uint32_t             stale_ms,
                                            float                value,
                                            uint32_t             observed_at_ms,
                                            uint32_t             generation);
poll_pid_policy_t reading_policy_adaptive(const char                *id,
                                          uint8_t                    service,
                                          uint16_t                   pid,
                                          uint32_t                   poll_ms,
                                          uint32_t                   stale_ms,
                                          bool                       visible,
                                          bool                       next_page,
                                          bool                       alert,
                                          const reading_stability_t *state,
                                          uint32_t                   now_ms,
                                          uint32_t                   generation);

reading_presentation_kind_t reading_presentation_kind(const char *id,
                                                      uint8_t     service,
                                                      uint16_t    pid,
                                                      uint32_t    poll_ms,
                                                      uint32_t    stale_ms);
