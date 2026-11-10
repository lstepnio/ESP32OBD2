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

static void review_frame(ui_t *ui, const char *directory, const char *name, unsigned y, unsigned expected_links)
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
    static const uint32_t colors[] = {0x8b9a9d, 0x538dbb, 0x5d9f78, 0x9382b4};
    int                   x        = 128;
    for (unsigned links = expected_links; links; links &= links - 1)
        x -= 8;
    for (unsigned i = 0; i < sizeof(colors) / sizeof(colors[0]); ++i)
    {
        if (expected_links & (1U << i))
        {
            assert(y && footer_color_at(x, y, colors[i]));
            x += 16;
        }
        else
            for (unsigned sample = 96; sample <= 144; sample += 8)
                assert(!footer_color_at(sample, FOOTER_Y, colors[i]));
    }
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
    /* A leftover ready bit cannot expose a child without a saved binding. */
    ui_set_connection_status(ui, false, false, false, 2);
    assert(!atomic_load(&ui->connection_links));
    review_frame(ui, directory, "01-splash-waiting", 0, 0);
    ui_set_connection_status(ui, false, true, false, 0);
    review_frame(ui, directory, "02-splash-phone-connected", FOOTER_Y, 2);
    ui_set_connection_status(ui, true, false, true, 0);
    review_frame(ui, directory, "03-splash-wifi-connected", FOOTER_Y, 1);
    ui_set_connection_status(ui, false, false, false, 0);
    review_frame(ui, directory, "04-splash-links-cleared", 0, 0);
    ui_waiting_logo_enable(ui, false);

    const struct
    {
        const char   *name;
        ui_renderer_t renderer;
        unsigned      adapters, ready;
        bool          phone, wifi, sample;
    } cases[] = {
        {"05-numeric-obd", UI_RENDERER_NUMERIC, 1, 1, false, false, true},
        {"06-numeric-phone-obd", UI_RENDERER_NUMERIC, 1, 1, true, false, true},
        {"07-numeric-both-obd", UI_RENDERER_NUMERIC, 2, 3, true, false, true},
        {"08-numeric-child-lost", UI_RENDERER_NUMERIC, 2, 1, true, false, true},
        {"09-numeric-primary-lost", UI_RENDERER_NUMERIC, 2, 2, true, false, false},
        {"10-numeric-no-links", UI_RENDERER_NUMERIC, 2, 0, false, false, true},
        {"11-arc-obd", UI_RENDERER_ARC, 1, 1, false, false, true},
        {"12-arc-dual-obd", UI_RENDERER_ARC, 2, 3, false, false, true},
        {"13-bar", UI_RENDERER_BAR, 1, 1, true, false, true},
        {"14-trend", UI_RENDERER_TREND, 2, 3, true, false, true},
        {"15-dual", UI_RENDERER_DUAL, 2, 3, false, false, true},
        {"16-dual-arc", UI_RENDERER_DUAL_ARC, 2, 3, false, false, true},
        {"17-numeric-wifi-maintenance", UI_RENDERER_NUMERIC, 2, 0, true, true, false},
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
        ui_set_connection_status(ui, cases[i].wifi, cases[i].phone, cases[i].adapters > 1, cases[i].ready);
        if (page.renderer == UI_RENDERER_TREND)
            for (unsigned step = 0; step < 60; ++step)
            {
                double value = rpm + 80 * sin(step * 0.7);
                ui_set_value(ui, 12, &value);
                tick(500);
            }
        unsigned expected = (cases[i].wifi ? 1U : 0U) | (cases[i].phone ? 2U : 0U) | (cases[i].ready << 2);
        unsigned y        = expected ? FOOTER_Y : 0;
        review_frame(ui, directory, cases[i].name, y, expected);
    }

    ui_set_value(ui, 12, &rpm);
    ui_set_diagnostics(ui, true, true, false, 3, "P0123");
    review_frame(ui, directory, "18-check-engine", 0, 0);
    ui_set_diagnostics(ui, false, false, false, 0, "");
    ui_set_alert_event(ui, 1, false, "Coolant", false, 1, 1, 1, 112, 105, "degC");
    review_frame(ui, directory, "19-threshold-warning", 0, 0);
    ui_set_alert_event(ui, 2, false, "Coolant", true, 1, 1, 1, 118, 110, "degC");
    review_frame(ui, directory, "20-critical-alert", 0, 0);
    ui_set_alert(ui, 0, false, "");
    ui_show_pairing_code(ui, UI_PAIRING_READY);
    review_frame(ui, directory, "21-pairing", 0, 0);
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
    review_frame(ui, directory, "22-action-ready", 0, 0);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_ACTIVE, 1, 287, 0, 0);
    review_frame(ui, directory, "23-action-active", 0, 0);
    ui_set_vehicle_action_status(ui, 1, VEHICLE_UNKNOWN, 1, 0, 0, 0);
    review_frame(ui, directory, "24-action-recovery", 0, 0);

    puts("PASS: 24 production footer captures, live link colors, and display priorities");
}
