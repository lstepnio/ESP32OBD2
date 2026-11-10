static bool footer_color_at(unsigned x, unsigned y, uint32_t color)
{
    for (int row = gy(y - 5); row <= gy(y + 5); ++row)
        for (int col = gx(x - 5); col <= gx(x + 5); ++col)
        {
            uint32_t pixel = pixels[row * width + col];
            if (abs((int)((pixel >> 16) & 255) - (int)((color >> 16) & 255)) <= 8 &&
                abs((int)((pixel >> 8) & 255) - (int)((color >> 8) & 255)) <= 8 &&
                abs((int)(pixel & 255) - (int)(color & 255)) <= 8)
                return true;
        }
    return false;
}

static void review_frame(ui_t *ui, const char *directory, const char *name, unsigned y)
{
    tick(60);
    unsigned rendered_y = ui->connection_footer_y;
    if (rendered_y && !ui->waiting_visible)
    {
        lv_area_t footer = connection_footer_area(rendered_y);
        if (!footer_clear(ui, &footer))
            rendered_y = 0;
    }
    if (rendered_y != y)
        fprintf(stderr, "%s: expected footer at %u, got %u\n", name, y, rendered_y);
    assert(rendered_y == y);
    if (y == FOOTER_STACKED_Y)
    {
        lv_area_t dots;
        lv_obj_get_coords(ui->widgets.page_dots[0], &dots);
        assert(gy(y + 6) + px(FOOTER_DOT_GAP) < dots.y1);
    }
    if (y && (ui->rendered_connections & (2U << FOOTER_ACTIVE_SHIFT)))
        assert(footer_color_at(112, y, 0x67aee8));
    else if (!y)
        assert(!footer_color_at(112, FOOTER_SINGLE_Y, 0x67aee8) && !footer_color_at(112, FOOTER_STACKED_Y, 0x67aee8));
    capture(directory, name);
}

