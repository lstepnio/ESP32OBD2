#include "worker_health.h"
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
#include "display_showcase.h"
#include "adapter_status.h"
#include "transfer_gate.h"
#include "util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "UI";

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct {
    uint16_t pid;
    uint8_t source;
    bool occupied;
    uint32_t generation;
    double value;
    TickType_t received_at;
    bool present;
} ui_sample_t;

/* Replaces the 32-entry sample FIFO, rather than adding another allocation.
 * Latest values survive navigation; timestamps and source sessions do not. */
#define UI_SAMPLE_CAPACITY 32
_Static_assert(EGAUGE_RUNTIME_PIDS <= UI_SAMPLE_CAPACITY, "UI cache must cover the configured PID catalog");
static portMUX_TYPE sample_mux = portMUX_INITIALIZER_UNLOCKED;

typedef struct {
    uint8_t severity;
    uint8_t additional;
    bool simulated;
    bool attention;
    uint16_t key;
    uint32_t boot,episode;
    float value,limit;
    char unit[12];
    bool unavailable;
    char label[32];
} ui_alert_t;

typedef struct {
    bool valid;
    bool mil_on;
    bool mil_known;
    bool transmission;
    uint8_t count;
    bool count_known, count_partial;
    char first_code[6];
} ui_diagnostics_t;

typedef struct { bool enabled, vehicle_ready, maintenance; uint8_t seconds; uint32_t sequence; } showcase_request_t;

struct _ui_t
{
    ui_touch_callback_t touch_cb;
    bool                long_press_handled;
    bool                press_active;
    bool suppress_action_click;
    bool touch_moved, motion_click_allowed;
    int16_t touch_x, touch_y;
    page_action_t action;
    atomic_bool action_allowed, action_pending;
    atomic_uint action_context_epoch;
    unsigned action_seen_epoch;
    uint32_t action_feedback_ms;
    bool action_feedback, action_feedback_cooldown;
    bool rendered_action_visible;
    lv_obj_t *action_label;
    bool                pairing_visible;
    uint32_t            pairing_code;
    TickType_t          reset_confirmation_at;
    TickType_t          owner_saved_at;
    char                pairing_identifier[7];
    bool                calibration_mode;
    bool                imperial_units;
    uint8_t             calibration_page;
    TickType_t          pressed_at;
    ui_alert_t          rendered_alert;
    ui_diagnostics_t    rendered_diagnostics;
    bool                alert_rendered;
    bool                diagnostics_rendered;
    bool status_dirty, simulated;
    display_showcase_t showcase;
    atomic_bool showcase_active;
    atomic_uint showcase_generation, showcase_sequence, showcase_packed, showcase_started;
    ui_page_t showcase_page;
    unsigned page_index, page_count;
#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
    struct {
        int64_t click_at_us;
        int64_t reported_at_us;
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

