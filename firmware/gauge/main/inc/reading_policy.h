#pragma once
#include <stdbool.h>
#include <stdint.h>
#include "poll_scheduler.h"
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
