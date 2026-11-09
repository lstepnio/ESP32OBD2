#pragma once

#include <stdbool.h>
#include <stdint.h>

/* Standalone consumers fail closed. Product builds define each switch explicitly. */
#ifndef EGAUGE_FEATURE_DUAL_ADAPTER
#define EGAUGE_FEATURE_DUAL_ADAPTER 0
#endif
#ifndef EGAUGE_FEATURE_SMART_CYCLE
#define EGAUGE_FEATURE_SMART_CYCLE 0
#endif
#ifndef EGAUGE_FEATURE_VEHICLE_ACTIONS
#define EGAUGE_FEATURE_VEHICLE_ACTIONS 0
#endif

#define EGAUGE_FEATURES_RESTRICTED                                                                                     \
    (!EGAUGE_FEATURE_DUAL_ADAPTER || !EGAUGE_FEATURE_SMART_CYCLE || !EGAUGE_FEATURE_VEHICLE_ACTIONS)

enum
{
    PLATFORM_FEATURE_DUAL_ADAPTER    = 1U << 0,
    PLATFORM_FEATURE_SMART_CYCLE     = 1U << 1,
    PLATFORM_FEATURE_VEHICLE_ACTIONS = 1U << 2,
    PLATFORM_FEATURE_ALL             = PLATFORM_FEATURE_DUAL_ADAPTER | PLATFORM_FEATURE_SMART_CYCLE |
        PLATFORM_FEATURE_VEHICLE_ACTIONS,
};

#define EGAUGE_COMPILED_FEATURES                                                                                       \
    ((EGAUGE_FEATURE_DUAL_ADAPTER ? PLATFORM_FEATURE_DUAL_ADAPTER : 0) |                                               \
     (EGAUGE_FEATURE_SMART_CYCLE ? PLATFORM_FEATURE_SMART_CYCLE : 0) |                                                 \
     (EGAUGE_FEATURE_VEHICLE_ACTIONS ? PLATFORM_FEATURE_VEHICLE_ACTIONS : 0))

#ifdef EGAUGE_OLED_LICENSE_REQUIRED
/* Called once during boot before configuration or worker startup. */
void    platform_features_init(void);
uint8_t platform_feature_mask(void);
#else
static inline void platform_features_init(void)
{
}

static inline uint8_t platform_feature_mask(void)
{
    return EGAUGE_COMPILED_FEATURES;
}
#endif

static inline bool platform_feature_enabled(uint8_t feature)
{
    return (platform_feature_mask() & feature) != 0;
}