    struct
    {
        ui_page_t page;
        ui_sample_t samples[UI_SAMPLE_CAPACITY];
        double rendered_values[2];
        bool rendered_available[2];
        bool rendered_once;
        int rendered_offset;
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
extern const lv_font_t notosans_semibold_48;
extern const lv_font_t notosans_semibold_40;
extern const lv_font_t notosans_medium_16;
extern const lv_font_t notosans_medium_24;

static const lv_font_t *const font_title    = &notosans_semibold_64;
static const lv_font_t *const font_compact  = &notosans_semibold_32;
static const lv_font_t *const font_large = &notosans_semibold_48;
static const lv_font_t *const font_dual = &notosans_semibold_40;
static const lv_font_t *const font_subtitle = &notosans_medium_16;
static const lv_font_t *const font_unit     = &notosans_medium_24;

static const uint32_t color_background = 0x05080A;
static const uint32_t color_text_primary = 0xE4EAED;
static const uint32_t color_text_secondary = 0x9CAAB2;
static const uint32_t color_track = 0x1B2A30;
static const uint32_t color_accent = 0x00FF70;
static const uint32_t color_warning = 0xFF8A00;
static const uint32_t color_critical = 0xFF1744;

// Keep essential glyphs 8 px from the circle; decoration retains a 4 px inset.
// Physical bezel clearance is qualified separately from native render bounds.
#define GAUGE_ARC_DIAMETER (2 * GAUGE_DECORATION_RADIUS)
#define GAUGE_ARC_WIDTH 11

#define CALIBRATION_PAGE_COUNT 5
#define PRIMARY_LABEL_WIDTH 160
#define PRIMARY_VALUE_WIDTH 176
#define PRIMARY_UNIT_WIDTH 156

static void showcase_publish(ui_t *ui);

static const ui_page_t *presented_page(const ui_t *ui) {
    return atomic_load(&ui->showcase_active) ? &ui->showcase_page : &ui->display.page;
}
static uint32_t glyph_next(const char **p) {
    const unsigned char *s=(const unsigned char *)*p;
    if(!*s)return 0;
    uint32_t cp=*s++;unsigned n=0;
    if(cp>=0xc2 && cp<=0xdf){cp&=31;n=1;}
    else if(cp>=0xe0 && cp<=0xef){cp&=15;n=2;}
    else if(cp>=0xf0 && cp<=0xf4){cp&=7;n=3;}
    else if(cp>=0x80)return UINT32_MAX;
    for(unsigned i=0;i<n;i++){if((*s&0xc0)!=0x80)return UINT32_MAX;cp=(cp<<6)|(*s++&63);}
    *p=(const char *)s;return cp;
}
typedef struct { int left,top,right,bottom,advance; } ink_t;
static bool ink(const char *text,const lv_font_t *font,ink_t *box) {
    *box=(ink_t){.left=10000,.top=10000,.right=-10000,.bottom=-10000};
    int x=0;bool any=false;
    for(const char *p=text;*p;){
        uint32_t cp=glyph_next(&p);const char *look=p;uint32_t next=glyph_next(&look);
        lv_font_glyph_dsc_t g;
        if(cp==UINT32_MAX || next==UINT32_MAX || !lv_font_get_glyph_dsc(font,&g,cp,next) || g.is_placeholder)return false;
        if(g.box_w&&g.box_h){
            int left=x+g.ofs_x,top=font->line_height-font->base_line-g.box_h-g.ofs_y;
            if(left<box->left)box->left=left;
            if(top<box->top)box->top=top;
            if(left+g.box_w>box->right)box->right=left+g.box_w;
            if(top+g.box_h>box->bottom)box->bottom=top+g.box_h;
            any=true;
        }
        x+=g.adv_w;
    }
    box->advance=x;
    if(!any)box->left=box->top=box->right=box->bottom=0;
    return true;
}
static bool label_at(lv_obj_t *label,const char *text,const lv_font_t *font,int cx,int cy,int maximum) {
    ink_t b;
    if(!ink(text,font,&b))return false;
    int w=b.right-b.left,h=b.bottom-b.top;
    if(w>maximum || !gauge_layout_fits(cx-(w+1)/2,cy-(h+1)/2,cx+(w+1)/2,cy+(h+1)/2))return false;
    lv_obj_set_style_text_font(label,font,0);
    lv_obj_set_style_text_align(label,LV_TEXT_ALIGN_LEFT,0);
    lv_label_set_long_mode(label,LV_LABEL_LONG_CLIP);
    lv_label_set_text(label,text);
    lv_obj_set_style_pad_left(label,4,0);
    lv_obj_set_size(label,b.advance+8,font->line_height);
    lv_obj_set_pos(label,cx-w/2-b.left-4,cy-h/2-b.top);
    return true;
}
static void small_label(lv_obj_t *label,const char *text,int cx,int cy,int maximum) {
    if(label_at(label,text,font_subtitle,cx,cy,maximum))return;
    char shortened[64];snprintf(shortened,sizeof(shortened),"%s",text);
    size_t n=strlen(shortened);
    while(n>0){
        do {n--;} while(n>0&&((unsigned char)shortened[n]&0xc0)==0x80);
        shortened[n]=0;char attempt[68];snprintf(attempt,sizeof(attempt),"%s...",shortened);
        if(label_at(label,attempt,font_subtitle,cx,cy,maximum))return;
    }
    label_at(label,"Reading",font_subtitle,cx,cy,maximum);
}
static const lv_font_t *fit_value(const char *text,int cx,int cy,int maximum,const lv_font_t *preferred) {
    const lv_font_t *fonts[]={font_title,font_large,font_dual,font_compact};bool admitted=false;
    for(unsigned i=0;i<4;i++){
        if(fonts[i]==preferred)admitted=true;
        if(!admitted)continue;
        ink_t b;if(!ink(text,fonts[i],&b))continue;
        int w=b.right-b.left,h=b.bottom-b.top;
        if(w<=maximum&&gauge_layout_fits(cx-(w+1)/2,cy-(h+1)/2,cx+(w+1)/2,cy+(h+1)/2))return fonts[i];
    }
    return NULL;
}
static void value_label(lv_obj_t *label,const char *text,int cx,int cy,int maximum,const lv_font_t *preferred) {
    const lv_font_t *font=fit_value(text,cx,cy,maximum,preferred);
    if(font)label_at(label,text,font,cx,cy,maximum);
    else small_label(label,"Value too wide",cx,cy,maximum);
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

static bool metric_value(ui_t *ui, unsigned index, double *value)
{
    if (index >= ui->display.page.metric_count) return false;
    const ui_metric_t *metric = &ui->display.page.metrics[index];
    ui_sample_t sample = {0};
    portENTER_CRITICAL(&sample_mux);
    for (unsigned i = 0; i < UI_SAMPLE_CAPACITY; ++i) {
        const ui_sample_t *entry = &ui->display.samples[i];
        if (entry->occupied && entry->source == metric->source && entry->pid == metric->pid) {
            sample = *entry;
            break;
        }
    }
    portEXIT_CRITICAL(&sample_mux);
    /* Adapter status has its own lock. Never nest it inside the cache lock. */
    if (!sample.present || !adapter_status_ready_for(metric->source) ||
        sample.generation != adapter_status_generation(metric->source)) return false;
    TickType_t age = xTaskGetTickCount() - sample.received_at;
    if (age > pdMS_TO_TICKS(metric->stale_after_ms)) return false;
    *value = sample.value;
    return true;
}

static void show(lv_obj_t *object, bool visible)
{
    if (!object) return;
    if (visible) lv_obj_remove_flag(object, LV_OBJ_FLAG_HIDDEN);
    else lv_obj_add_flag(object, LV_OBJ_FLAG_HIDDEN);
}

static void show_pairing(ui_t *ui, uint32_t pairing_code)
{
    ui->pairing_code = pairing_code;
    ui->pairing_visible = pairing_code != UI_PAIRING_HIDDEN;
    if (ui->pairing_visible) {
        ui->showcase.active=false;showcase_publish(ui);ui->display.rendered_once=false;ui->status_dirty=true;
        const char *title="Pair gauge",*body="";
        bool code=false,identifier=true;
        if (pairing_code == UI_PAIRING_READY) {
            body="Open eGauge App";
        } else if (pairing_code == UI_PAIRING_WAITING) {
            body="Hold to pair";
        } else if (pairing_code == UI_PAIRING_RESET_CONFIRM) {
            ui->reset_confirmation_at = xTaskGetTickCount();
            title="Forget phone?";body="Tap to confirm";identifier=false;
        } else if (pairing_code == UI_PAIRING_OWNER_SAVED) {
            ui->owner_saved_at = xTaskGetTickCount();
            title="Phone paired";identifier=false;
        } else {title="Pairing code";code=true;}
        label_at(ui->widgets.pairing_title,title,font_unit,120,75,174);
        small_label(ui->widgets.pairing_body,body,120,111,172);
        char digits[16];snprintf(digits,sizeof(digits),"%03" PRIu32 " %03" PRIu32,pairing_code/1000,pairing_code%1000);
        if(code)value_label(ui->widgets.pairing_code,digits,120,118,176,font_large);
        show(ui->widgets.pairing_body,!code);show(ui->widgets.pairing_code,code);
        char id[16];snprintf(id,sizeof(id),"ID %s",ui->pairing_identifier);
        small_label(ui->widgets.pairing_id,id,120,186,112);
        show(ui->widgets.pairing_id,identifier&&ui->pairing_identifier[0]);
    }
    show(ui->widgets.gauge_content, !ui->pairing_visible);
    show(ui->widgets.pairing_lbl, ui->pairing_visible);
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
    ESP_LOGI(TAG, "Display calibration page %u/%u", ui->calibration_page + 1,
             CALIBRATION_PAGE_COUNT);
}

static void select_renderer_widgets(ui_t *ui)
{
    ui_renderer_t renderer = presented_page(ui)->renderer;
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
        show(ui->widgets.dual_unit[i], dual);
    }
    show(ui->widgets.dual_divider, dual);
}

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void dispatch_touch(ui_t *ui, lv_event_code_t code)
{
    /* Calibration replaces screen objects, so defer that callback until the
     * current input event has finished. Normal navigation keeps its objects. */
    if (ui->calibration_mode) {
        xQueueSend(ui->rtos.touch_ev_que, &code, 0);
    } else if (ui->touch_cb &&
               (!(ui->pairing_visible && code == LV_EVENT_CLICKED) ||
                ui->pairing_code == UI_PAIRING_RESET_CONFIRM)) {
#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
        if (code == LV_EVENT_CLICKED && ui->timing.click_at_us == 0)
            ui->timing.click_at_us = esp_timer_get_time();
#endif
        ui->touch_cb(ui, code);
    }
}

static bool action_context(ui_t *ui)
{
    unsigned epoch = atomic_load(&ui->action_context_epoch);
    bool allowed = atomic_load(&ui->action_allowed) && !ui->pairing_visible && !ui->calibration_mode && !ui->rendered_alert.attention && !atomic_load(&ui->showcase_active);
    if (!allowed || epoch != ui->action_seen_epoch) {
        page_action_reset(&ui->action);
        ui->action_feedback = false;
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
#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
    if (code == LV_EVENT_PRESSED) ui->timing.presses++;
    if (code == LV_EVENT_RELEASED) ui->timing.releases++;
    if (code == LV_EVENT_CLICKED) ui->timing.clicks++;
    if (code == LV_EVENT_LONG_PRESSED) ui->timing.holds++;
#endif
    bool actions_allowed = action_context(ui);
    lv_point_t point = {0};
    lv_indev_t *input = lv_indev_active();
    if (input) lv_indev_get_point(input, &point);
    uint32_t now = (uint32_t)(esp_timer_get_time() / 1000);
    switch (code)
    {
    case LV_EVENT_PRESSED:
        ui->suppress_action_click = false;
        ui->touch_moved = false;
        ui->motion_click_allowed = !ui->action.config.enabled;
        ui->touch_x = point.x;
        ui->touch_y = point.y;
        if (actions_allowed && input) page_action_press(&ui->action, point.x, point.y, now);
        ui->press_active = true;
        ui->long_press_handled = false;
        ui->pressed_at = xTaskGetTickCount();
        dispatch_touch(ui, LV_EVENT_PRESSED);
        break;
    case LV_EVENT_PRESSING:
        if (input && (abs((int)point.x - ui->touch_x) > 12 || abs((int)point.y - ui->touch_y) > 12))
            ui->touch_moved = true;
        if (actions_allowed && input) page_action_move(&ui->action, point.x, point.y);
        break;
    case LV_EVENT_LONG_PRESSED:
        /* Motion belongs to a stroke, even when it will fail recognition.
         * A slow/diagonal stroke must never become a pairing hold. */
        if (ui->touch_moved) break;
        page_action_reset(&ui->action);
        ui->long_press_handled = true;
        dispatch_touch(ui, code);
        break;
    case LV_EVENT_CLICKED:
        if (!ui->long_press_handled && !ui->suppress_action_click && (!ui->touch_moved || ui->motion_click_allowed))
        {
            if (ui->pairing_code == UI_PAIRING_RESET_CONFIRM)
                dispatch_touch(ui, LV_EVENT_VALUE_CHANGED);
            else dispatch_touch(ui, code);
        }
        break;
    case LV_EVENT_RELEASED:
        if (!ui->press_active) {
            page_action_reset(&ui->action);
            ui->suppress_action_click = true;
            break;
        }
        if (input) {
            int dx = abs((int)point.x - ui->touch_x), dy = abs((int)point.y - ui->touch_y);
            /* Keep ordinary horizontal page browsing. A failed upward/diagonal
             * shortcut must not become that page click. Without a binding,
             * retain the existing swipe-to-browse behavior in all directions. */
            if (dx >= 32 && dx >= 2 * dy) ui->motion_click_allowed = true;
        }
        if (actions_allowed && input && !ui->long_press_handled) {
            bool fired = false;
            ui->suppress_action_click = page_action_release(&ui->action, point.x, point.y, now, &fired);
            if (fired) {
                dispatch_touch(ui, UI_EVENT_PAGE_ACTION);
            } else if (ui->suppress_action_click && ui->action.cooling) {
                ui->action_feedback = true;
                ui->action_feedback_cooldown = true;
                ui->action_feedback_ms = now;
            }
        } else page_action_reset(&ui->action);
        /* A release without our matching press must never clear the owner. */
        if (ui->press_active && !ui->touch_moved && xTaskGetTickCount() - ui->pressed_at >= pdMS_TO_TICKS(12000)) {
            dispatch_touch(ui, code);
        }
        ui->press_active = false;
        break;
    case LV_EVENT_PRESS_LOST:
        page_action_reset(&ui->action);
        ui->press_active = false;
        ui->touch_moved = true;
        ui->motion_click_allowed = false;
        ui->suppress_action_click = true;
        break;
    default:
        break;
    }
    /* Publish immediately so cycling cannot interrupt an in-flight stroke. */
    atomic_store(&ui->action_pending, ui->action.progress != 0 || ui->action.pressed);
}

static void format_reading(char out[32],const ui_metric_t *metric,bool available,double value,bool imperial) {
    if(!available){snprintf(out,32,"--");return;}
    if(!strcmp(metric->unit,"gear")) {
        const char *gear=transmission_gear_label(value);snprintf(out,32,"%s",gear?gear:"?");
    } else display_units_format(out,32,value,metric->unit,imperial);
}
static ui_alert_t visible_alert(ui_t *ui);
static ui_diagnostics_t visible_diagnostics(ui_t *ui);
static void render_page(ui_t *ui) {
    const ui_page_t *page=presented_page(ui);
    double values[2]={0};bool available[2]={false};
    bool demo=atomic_load(&ui->showcase_active);
    const display_showcase_frame_t *frame=demo?display_showcase_frame(ui->showcase.index):NULL;
    for(unsigned i=0;i<page->metric_count;i++) {
        if(frame){values[i]=frame->values[i];available[i]=frame->present[i];}
        else available[i]=metric_value(ui,i,&values[i]);
    }
    if(frame&&page->renderer==UI_RENDERER_TREND)values[0]+=2*sin((esp_timer_get_time()/1000000.0)*1.5);
    const ui_metric_t *primary=&page->metrics[0];
    ui_renderer_t renderer=page->renderer;
    ui_alert_t condition=visible_alert(ui);ui_diagnostics_t diagnostics=visible_diagnostics(ui);
    /* Owner's Round 3 optical correction, only for the no-CEL Numeric
     * condition view. CEL combinations and chart geometry remain unchanged. */
    int offset=renderer==UI_RENDERER_NUMERIC&&condition.severity&&!condition.attention&&
        !(diagnostics.valid&&(diagnostics.mil_on||diagnostics.count))?-12:0;
    if(!ui->display.rendered_once)select_renderer_widgets(ui);
    if(renderer==UI_RENDERER_DUAL) {
        for(unsigned i=0;i<2;i++) {
            if(ui->display.rendered_once&&available[i]==ui->display.rendered_available[i]&&(!available[i]||values[i]==ui->display.rendered_values[i]))continue;
            const ui_metric_t *metric=&page->metrics[i];char text[32];
            format_reading(text,metric,available[i],values[i],ui->imperial_units);
            const char *label=gauge_layout_label(metric->name,metric->unit);
            const char *unit=gauge_layout_unit(label,display_units_label(metric->unit,ui->imperial_units));
            small_label(ui->widgets.dual_info[i],label,120,i?135:72,184);
            int cy=i?160:97;ink_t u;
            if(!ink(unit,font_subtitle,&u)){unit="Unit?";ink(unit,font_subtitle,&u);}
            int uw=u.right-u.left;
            const lv_font_t *preferred=font_dual;
            if(ui->display.rendered_once){const lv_font_t *previous=lv_obj_get_style_text_font(ui->widgets.dual_value[i],0);if(previous==font_dual||previous==font_compact)preferred=previous;}
            const lv_font_t *font=fit_value(text,120-(uw?uw/2+3:0),cy,192-(uw?uw+6:0),preferred);
            bool placed=false;
            if(font){
                ink_t v;ink(text,font,&v);int vw=v.right-v.left;
                placed=label_at(ui->widgets.dual_value[i],text,font,120-(uw?uw/2+3:0),cy,192-(uw?uw+6:0));
                if(uw)placed=label_at(ui->widgets.dual_unit[i],unit,font_subtitle,120+vw/2+3,cy+7,48)&&placed;
            }
            if(!placed)small_label(ui->widgets.dual_value[i],"Value too wide",120,cy,166);
            show(ui->widgets.dual_unit[i],uw&&placed);
            lv_obj_set_style_text_color(ui->widgets.dual_value[i],lv_color_hex(available[i]?color_text_primary:color_text_secondary),0);
        }
    } else if(!ui->display.rendered_once||offset!=ui->display.rendered_offset||available[0]!=ui->display.rendered_available[0]||(available[0]&&values[0]!=ui->display.rendered_values[0])) {
        char text[32];format_reading(text,primary,available[0],values[0],ui->imperial_units);
        const char *label=gauge_layout_label(page->name?page->name:primary->name,primary->unit);
        const char *unit=gauge_layout_unit(label,display_units_label(primary->unit,ui->imperial_units));
        int title_y=renderer==UI_RENDERER_BAR?70:renderer==UI_RENDERER_TREND?72:87;
        int value_y=renderer==UI_RENDERER_BAR?108:renderer==UI_RENDERER_TREND?102:126;
        int unit_y=renderer==UI_RENDERER_BAR?147:renderer==UI_RENDERER_TREND?132:169;
        title_y+=offset;value_y+=offset;unit_y+=offset;
        small_label(ui->widgets.info_lbl,label,120,title_y,184);
        const lv_font_t *preferred=renderer==UI_RENDERER_TREND?font_large:font_title;
        if(ui->display.rendered_once){
            const lv_font_t *previous=lv_obj_get_style_text_font(ui->widgets.value_lbl,0);
            if(previous==font_title||previous==font_large||previous==font_dual||previous==font_compact)preferred=previous;
        }
        value_label(ui->widgets.value_lbl,text,120,value_y,192,preferred);
        if(renderer==UI_RENDERER_TREND)small_label(ui->widgets.unit_lbl,unit,120,unit_y,156);
        else if(!label_at(ui->widgets.unit_lbl,unit,font_unit,120,unit_y,156))small_label(ui->widgets.unit_lbl,"Unit?",120,unit_y,140);
        lv_obj_set_style_text_color(ui->widgets.value_lbl,lv_color_hex(available[0]?color_text_primary:color_text_secondary),0);
        if(renderer==UI_RENDERER_ARC)lv_arc_set_value(ui->widgets.arc,available[0]?normalized_value(values[0],primary,1000):0);
        if(renderer==UI_RENDERER_BAR)lv_bar_set_value(ui->widgets.bar,available[0]?normalized_value(values[0],primary,1000):0,LV_ANIM_OFF);
    }
    if(renderer==UI_RENDERER_TREND) {
        if(!available[0])ui->display.trend_count=0;
        else if(xTaskGetTickCount()-ui->display.trend_recorded_at>=pdMS_TO_TICKS(500)) {
            ui->display.trend_recorded_at=xTaskGetTickCount();
            if(ui->display.trend_count<60)ui->display.trend_count++;
            memmove(ui->display.trend_points,ui->display.trend_points+1,59*sizeof(ui->display.trend_points[0]));
            for(unsigned i=0;i<60;i++)ui->display.trend_points[i].x=(int32_t)i*144/59;
            ui->display.trend_points[59].y=30-normalized_value(values[0],primary,30);
            lv_line_set_points(ui->widgets.trend,ui->display.trend_points+60-ui->display.trend_count,ui->display.trend_count);
        }
        show(ui->widgets.trend,available[0]&&ui->display.trend_count>=2);
    }
    for(unsigned i=0;i<2;i++){if(available[i]!=ui->display.rendered_available[i])ui->status_dirty=true;ui->display.rendered_values[i]=values[i];ui->display.rendered_available[i]=available[i];}
    ui->display.rendered_once=true;ui->display.rendered_offset=offset;
}
static void showcase_publish(ui_t *ui) {
    atomic_fetch_add(&ui->showcase_generation,1);
    atomic_store(&ui->showcase_active,ui->showcase.active);
    atomic_store(&ui->showcase_sequence,ui->showcase.sequence);
    atomic_store(&ui->showcase_packed,ui->showcase.index|((unsigned)ui->showcase.seconds<<8)|((unsigned)ui->showcase.result<<16));
    atomic_store(&ui->showcase_started,ui->showcase.started);
    atomic_fetch_add(&ui->showcase_generation,1);
}
static void showcase_frame(ui_t *ui) {
    const display_showcase_frame_t *f=display_showcase_frame(ui->showcase.index);
    ui->showcase_page=(ui_page_t){.name=f->name,.renderer=f->renderer,.metric_count=f->metrics};
    for(unsigned i=0;i<f->metrics;i++)ui->showcase_page.metrics[i]=(ui_metric_t){.pid=0xd000+i,.name=f->labels[i],.unit=f->units[i],.minimum=f->minimum,.maximum=f->maximum,.stale_after_ms=2000};
    ui->display.rendered_once=false;ui->display.trend_count=0;ui->status_dirty=true;
    if(ui->showcase.active&&f->renderer==UI_RENDERER_TREND) {
        for(unsigned i=0;i<60;i++) {
            double sample=f->values[0]+4*sin(i*.24)+2*sin(i*.07);
            ui->display.trend_points[i]=(lv_point_precise_t){.x=(int)i*144/59,.y=30-normalized_value(sample,&ui->showcase_page.metrics[0],30)};
        }
        ui->display.trend_count=60;lv_line_set_points(ui->widgets.trend,ui->display.trend_points,60);
        ui->display.trend_recorded_at=xTaskGetTickCount();
    }
    showcase_publish(ui);
}

#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
static void render_finished(lv_event_t *event)
{
    ui_t *ui = lv_event_get_user_data(event);
    if (!ui->timing.click_at_us) return;
    uint32_t elapsed = (uint32_t)(esp_timer_get_time() - ui->timing.click_at_us);
    ui->timing.count++;
    ui->timing.total_us += elapsed;
    if (elapsed > ui->timing.maximum_us) ui->timing.maximum_us = elapsed;
    ui->timing.click_at_us = 0;
}
#endif

static const lv_point_precise_t engine_outline[]={{5,4},{13,4},{13,1},{22,1},{22,5},{29,5},{29,17},{22,17},{19,20},{11,20},{8,16},{5,16},{5,4}};
static const lv_point_precise_t engine_pipe[]={{1,7},{1,15}};
static const lv_point_precise_t engine_join[]={{1,11},{5,11}};
static const lv_point_precise_t wrench_handle[]={{7,18},{21,4}};
static const lv_point_precise_t wrench_head[]={{21,4},{25,7},{21,12},{17,8},{20,3}};
static const lv_point_precise_t warning_outline[]={{10,1},{19,18},{1,18},{10,1}};
static const lv_point_precise_t critical_outline[]={{10,1},{19,10},{10,19},{1,10},{10,1}};
static const lv_point_precise_t warning_stem[]={{10,6},{10,11}};
static const lv_point_precise_t warning_dot[]={{10,14},{10,16}};
static const lv_point_precise_t arrow_up[]={{4,14},{4,1},{1,5},{4,1},{7,5}};
static const lv_point_precise_t arrow_down[]={{4,1},{4,14},{1,10},{4,14},{7,10}};
static lv_obj_t *plain_view(lv_obj_t *parent,int width,int height) {
    lv_obj_t *view=lv_obj_create(parent);lv_obj_remove_style_all(view);lv_obj_set_size(view,width,height);
    lv_obj_clear_flag(view,LV_OBJ_FLAG_CLICKABLE|LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(view,LV_OBJ_FLAG_EVENT_BUBBLE);return view;
}
static lv_obj_t *plain_label(lv_obj_t *parent,uint32_t color) {
    lv_obj_t *label=lv_label_create(parent);lv_obj_remove_style_all(label);
    lv_obj_set_style_text_color(label,lv_color_hex(color),0);
    lv_obj_clear_flag(label,LV_OBJ_FLAG_CLICKABLE|LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(label,LV_OBJ_FLAG_EVENT_BUBBLE);return label;
}
static lv_obj_t *icon_create(lv_obj_t *parent,bool diagnostic) {
    lv_obj_t *icon=plain_view(parent,diagnostic?32:22,22);
    for(unsigned i=0;i<3;i++) {
        lv_obj_t *line=lv_line_create(icon);lv_obj_remove_style_all(line);
        lv_obj_set_style_line_width(line,2,0);lv_obj_set_style_line_rounded(line,true,0);
        lv_obj_clear_flag(line,LV_OBJ_FLAG_CLICKABLE);lv_obj_add_flag(line,LV_OBJ_FLAG_EVENT_BUBBLE);
    }
    return icon;
}
static void icon_style(lv_obj_t *icon,unsigned kind,uint32_t color) {
    lv_obj_t *a=lv_obj_get_child(icon,0),*b=lv_obj_get_child(icon,1),*c=lv_obj_get_child(icon,2);
    lv_obj_set_style_line_color(a,lv_color_hex(color),0);lv_obj_set_style_line_color(b,lv_color_hex(color),0);lv_obj_set_style_line_color(c,lv_color_hex(color),0);
    show(c,kind!=1);
    if(kind==0){lv_line_set_points(a,engine_outline,13);lv_line_set_points(b,engine_pipe,2);lv_line_set_points(c,engine_join,2);}
    else if(kind==1){lv_line_set_points(a,wrench_handle,2);lv_line_set_points(b,wrench_head,5);}
    else {lv_line_set_points(a,kind==3?critical_outline:warning_outline,kind==3?5:4);lv_line_set_points(b,warning_stem,2);lv_line_set_points(c,warning_dot,2);}
}
static ui_alert_t visible_alert(ui_t *ui) {
    if(!atomic_load(&ui->showcase_active))return ui->rendered_alert;
    const display_showcase_frame_t *f=display_showcase_frame(ui->showcase.index);
    ui_alert_t alert={.severity=f->severity,.attention=f->attention,.unavailable=f->last_seen,.value=f->values[0],.limit=110,.simulated=true};
    snprintf(alert.label,sizeof(alert.label),"%s",f->condition?f->condition:"");
    const char *unit=f->units[0];
    if(f->condition&&(!strcmp(f->condition,"Coolant")||!strcmp(f->condition,"ATF"))){unit="°C";alert.value=f->last_seen?92:f->severity==2?118:112;}
    snprintf(alert.unit,sizeof(alert.unit),"%s",unit);return alert;
}
static ui_diagnostics_t visible_diagnostics(ui_t *ui) {
    if(!atomic_load(&ui->showcase_active))return ui->rendered_diagnostics;
    const display_showcase_frame_t *f=display_showcase_frame(ui->showcase.index);
    return (ui_diagnostics_t){.valid=true,.mil_known=true,.mil_on=f->mil,.count=f->faults,.count_known=f->count_known};
}
static void render_status(ui_t *ui) {
    ui_alert_t alert=visible_alert(ui);ui_diagnostics_t d=visible_diagnostics(ui);
    uint32_t color=alert.severity==2?color_critical:alert.severity==1?color_warning:color_accent;
    uint32_t chart_color=ui->display.rendered_available[0]?color:color_track;
    lv_obj_set_style_arc_color(ui->widgets.arc,lv_color_hex(chart_color),LV_PART_INDICATOR);
    lv_obj_set_style_bg_color(ui->widgets.bar,lv_color_hex(chart_color),LV_PART_INDICATOR);
    lv_obj_set_style_line_color(ui->widgets.trend,lv_color_hex(color),LV_PART_MAIN);
    bool diagnostic=d.valid&&(d.mil_on||d.count);
    show(ui->widgets.diagnostics_icon,diagnostic);show(ui->widgets.diagnostics_lbl,diagnostic);
    if(diagnostic) {
        char count[12]="";
        if(!d.count_known||(d.count_partial&&!d.count))snprintf(count,sizeof(count),"?");
        else if(d.count>9)snprintf(count,sizeof(count),"9+");
        else if(d.count_partial)snprintf(count,sizeof(count),"%u+",d.count);
        else if(d.count>1)snprintf(count,sizeof(count),"%u",d.count);
        uint32_t dc=d.mil_on?color_warning:color_text_secondary;
        icon_style(ui->widgets.diagnostics_icon,d.mil_on?0:1,dc);
        lv_obj_set_pos(ui->widgets.diagnostics_icon,count[0]?94:104,31);
        lv_obj_set_style_text_color(ui->widgets.diagnostics_lbl,lv_color_hex(dc),0);
        small_label(ui->widgets.diagnostics_lbl,count,139,42,34);
    }
    bool condition=alert.severity!=0;
    show(ui->widgets.alert_lbl,condition);show(ui->widgets.alert_icon,condition);
    show(ui->widgets.alert_arrow,false);
    if(condition) {
        char alias[32],text[48];gauge_layout_alert_alias(alias,sizeof(alias),alert.label);
        if(alert.unavailable)snprintf(text,sizeof(text),"%s last seen",alias);
        else if(alert.additional)snprintf(text,sizeof(text),"%s +%u%s",alias,alert.additional>9?9:alert.additional,alert.additional>9?"+":"");
        else snprintf(text,sizeof(text),"%s",alias);
        ink_t bounds;bool measured=ink(text,font_subtitle,&bounds);
        bool direction=!alert.unavailable&&alert.key<32&&alert.unit[0]&&strcmp(alert.unit,"gear");
        int width=measured?bounds.right-bounds.left:10000,total=22+5+width+(direction?13:0);
        if(total>124) {
            snprintf(text,sizeof(text),"%s",alert.unavailable?"Last seen":"Alert");ink(text,font_subtitle,&bounds);
            width=bounds.right-bounds.left;total=27+width+(direction?13:0);
        }
        int x=120-total/2;icon_style(ui->widgets.alert_icon,alert.severity==2?3:2,color);
        lv_obj_set_pos(ui->widgets.alert_icon,x,183);x+=27;
        lv_obj_set_style_text_color(ui->widgets.alert_lbl,lv_color_hex(color),0);
        small_label(ui->widgets.alert_lbl,text,x+width/2,194,width+1);x+=width;
        if(direction) {
            lv_line_set_points(ui->widgets.alert_arrow,alert.value<alert.limit?arrow_down:arrow_up,5);
            lv_obj_set_style_line_color(ui->widgets.alert_arrow,lv_color_hex(color),0);lv_obj_set_pos(ui->widgets.alert_arrow,x+5,187);show(ui->widgets.alert_arrow,true);
        }
    }
    bool demo=atomic_load(&ui->showcase_active)||ui->simulated||alert.simulated;
    show(ui->widgets.demo_lbl,demo&&!atomic_load(&ui->showcase_active));
    bool position=!condition&&!demo&&!alert.attention&&!ui->rendered_action_visible;
    for(unsigned i=0;i<8;i++) {
        bool dot=position&&ui->page_count<=8&&ui->page_count>1&&i<ui->page_count;
        show(ui->widgets.page_dots[i],dot);
        if(dot){lv_obj_set_pos(ui->widgets.page_dots[i],120-(int)ui->page_count*4+(int)i*8+2,210);lv_obj_set_style_bg_color(ui->widgets.page_dots[i],lv_color_hex(i==ui->page_index?color_text_primary:color_track),0);}
    }
    show(ui->widgets.page_position,position&&ui->page_count>8);
    if(position&&ui->page_count>8){char p[24];snprintf(p,sizeof(p),"%u/%u",ui->page_index+1,ui->page_count);small_label(ui->widgets.page_position,p,120,213,48);}
    bool overlay=alert.attention&&!ui->pairing_visible&&!ui->calibration_mode;
    show(ui->widgets.alert_overlay,overlay);
    if(overlay) {
        char value[24],limit[24],reading[48],detail[48];
        display_units_format(value,sizeof(value),alert.value,alert.unit,ui->imperial_units);
        display_units_format(limit,sizeof(limit),alert.limit,alert.unit,ui->imperial_units);
        const char *unit=display_units_label(alert.unit,ui->imperial_units);
        snprintf(reading,sizeof(reading),"%s %s",alert.unavailable?"--":value,unit);
        snprintf(detail,sizeof(detail),"%s %s %s",alert.unavailable?"Last seen":"Limit",alert.unavailable?value:limit,unit);
        small_label(ui->widgets.attention_title,"CRITICAL",120,48,110);
        char alias[32];gauge_layout_alert_alias(alias,sizeof(alias),alert.label);
        small_label(ui->widgets.alert_overlay_label,alias,120,77,166);
        value_label(ui->widgets.attention_value,reading,120,118,176,font_large);
        small_label(ui->widgets.attention_detail,detail,120,151,160);
        lv_obj_move_foreground(ui->widgets.alert_overlay);
        if(demo)lv_obj_move_foreground(ui->widgets.demo_lbl);
    }
    ui->status_dirty=false;
}
static void render_action_feedback(ui_t *ui)
{
    bool visible = action_context(ui) && !ui->simulated && (ui->action.progress || ui->action_feedback);
    if (visible != ui->rendered_action_visible) ui->status_dirty = true;
    if (visible) {
        char feedback[16];
        if (ui->action_feedback) snprintf(feedback, sizeof(feedback), "%s", ui->action_feedback_cooldown ? "Wait" : "Done");
        else snprintf(feedback, sizeof(feedback), "%u/%u", ui->action.progress, ui->action.config.count);
        small_label(ui->action_label, feedback, 120, 213, 80);
    }
    show(ui->action_label, visible);
    ui->rendered_action_visible = visible;
}

static void acknowledge_alert(lv_event_t *event)
{
    if(lv_event_get_code(event)!=LV_EVENT_CLICKED)return;
    lv_event_stop_bubbling(event);
    ui_t *ui=lv_event_get_user_data(event);
    if(!ui)return;
    if(atomic_load(&ui->showcase_active)){ui_showcase_next(ui);return;}
    if(!ui->rendered_alert.episode)return;
    ui_alert_t chosen=ui->rendered_alert;
    uint8_t command[16]={0x3d,(uint8_t)chosen.key};
    memcpy(command+4,&chosen.episode,4);memcpy(command+8,&chosen.boot,4);
    alert_runtime_command(command,sizeof(command));
}

static void ui_task(lv_timer_t *timer)
{
    ESP_NULL_CHECK(timer, TAG, "timer is NULL");
    worker_health_progress(WORKER_UI, (uint32_t)(esp_timer_get_time() / 1000));
    ui_t *ui = (ui_t *)lv_timer_get_user_data(timer);
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");

#if CONFIG_EGAUGE_UI_PERFORMANCE_LOG
    int64_t now_us = esp_timer_get_time();
    if ((ui->timing.presses || ui->timing.count) && now_us - ui->timing.reported_at_us >= 30000000) {
        ESP_LOGI(TAG, "performance touch_frames=%" PRIu32 " mean_submit_us=%" PRIu64
                 " max_submit_us=%" PRIu32 " stack_free=%u"
                 " presses=%" PRIu32 " releases=%" PRIu32 " clicks=%" PRIu32 " holds=%" PRIu32,
                 ui->timing.count, ui->timing.count ? ui->timing.total_us / ui->timing.count : 0,
                 ui->timing.maximum_us, (unsigned)uxTaskGetStackHighWaterMark(NULL),
                 ui->timing.presses, ui->timing.releases, ui->timing.clicks, ui->timing.holds);
        ui->timing.count = 0;
        ui->timing.total_us = 0;
        ui->timing.maximum_us = 0;
        ui->timing.presses = 0;
        ui->timing.releases = 0;
        ui->timing.clicks = 0;
        ui->timing.holds = 0;
        ui->timing.reported_at_us = now_us;
    }
#endif

    action_context(ui);
    uint32_t action_now = (uint32_t)(esp_timer_get_time() / 1000);
    page_action_tick(&ui->action, action_now);
    atomic_store(&ui->action_pending, ui->action.progress != 0 || ui->action.pressed);
    if (ui->action_feedback && (action_now - ui->action_feedback_ms >= 2000 ||
        (ui->action_feedback_cooldown && !ui->action.cooling))) ui->action_feedback = false;
    if (ui->calibration_mode) {
        lv_event_code_t event_code;
        if (xQueueReceive(ui->rtos.touch_ev_que, &event_code, 0) == pdTRUE && ui->touch_cb)
            ui->touch_cb(ui, event_code);
        return;
    }

    char pairing_identifier[sizeof(ui->pairing_identifier)];
    if (xQueueReceive(ui->rtos.pairing_id_que, pairing_identifier, 0) == pdTRUE) {
        memcpy(ui->pairing_identifier, pairing_identifier, sizeof(ui->pairing_identifier));
        ui->pairing_identifier[sizeof(ui->pairing_identifier) - 1] = '\0';
        if (ui->pairing_visible) show_pairing(ui, ui->pairing_code);
    }

    uint32_t pairing_code;
    if (xQueueReceive(ui->rtos.pairing_que, &pairing_code, 0) == pdTRUE) {
        /* Repeated hidden status from phone reconnects is not a context change. */
        if (pairing_code != ui->pairing_code) page_action_reset(&ui->action);
        show_pairing(ui, pairing_code);
    }
    if (ui->pairing_code == UI_PAIRING_RESET_CONFIRM &&
        xTaskGetTickCount() - ui->reset_confirmation_at >= pdMS_TO_TICKS(30000)) {
        show_pairing(ui, UI_PAIRING_HIDDEN);
    }
    if (ui->pairing_code == UI_PAIRING_OWNER_SAVED &&
        xTaskGetTickCount() - ui->owner_saved_at >= pdMS_TO_TICKS(2500)) {
        show_pairing(ui, UI_PAIRING_HIDDEN);
    }

    ui_alert_t alert;
    if(xQueueReceive(ui->rtos.alert_que,&alert,0)==pdTRUE && memcmp(&ui->rendered_alert,&alert,sizeof(alert))) {
        ui->rendered_alert=alert;ui->alert_rendered=true;ui->status_dirty=true;
    }
    ui_diagnostics_t diagnostics;
    if(xQueueReceive(ui->rtos.diagnostics_que,&diagnostics,0)==pdTRUE && memcmp(&ui->rendered_diagnostics,&diagnostics,sizeof(diagnostics))) {
        ui->rendered_diagnostics=diagnostics;ui->diagnostics_rendered=true;ui->status_dirty=true;
    }
    uint32_t now=(uint32_t)(esp_timer_get_time()/1000);
    showcase_request_t request;
    if(xQueueReceive(ui->rtos.showcase_que,&request,0)==pdTRUE) {
        if(display_showcase_apply(&ui->showcase,request.enabled,request.seconds,request.sequence,now,
           request.vehicle_ready||adapter_status_ready_for(0)||adapter_status_ready_for(1),
           request.maintenance||transfer_gate_current()!=0,ui->rendered_alert.attention||ui->pairing_visible))showcase_frame(ui);
    }
    if(display_showcase_tick(&ui->showcase,now,ui->pairing_visible||ui->rendered_alert.attention||transfer_gate_current()!=0||adapter_status_ready_for(0)||adapter_status_ready_for(1)))showcase_frame(ui);

    render_page(ui);
    render_action_feedback(ui);
    if(ui->status_dirty)render_status(ui);

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

    ui->widgets.value_lbl=plain_label(gauge_content,color_text_primary);
    ui->widgets.info_lbl=plain_label(gauge_content,color_text_secondary);
    ui->widgets.unit_lbl=plain_label(gauge_content,color_text_secondary);
    lv_obj_t *arc=lv_arc_create(gauge_content);
    lv_obj_set_size(arc,GAUGE_ARC_DIAMETER,GAUGE_ARC_DIAMETER);lv_obj_align(arc,LV_ALIGN_CENTER,0,0);
    lv_arc_set_bg_angles(arc,135,45);lv_obj_remove_style(arc,NULL,LV_PART_KNOB);lv_obj_clear_flag(arc,LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_style_arc_width(arc,GAUGE_ARC_WIDTH,LV_PART_MAIN);lv_obj_set_style_arc_width(arc,GAUGE_ARC_WIDTH,LV_PART_INDICATOR);
    lv_obj_set_style_arc_rounded(arc,true,LV_PART_MAIN);lv_obj_set_style_arc_rounded(arc,true,LV_PART_INDICATOR);
    lv_obj_set_style_arc_color(arc,lv_color_hex(color_track),LV_PART_MAIN);lv_obj_set_style_arc_color(arc,lv_color_hex(color_accent),LV_PART_INDICATOR);ui->widgets.arc=arc;
    lv_obj_t *bar=lv_bar_create(gauge_content);lv_obj_remove_style_all(bar);
    lv_obj_set_size(bar,140,6);lv_obj_set_pos(bar,50,174);
    lv_obj_set_style_bg_opa(bar,LV_OPA_COVER,LV_PART_MAIN);lv_obj_set_style_bg_color(bar,lv_color_hex(color_track),LV_PART_MAIN);
    lv_obj_set_style_bg_opa(bar,LV_OPA_COVER,LV_PART_INDICATOR);lv_obj_set_style_bg_color(bar,lv_color_hex(color_accent),LV_PART_INDICATOR);
    lv_obj_set_style_radius(bar,3,LV_PART_MAIN);lv_obj_set_style_radius(bar,3,LV_PART_INDICATOR);lv_obj_clear_flag(bar,LV_OBJ_FLAG_CLICKABLE);ui->widgets.bar=bar;
    lv_obj_t *trend=lv_line_create(gauge_content);lv_obj_remove_style_all(trend);
    lv_obj_set_pos(trend,48,146);lv_obj_set_size(trend,144,32);
    lv_obj_set_style_line_color(trend,lv_color_hex(color_accent),0);lv_obj_set_style_line_width(trend,3,0);lv_obj_clear_flag(trend,LV_OBJ_FLAG_CLICKABLE);ui->widgets.trend=trend;
    for(unsigned i=0;i<2;i++) {
        ui->widgets.dual_value[i]=plain_label(gauge_content,color_text_primary);
        ui->widgets.dual_info[i]=plain_label(gauge_content,color_text_secondary);
        ui->widgets.dual_unit[i]=plain_label(gauge_content,color_text_secondary);
    }
    ui->widgets.dual_divider=plain_view(gauge_content,140,1);lv_obj_set_pos(ui->widgets.dual_divider,50,120);
    lv_obj_set_style_bg_color(ui->widgets.dual_divider,lv_color_hex(color_track),0);lv_obj_set_style_bg_opa(ui->widgets.dual_divider,LV_OPA_COVER,0);
    for(unsigned i=0;i<8;i++) {
        ui->widgets.page_dots[i]=plain_view(gauge_content,4,4);lv_obj_set_style_radius(ui->widgets.page_dots[i],2,0);
        lv_obj_set_style_bg_opa(ui->widgets.page_dots[i],LV_OPA_COVER,0);
    }
    ui->widgets.page_position=plain_label(gauge_content,color_text_secondary);
    ui->widgets.pairing_lbl=plain_view(scr,240,240);
    lv_obj_set_style_bg_color(ui->widgets.pairing_lbl,lv_color_hex(color_background),0);lv_obj_set_style_bg_opa(ui->widgets.pairing_lbl,LV_OPA_COVER,0);
    ui->widgets.pairing_title=plain_label(ui->widgets.pairing_lbl,color_text_primary);
    ui->widgets.pairing_body=plain_label(ui->widgets.pairing_lbl,color_text_secondary);
    ui->widgets.pairing_code=plain_label(ui->widgets.pairing_lbl,color_text_primary);
    ui->widgets.pairing_id=plain_label(ui->widgets.pairing_lbl,color_text_secondary);
    ui->widgets.alert_lbl=plain_label(gauge_content,color_warning);
    ui->widgets.alert_icon=icon_create(gauge_content,false);
    ui->widgets.alert_arrow=lv_line_create(gauge_content);lv_obj_remove_style_all(ui->widgets.alert_arrow);
    lv_obj_set_style_line_width(ui->widgets.alert_arrow,2,0);lv_obj_clear_flag(ui->widgets.alert_arrow,LV_OBJ_FLAG_CLICKABLE);
    ui->widgets.diagnostics_lbl=plain_label(gauge_content,color_warning);
    ui->widgets.diagnostics_icon=icon_create(gauge_content,true);
    ui->widgets.demo_lbl=plain_label(gauge_content,color_warning);small_label(ui->widgets.demo_lbl,"Demo",120,213,48);
    lv_obj_t *overlay=plain_view(gauge_content,240,240);
    lv_obj_set_style_bg_color(overlay,lv_color_hex(color_background),0);lv_obj_set_style_bg_opa(overlay,LV_OPA_COVER,0);
    lv_obj_clear_flag(overlay,LV_OBJ_FLAG_EVENT_BUBBLE|LV_OBJ_FLAG_GESTURE_BUBBLE);
    ui->widgets.alert_overlay=overlay;
    ui->widgets.attention_title=plain_label(overlay,color_critical);
    ui->widgets.alert_overlay_label=plain_label(overlay,color_text_secondary);
    ui->widgets.attention_value=plain_label(overlay,color_text_primary);
    ui->widgets.attention_detail=plain_label(overlay,color_text_secondary);
    lv_obj_t *button=plain_view(overlay,128,32);lv_obj_set_pos(button,56,168);
    lv_obj_set_style_bg_color(button,lv_color_hex(color_track),0);lv_obj_set_style_bg_opa(button,LV_OPA_COVER,0);lv_obj_set_style_radius(button,16,0);
    lv_obj_clear_flag(button,LV_OBJ_FLAG_EVENT_BUBBLE|LV_OBJ_FLAG_GESTURE_BUBBLE);lv_obj_add_flag(button,LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_ext_click_area(button,8);lv_obj_add_event_cb(button,acknowledge_alert,LV_EVENT_CLICKED,ui);
    lv_obj_t *button_label=plain_label(button,color_text_primary);lv_label_set_text(button_label,"Acknowledge");
    lv_obj_set_style_text_font(button_label,font_subtitle,0);lv_obj_center(button_label);ui->widgets.attention_button=button;
    show(overlay,false);ui->status_dirty=true;
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

ui_t *ui_init(ui_page_t const *page, uint32_t interval_ms, ui_touch_callback_t touch_cb,
              bool pairing_required, bool imperial_units)
{
    ESP_NULL_CHECK(page, TAG, "Page config is NULL");
    ESP_NULL_CHECK(touch_cb, TAG, "touch callback is NULL");

    ESP_LOGI(TAG, "Initializing UI...");

    ui_t *ui = malloc(sizeof(ui_t));
    ESP_NULL_CHECK(ui, TAG, "Failed to allocate memory for UI context");
    memset(ui, 0, sizeof(ui_t));
    ESP_LOGI(TAG, "ui pointer2: %p (addr: %p)", ui, (void *)&ui);

    ui->rtos.touch_ev_que = xQueueCreate(4, sizeof(lv_event_code_t));
    ui->rtos.pairing_que = xQueueCreate(1, sizeof(uint32_t));
    ui->rtos.pairing_id_que = xQueueCreate(1, sizeof(ui->pairing_identifier));
    ui->rtos.alert_que = xQueueCreate(1, sizeof(ui_alert_t));
    ui->rtos.diagnostics_que = xQueueCreate(1, sizeof(ui_diagnostics_t));
    ui->rtos.showcase_que = xQueueCreate(1, sizeof(showcase_request_t));
    ui->touch_cb          = touch_cb;
    ui->display.page = *page;
    ui->pairing_visible = pairing_required;
    ui->imperial_units = imperial_units;

    if (!ui->rtos.touch_ev_que || !ui->rtos.pairing_que ||
        !ui->rtos.pairing_id_que ||
        !ui->rtos.alert_que || !ui->rtos.diagnostics_que || !ui->rtos.showcase_que) {
        ESP_LOGE(TAG, "Failed to allocate UI mailboxes");
        if (ui->rtos.touch_ev_que) vQueueDelete(ui->rtos.touch_ev_que);
        if (ui->rtos.pairing_que) vQueueDelete(ui->rtos.pairing_que);
        if (ui->rtos.pairing_id_que) vQueueDelete(ui->rtos.pairing_id_que);
        if (ui->rtos.alert_que) vQueueDelete(ui->rtos.alert_que);
        if (ui->rtos.diagnostics_que) vQueueDelete(ui->rtos.diagnostics_que);
        if (ui->rtos.showcase_que) vQueueDelete(ui->rtos.showcase_que);
        free(ui);
        return NULL;
    }

    if (!lvgl_port_lock(portMAX_DELAY))
    {
        ESP_LOGE(TAG, "Failed to lock LVGL port");
        free(ui);
        return NULL;
    }

    atomic_init(&ui->showcase_active,false);atomic_init(&ui->showcase_generation,0);
    atomic_init(&ui->showcase_sequence,0);atomic_init(&ui->showcase_packed,0);atomic_init(&ui->showcase_started,0);
    ui->page_count=1;
    atomic_init(&ui->action_allowed, false);
    atomic_init(&ui->action_pending, false);
    atomic_init(&ui->action_context_epoch, 0);
    ui_init_screen(ui, page, interval_ms);
    ui->action_label = lv_label_create(ui->widgets.gauge_content);
    lv_obj_set_style_text_color(ui->action_label, lv_color_hex(color_accent), 0);
    lv_obj_set_style_text_font(ui->action_label, font_subtitle, 0);
    lv_obj_add_flag(ui->action_label, LV_OBJ_FLAG_HIDDEN);

    lvgl_port_unlock();

    ESP_LOGI(TAG, "UI initialized successfully");

    return ui;
}

void ui_set_value(ui_t *ui, unsigned source, uint32_t generation, uint16_t pid, double const *value)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    if (source >= 2) return;
    ui_sample_t sample = {
        .pid = pid, .source = source, .occupied = true, .generation = generation,
        .present = value != NULL && isfinite(*value),
        .value = value != NULL && isfinite(*value) ? *value : 0,
        .received_at = xTaskGetTickCount(),
    };
    bool stored = false;
    portENTER_CRITICAL(&sample_mux);
    ui_sample_t *slot = NULL;
    for (unsigned i = 0; i < UI_SAMPLE_CAPACITY; ++i) {
        ui_sample_t *entry = &ui->display.samples[i];
        if (!entry->occupied && !slot) slot = entry;
        if (entry->occupied && entry->source == source && entry->pid == pid) {
            slot = entry;
            break;
        }
    }
    if (slot) {
        if (!slot->occupied || (int32_t)(generation - slot->generation) >= 0) *slot = sample;
        stored = true;
    }
    portEXIT_CRITICAL(&sample_mux);
    if (!stored) ESP_LOGW(TAG, "UI catalog full for source %u PID 0x%04X", source, pid);
}

void ui_invalidate_source(ui_t *ui, unsigned source, uint32_t generation)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    portENTER_CRITICAL(&sample_mux);
    for (unsigned i = 0; i < UI_SAMPLE_CAPACITY; ++i) {
        ui_sample_t *entry = &ui->display.samples[i];
        if (entry->occupied && entry->source == source && (int32_t)(generation - entry->generation) >= 0) {
            entry->present = false;
            entry->generation = generation;
        }
    }
    portEXIT_CRITICAL(&sample_mux);
}

void ui_set_page(ui_t *ui, ui_page_t const *page)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    ESP_NULL_CHECK(page, TAG, "Page config is NULL");

    page_action_reset(&ui->action);
    if(ui->showcase.active){ui->showcase.active=false;showcase_publish(ui);}
    ui->display.page = *page;
    ui->display.rendered_once = false;
    ui->display.trend_count = 0;
    for (unsigned i = 0; i < 60; ++i) ui->display.trend_points[i].y = 30;
    lv_arc_set_range(ui->widgets.arc, 0, 1000);
    lv_bar_set_range(ui->widgets.bar, 0, 1000);
    select_renderer_widgets(ui);
    ui->status_dirty=true;
    /* Populate all renderer-specific widgets now, including dual values.
     * Waiting for the data timer adds another 50 ms to a page change. */
    render_page(ui);
    ESP_LOGD(TAG, "Updated page: %s renderer=%u", page->name, page->renderer);
}

void ui_set_units(ui_t *ui, bool imperial_units)
{
    ESP_NULL_CHECK(ui, TAG, "UI context is NULL");
    if (ui->imperial_units == imperial_units) return;
    ui->imperial_units = imperial_units;
    ui->status_dirty=true;
    ui->display.rendered_once = false;
    render_page(ui);
}

void ui_show_pairing_code(ui_t *ui, uint32_t passkey)
{
    if (ui != NULL) xQueueOverwrite(ui->rtos.pairing_que, &passkey);
}

void ui_set_pairing_identifier(ui_t *ui, const char *identifier)
{
    if (!ui || !ui->rtos.pairing_id_que) return;
    char value[sizeof(ui->pairing_identifier)] = {0};
    if (identifier) snprintf(value, sizeof(value), "%s", identifier);
    xQueueOverwrite(ui->rtos.pairing_id_que, value);
}

void ui_set_alert(ui_t *ui,uint8_t severity,bool unavailable,const char *label) {
    ui_set_alert_full(ui,severity,unavailable,label,false);
}
void ui_set_alert_full(ui_t *ui, uint8_t severity, bool unavailable, const char *label, bool attention)
{
    ui_set_alert_event(ui,severity,unavailable,label,attention,0,0,0,0,0,"");
}
void ui_set_alert_event(ui_t *ui,uint8_t severity,bool unavailable,const char *label,bool attention,uint16_t key,uint32_t boot,uint32_t episode,float value,float limit,const char *unit) {
    ui_set_alert_condition(ui,severity,unavailable,label,attention,key,boot,episode,value,limit,unit,0,false);
}
void ui_set_alert_condition(ui_t *ui,uint8_t severity,bool unavailable,const char *label,bool attention,uint16_t key,uint32_t boot,uint32_t episode,float value,float limit,const char *unit,uint8_t additional,bool simulated)
{
    if (!ui || !ui->rtos.alert_que) return;
    ui_alert_t alert = {.severity = severity, .attention = attention, .unavailable = unavailable,.key=key,.boot=boot,.episode=episode,.value=value,.limit=limit,.additional=additional,.simulated=simulated};
    if(unit)snprintf(alert.unit,sizeof(alert.unit),"%s",unit);
    if (label) snprintf(alert.label, sizeof(alert.label), "%s", label);
    xQueueOverwrite(ui->rtos.alert_que, &alert);
}

void ui_set_diagnostics(ui_t *ui, bool valid, bool mil_on, bool transmission,
                        uint8_t count, const char *first_code)
{
    if (!ui || !ui->rtos.diagnostics_que) return;
    ui_diagnostics_t diagnostics = {.valid = valid, .mil_known = valid, .mil_on = mil_on, .transmission = transmission, .count = count,.count_known=valid};
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

void ui_set_simulated(ui_t *ui) { if(ui){ui->simulated=true;ui->status_dirty=true;} }

void ui_configure_page_action(ui_t *ui, page_action_config_t config)
{
    page_action_init(&ui->action, config);
    ui->action_feedback = false;
}
void ui_set_action_context(ui_t *ui, bool allowed)
{
    if (atomic_exchange(&ui->action_allowed, allowed) != allowed)
        atomic_fetch_add(&ui->action_context_epoch, 1);
}
uint8_t ui_action_target(ui_t *ui) { return ui->action.config.target_page; }

void ui_reset_action_sequence(ui_t *ui)
{
    page_action_reset(&ui->action);
    atomic_store(&ui->action_pending, false);
}
bool ui_action_pending(ui_t *ui) { return atomic_load(&ui->action_pending)||atomic_load(&ui->showcase_active); }

void ui_action_applied(ui_t *ui)
{
    ui->action_feedback = true;
    ui->action_feedback_cooldown = false;
    ui->action_feedback_ms = (uint32_t)(esp_timer_get_time() / 1000);
}

void ui_set_vehicle_diagnostics(ui_t *ui,const diagnostics_snapshot_t *snapshot) {
    if(!ui||!snapshot)return;
    ui_diagnostics_t d={.valid=snapshot->valid,.mil_known=snapshot->mil_known,.mil_on=snapshot->mil_on,.transmission=snapshot->transmission,
        .count=snapshot->distinct_count>255?255:snapshot->distinct_count,.count_known=snapshot->count_known,.count_partial=snapshot->count_partial};
    snprintf(d.first_code,sizeof(d.first_code),"%s",snapshot->first_code);xQueueOverwrite(ui->rtos.diagnostics_que,&d);
}
void ui_set_page_position(ui_t *ui,unsigned index,unsigned count) {
    if(!ui||!count||index>=count)return;
    ui->page_count=count;ui->page_index=index;ui->status_dirty=true;
}
bool ui_request_showcase(ui_t *ui,bool enabled,uint8_t seconds,uint32_t sequence,bool vehicle_ready,bool maintenance) {
    if(!ui||!ui->rtos.showcase_que||!sequence||(seconds&&(seconds<5||seconds>30))||(enabled&&!seconds))return false;
    showcase_request_t r={.enabled=enabled,.seconds=seconds,.sequence=sequence,.vehicle_ready=vehicle_ready,.maintenance=maintenance};
    return xQueueSend(ui->rtos.showcase_que,&r,0)==pdTRUE;
}
bool ui_showcase_active(ui_t *ui) { return ui&&atomic_load(&ui->showcase_active); }
void ui_showcase_next(ui_t *ui) {
    if(!ui||!ui->showcase.active)return;
    display_showcase_next(&ui->showcase,(uint32_t)(esp_timer_get_time()/1000));showcase_frame(ui);
}
void ui_showcase_stop(ui_t *ui) {
    if(!ui||!ui->showcase.active)return;
    ui->showcase.active=false;showcase_frame(ui);render_page(ui);render_status(ui);
}
size_t ui_showcase_status(ui_t *ui,uint32_t now,uint8_t out[20]) {
    if(!ui||!out)return 0;
    for(unsigned attempt=0;attempt<3;attempt++) {
        unsigned before=atomic_load(&ui->showcase_generation);if(before&1)continue;
        bool active=atomic_load(&ui->showcase_active);
        uint32_t sequence=atomic_load(&ui->showcase_sequence),packed=atomic_load(&ui->showcase_packed),started=atomic_load(&ui->showcase_started);
        if(before!=atomic_load(&ui->showcase_generation))continue;
        memset(out,0,20);out[0]=21;out[1]=active;out[2]=(packed>>16)&255;out[3]=packed&255;
        memcpy(out+4,&sequence,4);out[8]=display_showcase_count();out[9]=(packed>>8)&255;out[10]=1;
        uint32_t remaining=active&&now-started<DISPLAY_SHOWCASE_DURATION_MS?DISPLAY_SHOWCASE_DURATION_MS-(now-started):0;
        memcpy(out+12,&remaining,4);return 20;
    }
    return 0;
}
