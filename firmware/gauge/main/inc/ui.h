#pragma once

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

#include <stdbool.h>
#include <stdint.h>

#include "misc/lv_types.h"
#include "misc/lv_event.h"

#include "obd.h"
#include "page_action.h"
#include "page_save_policy.h"
#include "diagnostics_state.h"

/* Internal callback event; no external vehicle control operation. */
#define UI_EVENT_PAGE_ACTION LV_EVENT_REFRESH
#define UI_EVENT_VEHICLE_COMMAND LV_EVENT_READY

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

#define UI_PAIRING_HIDDEN 0U
#define UI_PAIRING_READY UINT32_MAX
#define UI_PAIRING_WAITING (UINT32_MAX - 1U)
#define UI_PAIRING_RESET_CONFIRM (UINT32_MAX - 2U)
#define UI_PAIRING_OWNER_SAVED (UINT32_MAX - 3U)

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct _ui_t ui_t;

typedef enum
{
    UI_RENDERER_NUMERIC = 0,
    UI_RENDERER_ARC,
    UI_RENDERER_BAR,
    UI_RENDERER_TREND,
    UI_RENDERER_DUAL,
    UI_RENDERER_DUAL_ARC,
} ui_renderer_t;

typedef struct
{
    uint16_t    pid;
    uint8_t     source_index;
    bool        scoped;
    bool        presentation_eased; /* Internal reviewed fast numeric hint, never serialized. */
    uint32_t    revision;
    const char *name;
    const char *unit;
    double      minimum;
    double      maximum;
    uint32_t    stale_after_ms;
} ui_metric_t;

typedef struct
{
    const char   *name;
    ui_renderer_t renderer;
    uint8_t       metric_count;
    ui_metric_t   metrics[2];
    uint8_t       arc_metric_index;
} ui_page_t;

typedef void (*ui_touch_callback_t)(ui_t *ui, lv_event_code_t event_code);

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Declarations
// ---------------------------------------------------------------------------------------------------------------------

/* Reused vector mark drawn as soon as the display is ready; no bitmap/framebuffer. */
void  ui_boot_logo_early(void);
void  ui_waiting_logo_enable(ui_t *ui, bool enabled);
ui_t *ui_init(ui_page_t const    *page,
              uint32_t            interval_ms,
              ui_touch_callback_t touch_cb,
              bool                pairing_required,
              bool                imperial_units);
/* Bounded latest-value mailbox. Legacy/test values are unscoped. Production
 * values require the exact source scope armed by its worker. Observations use
 * monotonic milliseconds and retain their original age across page changes. */
void ui_set_value(ui_t *ui, uint16_t pid, double const *value);
/* Read-only latest sample for presentation policy. No LVGL lock or timestamp renewal. */
bool ui_source_sample(ui_t     *ui,
                      unsigned  source,
                      uint32_t  revision,
                      uint16_t  pid,
                      uint32_t  age_ms,
                      uint32_t  now,
                      double   *value,
                      uint32_t *observed_at,
                      uint32_t *generation);
void ui_set_source_state(ui_t *ui, unsigned source, bool ready, uint32_t revision, uint32_t generation);
void ui_set_source_value(ui_t         *ui,
                         unsigned      source,
                         uint32_t      revision,
                         uint32_t      generation,
                         uint16_t      pid,
                         double const *value,
                         uint32_t      observed_at_ms);
void ui_set_page(ui_t *ui, ui_page_t const *page);
void ui_set_page_position(ui_t *ui, unsigned index, unsigned count);
/* LVGL owner only for configuration and target access. Context mailbox is thread-safe. */
void    ui_configure_page_action(ui_t *ui, page_action_config_t config);
void    ui_set_action_context(ui_t *ui, bool allowed);
uint8_t ui_action_target(ui_t *ui);
void    ui_reset_action_sequence(ui_t *ui);
bool    ui_action_pending(ui_t *ui);
void    ui_action_applied(ui_t *ui);
/* Call on the LVGL task or while holding its lock. */
void ui_set_units(ui_t *ui, bool imperial_units);
/* Thread-safe: displays pairing or owner-reset instructions. Accepts a six-digit
 * passkey or UI_PAIRING_* state. UI_PAIRING_HIDDEN restores the gauge view. */
void ui_show_pairing_code(ui_t *ui, uint32_t passkey);
void ui_set_pairing_identifier(ui_t *ui, const char *identifier);
void ui_set_alert(ui_t *ui, uint8_t severity, bool unavailable, const char *label);
void ui_set_diagnostics(ui_t *ui, bool valid, bool mil_on, bool transmission, uint8_t count, const char *first_code);
void ui_start_display_calibration(ui_t *ui);
void ui_next_display_calibration(ui_t *ui);

/* Call at boot with the LVGL lock held for a trace-build bench source. */
void ui_set_simulated(ui_t *ui);

void ui_set_alert_full(ui_t *ui, uint8_t severity, bool unavailable, const char *label, bool attention);

void ui_set_alert_event(ui_t       *ui,
                        uint8_t     severity,
                        bool        unavailable,
                        const char *label,
                        bool        attention,
                        uint16_t    key,
                        uint32_t    boot,
                        uint32_t    episode,
                        float       value,
                        float       limit,
                        const char *unit);
void ui_set_alert_condition(ui_t       *ui,
                            uint8_t     severity,
                            bool        unavailable,
                            const char *label,
                            bool        attention,
                            uint16_t    key,
                            uint32_t    boot,
                            uint32_t    episode,
                            float       value,
                            float       limit,
                            const char *unit,
                            uint8_t     additional,
                            bool        simulated);
void ui_set_vehicle_diagnostics(ui_t *ui, const diagnostics_snapshot_t *snapshot);
/* Ephemeral, authenticated bench presentation. Does not change vehicle data or NVS. */
bool   ui_request_showcase(ui_t    *ui,
                           bool     enabled,
                           uint8_t  seconds,
                           uint32_t sequence,
                           bool     vehicle_ready,
                           bool     maintenance);
size_t ui_showcase_status(ui_t *ui, uint32_t now, uint8_t out[20]);
bool   ui_showcase_active(ui_t *ui);
void   ui_showcase_next(ui_t *ui);
void   ui_showcase_stop(ui_t *ui);

/* LVGL-owned menu reuses the page gesture. Only the status mailbox is thread-safe. */
bool ui_action_opens_vehicle_menu(ui_t *ui);
void ui_show_vehicle_actions(ui_t *ui);
void ui_set_vehicle_action_status(ui_t    *ui,
                                  uint8_t  kind,
                                  uint8_t  phase,
                                  uint8_t  flags,
                                  uint16_t remaining,
                                  uint8_t  reason,
                                  uint8_t  preset);
void ui_set_vehicle_action_snapshot(ui_t *ui, const uint8_t status[32]);
void ui_set_idle_protection(ui_t *ui, bool configured);
void ui_vehicle_command_request(ui_t *ui, uint8_t *kind, bool *stop, uint8_t *preset, uint16_t *seconds);
void ui_vehicle_command_queued(ui_t *ui, bool accepted);

page_cycle_reason_t ui_cycle_pause(ui_t *ui);
