#pragma once
#include "config_runtime.h"
#include <stdbool.h>
#include <stdint.h>

#define VEHICLE_IDLE_COOLANT_MAX_AGE_MS 2000U
#define VEHICLE_IDLE_COOLANT_POLL_MS 500U
/* Select the lowest high-temperature threshold for the real primary ECM's
 * standard coolant reading. Never infer protection from an alert's UI label. */
bool vehicle_idle_coolant_limit(const config_runtime_t *runtime, double *limit_c);
bool vehicle_idle_coolant_allows(bool     configured,
                                 bool     valid,
                                 double   value_c,
                                 double   limit_c,
                                 uint32_t observed_at,
                                 uint32_t now);

/* Missing readings permit only a bounded, explicitly confirmed gauge session. */
#define VEHICLE_IDLE_COOLANT_FALLBACK_MS 60000U
bool vehicle_idle_coolant_fallback_safe(bool configured, bool valid, double value, double limit);
