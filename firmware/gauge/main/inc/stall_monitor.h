#pragma once

#include "worker_health.h"

/* Allow startup and transient delays without reporting a stall. */
static inline bool worker_is_stalled(worker_health_t health, uint32_t monitor_age_ms)
{
    return health.observed ? health.age_ms >= 5000U : monitor_age_ms >= 10000U;
}
