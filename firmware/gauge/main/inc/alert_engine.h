#pragma once

#include <stdint.h>
#include "config_runtime.h"

typedef struct {
    uint8_t severity;
    bool unavailable;
    const char *label;
} alert_summary_t;

void alert_engine_init(const config_runtime_t *runtime);
void alert_engine_sample(uint8_t pid_index, double value, uint32_t now_ms);
alert_summary_t alert_engine_tick(uint32_t now_ms);
