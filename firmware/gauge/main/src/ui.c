// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>

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
#include "ui.h"
#include "util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "UI";

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct {
    uint8_t pid;
    int32_t value;
    TickType_t received_at;
} ui_sample_t;

typedef struct {
    uint8_t severity;
    bool unavailable;
    char label[32];
} ui_alert_t;

typedef struct {
    bool valid;
    bool mil_on;
    uint8_t count;
    char first_code[6];
} ui_diagnostics_t;

typedef struct {
    bool present;
    int32_t value;
    TickType_t received_at;
} ui_metric_sample_t;

struct _ui_t
{
    ui_touch_callback_t touch_cb;
    bool                long_press_handled;
    bool                calibration_mode;
    uint8_t             calibration_page;
    TickType_t          pressed_at;

    struct
    {
        QueueHandle_t value_que;
        QueueHandle_t touch_ev_que;
        QueueHandle_t pairing_que;
        QueueHandle_t alert_que;
        QueueHandle_t diagnostics_que;
    } rtos;
    struct
    {
        lv_obj_t *value_lbl;
        lv_obj_t *info_lbl;
        lv_obj_t *unit_lbl;
        lv_obj_t *pairing_lbl;
        lv_obj_t *alert_lbl;
        lv_obj_t *diagnostics_lbl;
        lv_obj_t *arc;
        lv_obj_t *bar;
        lv_obj_t *trend;
        lv_obj_t *dual_value[2];
        lv_obj_t *dual_info[2];
    } widgets;

    struct
    {
        ui_page_t page;
        ui_metric_sample_t samples[2];
        int32_t rendered_values[2];
        bool rendered_available[2];
        bool rendered_once;
        lv_point_precise_t trend_points[60];
        uint8_t trend_count;
        TickType_t trend_recorded_at;
    } display;
};

// ---------------------------------------------------------------------------------------------------------------------
// Fonts
// ---------------------------------------------------------------------------------------------------------------------

extern const lv_font_t notosans_semibold_64;
extern const lv_font_t notosans_semibold_32;
extern const lv_font_t notosans_medium_16;
extern const lv_font_t notosans_medium_24;

static const lv_font_t *const font_title    = &notosans_semibold_64;
static const lv_font_t *const font_compact  = &notosans_semibold_32;
static const lv_font_t *const font_subtitle = &notosans_medium_16;
static const lv_font_t *const font_unit     = &notosans_medium_24;

static const uint32_t color_background = 0x05080A;
static const uint32_t color_text_primary = 0xE4EAED;
static const uint32_t color_text_secondary = 0x9CAAB2;
static const uint32_t color_track = 0x202A30;
static const uint32_t color_accent = 0x26B895;
static const uint32_t color_warning = 0xE0A63A;
static const uint32_t color_critical = 0xE75A5A;

#define CALIBRATION_PAGE_COUNT 5
#define PRIMARY_LABEL_WIDTH 160
#define PRIMARY_VALUE_WIDTH 176
#define PRIMARY_UNIT_WIDTH 156

static int32_t bounded_range(int32_t minimum, int32_t maximum)
{
    int64_t range = (int64_t)maximum - minimum;
    return range > 0 && range <= INT32_MAX ? (int32_t)range : 1;
}

static bool metric_value(ui_t *ui, unsigned index, int32_t *value)
{
    if (index >= ui->display.page.metric_count || !ui->display.samples[index].present) return false;
    TickType_t age = xTaskGetTickCount() - ui->display.samples[index].received_at;
    if (age > pdMS_TO_TICKS(ui->display.page.metrics[index].stale_after_ms)) return false;
    *value = ui->display.samples[index].value;
    return true;
}

static void show(lv_obj_t *object, bool visible)
{
    if (!object) return;
    if (visible) lv_obj_remove_flag(object, LV_OBJ_FLAG_HIDDEN);
    else lv_obj_add_flag(object, LV_OBJ_FLAG_HIDDEN);
}

