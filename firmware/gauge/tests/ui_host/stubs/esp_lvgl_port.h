#pragma once
#include <stdbool.h>
static inline bool lvgl_port_lock(unsigned ticks)
{
    (void)ticks;
    return true;
}
static inline void lvgl_port_unlock(void)
{
}
