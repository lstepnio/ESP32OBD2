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

/* Auto cold boot uses the last checkpointed applied angle; manual mode always
 * uses the owner angle. Missing/malformed hints fall back to that owner angle. */
uint8_t display_settings_boot_rotation(void);

/* RAM-only observation of the successfully applied angle, on a monotonic clock.
 * Call regularly while Auto holds the angle; invalid/manual clears stability. */
void display_settings_observe_auto_rotation(uint8_t applied_rotation, uint64_t now_ms);

/* Application owner only, outside LVGL/BLE callbacks and the LVGL lock. Saves
 * after 5 seconds unchanged, at most one attempt per minute (including failures).
 * No settings revision changes. Success includes a no-op; busy returns TIMEOUT.
 * A last-second movement/power loss can therefore retain the older boot hint. */
esp_err_t display_settings_persist_auto_rotation(uint64_t now_ms);
