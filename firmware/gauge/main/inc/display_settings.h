#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

typedef struct
{
    uint8_t  rotation;
    uint8_t  brightness;
    uint8_t  units; /* 0 metric, 1 imperial */
    uint32_t revision;
    uint16_t cycle_seconds;    /* 0 disables saved-page cycling */
    bool     smart_cycle;      /* Retained while cycle_seconds is zero. */
    bool     auto_orientation; /* rotation remains the last owner-selected manual angle. */
} display_settings_t;

/* Defaults come from the active document, or the built-in gauge. A saved owner
 * adjustment overrides those defaults across restarts and document updates. */
esp_err_t          display_settings_init(uint8_t default_rotation, uint8_t default_brightness, uint8_t default_units);
display_settings_t display_settings_snapshot(void);
esp_err_t          display_settings_save(uint8_t             rotation,
                                         uint8_t             brightness,
                                         uint8_t             units,
                                         uint16_t            cycle_seconds,
                                         uint32_t            expected_revision,
                                         display_settings_t *saved);

/* Atomic preference update; the legacy save API retains the Smart preference. */
esp_err_t display_settings_save_smart(uint8_t             rotation,
                                      uint8_t             brightness,
                                      uint8_t             units,
                                      uint16_t            cycle_seconds,
                                      bool                smart_cycle,
                                      uint32_t            expected_revision,
                                      display_settings_t *saved);

/* Auto updates never mutate this persisted owner preference or its revision. */
esp_err_t display_settings_save_orientation(bool                automatic,
                                            uint8_t             manual_rotation,
                                            uint32_t            expected_revision,
                                            display_settings_t *saved);
