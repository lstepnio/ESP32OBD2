#pragma once
#include <stdbool.h>
#include <stdint.h>
#define DISPLAY_IDLE_DATA_MS 60000U
#define DISPLAY_IDLE_TOUCH_MS 30000U
typedef struct
{
    uint32_t last_data, last_touch;
    bool     saw_data, touched;
} display_idle_policy_t;
void display_idle_touch(display_idle_policy_t *s, uint32_t now);
bool display_idle_waiting(display_idle_policy_t *s,
                          bool                   available,
                          uint32_t               observed_at,
                          bool                   priority_ui,
                          uint32_t               now);
