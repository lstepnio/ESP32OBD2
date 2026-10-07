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
#define UI_PAIRING_HIDDEN 0U
#define UI_PAIRING_READY UINT32_MAX
#define UI_PAIRING_WAITING (UINT32_MAX - 1U)
#define UI_PAIRING_RESET_CONFIRM (UINT32_MAX - 2U)
#define UI_PAIRING_OWNER_SAVED (UINT32_MAX - 3U)

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct _ui_t ui_t;

typedef enum {
    UI_RENDERER_NUMERIC = 0,
    UI_RENDERER_ARC,
    UI_RENDERER_BAR,
    UI_RENDERER_TREND,
    UI_RENDERER_DUAL,
} ui_renderer_t;

typedef struct {
    uint16_t pid;
    const char *name;
    const char *unit;
    int32_t minimum;
    int32_t maximum;
    uint32_t stale_after_ms;
} ui_metric_t;

typedef struct {
    const char *name;
    ui_renderer_t renderer;
    uint8_t metric_count;
    ui_metric_t metrics[2];
} ui_page_t;

typedef void (*ui_touch_callback_t)(ui_t *ui, lv_event_code_t event_code);

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Declarations
// ---------------------------------------------------------------------------------------------------------------------

ui_t *ui_init(ui_page_t const *page, uint32_t interval_ms, ui_touch_callback_t touch_cb,
              bool pairing_required, bool imperial_units);
/* Thread-safe presentation mailbox. Samples are accepted only when their PID
 * still matches the selected page, preventing late replies from crossing a
 * page change. Passing NULL publishes an unavailable sample for that PID. */
void  ui_set_value(ui_t *ui, uint16_t pid, int32_t const *value);
void  ui_set_page(ui_t *ui, ui_page_t const *page);
/* Call on the LVGL task or while holding its lock. */
void  ui_set_units(ui_t *ui, bool imperial_units);
/* Thread-safe: displays pairing or owner-reset instructions. Accepts a six-digit
 * passkey or UI_PAIRING_* state. UI_PAIRING_HIDDEN restores the gauge view. */
void  ui_show_pairing_code(ui_t *ui, uint32_t passkey);
void  ui_set_pairing_identifier(ui_t *ui, const char *identifier);
void  ui_set_alert(ui_t *ui, uint8_t severity, bool unavailable, const char *label);
void  ui_set_diagnostics(ui_t *ui, bool valid, bool mil_on,
                         uint8_t count, const char *first_code);
void  ui_start_display_calibration(ui_t *ui);
void  ui_next_display_calibration(ui_t *ui);

/* Call at boot with the LVGL lock held for a trace-build bench source. */
void ui_set_simulated(ui_t *ui);