static void review_connections(const char *directory)
{
    ui_page_t page = {
        .name         = "RPM",
        .renderer     = UI_RENDERER_NUMERIC,
        .metric_count = 1,
        .metrics =
            {
                {.pid = 12, .name = "Engine RPM", .unit = "rpm", .maximum = 8000, .stale_after_ms = 12000},
                {.pid = 5, .name = "Coolant", .unit = "degC", .minimum = 40, .maximum = 125, .stale_after_ms = 12000},
            },
    };
    ui_t *ui = ui_init(&page, 50, touch, false, false);
    assert(ui);
    ui_waiting_logo_enable(ui, true);
    ui_set_connection_status(ui, false, false, 1, 0);
    review_frame(ui, directory, "01-splash-waiting", FOOTER_SINGLE_Y);
    ui_set_connection_status(ui, false, true, 1, 0);
    review_frame(ui, directory, "02-splash-phone-connected", FOOTER_SINGLE_Y);
    ui_set_connection_status(ui, true, false, 2, 0);
    review_frame(ui, directory, "03-splash-wifi-connected", FOOTER_SINGLE_Y);
    assert(footer_color_at(97, FOOTER_SINGLE_Y, 0xacbebd));
    ui_waiting_logo_enable(ui, false);

    const struct
    {
        const char   *name;
        ui_renderer_t renderer;
        unsigned      pages, adapters, ready;
        bool          phone, wifi, sample;
    } cases[] = {
        {"04-numeric-single-obd", UI_RENDERER_NUMERIC, 1, 1, 1, false, false, true},
        {"05-numeric-single-phone", UI_RENDERER_NUMERIC, 1, 1, 1, true, false, true},
        {"06-numeric-stacked-dual-obd", UI_RENDERER_NUMERIC, 5, 2, 3, true, false, true},
        {"07-numeric-stacked-child-lost", UI_RENDERER_NUMERIC, 5, 2, 1, true, false, true},
        {"08-numeric-stacked-primary-lost", UI_RENDERER_NUMERIC, 5, 2, 2, true, false, false},
        {"09-numeric-page-counter", UI_RENDERER_NUMERIC, 10, 2, 3, true, false, true},
        {"10-arc-single", UI_RENDERER_ARC, 1, 1, 1, false, false, true},
        {"11-arc-stacked", UI_RENDERER_ARC, 5, 2, 3, false, false, true},
        {"12-bar-stacked", UI_RENDERER_BAR, 5, 1, 1, true, false, true},
        {"13-trend-stacked", UI_RENDERER_TREND, 5, 2, 3, true, false, true},
        {"14-dual-single", UI_RENDERER_DUAL, 1, 2, 3, false, false, true},
        {"15-dual-arc-stacked", UI_RENDERER_DUAL_ARC, 5, 2, 3, false, false, true},
        {"16-dual-arc-page-counter", UI_RENDERER_DUAL_ARC, 12, 2, 3, true, false, true},
        {"17-numeric-wifi-maintenance", UI_RENDERER_NUMERIC, 5, 2, 0, true, true, false},
    };
    const ui_metric_t engine_speed = page.metrics[0];
    double            rpm = 2840, coolant = 92;
    for (unsigned i = 0; i < sizeof(cases) / sizeof(cases[0]); ++i)
    {
        page.renderer        = cases[i].renderer;
        bool temperature_arc = page.renderer == UI_RENDERER_ARC;
        page.name            = temperature_arc ? "Coolant" : "RPM";
        page.metrics[0]      = temperature_arc ? page.metrics[1] : engine_speed;
        page.metric_count    = (page.renderer == UI_RENDERER_DUAL || page.renderer == UI_RENDERER_DUAL_ARC) ? 2 : 1;
        ui_set_value(ui, 12, cases[i].sample ? &rpm : NULL);
        ui_set_value(ui, 5, &coolant);
        ui_set_page(ui, &page);
        ui_set_page_position(ui, cases[i].pages > 8 ? 9 : 0, cases[i].pages);
        ui_set_connection_status(ui, cases[i].wifi, cases[i].phone, cases[i].adapters, cases[i].ready);
        if (page.renderer == UI_RENDERER_TREND)
            for (unsigned step = 0; step < 60; ++step)
            {
                double value = rpm + 80 * sin(step * 0.7);
                ui_set_value(ui, 12, &value);
                tick(500);
            }
        bool     crowded = temperature_arc || page.renderer == UI_RENDERER_BAR || page.renderer == UI_RENDERER_TREND;
        unsigned y = cases[i].pages == 1 ? FOOTER_SINGLE_Y : cases[i].pages <= 8 && !crowded ? FOOTER_STACKED_Y : 0;
        review_frame(ui, directory, cases[i].name, y);
        if (y)
        {
            if (cases[i].ready & 1)
                assert(footer_color_at(128, y, 0x73c593));
            if (cases[i].ready & 2)
                assert(footer_color_at(143, y, 0xb6a0df));
            else
                assert(!footer_color_at(143, y, 0xb6a0df));
        }
    }

    ui_set_value(ui, 12, &rpm);
    ui_set_diagnostics(ui, true, true, false, 3, "P0123");
    review_frame(ui, directory, "18-check-engine", 0);
    ui_set_diagnostics(ui, false, false, false, 0, "");
    ui_set_alert_event(ui, 1, false, "Coolant", false, 1, 1, 1, 112, 105, "degC");
    review_frame(ui, directory, "19-threshold-warning", 0);
    ui_set_alert_event(ui, 2, false, "Coolant", true, 1, 1, 1, 118, 110, "degC");
    review_frame(ui, directory, "20-critical-alert", 0);
    ui_set_alert(ui, 0, false, "");
    ui_show_pairing_code(ui, UI_PAIRING_READY);
    review_frame(ui, directory, "21-pairing", 0);
    ui_show_pairing_code(ui, UI_PAIRING_HIDDEN);
    tick(60);

    ui_configure_page_action(ui, (page_action_config_t){
                                     .enabled        = true,
                                     .count          = 3,
                                     .window_ms      = 5000,
                                     .vehicle_menu   = true,
                                     .air_up_seconds = 300,
                                     .winch_seconds  = 60,
                                 });
    ui_set_action_context(ui, true);
    ui_set_idle_protection(ui, true);
    ui_show_vehicle_actions(ui);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_READY, 1, 0, 0, 0);
    review_frame(ui, directory, "22-action-ready", 0);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_ACTIVE, 1, 287, 0, 0);
    review_frame(ui, directory, "23-action-active", 0);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_UNKNOWN, 1, 0, 0, 0);
    review_frame(ui, directory, "24-action-recovery", 0);

    puts("PASS: 24 production footer captures, live link colors, and display priorities");
}
