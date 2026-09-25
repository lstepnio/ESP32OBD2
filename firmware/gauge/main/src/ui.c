// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include "esp_log.h"
#include "esp_log_color.h"
#include "esp_lvgl_port.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep

#include "freertos/projdefs.h"
#include "freertos/queue.h"
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

struct _ui_t
{
    ui_touch_callback_t touch_cb;
    bool                long_press_handled;

    struct
    {
        QueueHandle_t value_que;
        QueueHandle_t touch_ev_que;
    } rtos;
    struct
    {
        lv_obj_t *value_lbl;
        lv_obj_t *info_lbl;
        lv_obj_t *unit_lbl;
    } widgets;

    struct
    {
        float current_value;
    } display;
};

// ---------------------------------------------------------------------------------------------------------------------
// Fonts
// ---------------------------------------------------------------------------------------------------------------------

extern const lv_font_t notosans_semibold_64;
extern const lv_font_t notosans_medium_16;
extern const lv_font_t notosans_medium_24;

static const lv_font_t *const font_title    = &notosans_semibold_64;
static const lv_font_t *const font_subtitle = &notosans_medium_24;
static const lv_font_t *const font_unit     = &notosans_medium_16;

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
    default:
        // ignore event
        break;
    }
}

static void ui_align_labels(ui_t *ui)
{
    lv_obj_t *scr = lv_screen_active();
    ESP_NULL_CHECK(scr, TAG, "Current screen is NULL");

    // info label
    lv_obj_align_to(ui->widgets.info_lbl, ui->widgets.value_lbl, LV_ALIGN_OUT_BOTTOM_MID, 0, 12);

    // unit label
    lv_obj_set_x(ui->widgets.unit_lbl, lv_obj_get_width(scr) - 44);
    lv_obj_set_y(ui->widgets.unit_lbl, lv_obj_get_y(ui->widgets.value_lbl) + lv_obj_get_height(ui->widgets.value_lbl) -
                                           lv_obj_get_height(ui->widgets.unit_lbl) - 10);
}

