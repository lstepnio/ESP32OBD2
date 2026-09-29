#pragma once

#include <stdint.h>
#include "esp_err.h"

typedef struct {
    uint8_t rotation;
    uint8_t brightness;
    uint32_t revision;
} display_settings_t;

/* Defaults come from the active document, or the built-in gauge. A saved owner
 * adjustment overrides those defaults across restarts and document updates. */
esp_err_t display_settings_init(uint8_t default_rotation, uint8_t default_brightness);
display_settings_t display_settings_snapshot(void);
esp_err_t display_settings_save(uint8_t rotation, uint8_t brightness,
                                uint32_t expected_revision, display_settings_t *saved);
