#include "worker_health.h"
#include "vehicle_commands.h"
#include "jeep_idle_parked_test.h"
#include "vehicle_idle_coolant.h"
// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
#include "transmission_gear.h"
#include "display_idle_policy.h"
#include "sdkconfig.h"
#include "esp_timer.h"

#include "esp_log.h"
#include "esp_log_color.h"
#include "esp_lvgl_port.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep

#include "freertos/projdefs.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "portmacro.h"

#include "core/lv_obj.h"
#include "core/lv_obj_event.h"
#include "core/lv_obj_pos.h"
#include "core/lv_obj_style_gen.h"
#include "core/lv_refr.h"
#include "display/lv_display.h"
#include "misc/lv_area.h"
#include "misc/lv_color.h"
#include "misc/lv_event.h"
#include "misc/lv_text.h"
#include "misc/lv_timer.h"
#include "misc/lv_types.h"
#include "widgets/label/lv_label.h"
#include "widgets/arc/lv_arc.h"
#include "widgets/bar/lv_bar.h"
#include "widgets/line/lv_line.h"

#include "obd.h"
#include "display_units.h"
#include "ui.h"
#include "alert_runtime.h"
#include "gauge_layout.h"
#include "gauge_presentation.h"
#include "display_showcase.h"
#include "adapter_status.h"
#include "ble_mgr.h"
#include "transfer_gate.h"
#include "util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "UI";

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct
{
    uint16_t pid;
    uint8_t  source;
    bool     used, present;
    double   value;
    uint32_t observed_at_ms, revision, generation;
} ui_sample_t;

typedef struct
{
    uint8_t  severity;
    uint8_t  additional;
    bool     simulated;
    bool     attention;
    uint16_t key;
    uint32_t boot, episode;
    float    value, limit;
    char     unit[12];
    bool     unavailable;
    char     label[32];
} ui_alert_t;

typedef struct
{
    bool    valid;
    bool    mil_on;
    bool    mil_known;
    bool    transmission;
    uint8_t count;
    bool    count_known, count_partial;
    char    first_code[6];
} ui_diagnostics_t;

typedef struct
{
    bool     enabled, vehicle_ready, maintenance;
    uint8_t  seconds;
    uint32_t sequence;
} showcase_request_t;

struct _ui_t
{
    ui_touch_callback_t touch_cb;
    bool                long_press_handled;
    bool                press_active;
    bool                suppress_action_click;
    page_action_t       action;
    atomic_bool         action_allowed, action_pending;
    atomic_uint         action_context_epoch;
    unsigned            action_seen_epoch;
    uint32_t            action_feedback_ms;
    bool                action_feedback, action_enter_pending;
    uint32_t            action_enter_at;
    bool                rendered_action_visible, rendered_action_feedback;
    uint8_t             rendered_action_progress;
    lv_obj_t           *action_label, *action_arrows[5];
    lv_obj_t           *command_menu, *command_button, *command_back, *command_title, *command_value, *command_note,
        *command_button_text, *command_ring, *command_link_symbol;
    lv_obj_t   *command_dots[3];
    uint8_t     command_page, command_request_page, command_page_count, command_pages[3];
    bool        command_suspended, command_press, command_moved, command_waiting, idle_protection;
    int16_t     command_x, command_y;
    lv_obj_t   *command_press_target;
    uint32_t    command_activity, command_wait_at, command_presented_state, command_confirm_state;
    atomic_uint command_ui[2];
    atomic_bool command_menu_active;
    bool        command_menu_visible, command_layer_dirty;
    uint8_t     command_confirm, command_selected;
    bool        command_hold_armed, command_hold_commit;
    uint32_t    command_press_at, command_press_epoch, command_confirm_epoch;
    atomic_uint command_epoch;
    uint32_t    command_scope[2][3]; /* App-task writer only; epoch is the LVGL fence. */
    bool        command_request_stop, command_admission_failed;
    uint16_t    command_request_seconds, command_confirm_seconds;

    bool               pairing_visible;
    uint32_t           pairing_code;
    TickType_t         reset_confirmation_at;
    TickType_t         owner_saved_at;
    char               pairing_identifier[7];
    bool               calibration_mode;
    bool               imperial_units;
    uint8_t            calibration_page;
    TickType_t         pressed_at;
    ui_alert_t         rendered_alert;
    ui_diagnostics_t   rendered_diagnostics;
    bool               alert_rendered;
    bool               diagnostics_rendered;
    bool               status_dirty, simulated;
    display_showcase_t showcase;
    atomic_bool        showcase_active;
    atomic_uint        showcase_generation, showcase_sequence, showcase_packed, showcase_started;
    ui_page_t          showcase_page;
    unsigned           page_index, page_count;
#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
    struct
    {
        int64_t  click_at_us;
        int64_t  reported_at_us;
        uint32_t count;
        uint32_t maximum_us;
        uint64_t total_us;
        uint32_t presses;
        uint32_t releases;
        uint32_t clicks;
        uint32_t holds;
    } timing;
#endif

    struct
    {
        QueueHandle_t touch_ev_que;
        QueueHandle_t pairing_que;
        QueueHandle_t pairing_id_que;
        QueueHandle_t alert_que;
        QueueHandle_t diagnostics_que;
        QueueHandle_t showcase_que;
    } rtos;
    struct
    {
        lv_obj_t *gauge_content;
        lv_obj_t *value_lbl;
        lv_obj_t *info_lbl;
        lv_obj_t *unit_lbl;
        lv_obj_t *pairing_lbl;
        lv_obj_t *pairing_title, *pairing_body, *pairing_code, *pairing_id;
        lv_obj_t *alert_lbl;
        lv_obj_t *alert_overlay;
        lv_obj_t *alert_overlay_label;
        lv_obj_t *attention_title, *attention_value, *attention_detail, *attention_button;
        lv_obj_t *diagnostics_lbl;
        lv_obj_t *diagnostics_icon, *alert_icon, *alert_arrow, *demo_lbl, *dual_divider;
        lv_obj_t *dual_unit[2];
        lv_obj_t *page_dots[8], *page_position;
        lv_obj_t *arc;
        lv_obj_t *bar;
        lv_obj_t *trend;
        lv_obj_t *dual_value[2];
        lv_obj_t *dual_info[2];
    } widgets;

    display_idle_policy_t idle;
    bool                  waiting_enabled, waiting_visible, wake_sequence;
    atomic_uint           cycle_pause;
    portMUX_TYPE          sample_lock;
    ui_sample_t           samples[EGAUGE_RUNTIME_PIDS];
    struct
    {
        bool     ready;
        uint32_t revision, generation;
    } sample_sources[2];

    struct
    {
        ui_page_t          page;
        double             rendered_values[2];
        bool               rendered_available[2];
        bool               rendered_once;
        int                rendered_offset;
        lv_point_precise_t trend_points[60];
        uint8_t            trend_count;
        TickType_t         trend_recorded_at;
    } display;
};

// ---------------------------------------------------------------------------------------------------------------------
// Fonts
// ---------------------------------------------------------------------------------------------------------------------

#define font_title (gauge_presentation()->title)
#define font_compact (gauge_presentation()->compact)
#define font_large (gauge_presentation()->large)
#define font_dual (gauge_presentation()->dual)
#define font_subtitle (gauge_presentation()->subtitle)
#define font_unit (gauge_presentation()->unit)
#define px gauge_presentation_px
#define gx gauge_presentation_x
#define gy gauge_presentation_y

static const uint32_t color_background     = 0x05080A;
static const uint32_t color_text_primary   = 0xE4EAED;
static const uint32_t color_text_secondary = 0x9CAAB2;
static const uint32_t color_track          = 0x1B2A30;
static const uint32_t color_accent         = 0x00FF70;
static const uint32_t color_warning        = 0xFF8A00;
static const uint32_t color_critical       = 0xFF1744;

// Profiles own native bezel/content insets; the shared composition owns placement.
// Physical bezel clearance is qualified separately from native render bounds.
#define GAUGE_ARC_DIAMETER (px(240) - 2 * gauge_presentation()->surface->decoration_inset)
#define GAUGE_ARC_WIDTH px(11)

#define CALIBRATION_PAGE_COUNT 5
#define PRIMARY_LABEL_WIDTH 160
#define PRIMARY_VALUE_WIDTH 176
#define PRIMARY_UNIT_WIDTH 156

static void showcase_publish(ui_t *ui);

static const ui_page_t *presented_page(const ui_t *ui)
{
    return atomic_load(&ui->showcase_active) ? &ui->showcase_page : &ui->display.page;
}
static uint32_t glyph_next(const char **p)
{
    const unsigned char *s = (const unsigned char *)*p;
    if (!*s)
        return 0;
    uint32_t cp = *s++;
    unsigned n  = 0;
    if (cp >= 0xc2 && cp <= 0xdf)
    {
        cp &= 31;
        n = 1;
    }
    else if (cp >= 0xe0 && cp <= 0xef)
    {
        cp &= 15;
        n = 2;
    }
    else if (cp >= 0xf0 && cp <= 0xf4)
    {
        cp &= 7;
        n = 3;
    }
    else if (cp >= 0x80)
        return UINT32_MAX;
    for (unsigned i = 0; i < n; i++)
    {
        if ((*s & 0xc0) != 0x80)
            return UINT32_MAX;
        cp = (cp << 6) | (*s++ & 63);
    }
    *p = (const char *)s;
    return cp;
}
typedef struct
{
    int left, top, right, bottom, advance;
} ink_t;
static bool ink(const char *text, const lv_font_t *font, ink_t *box)
{
    *box     = (ink_t){.left = 10000, .top = 10000, .right = -10000, .bottom = -10000};
    int  x   = 0;
    bool any = false;
    for (const char *p = text; *p;)
    {
        uint32_t            cp   = glyph_next(&p);
        const char         *look = p;
        uint32_t            next = glyph_next(&look);
        lv_font_glyph_dsc_t g;
        if (cp == UINT32_MAX || next == UINT32_MAX || !lv_font_get_glyph_dsc(font, &g, cp, next) || g.is_placeholder)
            return false;
        if (g.box_w && g.box_h)
        {
            int left = x + g.ofs_x, top = font->line_height - font->base_line - g.box_h - g.ofs_y;
            if (left < box->left)
                box->left = left;
            if (top < box->top)
                box->top = top;
            if (left + g.box_w > box->right)
                box->right = left + g.box_w;
            if (top + g.box_h > box->bottom)
                box->bottom = top + g.box_h;
            any = true;
        }
        x += g.adv_w;
    }
    box->advance = x;
    if (!any)
        box->left = box->top = box->right = box->bottom = 0;
    return true;
}
static bool label_at(lv_obj_t *label, const char *text, const lv_font_t *font, int cx, int cy, int maximum)
{
    ink_t b;
    if (!ink(text, font, &b))
        return false;
    int w = b.right - b.left, h = b.bottom - b.top;
    if (w > maximum || !gauge_presentation_fits(cx - (w + 1) / 2, cy - (h + 1) / 2, cx + (w + 1) / 2, cy + (h + 1) / 2))
        return false;
    lv_obj_set_style_text_font(label, font, 0);
    lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_LEFT, 0);
    lv_label_set_long_mode(label, LV_LABEL_LONG_CLIP);
    lv_label_set_text(label, text);
    lv_obj_set_style_pad_left(label, 4, 0);
    lv_obj_set_size(label, b.advance + 8, font->line_height);
    lv_obj_set_pos(label, cx - w / 2 - b.left - 4, cy - h / 2 - b.top);
    return true;
}
static void small_label(lv_obj_t *label, const char *text, int cx, int cy, int maximum)
{
    if (label_at(label, text, font_subtitle, cx, cy, maximum))
        return;
    char shortened[64];
    snprintf(shortened, sizeof(shortened), "%s", text);
    size_t n = strlen(shortened);
    while (n > 0)
    {
        do
        {
            n--;
        } while (n > 0 && ((unsigned char)shortened[n] & 0xc0) == 0x80);
        shortened[n] = 0;
        char attempt[68];
        snprintf(attempt, sizeof(attempt), "%s...", shortened);
        if (label_at(label, attempt, font_subtitle, cx, cy, maximum))
            return;
    }
    label_at(label, "Reading", font_subtitle, cx, cy, maximum);
}
static const lv_font_t *fit_value(const char *text, int cx, int cy, int maximum, const lv_font_t *preferred)
{
    const lv_font_t *fonts[]  = {font_title, font_large, font_dual, font_compact};
    bool             admitted = false;
    for (unsigned i = 0; i < 4; i++)
    {
        if (fonts[i] == preferred)
            admitted = true;
        if (!admitted)
            continue;
        ink_t b;
        if (!ink(text, fonts[i], &b))
            continue;
        int w = b.right - b.left, h = b.bottom - b.top;
        if (w <= maximum &&
            gauge_presentation_fits(cx - (w + 1) / 2, cy - (h + 1) / 2, cx + (w + 1) / 2, cy + (h + 1) / 2))
            return fonts[i];
    }
    return NULL;
}
static void value_label(lv_obj_t *label, const char *text, int cx, int cy, int maximum, const lv_font_t *preferred)
{
    const lv_font_t *font = fit_value(text, cx, cy, maximum, preferred);
    if (font)
        label_at(label, text, font, cx, cy, maximum);
    else
        small_label(label, "Value too wide", cx, cy, maximum);
}

static double bounded_range(double minimum, double maximum)
{
    double range = maximum - minimum;
    return isfinite(range) && range > 0 ? range : 1;
}

static int32_t normalized_value(double value, const ui_metric_t *metric, int32_t extent)
{
    double normalized = (value - metric->minimum) / bounded_range(metric->minimum, metric->maximum);
    return (int32_t)lround(fmin(1, fmax(0, normalized)) * extent);
}