static lv_obj_t *calibration_box(lv_obj_t *parent, int32_t x, int32_t y,
                                 int32_t width, int32_t height, uint32_t color)
{
    lv_obj_t *box = lv_obj_create(parent);
    lv_obj_remove_style_all(box);
    lv_obj_set_pos(box, x, y);
    lv_obj_set_size(box, width, height);
    lv_obj_set_style_bg_color(box, lv_color_hex(color), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(box, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_clear_flag(box, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(box, LV_OBJ_FLAG_EVENT_BUBBLE);
    return box;
}

static lv_obj_t *calibration_label(lv_obj_t *parent, const char *text, int32_t y,
                                   int32_t width, const lv_font_t *font, uint32_t color)
{
    lv_obj_t *label = lv_label_create(parent);
    lv_obj_remove_style_all(label);
    lv_obj_set_width(label, width);
    lv_obj_align(label, LV_ALIGN_TOP_MID, 0, y);
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
    lv_obj_set_size(ring, diameter, diameter);
    lv_obj_center(ring);
    lv_obj_set_style_radius(ring, LV_RADIUS_CIRCLE, LV_PART_MAIN);
    lv_obj_set_style_border_width(ring, 1, LV_PART_MAIN);
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

    switch (ui->calibration_page) {
    case 0: {
        static const uint32_t colors[6] = {
            0xE53935, 0x43A047, 0x1E88E5, 0x00ACC1, 0x8E24AA, 0xFDD835,
        };
        for (unsigned i = 0; i < 3; ++i) {
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
    case 3: {
        calibration_ring(screen, 208, color_accent);
        lv_obj_t *safe = lv_obj_create(screen);
        lv_obj_remove_style_all(safe);
        lv_obj_set_size(safe, 147, 147);
        lv_obj_center(safe);
        lv_obj_set_style_border_width(safe, 1, LV_PART_MAIN);
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
    default: {
        lv_obj_t *arc = lv_arc_create(screen);
        lv_obj_set_size(arc, 198, 198);
        lv_obj_center(arc);
        lv_arc_set_bg_angles(arc, 135, 45);
        lv_arc_set_range(arc, 0, 8000);
        lv_arc_set_value(arc, 2840);
        lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
        lv_obj_clear_flag(arc, LV_OBJ_FLAG_CLICKABLE);
        lv_obj_add_flag(arc, LV_OBJ_FLAG_EVENT_BUBBLE);
        lv_obj_set_style_arc_width(arc, 7, LV_PART_MAIN);
        lv_obj_set_style_arc_width(arc, 7, LV_PART_INDICATOR);
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
    ESP_LOGI(TAG, "Display calibration page %u/%u", ui->calibration_page + 1,
             CALIBRATION_PAGE_COUNT);
}

static void select_renderer_widgets(ui_t *ui)
{
    ui_renderer_t renderer = ui->display.page.renderer;
    bool dual = renderer == UI_RENDERER_DUAL;
    show(ui->widgets.value_lbl, !dual);
    show(ui->widgets.info_lbl, !dual);
    show(ui->widgets.unit_lbl, !dual);
    show(ui->widgets.arc, renderer == UI_RENDERER_ARC);
    show(ui->widgets.bar, renderer == UI_RENDERER_BAR);
    show(ui->widgets.trend, renderer == UI_RENDERER_TREND);
    for (unsigned i = 0; i < 2; ++i) {
        show(ui->widgets.dual_value[i], dual);
        show(ui->widgets.dual_info[i], dual);
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void ui_touch_callback(lv_event_t *e)
{
    ESP_NULL_CHECK(e, TAG, "Event is NULL");
    ui_t *ui = (ui_t *)lv_event_get_user_data(e);
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    lv_event_code_t code = lv_event_get_code(e);
    switch (code)
    {
    case LV_EVENT_PRESSED:
        ui->long_press_handled = false;
        ui->pressed_at = xTaskGetTickCount();
        break;
    case LV_EVENT_LONG_PRESSED:
        ui->long_press_handled = true;
        xQueueSend(ui->rtos.touch_ev_que, &code, 0);
        break;
    case LV_EVENT_CLICKED:
        if (!ui->long_press_handled)
        {
            xQueueSend(ui->rtos.touch_ev_que, &code, 0);
        }
        break;
    case LV_EVENT_RELEASED:
        if (xTaskGetTickCount() - ui->pressed_at >= pdMS_TO_TICKS(12000)) {
            xQueueSend(ui->rtos.touch_ev_que, &code, 0);
        }
        break;
    default:
        // ignore event
        break;
    }
}

static void ui_align_labels(ui_t *ui)
{
    // These row-specific widths were physically verified inside the 104 px
    // essential-content radius on the 240 x 240 round panel.
    lv_obj_set_width(ui->widgets.info_lbl, PRIMARY_LABEL_WIDTH);
    lv_obj_set_width(ui->widgets.value_lbl, PRIMARY_VALUE_WIDTH);
    lv_obj_set_width(ui->widgets.unit_lbl, PRIMARY_UNIT_WIDTH);
    lv_obj_align(ui->widgets.info_lbl, LV_ALIGN_TOP_MID, 0, 48);
    lv_obj_align(ui->widgets.value_lbl, LV_ALIGN_TOP_MID, 0, 78);
    lv_obj_align(ui->widgets.unit_lbl, LV_ALIGN_TOP_MID, 0, 158);
}

static void ui_update_screen(ui_t *ui, int32_t const *value, const char *info, const char *unit)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

    if (value != NULL)
    {
        lv_label_set_text_fmt(ui->widgets.value_lbl, "%" PRId32, *value);
        const char *text = lv_label_get_text(ui->widgets.value_lbl);
        int32_t safe_width = lv_obj_get_width(ui->widgets.value_lbl);
        const lv_font_t *font = font_title;
        if (lv_text_get_width(text, strlen(text), font, 0) > safe_width) font = font_compact;
        if (lv_text_get_width(text, strlen(text), font, 0) > safe_width) font = font_subtitle;
        lv_obj_set_style_text_font(ui->widgets.value_lbl, font, LV_PART_MAIN);
    }
    else
    {
        lv_label_set_text(ui->widgets.value_lbl, "...");
        lv_obj_set_style_text_font(ui->widgets.value_lbl, font_title, LV_PART_MAIN);
    }

    if (info != NULL)
    {
        lv_label_set_text(ui->widgets.info_lbl, info);
    }

    if (unit != NULL)
    {
        lv_label_set_text(ui->widgets.unit_lbl, unit);
    }

    ui_align_labels(ui);
}

static void render_page(ui_t *ui)
{
    int32_t values[2] = {0};
    bool available[2] = {
        metric_value(ui, 0, &values[0]),
        metric_value(ui, 1, &values[1]),
    };
    ui_renderer_t renderer = ui->display.page.renderer;
    const ui_metric_t *primary = &ui->display.page.metrics[0];

    if (renderer == UI_RENDERER_DUAL) {
        for (unsigned i = 0; i < 2; ++i) {
            const ui_metric_t *metric = &ui->display.page.metrics[i];
            if (!ui->display.rendered_once || available[i] != ui->display.rendered_available[i] ||
                (available[i] && values[i] != ui->display.rendered_values[i])) {
                if (available[i]) lv_label_set_text_fmt(ui->widgets.dual_value[i], "%" PRId32, values[i]);
                else lv_label_set_text(ui->widgets.dual_value[i], "...");
                lv_label_set_text_fmt(ui->widgets.dual_info[i], "%s  %s",
                                      metric->name ? metric->name : "VALUE",
                                      metric->unit ? metric->unit : "");
            }
        }
    } else if (!ui->display.rendered_once || available[0] != ui->display.rendered_available[0] ||
               (available[0] && values[0] != ui->display.rendered_values[0])) {
        ui_update_screen(ui, available[0] ? &values[0] : NULL,
                         ui->display.page.name ? ui->display.page.name : primary->name,
                         primary->unit);
        if (available[0] && renderer == UI_RENDERER_ARC) lv_arc_set_value(ui->widgets.arc, values[0]);
        if (available[0] && renderer == UI_RENDERER_BAR) lv_bar_set_value(ui->widgets.bar, values[0], LV_ANIM_OFF);
    }

    if (renderer == UI_RENDERER_TREND && available[0] &&
        xTaskGetTickCount() - ui->display.trend_recorded_at >= pdMS_TO_TICKS(500)) {
        ui->display.trend_recorded_at = xTaskGetTickCount();
        if (ui->display.trend_count < 60) ui->display.trend_count++;
        memmove(&ui->display.trend_points[0], &ui->display.trend_points[1],
                59 * sizeof(ui->display.trend_points[0]));
        int32_t range = bounded_range(primary->minimum, primary->maximum);
        int64_t normalized = ((int64_t)(values[0] - primary->minimum) * 76) / range;
        if (normalized < 0) normalized = 0;
        if (normalized > 76) normalized = 76;
        for (unsigned i = 0; i < 60; ++i)
            ui->display.trend_points[i].x = (int32_t)i * 144 / 59;
        ui->display.trend_points[59].y = 76 - (int32_t)normalized;
        lv_line_set_points(ui->widgets.trend, ui->display.trend_points, 60);
    }

    for (unsigned i = 0; i < 2; ++i) {
        ui->display.rendered_values[i] = values[i];
        ui->display.rendered_available[i] = available[i];
    }
    ui->display.rendered_once = true;
}

static void ui_task(lv_timer_t *timer)
{
    ESP_NULL_CHECK(timer, TAG, "timer is NULL");
    ui_t *ui = (ui_t *)lv_timer_get_user_data(timer);
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

    if (ui->calibration_mode) {
        lv_event_code_t event_code;
        if (xQueueReceive(ui->rtos.touch_ev_que, &event_code, 0) == pdTRUE && ui->touch_cb)
            ui->touch_cb(ui, event_code);
        return;
    }

    uint32_t pairing_code;
    if (xQueueReceive(ui->rtos.pairing_que, &pairing_code, 0) == pdTRUE) {
        if (pairing_code == 0) lv_obj_add_flag(ui->widgets.pairing_lbl, LV_OBJ_FLAG_HIDDEN);
        else {
            if (pairing_code == UINT32_MAX) lv_label_set_text(ui->widgets.pairing_lbl, "PAIR\nREADY");
            else lv_label_set_text_fmt(ui->widgets.pairing_lbl, "PAIR\n%06" PRIu32, pairing_code);
            lv_obj_remove_flag(ui->widgets.pairing_lbl, LV_OBJ_FLAG_HIDDEN);
        }
    }

    ui_alert_t alert;
    if (xQueueReceive(ui->rtos.alert_que, &alert, 0) == pdTRUE) {
        lv_color_t indicator = alert.severity == 2 ? lv_color_hex(color_critical) :
                               alert.severity == 1 ? lv_color_hex(color_warning) :
                                                     lv_color_hex(color_accent);
        lv_obj_set_style_arc_color(ui->widgets.arc, indicator, LV_PART_INDICATOR);
        lv_obj_set_style_bg_color(ui->widgets.bar, indicator, LV_PART_INDICATOR);
        lv_obj_set_style_line_color(ui->widgets.trend, indicator, LV_PART_MAIN);
        if (alert.severity == 0) {
            lv_obj_add_flag(ui->widgets.alert_lbl, LV_OBJ_FLAG_HIDDEN);
            select_renderer_widgets(ui);
        }
        else {
            lv_obj_set_style_text_color(ui->widgets.alert_lbl,
                alert.severity == 2 ? lv_color_hex(color_critical) : lv_color_hex(color_warning),
                LV_PART_MAIN);
            lv_label_set_text_fmt(ui->widgets.alert_lbl, "%s\n%s%s",
                alert.severity == 2 ? "CRITICAL" : "WARNING", alert.label,
                alert.unavailable ? " LOST" : "");
            lv_obj_add_flag(ui->widgets.info_lbl, LV_OBJ_FLAG_HIDDEN);
            lv_obj_remove_flag(ui->widgets.alert_lbl, LV_OBJ_FLAG_HIDDEN);
        }
    }

    ui_diagnostics_t diagnostics;
    if (xQueueReceive(ui->rtos.diagnostics_que, &diagnostics, 0) == pdTRUE) {
        if (!diagnostics.valid || (!diagnostics.mil_on && diagnostics.count == 0 &&
                                   diagnostics.first_code[0] == 0))
            lv_obj_add_flag(ui->widgets.diagnostics_lbl, LV_OBJ_FLAG_HIDDEN);
        else {
            lv_obj_set_style_text_color(ui->widgets.diagnostics_lbl,
                diagnostics.mil_on ? lv_color_hex(color_warning) : lv_color_hex(color_text_primary),
                LV_PART_MAIN);
            lv_label_set_text_fmt(ui->widgets.diagnostics_lbl, "%s %u%s%s",
                diagnostics.mil_on ? "CEL" : "DTC", diagnostics.count,
                diagnostics.first_code[0] ? "  " : "", diagnostics.first_code);
            lv_obj_remove_flag(ui->widgets.diagnostics_lbl, LV_OBJ_FLAG_HIDDEN);
        }
    }

    ui_sample_t sample;
    while (xQueueReceive(ui->rtos.value_que, &sample, 0) == pdTRUE) {
        for (unsigned i = 0; i < ui->display.page.metric_count; ++i) {
            if (ui->display.page.metrics[i].pid != sample.pid) continue;
            ui->display.samples[i].present = sample.value != DISPLAY_VALUE_INVALID;
            ui->display.samples[i].value = sample.value;
            ui->display.samples[i].received_at = sample.received_at;
        }
    }
    render_page(ui);

    lv_event_code_t event_code;
    if (xQueueReceive(ui->rtos.touch_ev_que, &event_code, 0) == pdTRUE)
    {
        if (ui->touch_cb != NULL)
        {
            ui->touch_cb(ui, event_code);
        }
    }
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

    // Main number label
    lv_obj_t *value_lbl = lv_label_create(scr);
    ESP_NULL_CHECK(value_lbl, TAG, "Failed to create main label");
    lv_label_set_text(value_lbl, "...");
    lv_obj_set_style_text_color(value_lbl, lv_color_hex(color_text_primary), LV_PART_MAIN);
    lv_obj_set_style_text_font(value_lbl, font_title, LV_PART_MAIN);
    lv_obj_set_style_text_align(value_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_label_set_long_mode(value_lbl, LV_LABEL_LONG_DOT);

    // Info label
    lv_obj_t *info_lbl = lv_label_create(scr);
    ESP_NULL_CHECK(info_lbl, TAG, "Failed to create info label");
    lv_label_set_text(info_lbl, page->name ? page->name : "???");
    lv_obj_set_style_text_color(info_lbl, lv_color_hex(color_text_secondary), LV_PART_MAIN);
    lv_obj_set_style_text_font(info_lbl, font_subtitle, LV_PART_MAIN);
    lv_obj_set_style_text_align(info_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_label_set_long_mode(info_lbl, LV_LABEL_LONG_DOT);

    // Unit label centered below the number, away from the curved right edge.
    lv_obj_t *unit_lbl = lv_label_create(scr);
    ESP_NULL_CHECK(unit_lbl, TAG, "Failed to create unit label");
    lv_label_set_text(unit_lbl, page->metrics[0].unit ? page->metrics[0].unit : "");
    lv_obj_set_style_text_color(unit_lbl, lv_color_hex(color_text_secondary), LV_PART_MAIN);
    lv_obj_set_style_text_font(unit_lbl, font_unit, LV_PART_MAIN);
    lv_obj_set_style_text_align(unit_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_label_set_long_mode(unit_lbl, LV_LABEL_LONG_DOT);

    ui->widgets.value_lbl = value_lbl;
    ui->widgets.info_lbl  = info_lbl;
    ui->widgets.unit_lbl  = unit_lbl;

    lv_obj_t *arc = lv_arc_create(scr);
    lv_obj_set_size(arc, 198, 198);
    lv_obj_align(arc, LV_ALIGN_CENTER, 0, 0);
    lv_arc_set_bg_angles(arc, 135, 45);
    lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
    lv_obj_clear_flag(arc, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_style_arc_width(arc, 7, LV_PART_MAIN);
    lv_obj_set_style_arc_width(arc, 7, LV_PART_INDICATOR);
    lv_obj_set_style_arc_rounded(arc, true, LV_PART_MAIN);
    lv_obj_set_style_arc_rounded(arc, true, LV_PART_INDICATOR);
    lv_obj_set_style_arc_color(arc, lv_color_hex(color_track), LV_PART_MAIN);
    lv_obj_set_style_arc_color(arc, lv_color_hex(color_accent), LV_PART_INDICATOR);
    ui->widgets.arc = arc;

    lv_obj_t *bar = lv_bar_create(scr);
    lv_obj_set_size(bar, 142, 12);
    lv_obj_align(bar, LV_ALIGN_CENTER, 0, 52);
    lv_obj_set_style_bg_color(bar, lv_color_hex(color_track), LV_PART_MAIN);
    lv_obj_set_style_bg_color(bar, lv_color_hex(color_accent), LV_PART_INDICATOR);
    ui->widgets.bar = bar;

    lv_obj_t *trend = lv_line_create(scr);
    lv_obj_set_size(trend, 150, 80);
    lv_obj_align(trend, LV_ALIGN_CENTER, 0, 24);
    lv_obj_set_style_line_color(trend, lv_color_hex(color_accent), LV_PART_MAIN);
    lv_obj_set_style_line_width(trend, 3, LV_PART_MAIN);
    for (unsigned i = 0; i < 60; ++i) {
        ui->display.trend_points[i].x = (int32_t)i * 144 / 59;
        ui->display.trend_points[i].y = 76;
    }
    lv_line_set_points(trend, ui->display.trend_points, 60);
    ui->widgets.trend = trend;

    for (unsigned i = 0; i < 2; ++i) {
        lv_obj_t *dual_value = lv_label_create(scr);
        lv_obj_set_width(dual_value, 132);
        lv_obj_align(dual_value, LV_ALIGN_TOP_MID, 0, i == 0 ? 42 : 124);
        lv_obj_set_style_text_color(dual_value, lv_color_hex(color_text_primary), LV_PART_MAIN);
        lv_obj_set_style_text_font(dual_value, font_compact, LV_PART_MAIN);
        lv_obj_set_style_text_align(dual_value, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
        lv_label_set_text(dual_value, "...");
        ui->widgets.dual_value[i] = dual_value;

        lv_obj_t *dual_info = lv_label_create(scr);
        lv_obj_set_width(dual_info, 156);
        lv_obj_align(dual_info, LV_ALIGN_TOP_MID, 0, i == 0 ? 20 : 102);
        lv_obj_set_style_text_color(dual_info, lv_color_hex(color_text_secondary), LV_PART_MAIN);
        lv_obj_set_style_text_font(dual_info, font_subtitle, LV_PART_MAIN);
        lv_obj_set_style_text_align(dual_info, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
        ui->widgets.dual_info[i] = dual_info;
    }

    lv_obj_t *pairing_lbl = lv_label_create(scr);
    lv_obj_set_size(pairing_lbl, 176, 100);
    lv_obj_align(pairing_lbl, LV_ALIGN_CENTER, 0, 0);
    lv_obj_set_style_bg_color(pairing_lbl, lv_color_hex(color_background), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(pairing_lbl, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_text_color(pairing_lbl, lv_color_hex(color_text_primary), LV_PART_MAIN);
    lv_obj_set_style_text_font(pairing_lbl, font_compact, LV_PART_MAIN);
    lv_obj_set_style_text_align(pairing_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_obj_add_flag(pairing_lbl, LV_OBJ_FLAG_EVENT_BUBBLE);
    lv_obj_add_flag(pairing_lbl, LV_OBJ_FLAG_HIDDEN);
    ui->widgets.pairing_lbl = pairing_lbl;

    lv_obj_t *alert_lbl = lv_label_create(scr);
    lv_obj_set_size(alert_lbl, 104, 40);
    lv_obj_align(alert_lbl, LV_ALIGN_TOP_MID, 0, 30);
    lv_obj_set_style_text_font(alert_lbl, font_subtitle, LV_PART_MAIN);
    lv_obj_set_style_text_align(alert_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_label_set_long_mode(alert_lbl, LV_LABEL_LONG_DOT);
    lv_obj_add_flag(alert_lbl, LV_OBJ_FLAG_HIDDEN);
    ui->widgets.alert_lbl = alert_lbl;

    lv_obj_t *diagnostics_lbl = lv_label_create(scr);
    lv_obj_set_size(diagnostics_lbl, 132, 24);
    lv_obj_align(diagnostics_lbl, LV_ALIGN_BOTTOM_MID, 0, -22);
    lv_obj_set_style_text_font(diagnostics_lbl, font_subtitle, LV_PART_MAIN);
    lv_obj_set_style_text_align(diagnostics_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_label_set_long_mode(diagnostics_lbl, LV_LABEL_LONG_DOT);
    lv_obj_add_flag(diagnostics_lbl, LV_OBJ_FLAG_HIDDEN);
    ui->widgets.diagnostics_lbl = diagnostics_lbl;

    int32_t minimum = page->metrics[0].minimum;
    int32_t maximum = page->metrics[0].maximum;
    if (maximum <= minimum) maximum = minimum + 1;
    lv_arc_set_range(arc, minimum, maximum);
    lv_bar_set_range(bar, minimum, maximum);
    select_renderer_widgets(ui);

    lv_refr_now(NULL);  // force refresh to apply styles immediately
    ui_align_labels(ui);

    // add handlers
    lv_obj_add_event_cb(lv_screen_active(), ui_touch_callback, LV_EVENT_ALL, ui);
    lv_timer_create(ui_task, interval_ms, ui);

    ESP_LOGI(TAG, "Screen initialized successfully");
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

ui_t *ui_init(ui_page_t const *page, uint32_t interval_ms, ui_touch_callback_t touch_cb)
{
    ESP_NULL_CHECK(page, TAG, "Page config is NULL");
    ESP_NULL_CHECK(touch_cb, TAG, "touch callback is NULL");

    ESP_LOGI(TAG, "Initializing UI...");

    ui_t *ui = malloc(sizeof(ui_t));
    ESP_NULL_CHECK(ui, TAG, "Failed to allocate memory for UI context");
    memset(ui, 0, sizeof(ui_t));
    ESP_LOGI(TAG, "ui pointer2: %p (addr: %p)", ui, (void *)&ui);

    ui->rtos.value_que    = xQueueCreate(32, sizeof(ui_sample_t));
    ui->rtos.touch_ev_que = xQueueCreate(4, sizeof(lv_event_code_t));
    ui->rtos.pairing_que = xQueueCreate(1, sizeof(uint32_t));
    ui->rtos.alert_que = xQueueCreate(1, sizeof(ui_alert_t));
    ui->rtos.diagnostics_que = xQueueCreate(1, sizeof(ui_diagnostics_t));
    ui->touch_cb          = touch_cb;
    ui->display.page = *page;

    if (!ui->rtos.value_que || !ui->rtos.touch_ev_que || !ui->rtos.pairing_que ||
        !ui->rtos.alert_que || !ui->rtos.diagnostics_que) {
        ESP_LOGE(TAG, "Failed to allocate UI mailboxes");
        if (ui->rtos.value_que) vQueueDelete(ui->rtos.value_que);
        if (ui->rtos.touch_ev_que) vQueueDelete(ui->rtos.touch_ev_que);
        if (ui->rtos.pairing_que) vQueueDelete(ui->rtos.pairing_que);
        if (ui->rtos.alert_que) vQueueDelete(ui->rtos.alert_que);
        if (ui->rtos.diagnostics_que) vQueueDelete(ui->rtos.diagnostics_que);
        free(ui);
        return NULL;
    }

    if (!lvgl_port_lock(portMAX_DELAY))
    {
        ESP_LOGE(TAG, "Failed to lock LVGL port");
        free(ui);
        return NULL;
    }

    ui_init_screen(ui, page, interval_ms);

    lvgl_port_unlock();

    ESP_LOGI(TAG, "UI initialized successfully");

    return ui;
}

void ui_set_value(ui_t *ui, uint8_t pid, int32_t const *value)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

    ui_sample_t sample = {
        .pid = pid,
        .value = value != NULL ? *value : DISPLAY_VALUE_INVALID,
        .received_at = xTaskGetTickCount(),
    };

    if (xQueueSend(ui->rtos.value_que, &sample, 0) != pdTRUE)
    {
        ESP_LOGW(TAG, "Dropped UI sample for PID 0x%02X", pid);
    }
}

void ui_set_page(ui_t *ui, ui_page_t const *page)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    ESP_NULL_CHECK(page, TAG, "Page config is NULL");

    xQueueReset(ui->rtos.value_que);
    ui->display.page = *page;
    memset(ui->display.samples, 0, sizeof(ui->display.samples));
    ui->display.rendered_once = false;
    ui->display.trend_count = 0;
    for (unsigned i = 0; i < 60; ++i) ui->display.trend_points[i].y = 76;
    int32_t minimum = page->metrics[0].minimum;
    int32_t maximum = page->metrics[0].maximum;
    if (maximum <= minimum) maximum = minimum + 1;
    lv_arc_set_range(ui->widgets.arc, minimum, maximum);
    lv_bar_set_range(ui->widgets.bar, minimum, maximum);
    select_renderer_widgets(ui);
    ui_update_screen(ui, NULL, page->name, page->metrics[0].unit);
    ESP_LOGI(TAG, "Updated page: %s renderer=%u", page->name, page->renderer);
}

void ui_show_pairing_code(ui_t *ui, uint32_t passkey)
{
    if (ui != NULL) xQueueOverwrite(ui->rtos.pairing_que, &passkey);
}

void ui_set_alert(ui_t *ui, uint8_t severity, bool unavailable, const char *label)
{
    if (!ui || !ui->rtos.alert_que) return;
    ui_alert_t alert = {.severity = severity, .unavailable = unavailable};
    if (label) snprintf(alert.label, sizeof(alert.label), "%s", label);
    xQueueOverwrite(ui->rtos.alert_que, &alert);
}

void ui_set_diagnostics(ui_t *ui, bool valid, bool mil_on,
                        uint8_t count, const char *first_code)
{
    if (!ui || !ui->rtos.diagnostics_que) return;
    ui_diagnostics_t diagnostics = {.valid = valid, .mil_on = mil_on, .count = count};
    if (first_code) snprintf(diagnostics.first_code, sizeof(diagnostics.first_code),
                             "%s", first_code);
    xQueueOverwrite(ui->rtos.diagnostics_que, &diagnostics);
}

void ui_start_display_calibration(ui_t *ui)
{
    if (!ui || !lvgl_port_lock(portMAX_DELAY)) return;
    ui->calibration_mode = true;
    ui->calibration_page = 0;
    render_calibration_page(ui);
    lvgl_port_unlock();
}

void ui_next_display_calibration(ui_t *ui)
{
    if (!ui || !ui->calibration_mode) return;
    ui->calibration_page = (ui->calibration_page + 1) % CALIBRATION_PAGE_COUNT;
    render_calibration_page(ui);
}
