#pragma once

#include "esp_err.h"
#include "sunset_policy.h"

typedef struct
{
    uint32_t revision;
    int16_t  latitude;
    int16_t  longitude;
    uint8_t  night_brightness;
    bool     automatic;
    bool     night;
    bool     clock_known;
} sunset_status_t;

void            sunset_runtime_init(void);
sunset_status_t sunset_runtime_status(void);
esp_err_t       sunset_runtime_save(bool     automatic,
                                    uint8_t  night_brightness,
                                    int16_t  latitude,
                                    int16_t  longitude,
                                    uint32_t utc,
                                    uint32_t expected_revision,
                                    uint64_t now_ms);
void            sunset_runtime_sync(uint32_t utc, uint64_t now_ms);
void            sunset_runtime_toggle(void);
void            sunset_runtime_poll(uint64_t now_ms);
uint8_t         sunset_runtime_level(uint8_t day_brightness);
