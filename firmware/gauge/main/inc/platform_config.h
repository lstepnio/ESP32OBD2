#pragma once

#include "config_runtime.h"
#include "platform_features.h"

static inline bool platform_config_accepts(const config_runtime_t *runtime)
{
    return (EGAUGE_FEATURE_DUAL_ADAPTER || runtime->source_count <= 1) &&
           (EGAUGE_FEATURE_VEHICLE_ACTIONS || !runtime->page_action.vehicle_menu);
}

static inline void platform_config_apply(config_runtime_t *runtime)
{
    if (!EGAUGE_FEATURE_DUAL_ADAPTER && runtime->source_count == 2)
    {
        /* The ECM is the single active link. Keep the other slot and its PID
         * indices intact so its saved pages and alerts remain unavailable. */
        if (runtime->sources[0].transmission)
        {
            runtime_source_t transmission = runtime->sources[0];
            runtime->sources[0]           = runtime->sources[1];
            runtime->sources[1]           = transmission;
            for (unsigned i = 0; i < runtime->pid_count; ++i)
                runtime->pids[i].source_index ^= 1U;
        }
        runtime->source_count = 1;
    }
    if (!EGAUGE_FEATURE_VEHICLE_ACTIONS && runtime->page_action.vehicle_menu)
        runtime->page_action = (page_action_config_t){0};
}
