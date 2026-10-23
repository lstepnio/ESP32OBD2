#pragma once
#include "worker_health.h"

/* Trial acceptance requires independent, recent UI progress. This does not prove panel scanout. */
static inline bool boot_health_ready(bool            ble_ready,
                                     uint32_t        initial_app,
                                     uint32_t        app,
                                     uint32_t        initial_ui,
                                     worker_health_t ui)
{
    return ble_ready && app - initial_app >= 50U && ui.observed && ui.progress_count - initial_ui >= 2U &&
           ui.age_ms <= 1000U;
}
