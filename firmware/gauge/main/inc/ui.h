#pragma once

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

#include <stdbool.h>
#include <stdint.h>

#include "misc/lv_types.h"
#include "misc/lv_event.h"

#include "obd.h"

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

#define DISPLAY_VALUE_INVALID INT32_MAX

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct _ui_t ui_t;

typedef void (*ui_touch_callback_t)(ui_t *ui, lv_event_code_t event_code);

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Declarations
// ---------------------------------------------------------------------------------------------------------------------

ui_t *ui_init(obd_pid_cfg_t const *cfg, uint32_t interval_ms, ui_touch_callback_t touch_cb);
void  ui_set_value(ui_t *ui, int32_t const *value);
void  ui_set_obd_cfg(ui_t *ui, obd_pid_cfg_t const *cfg);
/* Thread-safe: displays a temporary six-digit pairing code. Zero clears it. */
void  ui_show_pairing_code(ui_t *ui, uint32_t passkey);
void  ui_set_alert(ui_t *ui, uint8_t severity, bool unavailable, const char *label);
void  ui_set_diagnostics(ui_t *ui, bool valid, bool mil_on,
                         uint8_t count, const char *first_code);
/* Call while holding the LVGL lock, or from an LVGL callback. */
void  ui_set_freshness(ui_t *ui, uint32_t stale_after_ms);
