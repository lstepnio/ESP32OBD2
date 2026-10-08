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

/* Retain the last alert severity as unavailable; abandon pending dwell. */
void alert_engine_invalidate(void);

void alert_engine_invalidate_pid(uint8_t pid_index);
void alert_engine_invalidate_source(unsigned source);

typedef void (*alert_transition_sink_t)(unsigned key, uint8_t severity, bool unavailable,
    const char *label, const char *unit, float value, float limit, uint32_t observed_at, uint32_t now);
void alert_engine_set_sink(alert_transition_sink_t sink);
