#pragma once
#include <stdint.h>
extern uint32_t host_ms;
static inline int64_t esp_timer_get_time(void) { return (int64_t)host_ms*1000; }
