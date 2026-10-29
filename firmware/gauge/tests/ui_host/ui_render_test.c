/* Runs the production UI and fonts against real LVGL software rendering.
 * Only RTOS mailboxes/time and hardware owners are replaced. Not a hardware test. */
#include <assert.h>
#include <stdio.h>
#include <sys/stat.h>
#include "lvgl.h"
#include "../../main/src/ui.c"
static bool metric_value(ui_t *ui, unsigned index, double *value)
{
    ui_sample_t sample;
    bool        available = metric_sample(ui, index, &sample);
    if (available)
        *value = sample.value;
    return available;
}

bool            host_psram_failure;
uint32_t        host_ms;
static bool     vehicle, maintenance, paused;
static uint32_t source_generations[2] = {1, 1};
static unsigned acknowledgments;
static unsigned command_requests;
static void     command_touch(ui_t *ui, lv_event_code_t event)
{
    if (event == UI_EVENT_VEHICLE_COMMAND)
        ++command_requests;
    else if (event == UI_EVENT_PAGE_ACTION)
        ui_show_vehicle_actions(ui);
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
bool ble_mgr_is_paused(void)
{
    return paused;
}
uint32_t adapter_status_generation(unsigned source)
{
    return source_generations[source];
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
static uint32_t *pixels;
static uint8_t  *draw_buffer;
static unsigned  width, height;
static void      flush(lv_display_t *display, const lv_area_t *area, uint8_t *data)
{
    unsigned stride = area->x2 - area->x1 + 1;
    for (int y = area->y1; y <= area->y2; y++)
#if LV_COLOR_DEPTH == 16
        for (unsigned x = 0; x < stride; ++x)
        {
            uint16_t value;
            memcpy(&value, data + ((y - area->y1) * stride + x) * 2, sizeof(value));
            uint32_t r = (value >> 11) & 31, g = (value >> 5) & 63, b = value & 31;
            pixels[y * width + area->x1 + x] = ((r << 3 | r >> 2) << 16) | ((g << 2 | g >> 4) << 8) | (b << 3 | b >> 2);
        }
#else
        memcpy(pixels + y * width + area->x1, data + (y - area->y1) * stride * 4, stride * 4);
#endif
    lv_display_flush_ready(display);
}
static void tick(unsigned ms)
{
    host_ms += ms;
    lv_tick_inc(ms);
    lv_timer_handler();
    lv_refr_now(NULL);
}
static void action_press(ui_t *ui)
{
    command_pointer(ui, LV_EVENT_PRESSED, ui->command_button, gx(120), gy(174));
}
static void action_release(ui_t *ui)
{
    command_pointer(ui, LV_EVENT_RELEASED, ui->command_button, gx(120), gy(174));
}
static unsigned object_count(lv_obj_t *root)
{
    unsigned count = 1;
    for (unsigned i = 0; i < lv_obj_get_child_count(root); ++i)
        count += object_count(lv_obj_get_child(root, i));
    return count;
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
#include "gesture_regression.inc"

static void presentation_regression(ui_t *ui)
{
    ui_page_t           page        = {.name         = "RPM",
                                       .metric_count = 1,
                                       .metrics      = {{.pid            = 12,
                                                         .name           = "Engine RPM",
                                                         .unit           = "rpm",
                                                         .maximum        = 8000,
                                                         .stale_after_ms = 2000,
                                                         .presentation   = READING_PRESENT_RPM}}};
    const ui_renderer_t renderers[] = {UI_RENDERER_NUMERIC, UI_RENDERER_ARC, UI_RENDERER_BAR, UI_RENDERER_DUAL,
                                       UI_RENDERER_DUAL_ARC};
    for (unsigned r = 0; r < sizeof(renderers) / sizeof(renderers[0]); r++)
    {
        page.renderer     = renderers[r];
        page.metric_count = r >= 3 ? 2 : 1;
        page.metrics[1]   = page.metrics[0];
        double measured   = 1000;
        ui_set_value(ui, 12, &measured);
        ui_set_page(ui, &page);
        assert(ui->display.rendered_values[0] == 1000); /* Cache on entry. */
        host_ms++;
        measured = 4000;
        ui_set_value(ui, 12, &measured);
        render_page(ui);
        assert(ui->display.rendered_values[0] == 1000);
        host_ms += 60;
        render_page(ui);
        double shown = 1000 + (measured - 1000) * (60.0 / 150.0) * (2 - 60.0 / 150.0);
        assert(ui->display.rendered_values[0] == 1000);
        double raw;
        assert(metric_value(ui, 0, &raw) && raw == measured); /* Raw remains measured. */
        if (page.renderer == UI_RENDERER_ARC || page.renderer == UI_RENDERER_DUAL_ARC)
            assert(lv_arc_get_value(ui->widgets.arc) == normalized_value(shown, &page.metrics[0], 1000));
        if (page.renderer == UI_RENDERER_BAR)
            assert(lv_bar_get_value(ui->widgets.bar) == normalized_value(shown, &page.metrics[0], 1000));
        if (page.metric_count == 2)
            assert(ui->display.rendered_values[1] == 1000);
        host_ms += 90;
        render_page(ui);
        if (page.renderer == UI_RENDERER_ARC || page.renderer == UI_RENDERER_DUAL_ARC)
            assert(lv_arc_get_value(ui->widgets.arc) == normalized_value(measured, &page.metrics[0], 1000));
        if (page.renderer == UI_RENDERER_BAR)
            assert(lv_bar_get_value(ui->widgets.bar) == normalized_value(measured, &page.metrics[0], 1000));
        assert(ui->display.rendered_values[0] == 1000); /* RPM text has its own cadence. */
        host_ms += 100;
        render_page(ui);
        assert(ui->display.rendered_values[0] == measured);
        /* Invalid then recovery between frames cannot bridge a dropout. */
        ui_set_value(ui, 12, NULL);
        measured = 2000;
        ui_set_value(ui, 12, &measured);
        render_page(ui);
        assert(ui->display.rendered_values[0] == measured);
        host_ms++;
        measured = 5000;
        ui_set_value(ui, 12, &measured);
        render_page(ui);
        assert(ui->display.rendered_values[0] == 2000);
        ui_set_units(ui, !ui->imperial_units);
        assert(ui->display.rendered_values[0] == measured);
        host_ms += 2001;
        render_page(ui);
        assert(!ui->display.rendered_available[0] && !ui->display.presentation[0].initialized);
        measured = 3000;
        ui_set_value(ui, 12, &measured);
        render_page(ui);
        assert(ui->display.rendered_values[0] == measured);
        /* A missed stale interval also resets without an intermediate render. */
        host_ms += 2001;
        measured = 6000;
        ui_set_value(ui, 12, &measured);
        render_page(ui);
        assert(ui->display.rendered_values[0] == measured);
        host_ms += 100;
        measured = 4000;
        set_sample(ui, UINT8_MAX, 0, 0, 12, &measured, host_ms - 60);
        render_page(ui);
        assert(ui->display.rendered_values[0] == 6000);
        double delayed = 6000 + (measured - 6000) * (60.0 / 150.0) * (2 - 60.0 / 150.0);
        if (page.renderer == UI_RENDERER_ARC || page.renderer == UI_RENDERER_DUAL_ARC)
            assert(lv_arc_get_value(ui->widgets.arc) == normalized_value(delayed, &page.metrics[0], 1000));
        if (page.renderer == UI_RENDERER_BAR)
            assert(lv_bar_get_value(ui->widgets.bar) == normalized_value(delayed, &page.metrics[0], 1000));
    }
    page.renderer     = UI_RENDERER_TREND;
    page.metric_count = 1;
    double measured   = 1000;
    ui_set_value(ui, 12, &measured);
    ui_set_page(ui, &page);
    host_ms += 50;
    measured = 2200;
    ui_set_value(ui, 12, &measured);
    ui->display.trend_recorded_at = xTaskGetTickCount() - pdMS_TO_TICKS(500);
    render_page(ui);
    assert(ui->display.rendered_values[0] == 1000); /* Trend label shares RPM stabilization. */
    assert(fabs(ui->display.trend_samples[TREND_CAPACITY - 1] - 21.75) < .001); /* History is raw 2200 RPM. */
    host_ms += 200;
    render_page(ui);
    assert(ui->display.rendered_values[0] == measured);
    page.renderer        = UI_RENDERER_NUMERIC;
    page.metrics[0].unit = "gear";
    ui_set_page(ui, &page);
    measured = 3;
    ui_set_value(ui, 12, &measured);
    render_page(ui);
    assert(ui->display.rendered_values[0] == measured);
    /* Source/session reset is immediate even when no disconnected UI frame ran. */
    page.metrics[0].unit         = "rpm";
    page.metrics[0].scoped       = true;
    page.metrics[0].source_index = 0;
    page.metrics[0].revision     = 30;
    vehicle                      = true;
    paused = maintenance  = false;
    source_generations[0] = 40;
    ui_set_source_state(ui, 0, true, 30, 40);
    measured = 1000;
    ui_set_source_value(ui, 0, 30, 40, 12, &measured, host_ms);
    ui_set_page(ui, &page);
    assert(ui->display.rendered_values[0] == measured);
    host_ms++;
    measured = 7000;
    ui_set_source_value(ui, 0, 30, 40, 12, &measured, host_ms);
    render_page(ui);
    assert(ui->display.rendered_values[0] == 1000);
    source_generations[0] = 41;
    ui_set_source_state(ui, 0, true, 30, 41);
    ui_set_source_value(ui, 0, 30, 41, 12, &measured, host_ms);
    render_page(ui);
    assert(ui->display.rendered_values[0] == measured);
    ui_set_source_state(ui, 0, true, 31, 41);
    measured = 2500;
    ui_set_source_value(ui, 0, 31, 41, 12, &measured, host_ms);
    render_page(ui);
    assert(!ui->display.rendered_available[0]); /* Old page revision stays unavailable. */
    page.metrics[0].revision = 31;
    ui_set_page(ui, &page);
    assert(ui->display.rendered_values[0] == measured);
    host_ms++;
    measured = 4500;
    ui_set_source_value(ui, 0, 31, 41, 12, &measured, host_ms);
    render_page(ui);
    assert(ui->display.rendered_values[0] == 2500);
    /* Critical attention retains the measured event and its existing next-frame
     * delivery while the ordinary reading is still catching up. */
    ui_set_alert_event(ui, 2, false, "RPM", true, 1, 7, 7, measured, 4000, "rpm");
    tick(50);
    assert(ui->rendered_alert.attention && ui->rendered_alert.value == measured);
    assert(visible_alert(ui).attention && ui->display.rendered_values[0] == measured);
    ui_set_alert_full(ui, 0, false, "", false);
    tick(60);
    puts("PASS: production numeric/arc/bar/dual easing, raw cache, unit/page, stale and dropout resets");
}

int main(int argc, char **argv)
{
    assert(argc == 2 || (argc == 3 && !strcmp(argv[2], "--no-psram")));
    host_psram_failure = argc == 3;
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
    width             = surface->width;
    height            = surface->height;
    pixels            = calloc(width * height, sizeof(*pixels));
    size_t draw_bytes = width * height * (LV_COLOR_DEPTH / 8);
    draw_buffer       = calloc(draw_bytes, 1);
    assert(pixels && draw_buffer);
    lv_display_t *display = lv_display_create(width, height);
#if LV_COLOR_DEPTH == 16
    lv_display_set_color_format(display, LV_COLOR_FORMAT_RGB565);
#else
    lv_display_set_color_format(display, LV_COLOR_FORMAT_XRGB8888);
#endif
    lv_display_set_flush_cb(display, flush);
    lv_display_set_buffers(display, draw_buffer, NULL, draw_bytes, LV_DISPLAY_RENDER_MODE_FULL);
    ui_page_t saved = {
        .name         = "RPM",
        .renderer     = UI_RENDERER_NUMERIC,
        .metric_count = 1,
        .metrics      = {{.pid = 12, .name = "Engine RPM", .unit = "rpm", .maximum = 8000, .stale_after_ms = 2000}}};
    ui_t *ui = ui_init(&saved, 50, touch, false, false);
    assert(ui);
    assert((ui->display.trend_mask == NULL) == host_psram_failure);
    /* Objects must be hidden before even the first UI timer callback. */
    assert(lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    assert(lv_obj_has_flag(ui->widgets.alert_icon, LV_OBJ_FLAG_HIDDEN));
    assert(lv_obj_has_flag(ui->widgets.alert_arrow, LV_OBJ_FLAG_HIDDEN));
    assert(lv_obj_has_flag(ui->widgets.demo_lbl, LV_OBJ_FLAG_HIDDEN));
    ui_set_diagnostics(ui, true, true, false, 1, "P0123");
    assert(lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    tick(60);
    assert(!lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    ui_set_page(ui, &saved);
    assert(!lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    ui_set_diagnostics(ui, true, false, false, 0, "");
    tick(60);
    assert(lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));

    /* A hidden reading is available immediately on page entry, without a timer
     * drain. Sources with the same PID remain independent. */
    vehicle                        = true;
    ui_page_t scoped               = saved;
    scoped.metrics[0].scoped       = true;
    scoped.metrics[0].source_index = 0;
    scoped.metrics[0].revision     = 7;
    ui_set_source_state(ui, 0, true, 7, 1);
    ui_set_source_state(ui, 1, true, 7, 1);
    double   primary = 1234, child = 2345, actual = 0;
    uint32_t observed = host_ms;
    ui_set_source_value(ui, 0, 7, 1, 12, &primary, observed);
    ui_set_source_value(ui, 1, 7, 1, 12, &child, observed);
    double   policy_value;
    uint32_t policy_at, policy_generation;
    assert(ui_source_sample(ui, 0, 7, 12, 2000, host_ms, &policy_value, &policy_at, &policy_generation));
    assert(policy_value == primary && policy_at == observed && policy_generation == 1);
    assert(ui_source_sample(ui, 1, 7, 12, 2000, host_ms, &policy_value, &policy_at, &policy_generation));
    assert(policy_value == child && policy_at == observed);
    assert(!ui_source_sample(ui, 1, 8, 12, 2000, host_ms, &policy_value, &policy_at, &policy_generation));
    assert(!ui_source_sample(ui, 1, 7, 12, 1, host_ms + 2000, &policy_value, &policy_at, &policy_generation));
    ui_set_page(ui, &scoped);
    assert(metric_value(ui, 0, &actual) && actual == primary);
    scoped.metrics[0].source_index = 1;
    ui_set_page(ui, &scoped);
    assert(metric_value(ui, 0, &actual) && actual == child);
    ui_set_page(ui, &saved);
    host_ms += 2001;
    ui_set_page(ui, &scoped);
    assert(!metric_value(ui, 0, &actual)); /* Original age, no rejuvenation. */
    ui_set_source_value(ui, 1, 7, 1, 12, &child, host_ms);
    assert(metric_value(ui, 0, &actual));
    ui_set_source_value(ui, 1, 7, 1, 12, NULL, host_ms);
    assert(!metric_value(ui, 0, &actual));
    ui_set_source_value(ui, 1, 7, 1, 12, &child, host_ms);
    ui_set_source_state(ui, 1, false, 7, 1);
    ui_set_source_value(ui, 1, 7, 1, 12, &child, host_ms); /* Retired writer. */
    ui_set_source_state(ui, 1, true, 7, 1);
    assert(!metric_value(ui, 0, &actual));
    source_generations[1] = 2;
    ui_set_source_state(ui, 1, true, 7, 2);
    ui_set_source_value(ui, 1, 7, 1, 12, &child, host_ms);
    assert(!metric_value(ui, 0, &actual));
    /* Latest-value mailbox has no FIFO backlog to drop/resurrect invalidation. */
    for (unsigned i = 0; i < 100; ++i)
        ui_set_source_value(ui, 1, 7, 2, 12, &child, host_ms);
    ui_set_source_value(ui, 1, 7, 2, 12, NULL, host_ms);
    assert(!metric_value(ui, 0, &actual));
    ui_set_source_value(ui, 1, 7, 2, 12, &child, host_ms);
    vehicle = false;
    assert(!ui_source_sample(ui, 1, 7, 12, 2000, host_ms, &policy_value, &policy_at, &policy_generation));
    assert(!metric_value(ui, 0, &actual));
    vehicle     = true;
    maintenance = true;
    assert(!metric_value(ui, 0, &actual));
    maintenance = false;
    paused      = true;
    assert(!metric_value(ui, 0, &actual));
    ui_set_source_state(ui, 1, false, 7, 2);
    ui_set_source_value(ui, 1, 7, 2, 12, &child, host_ms);
    paused = false;
    ui_set_source_state(ui, 1, true, 8, 2);
    ui_set_source_value(ui, 1, 7, 2, 12, &child, host_ms);
    assert(!metric_value(ui, 0, &actual));
    ui_set_source_value(ui, 1, 8, 2, 12, &child, host_ms);
    assert(!metric_value(ui, 0, &actual)); /* Old page revision. */
    scoped.metrics[0].revision = 8;
    ui_set_page(ui, &scoped);
    assert(metric_value(ui, 0, &actual));
    /* Production renderer uses bounded analog motion and a separate numeric
     * value, while raw policy reads and source-age invalidation stay exact. */
    scoped.renderer                = UI_RENDERER_ARC;
    scoped.metrics[0].presentation = READING_PRESENT_RPM;
    scoped.metrics[0].maximum      = 8000;
    child                          = 675;
    ui_set_source_value(ui, 1, 8, 2, 12, &child, host_ms);
    ui_set_page(ui, &scoped);
    assert(!strcmp(lv_label_get_text(ui->widgets.value_lbl), "675"));
    child = 1700;
    host_ms += 50;
    ui_set_source_value(ui, 1, 8, 2, 12, &child, host_ms);
    render_page(ui);
    int arc_start = lv_arc_get_value(ui->widgets.arc);
    host_ms += 50;
    render_page(ui);
    assert(lv_arc_get_value(ui->widgets.arc) > arc_start);
    assert(lv_arc_get_value(ui->widgets.arc) < normalized_value(child, &scoped.metrics[0], 1000));
    assert(!strcmp(lv_label_get_text(ui->widgets.value_lbl), "675"));
    assert(ui_source_sample(ui, 1, 8, 12, 2000, host_ms, &policy_value, &policy_at, &policy_generation));
    assert(policy_value == 1700); /* UI never rewrites measured values. */
    host_ms += 200;
    render_page(ui);
    assert(!strcmp(lv_label_get_text(ui->widgets.value_lbl), "1700"));
    host_ms += 2001;
    render_page(ui);
    assert(!strcmp(lv_label_get_text(ui->widgets.value_lbl), "--"));
    assert(lv_arc_get_value(ui->widgets.arc) == 0 && !ui->display.presentation[0].initialized);
    child                 = 700;
    source_generations[1] = 3;
    ui_set_source_state(ui, 1, true, 8, 3);
    ui_set_source_value(ui, 1, 8, 3, 12, &child, host_ms);
    render_page(ui);
    assert(lv_arc_get_value(ui->widgets.arc) == normalized_value(child, &scoped.metrics[0], 1000));
    source_generations[1] = 2;
    ui_set_source_state(ui, 1, true, 8, 2);
    ui_set_page(ui, &saved);
    /* Capacity follows the runtime PID ceiling, and source clear frees every
     * slot, including hidden entries. Both independent sources still fit. */
    ui_set_source_state(ui, 0, false, 7, 1);
    ui_set_source_state(ui, 1, false, 8, 2);
    ui_set_source_state(ui, 0, true, 9, 1);
    ui_set_source_state(ui, 1, true, 9, 2);
    for (unsigned i = 0; i < EGAUGE_RUNTIME_PIDS; ++i)
        ui_set_source_value(ui, i % 2, 9, source_generations[i % 2], 100 + i, &primary, host_ms);
    scoped.metrics[0].pid      = 131;
    scoped.metrics[0].revision = 9;
    ui_set_page(ui, &scoped);
    assert(metric_value(ui, 0, &actual));
    /* Full cache rejects overflow without evicting or corrupting existing data. */
    ui_set_source_value(ui, 1, 9, 2, 0x7777, &child, host_ms);
    assert(metric_value(ui, 0, &actual) && actual == primary);
    scoped.metrics[0].pid = 0x7777;
    ui_set_page(ui, &scoped);
    assert(!metric_value(ui, 0, &actual));
    scoped.metrics[0].pid = 131;
    ui_set_page(ui, &scoped);
    double nonfinite = NAN;
    ui_set_source_value(ui, 1, 9, 2, 131, &nonfinite, host_ms);
    assert(!metric_value(ui, 0, &actual));
    nonfinite = INFINITY;
    ui_set_source_value(ui, 1, 9, 2, 131, &nonfinite, host_ms);
    assert(!metric_value(ui, 0, &actual));
    ui_set_source_value(ui, 1, 9, 2, 131, &primary, host_ms);
    ui_set_source_state(ui, 0, false, 9, 1);
    assert(metric_value(ui, 0, &actual)); /* Healthy sibling retained. */
    ui_set_source_state(ui, 1, false, 9, 2);
    assert(!metric_value(ui, 0, &actual));
    ui_set_page(ui, &saved);
    ui_set_source_state(ui, 0, false, 7, 1);
    ui_set_source_state(ui, 1, false, 8, 2);
    vehicle = false;
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
    filtered.valid = filtered.mil_known = filtered.mil_on = true;
    filtered.count_known                                  = false;
    ui_set_vehicle_diagnostics(ui, &filtered);
    tick(60);
    assert(!lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    assert(!strcmp(lv_label_get_text(ui->widgets.diagnostics_lbl), ""));
    filtered.count_known = filtered.count_partial = true;
    filtered.distinct_count                       = 3;
    ui_set_vehicle_diagnostics(ui, &filtered);
    tick(60);
    assert(!strcmp(lv_label_get_text(ui->widgets.diagnostics_lbl), ""));
    filtered.count_partial = false;
    ui_set_vehicle_diagnostics(ui, &filtered);
    tick(60);
    assert(!strcmp(lv_label_get_text(ui->widgets.diagnostics_lbl), "3"));
    filtered = (diagnostics_snapshot_t){0};
    ui_set_vehicle_diagnostics(ui, &filtered);
    tick(60);
    ui->press_active = true;
    ui->pressed_at   = xTaskGetTickCount();
    tick(60);
    assert(ui_cycle_pause(ui) == PAGE_CYCLE_TOUCH);
    lv_obj_send_event(lv_screen_active(), LV_EVENT_PRESS_LOST, NULL);
    tick(60);
    assert(!ui->press_active && ui_cycle_pause(ui) == PAGE_CYCLE_RUNNING);
    ui->press_active = true;
    ui->pressed_at   = xTaskGetTickCount();
    tick(15001);
    assert(!ui->press_active && ui_cycle_pause(ui) == PAGE_CYCLE_RUNNING);
    test_gestures(ui, display);
    ui->touch_cb = command_touch;
    ui_configure_page_action(ui, (page_action_config_t){.enabled        = true,
                                                        .count          = 3,
                                                        .window_ms      = 5000,
                                                        .vehicle_menu   = true,
                                                        .air_up_seconds = 600,
                                                        .winch_seconds  = 60});
    ui_set_action_context(ui, true);
    ui_set_idle_protection(ui, true);
    ui_show_vehicle_actions(ui);
    tick(60);
    capture(argv[1], "actions-unavailable");
    assert(!strcmp(lv_label_get_text(ui->command_value), "Not supported"));
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1, 0, 0, 0);
    ui_set_vehicle_action_status(ui, 2, VEHICLE_READY, 1, 0, 0, 0);
    tick(60);
    capture(argv[1], "actions-ready");
    assert(!strcmp(lv_label_get_text(ui->command_button_text), "Hold to start"));
    assert(!strcmp(lv_label_get_text(ui->command_note), ""));
    /* A tap and an early release are not Start. Native long-press is not the timer. */
    action_press(ui);
    tick(500);
    action_release(ui);
    assert(command_requests == 0);
    action_press(ui);
    command_pointer(ui, LV_EVENT_LONG_PRESSED, ui->command_button, gx(120), gy(174));
    tick(899);
    assert(command_requests == 0);
    tick(60);
    assert(command_requests == 1);
    uint8_t  kind, preset;
    bool     stop;
    uint16_t seconds;
    ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
    assert(kind == 1 && !stop && preset == 1 && seconds == (CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST ? 300 : 600));
    ui_set_vehicle_action_status(ui, 1, VEHICLE_STARTING, 1, 0, 0, 1);
    tick(60);
    capture(argv[1], "actions-starting");
    assert(!lv_obj_has_flag(ui->command_ring, LV_OBJ_FLAG_HIDDEN));
    assert(!strcmp(lv_label_get_text(ui->command_value), ""));
    tick(1200);
    action_release(ui);
    assert(command_requests == 1); /* no duplicate or release Stop */
    ui_set_vehicle_action_status(ui, 1, VEHICLE_ACTIVE, 1 | 8, 287, 0, 1);
    tick(60);
    capture(argv[1], "actions-active");
    assert(lv_obj_has_flag(ui->command_ring, LV_OBJ_FLAG_HIDDEN));
    assert(!strcmp(lv_label_get_text(ui->command_note), ""));
    ui->command_page = 1;
    refresh_vehicle_menu(ui);
    tick(60);
    capture(argv[1], "actions-active-other");
    assert(!strcmp(lv_label_get_text(ui->command_button_text), "Stop"));
    action_press(ui);
    action_release(ui);
    assert(command_requests == 2 && ui->command_page == 0);
    ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
    assert(kind == 1 && stop && !preset && !seconds);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_STOPPING, 1, 0, 0, 1);
    tick(60);
    capture(argv[1], "actions-stopping");
    ui_set_vehicle_action_status(ui, 1, VEHICLE_VERIFYING, 1, 0, 0, 1);
    tick(60);
    capture(argv[1], "actions-verifying");
    ui_set_vehicle_action_status(ui, 1, VEHICLE_RESTORED, 1, 0, 0, 1);
    tick(60);
    capture(argv[1], "actions-restored-ready");
    assert(!strcmp(lv_label_get_text(ui->command_button_text), "Hold to start"));
    assert(command_requests == 2);
    /* Movement, loss, status ABA, scope, attention and config cancel holds. */
    unsigned before = command_requests;
    action_press(ui);
    command_pointer(ui, LV_EVENT_PRESSING, ui->command_button, gx(140), gy(174));
    tick(1000);
    action_release(ui);
    action_press(ui);
    command_pointer(ui, LV_EVENT_PRESS_LOST, ui->command_button, gx(120), gy(174));
    tick(1000);
    action_release(ui);
    action_press(ui);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1 | 16, 0, 0, 0);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_RESTORED, 1, 0, 0, 1);
    tick(1000);
    action_release(ui);
    assert(command_requests == before);
    uint8_t snapshot[32] = {22, 1, VEHICLE_READY};
    snapshot[27]         = 1;
    snapshot[8]          = 7;
    snapshot[12]         = 9;
    snapshot[16]         = 1;
    ui_set_vehicle_action_snapshot(ui, snapshot);
    tick(60);
    action_press(ui);
    snapshot[12] = 10;
    ui_set_vehicle_action_snapshot(ui, snapshot);
    tick(1000);
    action_release(ui);
    assert(command_requests == before);
    action_press(ui);
    ui_set_alert_full(ui, 2, false, "Coolant", true);
    tick(60);
    tick(1000);
    action_release(ui);
    assert(ui->command_suspended && command_requests == before);
    ui_set_alert_full(ui, 0, false, "", false);
    tick(60);
    tick(60);
    assert(!ui->command_suspended && !ui->command_confirm);
    action_press(ui);
    ui_set_idle_protection(ui, false);
    ui_set_idle_protection(ui, true);
    tick(1000);
    action_release(ui);
    assert(command_requests == before);
    command_pointer(ui, LV_EVENT_PRESSED, ui->command_button, gx(45), gy(174));
    command_pointer(ui, LV_EVENT_PRESSING, ui->command_button, gx(43), gy(174));
    tick(1000);
    action_release(ui);
    assert(command_requests == before); /* Leaving the hit region cancels even below movement tolerance. */
    /* Missing coolant retains one explicit Continue then a fresh timed hold. */
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1 | 8 | 16, 0, 0, 0);
    tick(60);
    capture(argv[1], "actions-coolant-missing");
    assert(!strcmp(lv_label_get_text(ui->command_note), "No coolant · 1 min max"));
    action_press(ui);
    action_release(ui);
    tick(60);
    assert(ui->command_confirm == 1 && command_requests == before);
    capture(argv[1], "actions-coolant-confirm");
    action_press(ui);
    tick(1000);
    action_release(ui);
    assert(command_requests == before + 1);
    ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
    assert(!stop && seconds == 60);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_ACTIVE, 1 | 4 | 8 | 16, 42, 9, 1);
    tick(60);
    capture(argv[1], "actions-coolant-timer");
    assert(!strcmp(lv_label_get_text(ui->command_note), "No coolant"));
    ui_set_vehicle_action_status(ui, 1, VEHICLE_ACTIVE, 1 | 4 | 8, 41, 9, 1);
    tick(60);
    capture(argv[1], "actions-coolant-recovered");
    assert(!strcmp(lv_label_get_text(ui->command_note), ""));
    ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
    assert(seconds == 60);
    /* Thermal Stop must not become Ready until the actual coolant context clears. */
    ui_set_vehicle_action_status(ui, 1, VEHICLE_STOPPING, 1 | VEHICLE_UI_COOLANT_HIGH, 0, VEHICLE_GUARD, 1);
    tick(60);
    capture(argv[1], "actions-thermal-stop");
    ui_set_alert_full(ui, 2, false, "Coolant", true);
    tick(60);
    tick(60);
    assert(ui->command_suspended && lv_obj_get_parent(ui->command_ring) == ui->widgets.alert_overlay);
    assert(!lv_obj_has_flag(ui->command_ring, LV_OBJ_FLAG_HIDDEN));
    capture(argv[1], "actions-critical-stop");
    ui_set_alert_full(ui, 0, false, "", false);
    tick(60);
    tick(60);
    assert(!ui->command_suspended && lv_obj_get_parent(ui->command_ring) == ui->command_menu);

    ui_set_vehicle_action_status(ui, 1, VEHICLE_RESTORED, 1 | VEHICLE_UI_COOLANT_HIGH, 0, 0, 1);
    tick(60);
    capture(argv[1], "actions-hot-blocked");
    assert(!strcmp(lv_label_get_text(ui->command_value), "Coolant high"));
    assert(lv_obj_has_state(ui->command_button, LV_STATE_DISABLED));
    before = command_requests;
    action_press(ui);
    tick(1000);
    action_release(ui);
    assert(command_requests == before);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1, 0, 0, 0);
    tick(60);
    assert(!strcmp(lv_label_get_text(ui->command_button_text), "Hold to start"));
    ui_set_vehicle_action_status(ui, 1, VEHICLE_UNAVAILABLE, VEHICLE_UI_RECONNECTING, 0, 0, 0);
    tick(60);
    capture(argv[1], "actions-reconnecting");
    assert(!lv_obj_has_flag(ui->command_ring, LV_OBJ_FLAG_HIDDEN));
    ui_set_vehicle_action_status(ui, 1, VEHICLE_UNKNOWN, 0, 0, 0, 1);
    tick(30100);
    capture(argv[1], "actions-unknown");
    assert(ui->command_menu_visible && !strcmp(lv_label_get_text(ui->command_value), "Check idle"));
    unsigned children = lv_obj_get_child_count(ui->command_menu);
    for (unsigned n = 0; n < 30; n++)
    {
        close_vehicle_menu(ui);
        ui_show_vehicle_actions(ui);
        tick(60);
    }
    assert(lv_obj_get_child_count(ui->command_menu) == children);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1, 0, 0, 0);
    tick(60);
    ui->command_page = 2;
    refresh_vehicle_menu(ui);
    tick(60);
    capture(argv[1], "actions-abs-ready");
    action_press(ui);
    action_release(ui);
    tick(60);
    capture(argv[1], "actions-abs-confirm");
    assert(ui->command_confirm == 3);
    command_activate(ui, true);
    refresh_vehicle_menu(ui);
    assert(!ui->command_confirm);
    ui_set_vehicle_action_status(ui, 2, VEHICLE_RESTART_REQUIRED, 1, 0, 0, 0);
    tick(60);
    capture(argv[1], "actions-restart-required");
    assert(lv_obj_has_flag(ui->command_button, LV_OBJ_FLAG_HIDDEN));
    ui_set_vehicle_action_status(ui, 2, VEHICLE_READY, 1, 0, 0, 0);
    close_vehicle_menu(ui);
    ui_show_vehicle_actions(ui);
    tick(60);
    /* Browse never starts. A fresh hold cannot survive exit or inactivity. */
    command_pointer(ui, LV_EVENT_PRESSED, ui->command_menu, gx(160), gy(110));
    command_pointer(ui, LV_EVENT_RELEASED, ui->command_menu, gx(100), gy(110));
    assert(ui->command_page == 1);
    action_press(ui);
    command_pointer(ui, LV_EVENT_RELEASED, ui->command_button, gx(120), gy(220));
    tick(1000);
    assert(!ui->command_menu_visible && command_requests == before);
    ui_show_vehicle_actions(ui);
    tick(30100);
    assert(!ui->command_menu_visible);
    ui_configure_page_action(ui, (page_action_config_t){.enabled        = true,
                                                        .count          = 3,
                                                        .window_ms      = 5000,
                                                        .vehicle_menu   = true,
                                                        .pages_explicit = true});
    ui_show_vehicle_actions(ui);
    tick(60);
    capture(argv[1], "actions-empty");
    ui_set_vehicle_action_status(ui, 1, VEHICLE_ACTIVE, 1, 32, 0, 2);
    tick(60);
    assert(command_id(ui) == 2 && !ui->action.config.page_count);
    capture(argv[1], "actions-unconfigured-active");
    close_vehicle_menu(ui);
    ui_configure_page_action(ui, (page_action_config_t){.enabled        = true,
                                                        .count          = 3,
                                                        .window_ms      = 5000,
                                                        .vehicle_menu   = true,
                                                        .air_up_seconds = 300,
                                                        .winch_seconds  = 60});
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1, 0, 0, 0);
    ui_set_idle_protection(ui, false);
    ui_show_vehicle_actions(ui);
    tick(60);
    capture(argv[1], "actions-protection-setup");
    ui_set_idle_protection(ui, true);
    ui_set_diagnostics(ui, true, true, false, 4, "U0121");
    ui_set_alert_full(ui, 1, false, "Coolant", false);
    tick(60);
    tick(60);
    capture(argv[1], "actions-threshold-mil");
    ui_set_diagnostics(ui, false, false, false, 0, "");
    ui_set_alert_full(ui, 0, false, "", false);
    close_vehicle_menu(ui);
    tick(60);
    ui->action.first_ms = host_ms;
    ui->action.progress = 1;
    tick(60);
    capture(argv[1], "actions-entry-one");
    ui->action.progress = 2;
    tick(60);
    capture(argv[1], "actions-entry-two");
    ui->action_enter_pending = true;
    ui->action_enter_at      = host_ms;
    tick(200);
    assert(!ui->command_menu_visible);
    capture(argv[1], "actions-entry-three");
    before = command_requests;
    tick(200);
    assert(ui->command_menu_visible && command_requests == before);
    close_vehicle_menu(ui);
    /* A dual arc reuses the original arc object, selects either numeric slot,
     * and retains the two reading identities/colors and tenth-page position. */
    ui->simulated = false;
    page_action_reset(&ui->action);
    ui_set_alert_full(ui, 0, false, "", false);
    ui_set_diagnostics(ui, false, false, false, 0, "");
    ui_page_t dual_arc = {
        .name             = "Gear and RPM",
        .renderer         = UI_RENDERER_DUAL_ARC,
        .metric_count     = 2,
        .arc_metric_index = 1,
        .metrics          = {
            {.pid = 0x5503, .name = "Gear", .unit = "gear", .minimum = 0, .maximum = 13, .stale_after_ms = 2000},
            {.pid = 0x0c, .name = "Engine RPM", .unit = "rpm", .minimum = 0, .maximum = 8000, .stale_after_ms = 2000}}};
    double park = 13, idle_rpm = 675;
    ui_set_value(ui, 0x5503, &park);
    ui_set_value(ui, 0x0c, &idle_rpm);
    ui_set_page(ui, &dual_arc);
    ui_set_page_position(ui, 9, 10);
    tick(60);
    assert(!lv_obj_has_flag(ui->widgets.arc, LV_OBJ_FLAG_HIDDEN));
    assert(lv_arc_get_value(ui->widgets.arc) == 84);
    assert(!strcmp(lv_label_get_text(ui->widgets.dual_value[0]), "P"));
    assert(!strcmp(lv_label_get_text(ui->widgets.page_position), "10/10"));
    assert(lv_color_to_u32(lv_obj_get_style_text_color(ui->widgets.dual_value[0], 0)) ==
           lv_color_to_u32(lv_color_hex(GAUGE_GEAR_PARK)));
    capture(argv[1], "dual-arc-gear-rpm");
    double reverse = 11;
    ui_set_value(ui, 0x5503, &reverse);
    tick(60);
    assert(lv_color_to_u32(lv_obj_get_style_text_color(ui->widgets.dual_value[0], 0)) ==
           lv_color_to_u32(lv_color_hex(GAUGE_GEAR_REVERSE)));
    capture(argv[1], "dual-arc-reverse");
    ui_set_source_state(ui, 0, false, 7, 1);
    ui_set_source_state(ui, 1, false, 7, 1);
    vehicle = false;
    ui_waiting_logo_enable(ui, true);
    tick(60);
    assert(ui->waiting_visible && ui_cycle_pause(ui) == PAGE_CYCLE_WAITING);
    capture(argv[1], "waiting-logo");
    /* Current waiting mark has exactly four retained objects. Warm rendering
     * before measuring LVGL arena stability, then redraw without allocating. */
    assert(lv_obj_get_child_count(boot_logo) == 3);
    assert(lv_obj_check_type(lv_obj_get_child(boot_logo, 0), &lv_arc_class));
    assert(lv_obj_check_type(lv_obj_get_child(boot_logo, 1), &lv_line_class));
    assert(object_count(boot_logo) == 4);
    assert(lv_color_to_u32(lv_obj_get_style_arc_color(lv_obj_get_child(boot_logo, 0), LV_PART_MAIN)) ==
           lv_color_to_u32(lv_color_hex(0x555555)));
    assert(lv_color_to_u32(lv_obj_get_style_line_color(lv_obj_get_child(boot_logo, 1), 0)) ==
           lv_color_to_u32(lv_color_hex(0x555555)));
    assert(lv_color_to_u32(lv_obj_get_style_bg_color(lv_obj_get_child(boot_logo, 2), 0)) ==
           lv_color_to_u32(lv_color_hex(0x555555)));
    unsigned         objects = object_count(lv_screen_active());
    lv_mem_monitor_t logo_before, logo_after;
    lv_mem_monitor(&logo_before);
    for (unsigned redraw = 0; redraw < 100; ++redraw)
    {
        ui_boot_logo_early();
        lv_obj_invalidate(boot_logo);
        tick(60);
        assert(ui->waiting_visible && lv_obj_get_child_count(boot_logo) == 3);
        assert(object_count(lv_screen_active()) == objects);
    }
    lv_mem_monitor(&logo_after);
    assert(logo_after.free_size == logo_before.free_size);
    assert(logo_after.free_biggest_size == logo_before.free_biggest_size);
    before = command_requests;
    boot_logo_touch(NULL);
    tick(60);
    assert(!ui->waiting_visible && command_requests == before);
    tick(DISPLAY_IDLE_TOUCH_MS + 1);
    assert(ui->waiting_visible);
    vehicle = true;
    ui_set_source_state(ui, 0, true, 7, source_generations[0]);
    ui_set_source_value(ui, 0, 7, source_generations[0], 0x0c, &idle_rpm, host_ms);
    tick(60);
    assert(!ui->waiting_visible);
    ui_waiting_logo_enable(ui, false);
    /* Live recording must preserve fractional data and reset unavailable runs. */
    ui_page_t trend_page          = saved;
    trend_page.renderer           = UI_RENDERER_TREND;
    trend_page.metrics[0].minimum = 0;
    trend_page.metrics[0].maximum = 100;
    ui_set_page(ui, &trend_page);
    double trend_value = 50.25;
    for (unsigned i = 0; i < 3; i++)
    {
        ui_set_value(ui, 12, &trend_value);
        tick(501);
    }
    assert(ui->display.trend_count == 3);
    assert(fabsf(ui->display.trend_samples[TREND_CAPACITY - 1] - 14.925f) < .001f);
    assert(!lv_obj_has_flag(ui->widgets.trend, LV_OBJ_FLAG_HIDDEN));
    if (!ui->display.trend_mask)
    {
        assert(ui->display.trend_points[0].x == 57 * px(144) / 59);
        assert(ui->display.trend_points[2].x == px(144));
    }
    ui_set_value(ui, 12, NULL);
    tick(60);
    assert(ui->display.trend_count == 0);
    assert(lv_obj_has_flag(ui->widgets.trend, LV_OBJ_FLAG_HIDDEN));
    ui_set_value(ui, 12, &trend_value);
    tick(501);
    assert(ui->display.trend_count == 1);
    assert(lv_obj_has_flag(ui->widgets.trend, LV_OBJ_FLAG_HIDDEN));
    tick(2001);
    assert(ui->display.trend_count == 0);
    ui_set_page(ui, &saved);
    assert(ui->display.trend_count == 0);
    presentation_regression(ui);
    ui_set_page(ui, &saved);
    /* Calibration pages exercise geometry/fonts outside the instrument compositor. */
    for (unsigned i = 0; i < CALIBRATION_PAGE_COUNT; i++)
    {
        ui->calibration_page = i;
        render_calibration_page(ui);
        char name[32];
        snprintf(name, sizeof(name), "calibration-%u", i + 1);
        capture(argv[1], name);
    }
    lv_mem_monitor_t memory;
    lv_mem_monitor(&memory);
    assert(memory.total_size <= 64U * 1024U && memory.free_size > 4096U);
    printf("LVGL fixed arena: total=%zu free=%zu biggest=%zu used_pct=%u\n", memory.total_size, memory.free_size,
           memory.free_biggest_size, memory.used_pct);
    puts("PASS: production LVGL showcase frames, circular glyphs, vehicle/maintenance/critical preemption and pairing "
         "restore");
}