static void ui_update_screen(ui_t *ui, int32_t const *value, const char *info, const char *unit)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

    if (value != NULL)
    {
        lv_label_set_text_fmt(ui->widgets.value_lbl, "%" PRId32, *value);
    }
    else
    {
        lv_label_set_text(ui->widgets.value_lbl, "...");
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

static void ui_task(lv_timer_t *timer)
{
    ESP_NULL_CHECK(timer, TAG, "timer is NULL");
    ui_t *ui = (ui_t *)lv_timer_get_user_data(timer);
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

    // get latest value (non-blocking). Continue with previous value if queue is empty
    int32_t target = DISPLAY_VALUE_INVALID;
    xQueuePeek(ui->rtos.value_que, &target, 0);
    if (target == DISPLAY_VALUE_INVALID)
    {
        ui->display.current_value = 0.0f;
        ui_update_screen(ui, NULL, NULL, NULL);
    }
    else
    {
        ui->display.current_value += (target - ui->display.current_value) * 0.4f;
        int32_t rounded = (int32_t)(ui->display.current_value >= 0.0f ? ui->display.current_value + 0.5f
                                                                      : ui->display.current_value - 0.5f);
        ESP_LOGD(TAG, "Current value: %" PRId32 " (target: %" PRId32 ")", rounded, target);
        ui_update_screen(ui, &rounded, NULL, NULL);
    }

    lv_event_code_t event_code;
    if (xQueueReceive(ui->rtos.touch_ev_que, &event_code, 0) == pdTRUE)
    {
        ui->display.current_value = 0.0f;  // Reset current value on touch
        if (ui->touch_cb != NULL)
        {
            ui->touch_cb(ui, event_code);
        }
    }
}

static void ui_init_screen(ui_t *ui, obd_pid_cfg_t const *cfg, uint32_t interval_ms)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    ESP_NULL_CHECK(cfg, TAG, "OBD PID config is NULL");

    ESP_LOGI(TAG, "Initializing screen ...");

    lv_obj_t *scr = lv_screen_active();
    ESP_NULL_CHECK(scr, TAG, "current screen is NULL");

    // Set the screen background to black and fully opaque
    lv_obj_set_style_bg_color(scr, lv_color_black(), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, LV_PART_MAIN);

    // Main number label
    lv_obj_t *value_lbl = lv_label_create(scr);
    ESP_NULL_CHECK(value_lbl, TAG, "Failed to create main label");
    lv_label_set_text(value_lbl, "...");
    lv_obj_set_style_text_color(value_lbl, lv_color_white(), LV_PART_MAIN);
    lv_obj_set_style_text_font(value_lbl, font_title, LV_PART_MAIN);
    lv_obj_set_style_text_align(value_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);
    lv_obj_center(value_lbl);

    // Info label
    lv_obj_t *info_lbl = lv_label_create(scr);
    ESP_NULL_CHECK(info_lbl, TAG, "Failed to create info label");
    lv_label_set_text(info_lbl, cfg->name ? cfg->name : "???");
    lv_obj_set_style_text_color(info_lbl, lv_color_white(), LV_PART_MAIN);
    lv_obj_set_style_text_font(info_lbl, font_subtitle, LV_PART_MAIN);
    lv_obj_set_style_text_align(info_lbl, LV_TEXT_ALIGN_CENTER, LV_PART_MAIN);

    // Unit label (bottom-aligned with widgets.value_lbl, right edge)
    lv_obj_t *unit_lbl = lv_label_create(scr);
    ESP_NULL_CHECK(unit_lbl, TAG, "Failed to create unit label");
    lv_label_set_text(unit_lbl, cfg->unit ? cfg->unit : "");
    lv_obj_set_style_text_color(unit_lbl, lv_color_white(), LV_PART_MAIN);
    lv_obj_set_style_text_font(unit_lbl, font_unit, LV_PART_MAIN);
    lv_obj_set_style_text_align(unit_lbl, LV_TEXT_ALIGN_LEFT, LV_PART_MAIN);
    lv_obj_align_to(info_lbl, value_lbl, LV_ALIGN_OUT_RIGHT_BOTTOM, 0, 0);

    ui->widgets.value_lbl = value_lbl;
    ui->widgets.info_lbl  = info_lbl;
    ui->widgets.unit_lbl  = unit_lbl;

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

ui_t *ui_init(obd_pid_cfg_t const *cfg, uint32_t interval_ms, ui_touch_callback_t touch_cb)
{
    ESP_NULL_CHECK(cfg, TAG, "OBD PID config is NULL");
    ESP_NULL_CHECK(touch_cb, TAG, "touch callback is NULL");

    ESP_LOGI(TAG, "Initializing UI...");

    ui_t *ui = malloc(sizeof(ui_t));
    ESP_NULL_CHECK(ui, TAG, "Failed to allocate memory for UI context");
    memset(ui, 0, sizeof(ui_t));
    ESP_LOGI(TAG, "ui pointer2: %p (addr: %p)", ui, (void *)&ui);

    ui->rtos.value_que    = xQueueCreate(1, sizeof(uint32_t));
    ui->rtos.touch_ev_que = xQueueCreate(4, sizeof(lv_event_code_t));
    ui->touch_cb          = touch_cb;

    if (!lvgl_port_lock(portMAX_DELAY))
    {
        ESP_LOGE(TAG, "Failed to lock LVGL port");
        free(ui);
        return NULL;
    }

    ui_init_screen(ui, cfg, interval_ms);

    lvgl_port_unlock();

    ESP_LOGI(TAG, "UI initialized successfully");

    return ui;
}

void ui_set_value(ui_t *ui, int32_t const *value)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

    int32_t set_value = value != NULL ? *value : DISPLAY_VALUE_INVALID;

    if (xQueueOverwrite(ui->rtos.value_que, &set_value) != pdTRUE)
    {
        ESP_LOGE(TAG, "Failed to overwrite value in queue");
    }
}

void ui_set_obd_cfg(ui_t *ui, obd_pid_cfg_t const *cfg)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    ESP_NULL_CHECK(cfg, TAG, "OBD PID config is NULL");

    ui_update_screen(ui, NULL, cfg->name, cfg->unit);
    ESP_LOGI(TAG, "Updated OBD PID config: 0x%02X (%s)", cfg->pid, cfg->name);
}
