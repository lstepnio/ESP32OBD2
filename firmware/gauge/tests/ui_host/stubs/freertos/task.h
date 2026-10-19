#pragma once
#include "FreeRTOS.h"
extern uint32_t host_ms;
static inline TickType_t xTaskGetTickCount(void) { return host_ms; }
