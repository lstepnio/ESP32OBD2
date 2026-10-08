#pragma once
#include "alert_events.h"
#include "esp_err.h"
esp_err_t alert_runtime_init(uint32_t revision);
void alert_runtime_observe(unsigned key, uint8_t severity, bool unavailable,
    const char *label, const char *unit, float value, float limit, uint32_t observed_at, uint32_t now);
void alert_runtime_tick(uint32_t now);
size_t alert_runtime_packet(uint32_t boot, uint32_t cursor, bool active, uint8_t out[360]);
/* Commands are admitted without blocking the BLE callback; readback confirms apply. */
bool alert_runtime_command(const uint8_t *bytes, size_t length);
bool alert_runtime_summary(uint32_t now, alert_event_t *out, bool *attention);

void alert_runtime_sample(unsigned pid, float value, uint32_t observed_at);
size_t alert_runtime_context(unsigned key,uint32_t boot,uint32_t episode,uint32_t offset,uint8_t out[216]);
void alert_runtime_simulated(unsigned key,bool value);

void alert_runtime_priority(unsigned key,uint8_t priority);
void alert_runtime_stale(unsigned pid,uint32_t milliseconds);