static lv_obj_t          *boot_logo;
static ui_t              *boot_logo_ui;
static bool               early_logo_touched;
static lv_point_precise_t boot_needle[2];
static void               boot_logo_touch(lv_event_t *event)
{
    (void)event;
    early_logo_touched = true;
    if (boot_logo_ui)
    {
        display_idle_touch(&boot_logo_ui->idle, (uint32_t)(esp_timer_get_time() / 1000));
        boot_logo_ui->waiting_visible = false;
        boot_logo_ui->wake_sequence   = true;
        boot_logo_ui->status_dirty    = true;
        if (boot_logo_ui->touch_cb)
            boot_logo_ui->touch_cb(boot_logo_ui, LV_EVENT_PRESSED);
    }
    lv_obj_add_flag(boot_logo, LV_OBJ_FLAG_HIDDEN);
}
void ui_boot_logo_early(void)
{
    if (boot_logo)
        return;
    lv_obj_t *screen = lv_screen_active();
    lv_obj_set_style_bg_color(screen, lv_color_hex(color_background), 0);
    boot_logo = lv_obj_create(screen);
    lv_obj_remove_style_all(boot_logo);
    lv_obj_set_size(boot_logo, LV_PCT(100), LV_PCT(100));
    lv_obj_set_style_bg_color(boot_logo, lv_color_hex(color_background), 0);
    lv_obj_set_style_bg_opa(boot_logo, LV_OPA_COVER, 0);
    lv_obj_clear_flag(boot_logo, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_EVENT_BUBBLE);
    lv_obj_add_event_cb(boot_logo, boot_logo_touch, LV_EVENT_PRESSED, NULL);
    lv_obj_t *arc = lv_arc_create(boot_logo);
    lv_obj_set_size(arc, px(112), px(112));
    lv_obj_set_pos(arc, gx(64), gy(64));
    lv_arc_set_bg_angles(arc, 135, 405);
    lv_arc_set_range(arc, 0, 100);
    lv_arc_set_value(arc, 100);
    lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
    lv_obj_clear_flag(arc, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_style_arc_width(arc, px(8), LV_PART_MAIN);
    lv_obj_set_style_arc_width(arc, px(8), LV_PART_INDICATOR);
    lv_obj_set_style_arc_color(arc, lv_color_hex(color_accent), LV_PART_MAIN);
    lv_obj_set_style_arc_color(arc, lv_color_hex(color_accent), LV_PART_INDICATOR);
    lv_obj_set_style_arc_rounded(arc, true, LV_PART_INDICATOR);
    boot_needle[0]   = (lv_point_precise_t){gx(120), gy(120)};
    boot_needle[1]   = (lv_point_precise_t){gx(151), gy(89)};
    lv_obj_t *needle = lv_line_create(boot_logo);
    lv_line_set_points(needle, boot_needle, 2);
    lv_obj_set_style_line_width(needle, px(8), 0);
    lv_obj_set_style_line_rounded(needle, true, 0);
    lv_obj_set_style_line_color(needle, lv_color_hex(color_accent), 0);
    lv_obj_clear_flag(needle, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_t *dot = lv_obj_create(boot_logo);
    lv_obj_remove_style_all(dot);
    lv_obj_set_size(dot, px(14), px(14));
    lv_obj_set_pos(dot, gx(113), gy(113));
    lv_obj_set_style_radius(dot, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_color(dot, lv_color_hex(color_accent), 0);
    lv_obj_set_style_bg_opa(dot, LV_OPA_COVER, 0);
    lv_obj_clear_flag(dot, LV_OBJ_FLAG_CLICKABLE);
    lv_refr_now(NULL);
}
void ui_waiting_logo_enable(ui_t *ui, bool enabled)
{
    if (!ui)
        return;
    if (enabled)
        ui_boot_logo_early();
    ui->waiting_enabled = enabled;
    boot_logo_ui        = enabled ? ui : NULL;
    if (enabled && early_logo_touched)
        display_idle_touch(&ui->idle, (uint32_t)(esp_timer_get_time() / 1000));
    ui->waiting_visible = enabled && !ui->pairing_visible && !ui->idle.touched;
    if (boot_logo)
    {
        if (ui->waiting_visible)
        {
            lv_obj_remove_flag(boot_logo, LV_OBJ_FLAG_HIDDEN);
            lv_obj_move_foreground(boot_logo);
        }
        else
            lv_obj_add_flag(boot_logo, LV_OBJ_FLAG_HIDDEN);
    }
}

static bool metric_value(ui_t *ui, unsigned index, double *value)
{
    if (index >= ui->display.page.metric_count)
        return false;
    const ui_metric_t *metric = &ui->display.page.metrics[index];
    unsigned           source = metric->scoped ? metric->source_index : UINT8_MAX;
    /* Check live transport truth as well as the worker fence. A disconnected
     * sample cannot reappear during the interval before the worker clears it. */
    if (metric->scoped &&
        (source >= 2 || !adapter_status_ready_for(source) || ble_mgr_is_paused() || transfer_gate_current() == 2))
        return false;
    uint32_t generation = metric->scoped ? adapter_status_generation(source) : 0;
    uint32_t now        = (uint32_t)(esp_timer_get_time() / 1000);
    bool     available  = false;
    portENTER_CRITICAL(&ui->sample_lock);
    for (unsigned i = 0; i < EGAUGE_RUNTIME_PIDS; ++i)
    {
        const ui_sample_t *sample = &ui->samples[i];
        if (!sample->used || sample->pid != metric->pid || sample->source != source)
            continue;
        available = sample->present && now - sample->observed_at_ms <= metric->stale_after_ms;
        if (metric->scoped)
            available = available && ui->sample_sources[source].ready && sample->revision == metric->revision &&
                        sample->generation == generation;
        if (available)
            *value = sample->value;
        break;
    }
    portEXIT_CRITICAL(&ui->sample_lock);
    return available;
}

bool ui_source_sample(ui_t     *ui,
                      unsigned  source,
                      uint32_t  revision,
                      uint16_t  pid,
                      uint32_t  age_ms,
                      uint32_t  now,
                      double   *value,
                      uint32_t *at,
                      uint32_t *generation)
{
    if (!ui || source >= 2 || !value || !at || !generation || !adapter_status_ready_for(source) ||
        ble_mgr_is_paused() || transfer_gate_current() == 2)
        return false;
    uint32_t live_generation = adapter_status_generation(source);
    bool     found           = false;
    portENTER_CRITICAL(&ui->sample_lock);
    for (unsigned i = 0; i < EGAUGE_RUNTIME_PIDS; ++i)
    {
        const ui_sample_t *sample = &ui->samples[i];
        if (!sample->used || sample->source != source || sample->pid != pid)
            continue;
        found = sample->present && now - sample->observed_at_ms <= age_ms && ui->sample_sources[source].ready &&
                sample->revision == revision && sample->generation == live_generation;
        if (found)
        {
            *value      = sample->value;
            *at         = sample->observed_at_ms;
            *generation = live_generation;
        }
        break;
    }
    portEXIT_CRITICAL(&ui->sample_lock);
    return found;
}

static void show(lv_obj_t *object, bool visible)
{
    if (!object)
        return;
    if (visible)
        lv_obj_remove_flag(object, LV_OBJ_FLAG_HIDDEN);
    else
        lv_obj_add_flag(object, LV_OBJ_FLAG_HIDDEN);
}

static void show_pairing(ui_t *ui, uint32_t pairing_code)
{
    ui->pairing_code    = pairing_code;
    ui->pairing_visible = pairing_code != UI_PAIRING_HIDDEN;
    if (ui->pairing_visible)
    {
        ui->showcase.active = false;
        showcase_publish(ui);
        ui->display.rendered_once = false;
        ui->status_dirty          = true;
        const char *title = "Pair gauge", *body = "";
        bool        code = false, identifier = true;
        if (pairing_code == UI_PAIRING_READY)
        {
            body = "Open eGauge App";
        }
        else if (pairing_code == UI_PAIRING_WAITING)
        {
            body = "Hold to pair";
        }
        else if (pairing_code == UI_PAIRING_RESET_CONFIRM)
        {
            ui->reset_confirmation_at = xTaskGetTickCount();
            title                     = "Forget phone?";
            body                      = "Tap to confirm";
            identifier                = false;
        }
        else if (pairing_code == UI_PAIRING_OWNER_SAVED)
        {
            ui->owner_saved_at = xTaskGetTickCount();
            title              = "Phone paired";
            identifier         = false;
        }
        else
        {
            title = "Pairing code";
            code  = true;
        }
        label_at(ui->widgets.pairing_title, title, font_unit, gx(120), gy(75), px(174));
        small_label(ui->widgets.pairing_body, body, gx(120), gy(111), px(172));
        char digits[16];
        snprintf(digits, sizeof(digits), "%03" PRIu32 " %03" PRIu32, pairing_code / 1000, pairing_code % 1000);
        if (code)
            value_label(ui->widgets.pairing_code, digits, gx(120), gy(118), px(176), font_large);
        show(ui->widgets.pairing_body, !code);
        show(ui->widgets.pairing_code, code);
        char id[16];
        snprintf(id, sizeof(id), "ID %s", ui->pairing_identifier);
        small_label(ui->widgets.pairing_id, id, gx(120), gy(186), px(112));
        show(ui->widgets.pairing_id, identifier && ui->pairing_identifier[0]);
    }
    show(ui->widgets.gauge_content, !ui->pairing_visible);
    show(ui->widgets.pairing_lbl, ui->pairing_visible);
}

static lv_obj_t *calibration_box(lv_obj_t *parent, int32_t x, int32_t y, int32_t width, int32_t height, uint32_t color)
{
    lv_obj_t *box = lv_obj_create(parent);
    lv_obj_remove_style_all(box);
    lv_obj_set_pos(box, gx(x), gy(y));
    lv_obj_set_size(box, px(width), px(height));
    lv_obj_set_style_bg_color(box, lv_color_hex(color), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(box, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_clear_flag(box, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(box, LV_OBJ_FLAG_EVENT_BUBBLE);
    return box;
}

static lv_obj_t *calibration_label(lv_obj_t        *parent,
                                   const char      *text,
                                   int32_t          y,
                                   int32_t          width,
                                   const lv_font_t *font,
                                   uint32_t         color)
{
    lv_obj_t *label = lv_label_create(parent);
    lv_obj_remove_style_all(label);
    lv_obj_set_width(label, px(width));
    lv_obj_align(label, LV_ALIGN_TOP_MID, 0, gy(y));
    lv_obj_set_style_text_align(label, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_obj_set_style_text_font(label, font, LV_PART_MAIN);
    lv_obj_set_style_text_color(label, lv_color_hex(color), LV_PART_MAIN);
    lv_label_set_text(label, text);
    lv_obj_clear_flag(label, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(label, LV_OBJ_FLAG_EVENT_BUBBLE);
    return label;
}

static void calibration_ring(lv_obj_t *parent, int32_t diameter, uint32_t color)
{
    lv_obj_t *ring = lv_obj_create(parent);
    lv_obj_remove_style_all(ring);
    lv_obj_set_size(ring, px(diameter), px(diameter));
    lv_obj_center(ring);
    lv_obj_set_style_radius(ring, LV_RADIUS_CIRCLE, LV_PART_MAIN);
    lv_obj_set_style_border_width(ring, px(1), LV_PART_MAIN);
    lv_obj_set_style_border_color(ring, lv_color_hex(color), LV_PART_MAIN);
    lv_obj_set_style_border_opa(ring, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_bg_opa(ring, LV_OPA_TRANSP, LV_PART_MAIN);
    lv_obj_clear_flag(ring, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(ring, LV_OBJ_FLAG_EVENT_BUBBLE);
}

static void render_calibration_page(ui_t *ui)
{
    lv_obj_t *screen = lv_screen_active();
    lv_obj_clean(screen);
    lv_obj_set_style_bg_color(screen, lv_color_hex(color_background), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(screen, LV_OPA_COVER, LV_PART_MAIN);

    switch (ui->calibration_page)
    {
    case 0:
    {
        static const uint32_t colors[6] = {
            0xE53935, 0x43A047, 0x1E88E5, 0x00ACC1, 0x8E24AA, 0xFDD835,
        };
        for (unsigned i = 0; i < 3; ++i)
        {
            calibration_box(screen, (int32_t)i * 80, 0, 80, 120, colors[i]);
            calibration_box(screen, (int32_t)i * 80, 120, 80, 120, colors[i + 3]);
        }
        lv_obj_t *disc = calibration_box(screen, 77, 77, 86, 86, color_background);
        lv_obj_set_style_radius(disc, LV_RADIUS_CIRCLE, LV_PART_MAIN);
        calibration_label(screen, "1/5\nCOLOR", 91, 96, font_subtitle, color_text_primary);
        break;
    }
    case 1:
        calibration_box(screen, 0, 0, 240, 48, 0x000000);
        calibration_box(screen, 0, 48, 240, 48, 0x101820);
        calibration_box(screen, 0, 96, 240, 48, 0x40505A);
        calibration_box(screen, 0, 144, 240, 48, 0x9CAAB2);
        calibration_box(screen, 0, 192, 240, 48, 0xE4EAED);
        calibration_label(screen, "2/5  NEUTRAL", 106, 150, font_subtitle, 0xFFFFFF);
        break;
    case 2:
        calibration_ring(screen, 236, 0xE75A5A);  // radius 118, physical edge reference
        calibration_ring(screen, 224, 0xE0A63A);  // radius 112
        calibration_ring(screen, 208, 0x26B895);  // radius 104, content candidate
        calibration_ring(screen, 192, 0x4AA3DF);  // radius 96
        calibration_box(screen, 119, 12, 2, 216, color_text_secondary);
        calibration_box(screen, 12, 119, 216, 2, color_text_secondary);
        calibration_label(screen, "3/5  RINGS", 71, 150, font_subtitle, color_text_primary);
        calibration_label(screen, "118 112 104 96", 145, 160, font_subtitle, color_text_primary);
        break;
    case 3:
    {
        calibration_ring(screen, 208, color_accent);
        lv_obj_t *safe = lv_obj_create(screen);
        lv_obj_remove_style_all(safe);
        lv_obj_set_size(safe, px(147), px(147));
        lv_obj_center(safe);
        lv_obj_set_style_border_width(safe, px(1), LV_PART_MAIN);
        lv_obj_set_style_border_color(safe, lv_color_hex(color_warning), LV_PART_MAIN);
        lv_obj_set_style_bg_opa(safe, LV_OPA_TRANSP, LV_PART_MAIN);
        lv_obj_clear_flag(safe, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
        lv_obj_add_flag(safe, LV_OBJ_FLAG_EVENT_BUBBLE);
        calibration_label(screen, "ENGINE RPM", 44, 160, font_subtitle, color_text_secondary);
        calibration_label(screen, "16,383", 78, 160, font_compact, color_text_primary);
        calibration_label(screen, "rpm", 126, 160, font_unit, color_text_secondary);
        calibration_label(screen, "4/5 TYPE SAFE", 180, 150, font_subtitle, color_accent);
        break;
    }
    default:
    {
        lv_obj_t *arc = lv_arc_create(screen);
        lv_obj_set_size(arc, GAUGE_ARC_DIAMETER, GAUGE_ARC_DIAMETER);
        lv_obj_center(arc);
        lv_arc_set_bg_angles(arc, 135, 45);
        lv_arc_set_range(arc, 0, 8000);
        lv_arc_set_value(arc, 2840);
        lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
        lv_obj_clear_flag(arc, LV_OBJ_FLAG_CLICKABLE);
        lv_obj_add_flag(arc, LV_OBJ_FLAG_EVENT_BUBBLE);
        lv_obj_set_style_arc_width(arc, GAUGE_ARC_WIDTH, LV_PART_MAIN);
        lv_obj_set_style_arc_width(arc, GAUGE_ARC_WIDTH, LV_PART_INDICATOR);
        lv_obj_set_style_arc_rounded(arc, true, LV_PART_MAIN);
        lv_obj_set_style_arc_rounded(arc, true, LV_PART_INDICATOR);
        lv_obj_set_style_arc_color(arc, lv_color_hex(color_track), LV_PART_MAIN);
        lv_obj_set_style_arc_color(arc, lv_color_hex(color_accent), LV_PART_INDICATOR);
        calibration_label(screen, "ENGINE RPM", 48, 166, font_subtitle, color_text_secondary);
        calibration_label(screen, "2840", 78, 166, font_title, color_text_primary);
        calibration_label(screen, "rpm", 158, 166, font_unit, color_text_secondary);
        calibration_label(screen, "5/5", 198, 60, font_subtitle, color_accent);
        break;
    }
    }
    lv_refr_now(NULL);
    ESP_LOGI(TAG, "Display calibration page %u/%u", ui->calibration_page + 1, CALIBRATION_PAGE_COUNT);
}

static void select_renderer_widgets(ui_t *ui)
{
    ui_renderer_t renderer = presented_page(ui)->renderer;
    bool          dual     = renderer == UI_RENDERER_DUAL || renderer == UI_RENDERER_DUAL_ARC;
    show(ui->widgets.value_lbl, !dual);
    show(ui->widgets.info_lbl, !dual);
    show(ui->widgets.unit_lbl, !dual);
    show(ui->widgets.arc, renderer == UI_RENDERER_ARC || renderer == UI_RENDERER_DUAL_ARC);
    show(ui->widgets.bar, renderer == UI_RENDERER_BAR);
    show(ui->widgets.trend, renderer == UI_RENDERER_TREND);
    for (unsigned i = 0; i < 2; ++i)
    {
        show(ui->widgets.dual_value[i], dual);
        show(ui->widgets.dual_info[i], dual);
        show(ui->widgets.dual_unit[i], dual);
    }
    show(ui->widgets.dual_divider, dual);
}

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void refresh_vehicle_menu(ui_t *ui);
static void command_hold_tick(ui_t *ui);
static void command_attention_ring(ui_t *ui);
static void close_vehicle_menu(ui_t *ui);
static bool command_busy(ui_t *ui);

static void dispatch_touch(ui_t *ui, lv_event_code_t code)
{
    /* Calibration replaces screen objects, so defer that callback until the
     * current input event has finished. Normal navigation keeps its objects. */
    if (ui->calibration_mode)
    {
        xQueueSend(ui->rtos.touch_ev_que, &code, 0);
    }
    else if (ui->touch_cb &&
             (!(ui->pairing_visible && code == LV_EVENT_CLICKED) || ui->pairing_code == UI_PAIRING_RESET_CONFIRM))
    {
#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
        if (code == LV_EVENT_CLICKED && ui->timing.click_at_us == 0)
            ui->timing.click_at_us = esp_timer_get_time();
#endif
        ui->touch_cb(ui, code);
    }
}

static bool action_context(ui_t *ui)
{
    unsigned epoch     = atomic_load(&ui->action_context_epoch);
    bool     allowed   = atomic_load(&ui->action_allowed) && !ui->pairing_visible && !ui->calibration_mode &&
                         !atomic_load(&ui->showcase_active) && !ui->rendered_alert.attention;
    bool     hard_loss = !atomic_load(&ui->action_allowed) || ui->pairing_visible || ui->calibration_mode ||
                         atomic_load(&ui->showcase_active) || epoch != ui->action_seen_epoch;
    if (hard_loss && ui->command_menu_visible)
        close_vehicle_menu(ui);
    if (ui->command_menu_visible && ui->command_suspended != ui->rendered_alert.attention)
    {
        ui->command_suspended = ui->rendered_alert.attention;
        ui->command_press     = false;
        ui->command_confirm   = 0;
        if (ui->command_suspended)
            lv_obj_add_flag(ui->command_menu, LV_OBJ_FLAG_HIDDEN);
        else
        {
            lv_obj_remove_flag(ui->command_menu, LV_OBJ_FLAG_HIDDEN);
            ui->command_activity = (uint32_t)(esp_timer_get_time() / 1000);
        }
    }
    if (!allowed || epoch != ui->action_seen_epoch)
    {
        page_action_reset(&ui->action);
        ui->action_enter_pending = false;
    }
    ui->action_seen_epoch = epoch;
    return allowed;
}

static void ui_touch_callback(lv_event_t *e)
{
    ESP_NULL_CHECK(e, TAG, "Event is NULL");
    ui_t *ui = (ui_t *)lv_event_get_user_data(e);
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    lv_event_code_t code = lv_event_get_code(e);
    if (ui->wake_sequence)
    {
        if (code == LV_EVENT_PRESSED)
            ui->wake_sequence = false;
        else
        {
            if (code == LV_EVENT_CLICKED || code == LV_EVENT_PRESS_LOST)
                ui->wake_sequence = false;
            return;
        }
    }
    if (code == LV_EVENT_PRESSED)
        display_idle_touch(&ui->idle, (uint32_t)(esp_timer_get_time() / 1000));
    if (ui->command_menu_visible)
        return;
#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
    if (code == LV_EVENT_PRESSED)
        ui->timing.presses++;
    if (code == LV_EVENT_RELEASED)
        ui->timing.releases++;
    if (code == LV_EVENT_CLICKED)
        ui->timing.clicks++;
    if (code == LV_EVENT_LONG_PRESSED)
        ui->timing.holds++;
#endif
    bool        actions_allowed = action_context(ui);
    lv_point_t  point           = {0};
    lv_indev_t *input           = lv_indev_active();
    if (input)
    {
        lv_indev_get_point(input, &point);
        point.x = gauge_presentation_design_x(point.x);
        point.y = gauge_presentation_design_y(point.y);
    }
    uint32_t now = (uint32_t)(esp_timer_get_time() / 1000);
    switch (code)
    {
    case LV_EVENT_PRESSED:
        ui->action_enter_pending  = false;
        ui->suppress_action_click = false;
        if (actions_allowed && input)
            page_action_press(&ui->action, point.x, point.y, now);
        ui->press_active       = true;
        ui->long_press_handled = false;
        ui->pressed_at         = xTaskGetTickCount();
        dispatch_touch(ui, LV_EVENT_PRESSED);
        break;
    case LV_EVENT_PRESS_LOST:
        page_action_reset(&ui->action);
        ui->press_active = ui->action_enter_pending = false;
        ui->suppress_action_click                   = true;
        break;
    case LV_EVENT_PRESSING:
        if (actions_allowed && input)
            page_action_move(&ui->action, point.x, point.y);
        break;
    case LV_EVENT_LONG_PRESSED:
        if (actions_allowed && ui->action.pressed && !ui->action.cancelled && (int)ui->action.y - point.y >= 32)
            break;
        page_action_reset(&ui->action);
        ui->long_press_handled = true;
        dispatch_touch(ui, code);
        break;
    case LV_EVENT_CLICKED:
        if (!ui->long_press_handled && !ui->suppress_action_click)
        {
            if (ui->pairing_code == UI_PAIRING_RESET_CONFIRM)
                dispatch_touch(ui, LV_EVENT_VALUE_CHANGED);
            else
                dispatch_touch(ui, code);
        }
        break;
    case LV_EVENT_RELEASED:
        if (actions_allowed && input && !ui->long_press_handled)
        {
            bool fired                = false;
            ui->suppress_action_click = page_action_release(&ui->action, point.x, point.y, now, &fired);
            if (fired)
            {
                if (ui->action.config.vehicle_menu)
                {
                    ui->action_enter_pending = true;
                    ui->action_enter_at      = now;
                    atomic_store(&ui->action_pending, true);
                }
                else
                    dispatch_touch(ui, UI_EVENT_PAGE_ACTION);
            }
        }
        else
            page_action_reset(&ui->action);
        /* A release without our matching press must never clear the owner. */
        if (ui->press_active && xTaskGetTickCount() - ui->pressed_at >= pdMS_TO_TICKS(12000))
        {
            dispatch_touch(ui, code);
        }
        ui->press_active = false;
        break;
    default:
        // ignore event
        break;
    }
}

static void format_reading(char out[32], const ui_metric_t *metric, bool available, double value, bool imperial)
{
    if (!available)
    {
        snprintf(out, 32, "--");
        return;
    }
    if (!strcmp(metric->unit, "gear"))
    {
        const char *gear = transmission_gear_label(value);
        snprintf(out, 32, "%s", gear ? gear : "?");
    }
    else
        display_units_format(out, 32, value, metric->unit, imperial);
}
static ui_alert_t       visible_alert(ui_t *ui);
static ui_diagnostics_t visible_diagnostics(ui_t *ui);
static void             render_page(ui_t *ui)
{
    const ui_page_t                *page         = presented_page(ui);
    double                          values[2]    = {0};
    bool                            available[2] = {false};
    bool                            demo         = atomic_load(&ui->showcase_active);
    const display_showcase_frame_t *frame        = demo ? display_showcase_frame(ui->showcase.index) : NULL;
    for (unsigned i = 0; i < page->metric_count; i++)
    {
        if (frame)
        {
            values[i]    = frame->values[i];
            available[i] = frame->present[i];
        }
        else
            available[i] = metric_value(ui, i, &values[i]);
    }
    if (frame && page->renderer == UI_RENDERER_TREND)
        values[0] += 2 * sin((esp_timer_get_time() / 1000000.0) * 1.5);
    const ui_metric_t *primary     = &page->metrics[0];
    ui_renderer_t      renderer    = page->renderer;
    ui_alert_t         condition   = visible_alert(ui);
    ui_diagnostics_t   diagnostics = visible_diagnostics(ui);
    /* Owner's Round 3 optical correction, only for the no-CEL Numeric
     * condition view. CEL combinations and chart geometry remain unchanged. */
    int offset = renderer == UI_RENDERER_NUMERIC && condition.severity && !condition.attention &&
                         !(diagnostics.valid && (diagnostics.mil_on || diagnostics.count))
                     ? -12
                     : 0;
    if (!ui->display.rendered_once)
        select_renderer_widgets(ui);
    if (renderer == UI_RENDERER_DUAL || renderer == UI_RENDERER_DUAL_ARC)
    {
        for (unsigned i = 0; i < 2; i++)
        {
            if (ui->display.rendered_once && available[i] == ui->display.rendered_available[i] &&
                (!available[i] || values[i] == ui->display.rendered_values[i]))
                continue;
            const ui_metric_t *metric = &page->metrics[i];
            char               text[32];
            format_reading(text, metric, available[i], values[i], ui->imperial_units);
            const char *label         = gauge_layout_label(metric->name, metric->unit);
            const char *unit          = gauge_layout_unit(label, display_units_label(metric->unit, ui->imperial_units));
            int         content_width = renderer == UI_RENDERER_DUAL_ARC ? 164 : 192;
            small_label(ui->widgets.dual_info[i], label, gx(120), gy(i ? 135 : 72), px(content_width - 8));
            int   cy = gy(i ? 160 : 97);
            ink_t u;
            if (!ink(unit, font_subtitle, &u))
            {
                unit = "Unit?";
                ink(unit, font_subtitle, &u);
            }
            int              uw        = u.right - u.left;
            const lv_font_t *preferred = font_dual;
            if (ui->display.rendered_once)
            {
                const lv_font_t *previous = lv_obj_get_style_text_font(ui->widgets.dual_value[i], 0);
                if (previous == font_dual || previous == font_compact)
                    preferred = previous;
            }
            const lv_font_t *font   = fit_value(text, gx(120) - (uw ? uw / 2 + px(3) : 0), cy,
                                                px(content_width) - (uw ? uw + px(6) : 0), preferred);
            bool             placed = false;
            if (font)
            {
                ink_t v;
                ink(text, font, &v);
                int vw = v.right - v.left;
                placed = label_at(ui->widgets.dual_value[i], text, font, gx(120) - (uw ? uw / 2 + px(3) : 0), cy,
                                  px(content_width) - (uw ? uw + px(6) : 0));
                if (uw)
                    placed = label_at(ui->widgets.dual_unit[i], unit, font_subtitle, gx(120) + vw / 2 + px(3),
                                      cy + px(7), px(48)) &&
                             placed;
            }
            if (!placed)
                small_label(ui->widgets.dual_value[i], "Value too wide", gx(120), cy, px(166));
            show(ui->widgets.dual_unit[i], uw && placed);
            lv_obj_set_style_text_color(ui->widgets.dual_value[i],
                                        lv_color_hex(!available[i]                   ? color_text_secondary
                                                     : !strcmp(metric->unit, "gear") ? transmission_gear_color(text)
                                                                                     : color_text_primary),
                                        0);
        }
    }
    else if (!ui->display.rendered_once || offset != ui->display.rendered_offset ||
             available[0] != ui->display.rendered_available[0] ||
             (available[0] && values[0] != ui->display.rendered_values[0]))
    {
        char text[32];
        format_reading(text, primary, available[0], values[0], ui->imperial_units);
        const char *label   = gauge_layout_label(page->name ? page->name : primary->name, primary->unit);
        const char *unit    = gauge_layout_unit(label, display_units_label(primary->unit, ui->imperial_units));
        int         title_y = renderer == UI_RENDERER_BAR ? 70 : renderer == UI_RENDERER_TREND ? 72 : 87;
        int         value_y = renderer == UI_RENDERER_BAR ? 108 : renderer == UI_RENDERER_TREND ? 102 : 126;
        int         unit_y  = renderer == UI_RENDERER_BAR ? 147 : renderer == UI_RENDERER_TREND ? 132 : 169;
        title_y += offset;
        value_y += offset;
        unit_y += offset;
        small_label(ui->widgets.info_lbl, label, gx(120), gy(title_y), px(184));
        const lv_font_t *preferred = renderer == UI_RENDERER_TREND ? font_large : font_title;
        if (ui->display.rendered_once)
        {
            const lv_font_t *previous = lv_obj_get_style_text_font(ui->widgets.value_lbl, 0);
            if (previous == font_title || previous == font_large || previous == font_dual || previous == font_compact)
                preferred = previous;
        }
        value_label(ui->widgets.value_lbl, text, gx(120), gy(value_y), px(192), preferred);
        if (renderer == UI_RENDERER_TREND)
            small_label(ui->widgets.unit_lbl, unit, gx(120), gy(unit_y), px(156));
        else if (!label_at(ui->widgets.unit_lbl, unit, font_unit, gx(120), gy(unit_y), px(156)))
            small_label(ui->widgets.unit_lbl, "Unit?", gx(120), gy(unit_y), px(140));
        lv_obj_set_style_text_color(ui->widgets.value_lbl,
                                    lv_color_hex(!available[0]                    ? color_text_secondary
                                                 : !strcmp(primary->unit, "gear") ? transmission_gear_color(text)
                                                                                  : color_text_primary),
                                    0);
        if (renderer == UI_RENDERER_ARC)
            lv_arc_set_value(ui->widgets.arc, available[0] ? normalized_value(values[0], primary, 1000) : 0);
        if (renderer == UI_RENDERER_BAR)
            lv_bar_set_value(ui->widgets.bar, available[0] ? normalized_value(values[0], primary, 1000) : 0,
                             LV_ANIM_OFF);
    }
    if (renderer == UI_RENDERER_DUAL_ARC)
    {
        unsigned arc = page->arc_metric_index < 2 ? page->arc_metric_index : 0;
        lv_arc_set_value(ui->widgets.arc,
                         available[arc] ? normalized_value(values[arc], &page->metrics[arc], 1000) : 0);
    }
    if (renderer == UI_RENDERER_TREND)
    {
        if (!available[0])
            ui->display.trend_count = 0;
        else if (xTaskGetTickCount() - ui->display.trend_recorded_at >= pdMS_TO_TICKS(500))
        {
            ui->display.trend_recorded_at = xTaskGetTickCount();
            if (ui->display.trend_count < 60)
                ui->display.trend_count++;
            memmove(ui->display.trend_points, ui->display.trend_points + 1, 59 * sizeof(ui->display.trend_points[0]));
            for (unsigned i = 0; i < 60; i++)
                ui->display.trend_points[i].x = (int32_t)i * px(144) / 59;
            ui->display.trend_points[59].y = px(30) - normalized_value(values[0], primary, px(30));
            lv_line_set_points(ui->widgets.trend, ui->display.trend_points + 60 - ui->display.trend_count,
                               ui->display.trend_count);
        }
        show(ui->widgets.trend, available[0] && ui->display.trend_count >= 2);
    }
    for (unsigned i = 0; i < 2; i++)
    {
        if (available[i] != ui->display.rendered_available[i])
            ui->status_dirty = true;
        ui->display.rendered_values[i]    = values[i];
        ui->display.rendered_available[i] = available[i];
    }
    ui->display.rendered_once   = true;
    ui->display.rendered_offset = offset;
}
static void showcase_publish(ui_t *ui)
{
    atomic_fetch_add(&ui->showcase_generation, 1);
    atomic_store(&ui->showcase_active, ui->showcase.active);
    atomic_store(&ui->showcase_sequence, ui->showcase.sequence);
    atomic_store(&ui->showcase_packed,
                 ui->showcase.index | ((unsigned)ui->showcase.seconds << 8) | ((unsigned)ui->showcase.result << 16));
    atomic_store(&ui->showcase_started, ui->showcase.started);
    atomic_fetch_add(&ui->showcase_generation, 1);
}
static void showcase_frame(ui_t *ui)
{
    const display_showcase_frame_t *f = display_showcase_frame(ui->showcase.index);
    ui->showcase_page = (ui_page_t){.name = f->name, .renderer = f->renderer, .metric_count = f->metrics};
    for (unsigned i = 0; i < f->metrics; i++)
        ui->showcase_page.metrics[i] = (ui_metric_t){.pid            = 0xd000 + i,
                                                     .name           = f->labels[i],
                                                     .unit           = f->units[i],
                                                     .minimum        = f->minimum,
                                                     .maximum        = f->maximum,
                                                     .stale_after_ms = 2000};
    ui->display.rendered_once = false;
    ui->display.trend_count   = 0;
    ui->status_dirty          = true;
    if (ui->showcase.active && f->renderer == UI_RENDERER_TREND)
    {
        for (unsigned i = 0; i < 60; i++)
        {
            double sample = f->values[0] + 4 * sin(i * .24) + 2 * sin(i * .07);
            ui->display.trend_points[i] =
                (lv_point_precise_t){.x = (int)i * px(144) / 59,
                                     .y = px(30) - normalized_value(sample, &ui->showcase_page.metrics[0], px(30))};
        }
        ui->display.trend_count = 60;
        lv_line_set_points(ui->widgets.trend, ui->display.trend_points, 60);
        ui->display.trend_recorded_at = xTaskGetTickCount();
    }
    showcase_publish(ui);
}

#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
static void render_finished(lv_event_t *event)
{
    ui_t *ui = lv_event_get_user_data(event);
    if (!ui->timing.click_at_us)
        return;
    uint32_t elapsed = (uint32_t)(esp_timer_get_time() - ui->timing.click_at_us);
    ui->timing.count++;
    ui->timing.total_us += elapsed;
    if (elapsed > ui->timing.maximum_us)
        ui->timing.maximum_us = elapsed;
    ui->timing.click_at_us = 0;
}
#endif

#define engine_outline (gauge_presentation()->symbols[GAUGE_SYMBOL_ENGINE_OUTLINE])
#define engine_pipe (gauge_presentation()->symbols[GAUGE_SYMBOL_ENGINE_PIPE])
#define engine_join (gauge_presentation()->symbols[GAUGE_SYMBOL_ENGINE_JOIN])
#define wrench_handle (gauge_presentation()->symbols[GAUGE_SYMBOL_WRENCH_HANDLE])
#define wrench_head (gauge_presentation()->symbols[GAUGE_SYMBOL_WRENCH_HEAD])
#define warning_outline (gauge_presentation()->symbols[GAUGE_SYMBOL_WARNING_OUTLINE])
#define critical_outline (gauge_presentation()->symbols[GAUGE_SYMBOL_CRITICAL_OUTLINE])
#define warning_stem (gauge_presentation()->symbols[GAUGE_SYMBOL_WARNING_STEM])
#define warning_dot (gauge_presentation()->symbols[GAUGE_SYMBOL_WARNING_DOT])
#define arrow_up (gauge_presentation()->symbols[GAUGE_SYMBOL_ARROW_UP])
#define arrow_down (gauge_presentation()->symbols[GAUGE_SYMBOL_ARROW_DOWN])
static lv_obj_t *plain_view(lv_obj_t *parent, int width, int height)
{
    lv_obj_t *view = lv_obj_create(parent);
    lv_obj_remove_style_all(view);
    lv_obj_set_size(view, px(width), px(height));
    lv_obj_clear_flag(view, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(view, LV_OBJ_FLAG_EVENT_BUBBLE);
    return view;
}
static lv_obj_t *plain_label(lv_obj_t *parent, uint32_t color)
{
    lv_obj_t *label = lv_label_create(parent);
    lv_obj_remove_style_all(label);
    lv_obj_set_style_text_color(label, lv_color_hex(color), 0);
    lv_obj_clear_flag(label, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(label, LV_OBJ_FLAG_EVENT_BUBBLE);
    return label;
}
static lv_obj_t *icon_create(lv_obj_t *parent, bool diagnostic)
{
    lv_obj_t *icon = plain_view(parent, diagnostic ? 32 : 22, 22);
    for (unsigned i = 0; i < 3; i++)
    {
        lv_obj_t *line = lv_line_create(icon);
        lv_obj_remove_style_all(line);
        lv_obj_set_style_line_width(line, px(2), 0);
        lv_obj_set_style_line_rounded(line, true, 0);
        lv_obj_clear_flag(line, LV_OBJ_FLAG_CLICKABLE);
        lv_obj_add_flag(line, LV_OBJ_FLAG_EVENT_BUBBLE);
    }
    return icon;
}
static void icon_style(lv_obj_t *icon, unsigned kind, uint32_t color)
{
    lv_obj_t *a = lv_obj_get_child(icon, 0), *b = lv_obj_get_child(icon, 1), *c = lv_obj_get_child(icon, 2);
    lv_obj_set_style_line_color(a, lv_color_hex(color), 0);
    lv_obj_set_style_line_color(b, lv_color_hex(color), 0);
    lv_obj_set_style_line_color(c, lv_color_hex(color), 0);
    show(c, kind != 1);
    if (kind == 0)
    {
        lv_line_set_points(a, engine_outline, 13);
        lv_line_set_points(b, engine_pipe, 2);
        lv_line_set_points(c, engine_join, 2);
    }
    else if (kind == 1)
    {
        lv_line_set_points(a, wrench_handle, 2);
        lv_line_set_points(b, wrench_head, 5);
    }
    else
    {
        lv_line_set_points(a, kind == 3 ? critical_outline : warning_outline, kind == 3 ? 5 : 4);
        lv_line_set_points(b, warning_stem, 2);
        lv_line_set_points(c, warning_dot, 2);
    }
}
static ui_alert_t visible_alert(ui_t *ui)
{
    if (!atomic_load(&ui->showcase_active))
        return ui->rendered_alert;
    const display_showcase_frame_t *f     = display_showcase_frame(ui->showcase.index);
    ui_alert_t                      alert = {.severity    = f->severity,
                                             .attention   = f->attention,
                                             .unavailable = f->last_seen,
                                             .value       = f->values[0],
                                             .limit       = 110,
                                             .simulated   = true};
    snprintf(alert.label, sizeof(alert.label), "%s", f->condition ? f->condition : "");
    const char *unit = f->units[0];
    if (f->condition && (!strcmp(f->condition, "Coolant") || !strcmp(f->condition, "ATF")))
    {
        unit        = "°C";
        alert.value = f->last_seen ? 92 : f->severity == 2 ? 118 : 112;
    }
    snprintf(alert.unit, sizeof(alert.unit), "%s", unit);
    return alert;
}
static ui_diagnostics_t visible_diagnostics(ui_t *ui)
{
    if (!atomic_load(&ui->showcase_active))
        return ui->rendered_diagnostics;
    const display_showcase_frame_t *f = display_showcase_frame(ui->showcase.index);
    return (ui_diagnostics_t){.valid       = true,
                              .mil_known   = true,
                              .mil_on      = f->mil,
                              .count       = f->faults,
                              .count_known = f->count_known};
}
static void render_status(ui_t *ui)
{
    ui_alert_t       alert = visible_alert(ui);
    ui_diagnostics_t d     = visible_diagnostics(ui);
    uint32_t         color = alert.severity == 2 ? color_critical : alert.severity == 1 ? color_warning : color_accent;
    const ui_page_t *page  = presented_page(ui);
    unsigned         chart_index =
        page->renderer == UI_RENDERER_DUAL_ARC && page->arc_metric_index < 2 ? page->arc_metric_index : 0;
    uint32_t chart_color = ui->display.rendered_available[chart_index] ? color : color_track;
    lv_obj_set_style_arc_color(ui->widgets.arc, lv_color_hex(chart_color), LV_PART_INDICATOR);
    lv_obj_set_style_bg_color(ui->widgets.bar, lv_color_hex(chart_color), LV_PART_INDICATOR);
    lv_obj_set_style_line_color(ui->widgets.trend, lv_color_hex(color), LV_PART_MAIN);
    bool diagnostic = d.valid && (d.mil_on || d.count);
    show(ui->widgets.diagnostics_icon, diagnostic);
    show(ui->widgets.diagnostics_lbl, diagnostic);
    if (diagnostic)
    {
        char count[12] = "";
        if (!d.count_known || d.count_partial)
            count[0] = 0;
        else if (d.count > 9)
            snprintf(count, sizeof(count), "9+");
        else if (d.count > 1)
            snprintf(count, sizeof(count), "%u", d.count);
        uint32_t dc = d.mil_on ? color_warning : color_text_secondary;
        icon_style(ui->widgets.diagnostics_icon, d.mil_on ? 0 : 1, dc);
        lv_obj_set_pos(ui->widgets.diagnostics_icon, gx(ui->command_menu_visible ? 145 : count[0] ? 94 : 104), gy(31));
        lv_obj_set_style_text_color(ui->widgets.diagnostics_lbl, lv_color_hex(dc), 0);
        small_label(ui->widgets.diagnostics_lbl, count, gx(ui->command_menu_visible ? 185 : 139), gy(42), px(34));
    }
    bool condition = alert.severity != 0 && (!ui->command_menu_visible || !lv_label_get_text(ui->command_note)[0]);
    show(ui->widgets.alert_lbl, condition);
    show(ui->widgets.alert_icon, condition);
    show(ui->widgets.alert_arrow, false);
    if (condition)
    {
        char alias[32], text[48];
        gauge_layout_alert_alias(alias, sizeof(alias), alert.label);
        if (alert.unavailable)
            snprintf(text, sizeof(text), "%s last seen", alias);
        else if (alert.additional)
            snprintf(text, sizeof(text), "%s +%u%s", alias, alert.additional > 9 ? 9 : alert.additional,
                     alert.additional > 9 ? "+" : "");
        else
            snprintf(text, sizeof(text), "%s", alias);
        ink_t bounds;
        bool  measured  = ink(text, font_subtitle, &bounds);
        bool  direction = !alert.unavailable && alert.key < 32 && alert.unit[0] && strcmp(alert.unit, "gear");
        int   width     = measured ? bounds.right - bounds.left : 10000,
              total     = px(22) + px(5) + width + (direction ? px(13) : 0);
        if (total > px(124))
        {
            snprintf(text, sizeof(text), "%s", alert.unavailable ? "Last seen" : "Alert");
            ink(text, font_subtitle, &bounds);
            width = bounds.right - bounds.left;
            total = px(27) + width + (direction ? px(13) : 0);
        }
        int x = gx(120) - total / 2;
        icon_style(ui->widgets.alert_icon, alert.severity == 2 ? 3 : 2, color);
        lv_obj_set_pos(ui->widgets.alert_icon, x, gy(ui->command_menu_visible ? 125 : 183));
        x += px(27);
        lv_obj_set_style_text_color(ui->widgets.alert_lbl, lv_color_hex(color), 0);
        small_label(ui->widgets.alert_lbl, text, x + width / 2, gy(ui->command_menu_visible ? 136 : 194), width + 1);
        x += width;
        if (direction)
        {
            lv_line_set_points(ui->widgets.alert_arrow, alert.value < alert.limit ? arrow_down : arrow_up, 5);
            lv_obj_set_style_line_color(ui->widgets.alert_arrow, lv_color_hex(color), 0);
            lv_obj_set_pos(ui->widgets.alert_arrow, x + px(5), gy(ui->command_menu_visible ? 129 : 187));
            show(ui->widgets.alert_arrow, true);
        }
    }
    if (ui->command_menu_visible && condition)
    {
        lv_obj_move_foreground(ui->widgets.alert_icon);
        lv_obj_move_foreground(ui->widgets.alert_lbl);
        lv_obj_move_foreground(ui->widgets.alert_arrow);
    }
    bool demo = atomic_load(&ui->showcase_active) || ui->simulated || alert.simulated;
    show(ui->widgets.demo_lbl, demo && !atomic_load(&ui->showcase_active));
    bool position = !condition && !demo && !alert.attention && !ui->action.progress && !ui->action_enter_pending;
    for (unsigned i = 0; i < 8; i++)
    {
        bool dot = position && ui->page_count <= 8 && ui->page_count > 1 && i < ui->page_count;
        show(ui->widgets.page_dots[i], dot);
        if (dot)
        {
            lv_obj_set_pos(ui->widgets.page_dots[i], gx(120) - (int)ui->page_count * px(4) + (int)i * px(8) + px(2),
                           gy(210));
            lv_obj_set_style_bg_color(ui->widgets.page_dots[i],
                                      lv_color_hex(i == ui->page_index ? color_text_primary : color_track), 0);
        }
    }
    show(ui->widgets.page_position, position && ui->page_count > 8);
    if (position && ui->page_count > 8)
    {
        char p[24];
        snprintf(p, sizeof(p), "%u/%u", ui->page_index + 1, ui->page_count);
        small_label(ui->widgets.page_position, p, gx(120), gy(213), px(48));
    }
    bool overlay = alert.attention && !ui->pairing_visible && !ui->calibration_mode;
    show(ui->widgets.alert_overlay, overlay);
    if (overlay)
    {
        char value[24], limit[24], reading[48], detail[48];
        display_units_format(value, sizeof(value), alert.value, alert.unit, ui->imperial_units);
        display_units_format(limit, sizeof(limit), alert.limit, alert.unit, ui->imperial_units);
        const char *unit = display_units_label(alert.unit, ui->imperial_units);
        snprintf(reading, sizeof(reading), "%s %s", alert.unavailable ? "--" : value, unit);
        snprintf(detail, sizeof(detail), "%s %s %s", alert.unavailable ? "Last seen" : "Limit",
                 alert.unavailable ? value : limit, unit);
        small_label(ui->widgets.attention_title, "CRITICAL", gx(120), gy(48), px(110));
        char alias[32];
        gauge_layout_alert_alias(alias, sizeof(alias), alert.label);
        small_label(ui->widgets.alert_overlay_label, alias, gx(120), gy(77), px(166));
        value_label(ui->widgets.attention_value, reading, gx(120), gy(118), px(176), font_large);
        small_label(ui->widgets.attention_detail, detail, gx(120), gy(151), px(160));
        lv_obj_move_foreground(ui->widgets.alert_overlay);
        if (demo)
            lv_obj_move_foreground(ui->widgets.demo_lbl);
    }
    ui->status_dirty = false;
}
static void acknowledge_alert(lv_event_t *event)
{
    if (lv_event_get_code(event) != LV_EVENT_CLICKED)
        return;
    lv_event_stop_bubbling(event);
    ui_t *ui = lv_event_get_user_data(event);
    if (!ui)
        return;
    if (atomic_load(&ui->showcase_active))
    {
        ui_showcase_next(ui);
        return;
    }
    if (!ui->rendered_alert.episode)
        return;
    ui_alert_t chosen      = ui->rendered_alert;
    uint8_t    command[16] = {0x3d, (uint8_t)chosen.key};
    memcpy(command + 4, &chosen.episode, 4);
    memcpy(command + 8, &chosen.boot, 4);
    alert_runtime_command(command, sizeof(command));
}

static void ui_task(lv_timer_t *timer)
{
    ESP_NULL_CHECK(timer, TAG, "timer is NULL");
    worker_health_progress(WORKER_UI, (uint32_t)(esp_timer_get_time() / 1000));
    ui_t *ui = (ui_t *)lv_timer_get_user_data(timer);
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
    int64_t now_us = esp_timer_get_time();
    if ((ui->timing.presses || ui->timing.count) && now_us - ui->timing.reported_at_us >= 30000000)
    {
        ESP_LOGI(TAG,
                 "performance touch_frames=%" PRIu32 " mean_submit_us=%" PRIu64 " max_submit_us=%" PRIu32
                 " stack_free=%u"
                 " presses=%" PRIu32 " releases=%" PRIu32 " clicks=%" PRIu32 " holds=%" PRIu32,
                 ui->timing.count, ui->timing.count ? ui->timing.total_us / ui->timing.count : 0, ui->timing.maximum_us,
                 (unsigned)uxTaskGetStackHighWaterMark(NULL), ui->timing.presses, ui->timing.releases,
                 ui->timing.clicks, ui->timing.holds);
        ui->timing.count          = 0;
        ui->timing.total_us       = 0;
        ui->timing.maximum_us     = 0;
        ui->timing.presses        = 0;
        ui->timing.releases       = 0;
        ui->timing.clicks         = 0;
        ui->timing.holds          = 0;
        ui->timing.reported_at_us = now_us;
    }
#endif

    action_context(ui);
    refresh_vehicle_menu(ui);
    command_hold_tick(ui);
    command_attention_ring(ui);
    uint32_t    action_now  = (uint32_t)(esp_timer_get_time() / 1000);
    lv_indev_t *touch_input = lv_indev_get_next(NULL);
    if (ui->press_active && ((uint32_t)(xTaskGetTickCount() - ui->pressed_at) > pdMS_TO_TICKS(15000) ||
                             (touch_input && lv_indev_get_state(touch_input) == LV_INDEV_STATE_RELEASED)))
    {
        page_action_reset(&ui->action);
        ui->press_active = ui->action_enter_pending = false;
        ui->suppress_action_click                   = true;
    }
    page_action_tick(&ui->action, action_now);
    if (ui->action_enter_pending && action_now - ui->action_enter_at >= 350)
    {
        ui->action_enter_pending = false;
        dispatch_touch(ui, UI_EVENT_PAGE_ACTION);
    }
    unsigned progress = ui->action_enter_pending ? ui->action.config.count : ui->action.progress;
    atomic_store(&ui->action_pending, progress != 0 || ui->action.pressed);
    if (ui->action_feedback && action_now - ui->action_feedback_ms >= 2000)
        ui->action_feedback = false;
    if (ui->action_label && !ui->calibration_mode)
    {
        bool visible = !ui->pairing_visible && !ui->calibration_mode && !atomic_load(&ui->showcase_active) &&
                       !ui->rendered_alert.attention && !ui->command_menu_visible &&
                       (ui->action.progress || ui->action_feedback);
        if (ui->action.config.vehicle_menu)
        {
            lv_obj_add_flag(ui->action_label, LV_OBJ_FLAG_HIDDEN);
            if (ui->rendered_action_progress != progress)
                ui->status_dirty = true;
            static lv_point_precise_t arrow[5];
            arrow[0] = (lv_point_precise_t){px(0), px(6)};
            arrow[1] = (lv_point_precise_t){px(5), px(0)};
            arrow[2] = (lv_point_precise_t){px(10), px(6)};
            arrow[3] = arrow[1];
            arrow[4] = (lv_point_precise_t){px(5), px(14)};
            for (unsigned i = 0; i < 5; ++i)
            {
                if (!ui->action_arrows[i])
                {
                    ui->action_arrows[i] = lv_line_create(ui->widgets.gauge_content);
                    lv_line_set_points(ui->action_arrows[i], arrow, 5);
                    lv_obj_set_style_line_width(ui->action_arrows[i], px(2), 0);
                    lv_obj_set_style_line_color(ui->action_arrows[i], lv_color_hex(color_accent), 0);
                }
                lv_obj_set_pos(ui->action_arrows[i], gx(125 - (int)progress * 10 + (int)i * 20), gy(205));
                show(ui->action_arrows[i], visible && i < progress);
            }
        }
        else
        {
            for (unsigned i = 0; i < 5; ++i)
                if (ui->action_arrows[i])
                    show(ui->action_arrows[i], false);
            if (visible && (!ui->rendered_action_visible || ui->rendered_action_feedback != ui->action_feedback ||
                            ui->rendered_action_progress != ui->action.progress))
            {
                char feedback[32];
                if (ui->action_feedback)
                    snprintf(feedback, sizeof(feedback), "Page changed");
                else
                    snprintf(feedback, sizeof(feedback), "%u/%u", ui->action.progress, ui->action.config.count);
                small_label(ui->action_label, feedback, gx(120), gy(199), px(160));
            }
            show(ui->action_label, visible);
        }
        ui->rendered_action_visible  = visible;
        ui->rendered_action_progress = ui->action.progress;
        ui->rendered_action_feedback = ui->action_feedback;
    }
    if (ui->calibration_mode)
    {
        atomic_store(&ui->cycle_pause, PAGE_CYCLE_ATTENTION);
        lv_event_code_t event_code;
        if (xQueueReceive(ui->rtos.touch_ev_que, &event_code, 0) == pdTRUE && ui->touch_cb)
            ui->touch_cb(ui, event_code);
        return;
    }

    char pairing_identifier[sizeof(ui->pairing_identifier)];
    if (xQueueReceive(ui->rtos.pairing_id_que, pairing_identifier, 0) == pdTRUE)
    {
        memcpy(ui->pairing_identifier, pairing_identifier, sizeof(ui->pairing_identifier));
        ui->pairing_identifier[sizeof(ui->pairing_identifier) - 1] = '\0';
        if (ui->pairing_visible)
            show_pairing(ui, ui->pairing_code);
    }

    uint32_t pairing_code;
    if (xQueueReceive(ui->rtos.pairing_que, &pairing_code, 0) == pdTRUE)
    {
        page_action_reset(&ui->action);
        show_pairing(ui, pairing_code);
    }
    if (ui->pairing_code == UI_PAIRING_RESET_CONFIRM &&
        xTaskGetTickCount() - ui->reset_confirmation_at >= pdMS_TO_TICKS(30000))
    {
        show_pairing(ui, UI_PAIRING_HIDDEN);
    }
    if (ui->pairing_code == UI_PAIRING_OWNER_SAVED && xTaskGetTickCount() - ui->owner_saved_at >= pdMS_TO_TICKS(2500))
    {
        show_pairing(ui, UI_PAIRING_HIDDEN);
    }

    ui_alert_t alert;
    if (xQueueReceive(ui->rtos.alert_que, &alert, 0) == pdTRUE && memcmp(&ui->rendered_alert, &alert, sizeof(alert)))
    {
        ui->rendered_alert = alert;
        ui->alert_rendered = true;
        ui->status_dirty   = true;
    }
    ui_diagnostics_t diagnostics;
    if (xQueueReceive(ui->rtos.diagnostics_que, &diagnostics, 0) == pdTRUE &&
        memcmp(&ui->rendered_diagnostics, &diagnostics, sizeof(diagnostics)))
    {
        ui->rendered_diagnostics = diagnostics;
        ui->diagnostics_rendered = true;
        ui->status_dirty         = true;
    }
    uint32_t           now = (uint32_t)(esp_timer_get_time() / 1000);
    showcase_request_t request;
    if (xQueueReceive(ui->rtos.showcase_que, &request, 0) == pdTRUE)
    {
        if (display_showcase_apply(&ui->showcase, request.enabled, request.seconds, request.sequence, now,
                                   request.vehicle_ready || adapter_status_ready_for(0) || adapter_status_ready_for(1),
                                   request.maintenance || transfer_gate_current() != 0,
                                   ui->rendered_alert.attention || ui->pairing_visible))
            showcase_frame(ui);
    }
    if (display_showcase_tick(&ui->showcase, now,
                              ui->pairing_visible || ui->rendered_alert.attention || transfer_gate_current() != 0 ||
                                  adapter_status_ready_for(0) || adapter_status_ready_for(1)))
        showcase_frame(ui);

    render_page(ui);
    if (ui->status_dirty)
        render_status(ui);
    if (ui->waiting_enabled)
    {
        uint32_t now_ms = (uint32_t)(esp_timer_get_time() / 1000), newest = 0, minimum_age = UINT32_MAX;
        bool     available = false;
        bool     source_ready[2];
        uint32_t generations[2];
        for (unsigned source = 0; source < 2; ++source)
        {
            source_ready[source] =
                adapter_status_ready_for(source) && !ble_mgr_is_paused() && transfer_gate_current() != 2;
            generations[source] = adapter_status_generation(source);
        }
        portENTER_CRITICAL(&ui->sample_lock);
        for (unsigned i = 0; i < EGAUGE_RUNTIME_PIDS; ++i)
        {
            const ui_sample_t *sample = &ui->samples[i];
            if (!sample->used || !sample->present || sample->source >= 2 || !source_ready[sample->source] ||
                !ui->sample_sources[sample->source].ready || sample->generation != generations[sample->source] ||
                sample->revision != ui->sample_sources[sample->source].revision)
                continue;
            uint32_t age = now_ms - sample->observed_at_ms;
            if (age < DISPLAY_IDLE_DATA_MS && age < minimum_age)
            {
                available   = true;
                newest      = sample->observed_at_ms;
                minimum_age = age;
            }
        }
        portEXIT_CRITICAL(&ui->sample_lock);
        ui_diagnostics_t d        = visible_diagnostics(ui);
        bool             priority = ui->pairing_visible || ui->calibration_mode || ui->command_menu_visible ||
                                    atomic_load(&ui->showcase_active) || ui->rendered_alert.severity ||
                                    (d.valid && (d.mil_on || d.count));
        bool             waiting  = display_idle_waiting(&ui->idle, available, newest, priority, now_ms);
        if (waiting != ui->waiting_visible)
        {
            ui->waiting_visible = waiting;
            if (waiting)
            {
                lv_obj_remove_flag(boot_logo, LV_OBJ_FLAG_HIDDEN);
                lv_obj_move_foreground(boot_logo);
            }
            else
                lv_obj_add_flag(boot_logo, LV_OBJ_FLAG_HIDDEN);
        }
    }
    atomic_store(&ui->cycle_pause, ui->pairing_visible || ui->rendered_alert.attention    ? PAGE_CYCLE_ATTENTION
                                   : ui->command_menu_visible                             ? PAGE_CYCLE_ACTIONS
                                   : atomic_load(&ui->showcase_active)                    ? PAGE_CYCLE_SHOWCASE
                                   : ui->waiting_visible                                  ? PAGE_CYCLE_WAITING
                                   : ui->press_active || atomic_load(&ui->action_pending) ? PAGE_CYCLE_TOUCH
                                                                                          : PAGE_CYCLE_RUNNING);
}

static void ui_init_screen(ui_t *ui, ui_page_t const *page, uint32_t interval_ms)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    ESP_NULL_CHECK(page, TAG, "Page config is NULL");

    ESP_LOGI(TAG, "Initializing screen ...");

    lv_obj_t *scr = lv_screen_active();
    ESP_NULL_CHECK(scr, TAG, "current screen is NULL");

    // Near-black reduces backlight haze while preserving shadow detail on the IPS panel.
    lv_obj_set_style_bg_color(scr, lv_color_hex(color_background), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, LV_PART_MAIN);

    // Hide the whole gauge view during pairing so renderer and status updates
    // cannot reveal individual widgets behind the pairing prompt.
    lv_obj_t *gauge_content = lv_obj_create(scr);
    lv_obj_remove_style_all(gauge_content);
    lv_obj_set_size(gauge_content, LV_PCT(100), LV_PCT(100));
    lv_obj_clear_flag(gauge_content, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(gauge_content, LV_OBJ_FLAG_EVENT_BUBBLE);
    ui->widgets.gauge_content = gauge_content;

    ui->widgets.value_lbl = plain_label(gauge_content, color_text_primary);
    ui->widgets.info_lbl  = plain_label(gauge_content, color_text_secondary);
    ui->widgets.unit_lbl  = plain_label(gauge_content, color_text_secondary);
    lv_obj_t *arc         = lv_arc_create(gauge_content);
    lv_obj_set_size(arc, GAUGE_ARC_DIAMETER, GAUGE_ARC_DIAMETER);
    lv_obj_align(arc, LV_ALIGN_CENTER, 0, 0);
    lv_arc_set_bg_angles(arc, 135, 45);
    lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
    lv_obj_clear_flag(arc, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_style_arc_width(arc, GAUGE_ARC_WIDTH, LV_PART_MAIN);
    lv_obj_set_style_arc_width(arc, GAUGE_ARC_WIDTH, LV_PART_INDICATOR);
    lv_obj_set_style_arc_rounded(arc, true, LV_PART_MAIN);
    lv_obj_set_style_arc_rounded(arc, true, LV_PART_INDICATOR);
    lv_obj_set_style_arc_color(arc, lv_color_hex(color_track), LV_PART_MAIN);
    lv_obj_set_style_arc_color(arc, lv_color_hex(color_accent), LV_PART_INDICATOR);
    ui->widgets.arc = arc;
    lv_obj_t *bar   = lv_bar_create(gauge_content);
    lv_obj_remove_style_all(bar);
    lv_obj_set_size(bar, px(140), px(6));
    lv_obj_set_pos(bar, gx(50), gy(174));
    lv_obj_set_style_bg_opa(bar, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_bg_color(bar, lv_color_hex(color_track), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(bar, LV_OPA_COVER, LV_PART_INDICATOR);
    lv_obj_set_style_bg_color(bar, lv_color_hex(color_accent), LV_PART_INDICATOR);
    lv_obj_set_style_radius(bar, px(3), LV_PART_MAIN);
    lv_obj_set_style_radius(bar, px(3), LV_PART_INDICATOR);
    lv_obj_clear_flag(bar, LV_OBJ_FLAG_CLICKABLE);
    ui->widgets.bar = bar;
    lv_obj_t *trend = lv_line_create(gauge_content);
    lv_obj_remove_style_all(trend);
    lv_obj_set_pos(trend, gx(48), gy(146));
    lv_obj_set_size(trend, px(144), px(32));
    lv_obj_set_style_line_color(trend, lv_color_hex(color_accent), 0);
    lv_obj_set_style_line_width(trend, px(3), 0);
    lv_obj_clear_flag(trend, LV_OBJ_FLAG_CLICKABLE);
    ui->widgets.trend = trend;
    for (unsigned i = 0; i < 2; i++)
    {
        ui->widgets.dual_value[i] = plain_label(gauge_content, color_text_primary);
        ui->widgets.dual_info[i]  = plain_label(gauge_content, color_text_secondary);
        ui->widgets.dual_unit[i]  = plain_label(gauge_content, color_text_secondary);
    }
    ui->widgets.dual_divider = plain_view(gauge_content, 140, 1);
    lv_obj_set_pos(ui->widgets.dual_divider, gx(50), gy(120));
    lv_obj_set_style_bg_color(ui->widgets.dual_divider, lv_color_hex(color_track), 0);
    lv_obj_set_style_bg_opa(ui->widgets.dual_divider, LV_OPA_COVER, 0);
    for (unsigned i = 0; i < 8; i++)
    {
        ui->widgets.page_dots[i] = plain_view(gauge_content, 4, 4);
        lv_obj_set_style_radius(ui->widgets.page_dots[i], px(2), 0);
        lv_obj_set_style_bg_opa(ui->widgets.page_dots[i], LV_OPA_COVER, 0);
    }
    ui->widgets.page_position = plain_label(gauge_content, color_text_secondary);
    ui->widgets.pairing_lbl   = plain_view(scr, 240, 240);
    lv_obj_set_size(ui->widgets.pairing_lbl, LV_PCT(100), LV_PCT(100));
    lv_obj_set_style_bg_color(ui->widgets.pairing_lbl, lv_color_hex(color_background), 0);
    lv_obj_set_style_bg_opa(ui->widgets.pairing_lbl, LV_OPA_COVER, 0);
    ui->widgets.pairing_title = plain_label(ui->widgets.pairing_lbl, color_text_primary);
    ui->widgets.pairing_body  = plain_label(ui->widgets.pairing_lbl, color_text_secondary);
    ui->widgets.pairing_code  = plain_label(ui->widgets.pairing_lbl, color_text_primary);
    ui->widgets.pairing_id    = plain_label(ui->widgets.pairing_lbl, color_text_secondary);
    ui->widgets.alert_lbl     = plain_label(gauge_content, color_warning);
    ui->widgets.alert_icon    = icon_create(gauge_content, false);
    ui->widgets.alert_arrow   = lv_line_create(gauge_content);
    lv_obj_remove_style_all(ui->widgets.alert_arrow);
    lv_obj_set_style_line_width(ui->widgets.alert_arrow, px(2), 0);
    lv_obj_clear_flag(ui->widgets.alert_arrow, LV_OBJ_FLAG_CLICKABLE);
    ui->widgets.diagnostics_lbl  = plain_label(gauge_content, color_warning);
    ui->widgets.diagnostics_icon = icon_create(gauge_content, true);
    ui->widgets.demo_lbl         = plain_label(gauge_content, color_warning);
    small_label(ui->widgets.demo_lbl, "Demo", gx(120), gy(213), px(48));
    lv_obj_t *overlay = plain_view(gauge_content, 240, 240);
    lv_obj_set_size(overlay, LV_PCT(100), LV_PCT(100));
    lv_obj_set_style_bg_color(overlay, lv_color_hex(color_background), 0);
    lv_obj_set_style_bg_opa(overlay, LV_OPA_COVER, 0);
    lv_obj_clear_flag(overlay, LV_OBJ_FLAG_EVENT_BUBBLE | LV_OBJ_FLAG_GESTURE_BUBBLE);
    ui->widgets.alert_overlay       = overlay;
    ui->widgets.attention_title     = plain_label(overlay, color_critical);
    ui->widgets.alert_overlay_label = plain_label(overlay, color_text_secondary);
    ui->widgets.attention_value     = plain_label(overlay, color_text_primary);
    ui->widgets.attention_detail    = plain_label(overlay, color_text_secondary);
    lv_obj_t *button                = plain_view(overlay, 128, 32);
    lv_obj_set_pos(button, gx(56), gy(168));
    lv_obj_set_style_bg_color(button, lv_color_hex(color_track), 0);
    lv_obj_set_style_bg_opa(button, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(button, px(16), 0);
    lv_obj_clear_flag(button, LV_OBJ_FLAG_EVENT_BUBBLE | LV_OBJ_FLAG_GESTURE_BUBBLE);
    lv_obj_add_flag(button, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_ext_click_area(button, px(8));
    lv_obj_add_event_cb(button, acknowledge_alert, LV_EVENT_CLICKED, ui);
    lv_obj_t *button_label = plain_label(button, color_text_primary);
    lv_label_set_text(button_label, "Acknowledge");
    lv_obj_set_style_text_font(button_label, font_subtitle, 0);
    lv_obj_center(button_label);
    ui->widgets.attention_button = button;
    show(overlay, false);
    /* New LVGL objects start visible. Keep status chrome hidden until its first
     * truthful status render, including the boot interval before the UI timer. */
    show(ui->widgets.alert_lbl, false);
    show(ui->widgets.alert_icon, false);
    show(ui->widgets.alert_arrow, false);
    show(ui->widgets.diagnostics_lbl, false);
    show(ui->widgets.diagnostics_icon, false);
    show(ui->widgets.demo_lbl, false);
    ui->status_dirty = true;
    lv_arc_set_range(arc, 0, 1000);
    lv_bar_set_range(bar, 0, 1000);
    select_renderer_widgets(ui);
    show_pairing(ui, ui->pairing_visible ? UI_PAIRING_WAITING : UI_PAIRING_HIDDEN);

    /* Row positions use measured glyph ink in render_page. */
    // Let the LVGL task draw after ui_init releases the port lock. Rendering
    // the nested gauge view here can overflow app_main's smaller task stack.

    // add handlers
    lv_obj_add_event_cb(lv_screen_active(), ui_touch_callback, LV_EVENT_ALL, ui);
#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
    lv_display_add_event_cb(lv_display_get_default(), render_finished, LV_EVENT_REFR_READY, ui);
#endif
    lv_timer_create(ui_task, interval_ms, ui);

    ESP_LOGI(TAG, "Screen initialized successfully");
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

ui_t *ui_init(ui_page_t const    *page,
              uint32_t            interval_ms,
              ui_touch_callback_t touch_cb,
              bool                pairing_required,
              bool                imperial_units)
{
    ESP_NULL_CHECK(page, TAG, "Page config is NULL");
    ESP_NULL_CHECK(touch_cb, TAG, "touch callback is NULL");

    const gauge_presentation_t *profile = gauge_presentation();
    if (!profile || !gauge_presentation_matches(profile->surface) || !lv_display_get_default() ||
        lv_display_get_horizontal_resolution(NULL) != profile->surface->width ||
        lv_display_get_vertical_resolution(NULL) != profile->surface->height)
    {
        ESP_LOGE(TAG, "UI profile does not match the active display");
        return NULL;
    }

    ESP_LOGI(TAG, "Initializing UI...");

    ui_t *ui = malloc(sizeof(ui_t));
    ESP_NULL_CHECK(ui, TAG, "Failed to allocate memory for UI context");
    memset(ui, 0, sizeof(ui_t));
    ESP_LOGI(TAG, "ui pointer2: %p (addr: %p)", ui, (void *)&ui);

    ui->sample_lock          = (portMUX_TYPE)portMUX_INITIALIZER_UNLOCKED;
    ui->rtos.touch_ev_que    = xQueueCreate(4, sizeof(lv_event_code_t));
    ui->rtos.pairing_que     = xQueueCreate(1, sizeof(uint32_t));
    ui->rtos.pairing_id_que  = xQueueCreate(1, sizeof(ui->pairing_identifier));
    ui->rtos.alert_que       = xQueueCreate(1, sizeof(ui_alert_t));
    ui->rtos.diagnostics_que = xQueueCreate(1, sizeof(ui_diagnostics_t));
    ui->rtos.showcase_que    = xQueueCreate(1, sizeof(showcase_request_t));
    ui->touch_cb             = touch_cb;
    ui->display.page         = *page;
    ui->pairing_visible      = pairing_required;
    ui->imperial_units       = imperial_units;

    if (!ui->rtos.touch_ev_que || !ui->rtos.pairing_que || !ui->rtos.pairing_id_que || !ui->rtos.alert_que ||
        !ui->rtos.diagnostics_que || !ui->rtos.showcase_que)
    {
        ESP_LOGE(TAG, "Failed to allocate UI mailboxes");
        if (ui->rtos.touch_ev_que)
            vQueueDelete(ui->rtos.touch_ev_que);
        if (ui->rtos.pairing_que)
            vQueueDelete(ui->rtos.pairing_que);
        if (ui->rtos.pairing_id_que)
            vQueueDelete(ui->rtos.pairing_id_que);
        if (ui->rtos.alert_que)
            vQueueDelete(ui->rtos.alert_que);
        if (ui->rtos.diagnostics_que)
            vQueueDelete(ui->rtos.diagnostics_que);
        if (ui->rtos.showcase_que)
            vQueueDelete(ui->rtos.showcase_que);
        free(ui);
        return NULL;
    }

    if (!lvgl_port_lock(portMAX_DELAY))
    {
        ESP_LOGE(TAG, "Failed to lock LVGL port");
        free(ui);
        return NULL;
    }

    atomic_init(&ui->showcase_active, false);
    atomic_init(&ui->showcase_generation, 0);
    atomic_init(&ui->showcase_sequence, 0);
    atomic_init(&ui->showcase_packed, 0);
    atomic_init(&ui->showcase_started, 0);
    ui->page_count = 1;
    atomic_init(&ui->action_allowed, false);
    atomic_init(&ui->action_pending, false);
    atomic_init(&ui->action_context_epoch, 0);
    atomic_init(&ui->command_menu_active, false);
    atomic_init(&ui->command_epoch, 0);
    atomic_init(&ui->command_ui[0], 0);
    atomic_init(&ui->command_ui[1], 0);
    ui_init_screen(ui, page, interval_ms);
    if (boot_logo)
        ui_waiting_logo_enable(ui, true);
    ui->action_label = lv_label_create(ui->widgets.gauge_content);
    lv_obj_set_style_text_color(ui->action_label, lv_color_hex(color_accent), 0);
    lv_obj_set_style_text_font(ui->action_label, font_subtitle, 0);
    lv_obj_add_flag(ui->action_label, LV_OBJ_FLAG_HIDDEN);

    lvgl_port_unlock();

    ESP_LOGI(TAG, "UI initialized successfully");

    return ui;
}

void ui_set_source_state(ui_t *ui, unsigned source, bool ready, uint32_t revision, uint32_t generation)
{
    if (!ui || source >= 2)
        return;
    portENTER_CRITICAL(&ui->sample_lock);
    if (!ready || ui->sample_sources[source].revision != revision ||
        ui->sample_sources[source].generation != generation)
        for (unsigned i = 0; i < EGAUGE_RUNTIME_PIDS; ++i)
            if (ui->samples[i].used && ui->samples[i].source == source)
                ui->samples[i].used = ui->samples[i].present = false;
    ui->sample_sources[source].ready      = ready;
    ui->sample_sources[source].revision   = revision;
    ui->sample_sources[source].generation = generation;
    portEXIT_CRITICAL(&ui->sample_lock);
}

static void set_sample(ui_t         *ui,
                       unsigned      source,
                       uint32_t      revision,
                       uint32_t      generation,
                       uint16_t      pid,
                       double const *value,
                       uint32_t      observed_at_ms)
{
    if (!ui || (source != UINT8_MAX && source >= 2))
        return;
    portENTER_CRITICAL(&ui->sample_lock);
    if (source != UINT8_MAX && (!ui->sample_sources[source].ready || ui->sample_sources[source].revision != revision ||
                                ui->sample_sources[source].generation != generation))
    {
        portEXIT_CRITICAL(&ui->sample_lock);
        return;
    }
    ui_sample_t *slot = NULL;
    for (unsigned i = 0; i < EGAUGE_RUNTIME_PIDS; ++i)
    {
        if (ui->samples[i].used && ui->samples[i].source == source && ui->samples[i].pid == pid)
        {
            slot = &ui->samples[i];
            break;
        }
        if (!ui->samples[i].used && !slot)
            slot = &ui->samples[i];
    }
    if (slot)
        *slot = (ui_sample_t){.used           = true,
                              .source         = source,
                              .pid            = pid,
                              .present        = value && isfinite(*value),
                              .value          = value && isfinite(*value) ? *value : 0,
                              .revision       = revision,
                              .generation     = generation,
                              .observed_at_ms = observed_at_ms};
    portEXIT_CRITICAL(&ui->sample_lock);
}

void ui_set_source_value(ui_t         *ui,
                         unsigned      source,
                         uint32_t      revision,
                         uint32_t      generation,
                         uint16_t      pid,
                         double const *value,
                         uint32_t      observed_at_ms)
{
    /* Reject retired callbacks even before the source worker observes loss. */
    if (source >= 2 || !adapter_status_ready_for(source) || ble_mgr_is_paused() ||
        adapter_status_generation(source) != generation || transfer_gate_current() == 2)
        return;
    set_sample(ui, source, revision, generation, pid, value, observed_at_ms);
}

void ui_set_value(ui_t *ui, uint16_t pid, double const *value)
{
    set_sample(ui, UINT8_MAX, 0, 0, pid, value, (uint32_t)(esp_timer_get_time() / 1000));
}

void ui_set_page(ui_t *ui, ui_page_t const *page)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    ESP_NULL_CHECK(page, TAG, "Page config is NULL");

    page_action_reset(&ui->action);
    if (ui->showcase.active)
    {
        ui->showcase.active = false;
        showcase_publish(ui);
    }
    ui->display.page          = *page;
    ui->display.rendered_once = false;
    ui->display.trend_count   = 0;
    for (unsigned i = 0; i < 60; ++i)
        ui->display.trend_points[i].y = px(30);
    lv_arc_set_range(ui->widgets.arc, 0, 1000);
    lv_bar_set_range(ui->widgets.bar, 0, 1000);
    select_renderer_widgets(ui);
    ui->status_dirty = true;
    /* Populate all renderer-specific widgets now, including dual values.
     * Waiting for the data timer adds another 50 ms to a page change. */
    render_page(ui);
    ESP_LOGD(TAG, "Updated page: %s renderer=%u", page->name, page->renderer);
}

void ui_set_units(ui_t *ui, bool imperial_units)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    if (ui->imperial_units == imperial_units)
        return;
    ui->imperial_units        = imperial_units;
    ui->status_dirty          = true;
    ui->display.rendered_once = false;
    render_page(ui);
}

void ui_show_pairing_code(ui_t *ui, uint32_t passkey)
{
    if (ui != NULL)
        xQueueOverwrite(ui->rtos.pairing_que, &passkey);
}

void ui_set_pairing_identifier(ui_t *ui, const char *identifier)
{
    if (!ui || !ui->rtos.pairing_id_que)
        return;
    char value[sizeof(ui->pairing_identifier)] = {0};
    if (identifier)
        snprintf(value, sizeof(value), "%s", identifier);
    xQueueOverwrite(ui->rtos.pairing_id_que, value);
}

void ui_set_alert(ui_t *ui, uint8_t severity, bool unavailable, const char *label)
{
    ui_set_alert_full(ui, severity, unavailable, label, false);
}
void ui_set_alert_full(ui_t *ui, uint8_t severity, bool unavailable, const char *label, bool attention)
{
    ui_set_alert_event(ui, severity, unavailable, label, attention, 0, 0, 0, 0, 0, "");
}
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
                        const char *unit)
{
    ui_set_alert_condition(ui, severity, unavailable, label, attention, key, boot, episode, value, limit, unit, 0,
                           false);
}
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
                            bool        simulated)
{
    if (!ui || !ui->rtos.alert_que)
        return;
    ui_alert_t alert = {.severity    = severity,
                        .attention   = attention,
                        .unavailable = unavailable,
                        .key         = key,
                        .boot        = boot,
                        .episode     = episode,
                        .value       = value,
                        .limit       = limit,
                        .additional  = additional,
                        .simulated   = simulated};
    if (unit)
        snprintf(alert.unit, sizeof(alert.unit), "%s", unit);
    if (label)
        snprintf(alert.label, sizeof(alert.label), "%s", label);
    xQueueOverwrite(ui->rtos.alert_que, &alert);
}

void ui_set_diagnostics(ui_t *ui, bool valid, bool mil_on, bool transmission, uint8_t count, const char *first_code)
{
    if (!ui || !ui->rtos.diagnostics_que)
        return;
    ui_diagnostics_t diagnostics = {.valid        = valid,
                                    .mil_known    = valid,
                                    .mil_on       = mil_on,
                                    .transmission = transmission,
                                    .count        = count,
                                    .count_known  = valid};
    if (first_code)
        snprintf(diagnostics.first_code, sizeof(diagnostics.first_code), "%s", first_code);
    xQueueOverwrite(ui->rtos.diagnostics_que, &diagnostics);
}

void ui_start_display_calibration(ui_t *ui)
{
    if (!ui || !lvgl_port_lock(portMAX_DELAY))
        return;
    ui->calibration_mode = true;
    ui->calibration_page = 0;
    render_calibration_page(ui);
    lvgl_port_unlock();
}

void ui_next_display_calibration(ui_t *ui)
{
    if (!ui || !ui->calibration_mode)
        return;
    ui->calibration_page = (ui->calibration_page + 1) % CALIBRATION_PAGE_COUNT;
    render_calibration_page(ui);
}

void ui_set_simulated(ui_t *ui)
{
    if (ui)
    {
        ui->simulated    = true;
        ui->status_dirty = true;
    }
}

void ui_configure_page_action(ui_t *ui, page_action_config_t config)
{
    if (ui->command_menu_visible)
        close_vehicle_menu(ui);
    if (config.vehicle_menu && !config.pages_explicit)
    {
        config.page_count = 3;
        for (unsigned i = 0; i < 3; ++i)
            config.pages[i] = i + 1;
    }
    page_action_init(&ui->action, config);
    ui->action_enter_pending = false;
    ui->action_feedback      = false;
}
void ui_set_action_context(ui_t *ui, bool allowed)
{
    if (atomic_exchange(&ui->action_allowed, allowed) != allowed)
        atomic_fetch_add(&ui->action_context_epoch, 1);
}
uint8_t ui_action_target(ui_t *ui)
{
    return ui->action.config.target_page;
}

void ui_reset_action_sequence(ui_t *ui)
{
    page_action_reset(&ui->action);
    ui->action_enter_pending = false;
    atomic_store(&ui->action_pending, false);
}
bool ui_action_pending(ui_t *ui)
{
    return atomic_load(&ui->action_pending) || atomic_load(&ui->showcase_active) ||
           atomic_load(&ui->command_menu_active);
}

void ui_action_applied(ui_t *ui)
{
    ui->action_feedback    = true;
    ui->action_feedback_ms = (uint32_t)(esp_timer_get_time() / 1000);
}

void ui_set_vehicle_diagnostics(ui_t *ui, const diagnostics_snapshot_t *snapshot)
{
    if (!ui || !snapshot)
        return;
    ui_diagnostics_t d = {.valid         = snapshot->valid,
                          .mil_known     = snapshot->mil_known,
                          .mil_on        = snapshot->mil_on,
                          .transmission  = snapshot->transmission,
                          .count         = snapshot->distinct_count > 255 ? 255 : snapshot->distinct_count,
                          .count_known   = snapshot->count_known,
                          .count_partial = snapshot->count_partial};
    snprintf(d.first_code, sizeof(d.first_code), "%s", snapshot->first_code);
    xQueueOverwrite(ui->rtos.diagnostics_que, &d);
}
void ui_set_page_position(ui_t *ui, unsigned index, unsigned count)
{
    if (!ui || !count || index >= count)
        return;
    ui->page_count   = count;
    ui->page_index   = index;
    ui->status_dirty = true;
}
bool ui_request_showcase(ui_t    *ui,
                         bool     enabled,
                         uint8_t  seconds,
                         uint32_t sequence,
                         bool     vehicle_ready,
                         bool     maintenance)
{
    if (!ui || !ui->rtos.showcase_que || !sequence || (seconds && (seconds < 5 || seconds > 30)) ||
        (enabled && !seconds))
        return false;
    showcase_request_t r = {.enabled       = enabled,
                            .seconds       = seconds,
                            .sequence      = sequence,
                            .vehicle_ready = vehicle_ready,
                            .maintenance   = maintenance};
    return xQueueSend(ui->rtos.showcase_que, &r, 0) == pdTRUE;
}
bool ui_showcase_active(ui_t *ui)
{
    return ui && atomic_load(&ui->showcase_active);
}
void ui_showcase_next(ui_t *ui)
{
    if (!ui || !ui->showcase.active)
        return;
    display_showcase_next(&ui->showcase, (uint32_t)(esp_timer_get_time() / 1000));
    showcase_frame(ui);
}
void ui_showcase_stop(ui_t *ui)
{
    if (!ui || !ui->showcase.active)
        return;
    ui->showcase.active = false;
    showcase_frame(ui);
    render_page(ui);
    render_status(ui);
}
size_t ui_showcase_status(ui_t *ui, uint32_t now, uint8_t out[20])
{
    if (!ui || !out)
        return 0;
    for (unsigned attempt = 0; attempt < 3; attempt++)
    {
        unsigned before = atomic_load(&ui->showcase_generation);
        if (before & 1)
            continue;
        bool     active   = atomic_load(&ui->showcase_active);
        uint32_t sequence = atomic_load(&ui->showcase_sequence), packed = atomic_load(&ui->showcase_packed),
                 started = atomic_load(&ui->showcase_started);
        if (before != atomic_load(&ui->showcase_generation))
            continue;
        memset(out, 0, 20);
        out[0] = 21;
        out[1] = active;
        out[2] = (packed >> 16) & 255;
        out[3] = packed & 255;
        memcpy(out + 4, &sequence, 4);
        out[8]  = display_showcase_count();
        out[9]  = (packed >> 8) & 255;
        out[10] = 1;
        uint32_t remaining =
            active && now - started < DISPLAY_SHOWCASE_DURATION_MS ? DISPLAY_SHOWCASE_DURATION_MS - (now - started) : 0;
        memcpy(out + 12, &remaining, 4);
        return 20;
    }
    return 0;
}

/* One reusable action face. Navigation is LVGL-owned; status is a bounded mailbox.
 * Commands remain owned by the primary worker, including after navigation/attention. */
static uint8_t command_id(ui_t *ui)
{
    return ui->command_page_count ? ui->command_pages[ui->command_page] : 0;
}
static bool command_busy(ui_t *ui)
{
    for (unsigned i = 0; i < 2; ++i)
    {
        unsigned phase = atomic_load(&ui->command_ui[i]) & 255U;
        if (phase == VEHICLE_STARTING || phase == VEHICLE_ACTIVE || phase == VEHICLE_STOPPING ||
            phase == VEHICLE_VERIFYING || phase == VEHICLE_UNKNOWN || phase == VEHICLE_RESTART_REQUIRED)
            return true;
    }
    return false;
}
static void close_vehicle_menu(ui_t *ui)
{
    ui->command_menu_visible = ui->command_suspended = ui->command_press = ui->command_hold_armed = false;
    ui->command_confirm                                                                           = 0;
    atomic_store(&ui->command_menu_active, false);
    if (ui->command_menu)
    {
        if (lv_obj_get_parent(ui->command_ring) != ui->command_menu)
            lv_obj_set_parent(ui->command_ring, ui->command_menu);
        show(ui->command_ring, false);
        lv_obj_add_flag(ui->command_menu, LV_OBJ_FLAG_HIDDEN);
    }
    /* Restart the page-cycle quiet period without changing the originating page. */
    dispatch_touch(ui, LV_EVENT_PRESSED);
    render_page(ui);
    render_status(ui);
}
static void command_text(ui_t *ui, lv_obj_t *label, const char *text, int y, int width, const lv_font_t *font)
{
    /* Repeated status polls must not churn LVGL text allocations. */
    if (!strcmp(lv_label_get_text(label), text))
        return;
    if (label == ui->command_note)
        ui->status_dirty = true;
    if (!label_at(label, text, font, gx(120), gy(y), px(width)) &&
        !(font != font_subtitle && label_at(label, text, font_unit, gx(120), gy(y), px(width))) &&
        !label_at(label, text, font_subtitle, gx(120), gy(y), px(width)))
        label_at(label, "Unavailable", font_subtitle, gx(120), gy(y), px(width));
}
static uint8_t active_idle_id(ui_t *ui)
{
    unsigned preset = (atomic_load(&ui->command_ui[0]) >> 26) & 3U;
    return preset == 2 ? 2 : 1;
}
static void command_sync_pages(ui_t *ui)
{
    uint8_t selected       = command_id(ui);
    ui->command_page_count = ui->action.config.page_count;
    memcpy(ui->command_pages, ui->action.config.pages, 3);
    for (unsigned k = 0; k < 2; ++k)
    {
        unsigned state = atomic_load(&ui->command_ui[k]), phase = state & 255U;
        bool unresolved = phase == VEHICLE_STARTING || phase == VEHICLE_ACTIVE || phase == VEHICLE_STOPPING ||
                          phase == VEHICLE_VERIFYING || phase == VEHICLE_UNKNOWN || phase == VEHICLE_RESTART_REQUIRED;
        uint8_t id      = k ? 3 : active_idle_id(ui);
        bool    exists  = false;
        for (unsigned i = 0; i < ui->command_page_count; ++i)
            exists |= ui->command_pages[i] == id;
        if (unresolved && !exists && ui->command_page_count < 3)
            ui->command_pages[ui->command_page_count++] = id;
    }
    ui->command_page = 0;
    for (unsigned i = 0; i < ui->command_page_count; ++i)
        if (ui->command_pages[i] == selected)
            ui->command_page = i;
}
static unsigned command_duration(ui_t *ui, unsigned id, unsigned state)
{
    unsigned seconds = id == 2 ? ui->action.config.winch_seconds : ui->action.config.air_up_seconds;
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST
    if (seconds > JEEP_IDLE_PARKED_MAX_SECONDS)
        seconds = JEEP_IDLE_PARKED_MAX_SECONDS;
#endif
    if ((state & (16U << 8)) && seconds > 60)
        seconds = 60;
    return seconds;
}
static void refresh_vehicle_menu(ui_t *ui)
{
    if (!ui->command_menu_visible || ui->command_suspended)
        return;
    if (lv_obj_get_parent(ui->command_ring) != ui->command_menu)
    {
        lv_obj_set_parent(ui->command_ring, ui->command_menu);
        lv_obj_center(ui->command_ring);
    }
    command_sync_pages(ui);
    uint32_t now = (uint32_t)(esp_timer_get_time() / 1000);
    if (now - ui->command_activity >= 30000 && !command_busy(ui) && !ui->command_waiting)
    {
        close_vehicle_menu(ui);
        return;
    }
    if (ui->command_admission_failed && now - ui->command_wait_at >= 2000)
        ui->command_admission_failed = false;
    unsigned id = command_id(ui), kind = id == 3 ? 1 : 0;
    unsigned state = atomic_load(&ui->command_ui[kind]), phase = state & 255U;
    if (phase == VEHICLE_RESTORED)
        phase = VEHICLE_READY;
    unsigned    reason = (state >> 28) & 15U;
    const char *title  = id == 1 ? "Air up" : id == 2 ? "Winch" : id == 3 ? "ABS + TCS" : "No actions";
    const char *value = "Unavailable", *note = "", *button = "";
    char        duration[24];
    bool        enabled = false;
    if (ui->command_confirm && ((phase != VEHICLE_READY && phase != VEHICLE_REJECTED) ||
                                (state & 0xfc00ffffU) != (ui->command_confirm_state & 0xfc00ffffU) ||
                                ui->command_confirm_epoch != atomic_load(&ui->command_epoch)))
        ui->command_confirm = 0;
    ui->command_presented_state = state;
    if (ui->command_waiting && (phase != ui->command_selected || now - ui->command_wait_at >= 2000))
        ui->command_waiting = false;
    unsigned ring = 0;
    if (!id)
        value = "Add in App";
    else if (phase == VEHICLE_UNKNOWN)
    {
        value = id < 3 ? "Check idle" : "Check vehicle";
        note  = "Status unknown";
    }
    else if (id == 3 && (phase == VEHICLE_ACTIVE || phase == VEHICLE_RESTART_REQUIRED))
    {
        value = "Off";
        note  = "Restart to restore";
    }
    else if (phase == VEHICLE_STOPPING || phase == VEHICLE_VERIFYING)
    {
        value = "";
        ring  = 2;
        if (state & (VEHICLE_UI_COOLANT_HIGH << 8))
            note = "Coolant high";
    }
    else if (phase == VEHICLE_STARTING || phase == VEHICLE_ACTIVE)
    {
        unsigned seconds = (state >> 16) & 1023U;
        snprintf(duration, sizeof(duration), "%u:%02u", seconds / 60, seconds % 60);
        value   = phase == VEHICLE_STARTING ? "" : duration;
        ring    = phase == VEHICLE_STARTING ? 1 : 0;
        button  = id < 3 ? "Stop" : "";
        enabled = id < 3;
        if (id < 3 && id != active_idle_id(ui))
            note = active_idle_id(ui) == 2 ? "Winch running" : "Air up running";
        else if (state & (16U << 8))
            note = "No coolant";
    }
    else if (ui->command_waiting)
    {
        value = "";
        ring  = ui->command_request_stop ? 2 : 1;
    }
    else if (!(state & 256U))
    {
        value = (state & (VEHICLE_UI_RECONNECTING << 8)) ? "" : "Not supported";
        ring  = (state & (VEHICLE_UI_RECONNECTING << 8)) ? 3 : 0;
    }
    else if (command_busy(ui))
        value = "Action in progress";
    else if (id < 3 && !ui->idle_protection)
    {
        value = "Set coolant limit";
        note  = "In the App";
    }
    else if (id < 3 && (state & (VEHICLE_UI_COOLANT_HIGH << 8)))
        value = "Coolant high";
    else if (phase == VEHICLE_READY || phase == VEHICLE_REJECTED)
    {
        unsigned seconds = ui->command_confirm ? ui->command_confirm_seconds : command_duration(ui, id, state);
        snprintf(duration, sizeof(duration), "%u:%02u", seconds / 60, seconds % 60);
        value   = id == 3 ? "On" : duration;
        button  = id == 3 ? "Disable" : "Hold to start";
        enabled = true;
        if (id < 3 && (state & (16U << 8)))
        {
            note = ui->command_confirm ? "No coolant" : "No coolant · 1 min max";
            if (!ui->command_confirm)
                button = "Continue";
        }
        if (id == 3 && ui->command_confirm)
        {
            title = "Disable both?";
            value = "ABS + TCS";
            note  = "Restart to restore";
        }
        if (phase == VEHICLE_REJECTED && reason == VEHICLE_GUARD && !note[0])
            note = "Check conditions";
    }
    if (ui->command_admission_failed && enabled && !note[0] && !ui->rendered_alert.severity)
        note = "Try again";
    /* Existing fonts and one arc tree, no per-frame allocations or animation task. */
    command_text(ui, ui->command_title, title, 59, 184, font_unit);
    command_text(ui, ui->command_value, value, 104, 186, strchr(value, ':') && id < 3 ? font_large : font_compact);
    command_text(ui, ui->command_note, note, 136, 200, font_subtitle);
    bool coolant_warning = id < 3 && (state & ((16U | VEHICLE_UI_COOLANT_HIGH) << 8));
    lv_obj_set_style_text_color(ui->command_value, lv_color_hex(coolant_warning ? color_warning : color_text_primary),
                                0);
    bool quiet_note = !strcmp(note, "In the App") || strstr(note, "running");
    lv_obj_set_style_text_color(ui->command_note, lv_color_hex(quiet_note ? color_text_secondary : color_warning), 0);
    if (strcmp(lv_label_get_text(ui->command_button_text), button))
        lv_label_set_text(ui->command_button_text, button);
    lv_obj_set_style_text_font(ui->command_button_text, font_subtitle, 0);
    lv_obj_center(ui->command_button_text);
    show(ui->command_button, button[0]);
    if (enabled)
        lv_obj_remove_state(ui->command_button, LV_STATE_DISABLED);
    else
        lv_obj_add_state(ui->command_button, LV_STATE_DISABLED);
    show(ui->command_ring, ring != 0);
    show(ui->command_link_symbol, ring == 3);
    if (ring)
    {
        uint32_t color = ring == 1 ? color_accent : ring == 2 ? color_critical : 0xFFD600;
        lv_obj_set_style_arc_color(ui->command_ring, lv_color_hex(color), LV_PART_INDICATOR);
        lv_arc_set_rotation(ui->command_ring, (now % 1400) * 360 / 1400);
        if (ring == 3)
        {
            static lv_point_precise_t symbols[8];
            static const int16_t      points[8][2] = {{120, 95}, {108, 95}, {108, 109}, {122, 109},
                                                      {122, 95}, {134, 95}, {134, 109}, {120, 109}};
            for (unsigned i = 0; i < 8; i++)
                symbols[i] = (lv_point_precise_t){px(points[i][0]), px(points[i][1])};
            lv_line_set_points(ui->command_link_symbol, symbols, 8);
            lv_obj_set_style_line_color(ui->command_link_symbol, lv_color_hex(color), 0);
        }
    }
    for (unsigned i = 0; i < 3; ++i)
    {
        lv_obj_set_pos(ui->command_dots[i], gx(120 - (int)ui->command_page_count * 6 + (int)i * 12 + 4), gy(211));
        show(ui->command_dots[i], ui->command_page_count > 1 && i < ui->command_page_count);
        lv_obj_set_style_bg_color(ui->command_dots[i],
                                  lv_color_hex(i == ui->command_page ? color_text_primary : color_track), 0);
    }
    if (ui->status_dirty)
        render_status(ui);
    if (ui->command_layer_dirty)
    {
        lv_obj_move_foreground(ui->widgets.diagnostics_icon);
        lv_obj_move_foreground(ui->widgets.diagnostics_lbl);
        lv_obj_move_foreground(ui->widgets.alert_overlay);
        ui->command_layer_dirty = false;
    }
}
static void command_activate(ui_t *ui, bool back)
{
    if (back)
    {
        if (ui->command_confirm)
            ui->command_confirm = 0;
        else
            close_vehicle_menu(ui);
        return;
    }
    unsigned id = command_id(ui);
    if (!id || ui->command_waiting || lv_obj_has_state(ui->command_button, LV_STATE_DISABLED))
        return;
    unsigned state = atomic_load(&ui->command_ui[id == 3 ? 1 : 0]), phase = state & 255U;
    /* A state change must never turn a displayed Start into an implicit Stop. */
    if ((state & 0xfc00ffffU) != (ui->command_presented_state & 0xfc00ffffU))
    {
        ui->command_confirm = 0;
        return;
    }
    if (phase == VEHICLE_RESTORED)
        phase = VEHICLE_READY;
    bool stop = id < 3 && (phase == VEHICLE_STARTING || phase == VEHICLE_ACTIVE);
    if (!stop && id < 3 && (!ui->idle_protection || (state & (VEHICLE_UI_COOLANT_HIGH << 8))))
        return;
    if (!stop && !ui->command_confirm && (id == 3 || (state & (16U << 8))))
    {
        ui->command_confirm         = id;
        ui->command_confirm_state   = state;
        ui->command_confirm_epoch   = atomic_load(&ui->command_epoch);
        ui->command_confirm_seconds = id < 3 ? command_duration(ui, id, state) : 0;
        return;
    }
    if (!stop && id < 3 && !ui->command_hold_commit)
        return;
    unsigned seconds = ui->command_confirm ? ui->command_confirm_seconds : command_duration(ui, id, state);
    if (stop)
    {
        id = active_idle_id(ui);
        for (unsigned i = 0; i < ui->command_page_count; i++)
            if (ui->command_pages[i] == id)
                ui->command_page = i;
    }
    if ((!stop && !(state & 256U)) || (!stop && phase != VEHICLE_READY && phase != VEHICLE_REJECTED))
    {
        ui->command_confirm = 0;
        return;
    }
    ui->command_request_page     = id;
    ui->command_request_stop     = stop;
    ui->command_admission_failed = false;
    /* Freeze the confirmed duration. A later coolant recovery cannot lengthen it. */
    ui->command_request_seconds = id < 3 && !stop ? seconds : 0;
    ui->command_selected        = phase;
    ui->command_confirm         = 0;
    ui->command_waiting         = true;
    ui->command_wait_at         = (uint32_t)(esp_timer_get_time() / 1000);
    dispatch_touch(ui, UI_EVENT_VEHICLE_COMMAND);
}
static void command_pointer(ui_t *ui, lv_event_code_t code, lv_obj_t *target, int x, int y)
{
    if (!ui->command_menu_visible || ui->command_suspended || !action_context(ui))
        return;
    ui->command_activity = (uint32_t)(esp_timer_get_time() / 1000);
    if (code == LV_EVENT_PRESSED)
    {
        ui->command_press        = true;
        ui->command_moved        = false;
        ui->command_x            = x;
        ui->command_y            = y;
        ui->command_press_target = target;
        ui->command_press_at     = (uint32_t)(esp_timer_get_time() / 1000);
        ui->command_press_epoch  = atomic_load(&ui->command_epoch);
        ui->command_hold_armed =
            target == ui->command_button && !strcmp(lv_label_get_text(ui->command_button_text), "Hold to start");
    }
    else if (code == LV_EVENT_PRESSING && ui->command_press)
    {
        if (abs(x - ui->command_x) > px(12) || abs(y - ui->command_y) > px(12))
            ui->command_moved = true;
        if (ui->command_hold_armed)
        {
            lv_area_t area;
            lv_obj_get_coords(ui->command_button, &area);
            if (x < area.x1 || x > area.x2 || y < area.y1 || y > area.y2)
                ui->command_moved = true;
        }
    }
    else if (code == LV_EVENT_PRESS_LOST)
        ui->command_press = ui->command_hold_armed = false;
    else if (code == LV_EVENT_RELEASED && ui->command_press)
    {
        bool held         = ui->command_hold_armed;
        ui->command_press = ui->command_hold_armed = false;
        int dx = x - ui->command_x, dy = y - ui->command_y;
        if (dy >= px(32) && abs(dy) >= 2 * abs(dx))
        {
            ui->command_confirm = 0;
            close_vehicle_menu(ui);
        }
        else if (abs(dx) >= px(32) && abs(dx) >= 2 * abs(dy))
        {
            if (!ui->command_confirm && ui->command_page_count)
            {
                int next = (int)ui->command_page + (dx < 0 ? 1 : -1);
                if (next >= 0 && next < ui->command_page_count)
                    ui->command_page = next;
            }
        }
        else if (!held && !ui->command_moved && abs(dx) <= px(12) && abs(dy) <= px(12) &&
                 target == ui->command_press_target && (target == ui->command_button || target == ui->command_back))
        {
            lv_area_t area;
            lv_obj_get_coords(target, &area);
            if (x >= area.x1 && x <= area.x2 && y >= area.y1 && y <= area.y2)
                command_activate(ui, target == ui->command_back);
        }
    }
    refresh_vehicle_menu(ui);
}
static void command_menu_event(lv_event_t *event)
{
    lv_point_t  point = {0};
    lv_indev_t *input = lv_indev_active();
    if (!input)
        return;
    lv_indev_get_point(input, &point);
    command_pointer(lv_event_get_user_data(event), lv_event_get_code(event), lv_event_get_target(event), point.x,
                    point.y);
}
bool ui_action_opens_vehicle_menu(ui_t *ui)
{
    return ui && ui->action.config.vehicle_menu;
}
void ui_show_vehicle_actions(ui_t *ui)
{
    if (!ui || !action_context(ui))
        return;
    if (!ui->command_menu)
    {
        ui->command_menu = plain_view(ui->widgets.gauge_content, 240, 240);
        lv_obj_set_style_bg_color(ui->command_menu, lv_color_hex(color_background), 0);
        lv_obj_set_style_bg_opa(ui->command_menu, LV_OPA_COVER, 0);
        lv_obj_remove_flag(ui->command_menu,
                           LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_EVENT_BUBBLE | LV_OBJ_FLAG_GESTURE_BUBBLE);
        lv_obj_add_flag(ui->command_menu, LV_OBJ_FLAG_CLICKABLE);
        lv_obj_add_event_cb(ui->command_menu, command_menu_event, LV_EVENT_ALL, ui);
        ui->command_ring = lv_arc_create(ui->command_menu);
        lv_obj_set_size(ui->command_ring, px(232), px(232));
        lv_obj_center(ui->command_ring);
        lv_arc_set_bg_angles(ui->command_ring, 0, 360);
        lv_arc_set_angles(ui->command_ring, 0, 80);
        lv_obj_remove_style(ui->command_ring, NULL, LV_PART_KNOB);
        lv_obj_remove_flag(ui->command_ring, LV_OBJ_FLAG_CLICKABLE);
        lv_obj_set_style_arc_width(ui->command_ring, px(6), LV_PART_MAIN);
        lv_obj_set_style_arc_width(ui->command_ring, px(6), LV_PART_INDICATOR);
        lv_obj_set_style_arc_color(ui->command_ring, lv_color_hex(color_track), LV_PART_MAIN);
        ui->command_link_symbol = lv_line_create(ui->command_menu);
        lv_obj_set_style_line_width(ui->command_link_symbol, px(3), 0);
        lv_obj_remove_flag(ui->command_link_symbol, LV_OBJ_FLAG_CLICKABLE);
        ui->command_title = plain_label(ui->command_menu, color_text_primary);
        ui->command_value = plain_label(ui->command_menu, color_text_primary);
        ui->command_note  = plain_label(ui->command_menu, color_warning);
        ui->command_back  = plain_view(ui->command_menu, 44, 44);
        lv_obj_align(ui->command_back, LV_ALIGN_TOP_MID, 0, px(11));
        lv_obj_t                 *chevron = lv_line_create(ui->command_back);
        static lv_point_precise_t back_path[3];
        back_path[0] = (lv_point_precise_t){px(0), px(0)};
        back_path[1] = (lv_point_precise_t){px(8), px(6)};
        back_path[2] = (lv_point_precise_t){px(16), px(0)};
        lv_line_set_points(chevron, back_path, 3);
        lv_obj_set_style_line_color(chevron, lv_color_hex(color_text_secondary), 0);
        lv_obj_set_style_line_width(chevron, px(2), 0);
        lv_obj_center(chevron);
        lv_obj_remove_flag(chevron, LV_OBJ_FLAG_CLICKABLE);
        for (unsigned i = 0; i < 3; ++i)
        {
            ui->command_dots[i] = plain_view(ui->command_menu, 4, 4);
            lv_obj_set_style_radius(ui->command_dots[i], px(2), 0);
            lv_obj_set_style_bg_opa(ui->command_dots[i], LV_OPA_COVER, 0);
            lv_obj_set_pos(ui->command_dots[i], px(106 + i * 12), px(211));
        }
        ui->command_button = plain_view(ui->command_menu, 152, 48);
        lv_obj_align(ui->command_button, LV_ALIGN_TOP_MID, 0, px(150));
        lv_obj_set_style_radius(ui->command_button, px(12), 0);
        lv_obj_set_style_bg_color(ui->command_button, lv_color_hex(color_accent), 0);
        lv_obj_set_style_bg_color(ui->command_button, lv_color_mix(lv_color_hex(color_accent), lv_color_black(), 190),
                                  LV_STATE_PRESSED);
        lv_obj_set_style_bg_opa(ui->command_button, LV_OPA_COVER, 0);
        ui->command_button_text = plain_label(ui->command_button, color_background);
        lv_obj_t *targets[]     = {ui->command_back, ui->command_button};
        for (unsigned i = 0; i < 2; ++i)
        {
            lv_obj_add_flag(targets[i], LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_EVENT_BUBBLE);
            lv_obj_remove_flag(targets[i], LV_OBJ_FLAG_SCROLLABLE);
        }
    }
    ui->command_menu_visible = true;
    ui->command_layer_dirty = ui->status_dirty = true;
    ui->command_page = ui->command_confirm = 0;
    command_sync_pages(ui);
    ui->command_activity = (uint32_t)(esp_timer_get_time() / 1000);
    for (unsigned i = 0; i < ui->command_page_count; ++i)
    {
        unsigned id = ui->command_pages[i], phase = atomic_load(&ui->command_ui[id == 3 ? 1 : 0]) & 255U;
        if ((id == 3 || id == active_idle_id(ui)) &&
            (phase == VEHICLE_ACTIVE || phase == VEHICLE_STARTING || phase == VEHICLE_STOPPING ||
             phase == VEHICLE_VERIFYING || phase == VEHICLE_UNKNOWN || phase == VEHICLE_RESTART_REQUIRED))
        {
            ui->command_page = i;
            break;
        }
    }
    atomic_store(&ui->command_menu_active, true);
    page_action_reset(&ui->action);
    lv_obj_remove_flag(ui->command_menu, LV_OBJ_FLAG_HIDDEN);
    lv_obj_move_foreground(ui->command_menu);
    refresh_vehicle_menu(ui);
}
void ui_set_vehicle_action_status(ui_t    *ui,
                                  uint8_t  kind,
                                  uint8_t  phase,
                                  uint8_t  flags,
                                  uint16_t remaining,
                                  uint8_t  reason,
                                  uint8_t  preset)
{
    if (ui && kind >= 1 && kind <= 2)
    {
        unsigned packed = phase | ((unsigned)flags << 8) | (((unsigned)remaining & 1023U) << 16) |
                          (((unsigned)preset & 3U) << 26) | (((unsigned)reason & 15U) << 28);
        unsigned old    = atomic_exchange(&ui->command_ui[kind - 1], packed);
        if ((old & 0xfc00ffffU) != (packed & 0xfc00ffffU))
            atomic_fetch_add(&ui->command_epoch, 1);
    }
}
void ui_set_idle_protection(ui_t *ui, bool configured)
{
    if (ui)
    {
        if (ui->idle_protection != configured)
            ui->command_press = ui->command_hold_armed = false;
        ui->idle_protection = configured;
    }
}
void ui_vehicle_command_request(ui_t *ui, uint8_t *kind, bool *stop, uint8_t *preset, uint16_t *seconds)
{
    unsigned id = ui->command_request_page;
    *kind       = id == 3 ? 2 : 1;
    *stop       = ui->command_request_stop;
    *preset     = *kind == 1 && !*stop ? id : 0;
    *seconds    = ui->command_request_seconds;
}
void ui_vehicle_command_queued(ui_t *ui, bool accepted)
{
    if (!accepted)
    {
        ui->command_waiting          = false;
        ui->command_admission_failed = true;
        ui->command_wait_at          = (uint32_t)(esp_timer_get_time() / 1000);
        refresh_vehicle_menu(ui);
    }
}

void ui_set_vehicle_action_snapshot(ui_t *ui, const uint8_t status[32])
{
    if (!ui || !status || status[1] < 1 || status[1] > 2)
        return;
    uint32_t scope[3];
    memcpy(scope, status + 8, sizeof(scope));
    unsigned kind = status[1];
    if (memcmp(ui->command_scope[kind - 1], scope, sizeof(scope)))
    {
        memcpy(ui->command_scope[kind - 1], scope, sizeof(scope));
        atomic_fetch_add(&ui->command_epoch, 1);
    }
    ui_set_vehicle_action_status(ui, kind, status[2], status[27], status[24] | (uint16_t)status[25] << 8, status[3],
                                 status[26]);
}
static void command_hold_tick(ui_t *ui)
{
    if (!ui->command_press || !ui->command_hold_armed)
        return;
    if (!ui->command_menu_visible || ui->command_suspended || !action_context(ui) || ui->command_moved ||
        ui->command_press_epoch != atomic_load(&ui->command_epoch) ||
        lv_obj_has_state(ui->command_button, LV_STATE_DISABLED) ||
        strcmp(lv_label_get_text(ui->command_button_text), "Hold to start"))
    {
        ui->command_press = ui->command_hold_armed = false;
        return;
    }
    if ((uint32_t)(esp_timer_get_time() / 1000) - ui->command_press_at < 900)
        return;
    /* Consume the entire touch before dispatch: its release cannot also Stop. */
    ui->command_press = ui->command_hold_armed = false;
    ui->command_hold_commit                    = true;
    command_activate(ui, false);
    ui->command_hold_commit = false;
    refresh_vehicle_menu(ui);
}

static void command_attention_ring(ui_t *ui)
{
    if (!ui->command_menu_visible || !ui->command_suspended || !ui->command_ring)
        return;
    unsigned phase    = atomic_load(&ui->command_ui[0]) & 255U;
    bool     stopping = phase == VEHICLE_STOPPING || phase == VEHICLE_VERIFYING;
    if (lv_obj_get_parent(ui->command_ring) != ui->widgets.alert_overlay)
    {
        lv_obj_set_parent(ui->command_ring, ui->widgets.alert_overlay);
        lv_obj_center(ui->command_ring);
    }
    show(ui->command_ring, stopping);
    if (stopping)
    {
        lv_obj_set_style_arc_color(ui->command_ring, lv_color_hex(color_critical), LV_PART_INDICATOR);
        lv_arc_set_rotation(ui->command_ring, ((uint32_t)(esp_timer_get_time() / 1000) % 1400) * 360 / 1400);
    }
}

page_cycle_reason_t ui_cycle_pause(ui_t *ui)
{
    return (page_cycle_reason_t)atomic_load(&ui->cycle_pause);
}
