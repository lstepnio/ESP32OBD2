/* Runs the production UI and fonts against real LVGL software rendering.
 * Only RTOS mailboxes/time and hardware owners are replaced. Not a hardware test. */
#include <assert.h>
#include <stdio.h>
#include <sys/stat.h>
#include "lvgl.h"
#include "../../main/src/ui.c"

uint32_t        host_ms;
static bool     vehicle, maintenance;
static unsigned acknowledgments;
static unsigned command_requests;
static void     command_touch(ui_t *ui, lv_event_code_t event)
{
    if (event == UI_EVENT_VEHICLE_COMMAND)
        ++command_requests;
}

void worker_health_progress(worker_id_t id, uint32_t now)
{
    (void)id;
    (void)now;
}
bool adapter_status_ready_for(unsigned source)
{
    (void)source;
    return vehicle;
}
uint8_t transfer_gate_current(void)
{
    return maintenance ? 2 : 0;
}
bool alert_runtime_command(const uint8_t *data, size_t length)
{
    (void)data;
    (void)length;
    acknowledgments++;
    return true;
}
static uint32_t *pixels, *draw_buffer;
static unsigned  width, height;
static void      flush(lv_display_t *display, const lv_area_t *area, uint8_t *data)
{
    unsigned stride = area->x2 - area->x1 + 1;
    for (int y = area->y1; y <= area->y2; y++)
        memcpy(pixels + y * width + area->x1, data + (y - area->y1) * stride * 4, stride * 4);
    lv_display_flush_ready(display);
}
static void tick(unsigned ms)
{
    host_ms += ms;
    lv_tick_inc(ms);
    lv_timer_handler();
    lv_refr_now(NULL);
}
static void check_labels(lv_obj_t *root)
{
    if (lv_obj_has_flag(root, LV_OBJ_FLAG_HIDDEN))
        return;
    if (lv_obj_check_type(root, &lv_label_class))
    {
        const char      *text = lv_label_get_text(root);
        const lv_font_t *font = lv_obj_get_style_text_font(root, 0);
        ink_t            b;
        if (strchr(text, '\n'))
        {
            lv_area_t area;
            lv_obj_get_coords(root, &area);
            assert(gauge_presentation_fits(area.x1, area.y1, area.x2, area.y2));
            return;
        }
        assert(ink(text, font, &b));
        lv_area_t a;
        lv_obj_get_coords(root, &a);
        int x = a.x1 + lv_obj_get_style_pad_left(root, 0), y = a.y1 + lv_obj_get_style_pad_top(root, 0);
        if (*text && !gauge_presentation_fits(x + b.left, y + b.top, x + b.right, y + b.bottom))
        {
            fprintf(stderr, "Outside circle: %s (%d,%d,%d,%d)\n", text, x + b.left, y + b.top, x + b.right,
                    y + b.bottom);
            assert(false);
        }
        assert(strcmp(text, "Value too wide") && strcmp(text, "Unit?"));
    }
    for (unsigned i = 0; i < lv_obj_get_child_count(root); i++)
        check_labels(lv_obj_get_child(root, i));
}
static void capture(const char *directory, const char *name)
{
    check_labels(lv_screen_active());
    char path[512];
    snprintf(path, sizeof(path), "%s/%s.rgb", directory, name);
    FILE *out = fopen(path, "wb");
    assert(out);
    for (unsigned i = 0; i < width * height; i++)
    {
        unsigned char rgb[] = {pixels[i] >> 16, pixels[i] >> 8, pixels[i]};
        assert(fwrite(rgb, 1, 3, out) == 3);
    }
    fclose(out);
}
static void touch(ui_t *ui, lv_event_code_t event)
{
    (void)ui;
    (void)event;
}
int main(int argc, char **argv)
{
    assert(argc == 2);
    mkdir(argv[1], 0700);
    lv_init();
    const gauge_surface_t *surface = gauge_presentation()->surface;
    assert(gauge_presentation_matches(surface));
    assert(!gauge_presentation_matches(NULL));
    gauge_surface_t wrong = *surface;
    wrong.content_inset++;
    assert(!gauge_presentation_matches(&wrong));
    assert(gauge_presentation_px(0) == 0);
    assert(gauge_presentation_px(-1) == -gauge_presentation_px(1));
    assert(abs(2 * gauge_presentation_x(120) - surface->width) <= 1);
    assert(abs(2 * gauge_presentation_y(120) - surface->height) <= 1);
    width       = surface->width;
    height      = surface->height;
    pixels      = calloc(width * height, sizeof(*pixels));
    draw_buffer = calloc(width * height, sizeof(*draw_buffer));
    assert(pixels && draw_buffer);
    lv_display_t *display = lv_display_create(width, height);
    lv_display_set_color_format(display, LV_COLOR_FORMAT_XRGB8888);
    lv_display_set_flush_cb(display, flush);
    lv_display_set_buffers(display, draw_buffer, NULL, width * height * sizeof(*draw_buffer),
                           LV_DISPLAY_RENDER_MODE_FULL);
    ui_page_t saved = {
        .name         = "RPM",
        .renderer     = UI_RENDERER_NUMERIC,
        .metric_count = 1,
        .metrics      = {{.pid = 12, .name = "Engine RPM", .unit = "rpm", .maximum = 8000, .stale_after_ms = 2000}}};
    ui_t *ui = ui_init(&saved, 50, touch, false, false);
    assert(ui);
    ui_set_page_position(ui, 1, 3);
    assert(ui_request_showcase(ui, true, 8, 7, false, false));
    tick(60);
    assert(lv_obj_has_flag(ui->widgets.demo_lbl, LV_OBJ_FLAG_HIDDEN));
    for (unsigned i = 0; i < display_showcase_count(); i++)
    {
        assert(lv_obj_has_flag(ui->widgets.demo_lbl, LV_OBJ_FLAG_HIDDEN));
        uint8_t status[20];
        assert(ui_showcase_status(ui, host_ms, status) == 20);
        assert(status[1] == 1 && status[3] == i);
        if (display_showcase_frame(i)->renderer == UI_RENDERER_TREND)
            for (unsigned j = 0; j < 20; j++)
                tick(100);
        char name[64];
        snprintf(name, sizeof(name), "demo-%02u", i + 1);
        capture(argv[1], name);
        if (display_showcase_frame(i)->attention)
            lv_obj_send_event(ui->widgets.attention_button, LV_EVENT_CLICKED, NULL);
        else
            ui_showcase_next(ui);
        tick(60);
    }
    assert(acknowledgments == 0);
    vehicle = true;
    tick(60);
    assert(!ui_showcase_active(ui));
    assert(ui->display.page.metrics[0].pid == 12);
    vehicle = false;
    assert(ui_request_showcase(ui, true, 8, 8, false, false));
    tick(60);
    assert(ui_showcase_active(ui));
    maintenance = true;
    tick(60);
    assert(!ui_showcase_active(ui));
    maintenance = false;
    ui_set_alert_event(ui, 2, false, "Coolant", true, 1, 1, 1, 118, 110, "degC");
    tick(60);
    capture(argv[1], "critical-live");
    assert(ui_request_showcase(ui, true, 8, 9, false, false));
    tick(60);
    assert(!ui_showcase_active(ui));
    ui_set_alert(ui, 0, false, "");
    tick(60);
    ui_show_pairing_code(ui, UI_PAIRING_READY);
    tick(60);
    capture(argv[1], "pair-ready");
    ui_show_pairing_code(ui, 123456);
    tick(60);
    capture(argv[1], "pair-code");
    ui_show_pairing_code(ui, UI_PAIRING_OWNER_SAVED);
    tick(60);
    capture(argv[1], "pair-success");
    tick(2600);
    assert(!ui->pairing_visible);
    assert(!ui_showcase_active(ui));
    assert(ui->display.page.metrics[0].pid == 12);
    assert(acknowledgments == 0);
    /* Font can step down for a longer value, then stays stable until page/unit change. */
    double wide = 16383, shorter = 9;
    ui_set_value(ui, 12, &wide);
    tick(60);
    const lv_font_t *chosen = lv_obj_get_style_text_font(ui->widgets.value_lbl, 0);
    ui_set_value(ui, 12, &shorter);
    tick(60);
    assert(lv_obj_get_style_text_font(ui->widgets.value_lbl, 0) == chosen);
    ui_set_units(ui, true);
    tick(60);
    assert(ui_request_showcase(ui, true, 8, 10, false, false));
    tick(60);
    for (unsigned i = 0; i < display_showcase_count(); i++)
    {
        check_labels(lv_screen_active());
        ui_showcase_next(ui);
        tick(60);
    }
    ui_showcase_stop(ui);
    tick(60);
    assert(!ui_showcase_active(ui) && ui->display.page.metrics[0].pid == 12);
    diagnostics_snapshot_t filtered = {.valid = true, .mil_known = true, .count_known = true};
    ui_set_vehicle_diagnostics(ui, &filtered);
    tick(60);
    assert(lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    filtered.mil_known = false;
    ui_set_vehicle_diagnostics(ui, &filtered);
    tick(60);
    assert(lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    assert(lv_obj_has_flag(ui->widgets.diagnostics_lbl, LV_OBJ_FLAG_HIDDEN));
    ui->touch_cb = command_touch;
    ui_configure_page_action(ui, (page_action_config_t){.enabled        = true,
                                                        .count          = 3,
                                                        .window_ms      = 5000,
                                                        .vehicle_menu   = true,
                                                        .air_up_seconds = 600,
                                                        .winch_seconds  = 60});
    ui_set_action_context(ui, true);
    assert(ui_action_opens_vehicle_menu(ui));
    ui_show_vehicle_actions(ui);
    tick(60);
    assert(ui_action_pending(ui));
    capture(argv[1], "actions-unavailable");
    lv_obj_send_event(ui->command_rows[0], LV_EVENT_CLICKED, NULL);
    assert(command_requests == 0);
    ui_set_vehicle_action_status(ui, 1, 1, 1, 0);
    ui_set_vehicle_action_status(ui, 2, 1, 1, 0);
    tick(60);
    capture(argv[1], "actions-ready");
    lv_obj_send_event(ui->command_rows[0], LV_EVENT_CLICKED, NULL);
    tick(60);
    assert(command_requests == 0);
    capture(argv[1], "actions-confirm");
    lv_obj_send_event(ui->command_rows[0], LV_EVENT_CLICKED, NULL);
    assert(command_requests == 1);
    uint8_t  kind, preset;
    bool     stop;
    uint16_t seconds;
    ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
    assert(kind == 1 && !stop && preset == 1 && seconds == 600);
    ui_set_vehicle_action_status(ui, 1, 3, 1, 0);
    ui_set_vehicle_action_status(ui, 2, 7, 1, 0);
    tick(60);
    capture(argv[1], "actions-restart-required");
    lv_obj_send_event(ui->command_rows[2], LV_EVENT_CLICKED, NULL);
    assert(command_requests == 1);
    lv_obj_send_event(ui->command_rows[0], LV_EVENT_CLICKED, NULL);
    assert(command_requests == 2);
    ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
    assert(kind == 1 && stop && !preset && !seconds);
    lv_obj_send_event(ui->command_rows[3], LV_EVENT_CLICKED, NULL);
    assert(!ui_action_pending(ui));
    /* Calibration pages exercise geometry/fonts outside the instrument compositor. */
    for (unsigned i = 0; i < CALIBRATION_PAGE_COUNT; i++)
    {
        ui->calibration_page = i;
        render_calibration_page(ui);
        char name[32];
        snprintf(name, sizeof(name), "calibration-%u", i + 1);
        capture(argv[1], name);
    }
    ui_show_vehicle_actions(ui);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1 | 8 | 16, 0);
    ui_set_vehicle_action_status(ui, 2, VEHICLE_READY, 1, 0);
    tick(60);
    assert(strstr(lv_label_get_text(ui->command_text[0]), "1:00"));
    lv_obj_send_event(ui->command_rows[0], LV_EVENT_CLICKED, NULL);
    lv_obj_send_event(ui->command_rows[0], LV_EVENT_CLICKED, NULL);
    ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
    assert(!stop && seconds == 60);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_ACTIVE, 1 | 4 | 8 | 16, 47);
    tick(60);
    assert(strstr(lv_label_get_text(ui->command_text[0]), "0:47"));
    assert(!strcmp(lv_label_get_text(ui->command_note), "Coolant unavailable"));
    capture(argv[1], "actions-coolant-timer");
    puts("PASS: production LVGL showcase frames, circular glyphs, vehicle/maintenance/critical preemption and pairing "
         "restore");
}
