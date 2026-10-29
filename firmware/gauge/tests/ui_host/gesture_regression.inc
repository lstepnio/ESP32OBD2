/* Actual LVGL pointer dispatch through the production screen callback. */
static unsigned gesture_clicks, gesture_holds, gesture_jumps, gesture_resets;
static void     gesture_touch(ui_t *ui, lv_event_code_t event)
{
    if (event == LV_EVENT_CLICKED)
        ++gesture_clicks;
    if (event == LV_EVENT_LONG_PRESSED)
        ++gesture_holds;
    if (event == LV_EVENT_RELEASED || event == LV_EVENT_VALUE_CHANGED)
        ++gesture_resets;
    if (event == UI_EVENT_PAGE_ACTION)
    {
        ++gesture_jumps;
        ui_action_applied(ui);
    }
}
static lv_point_t finger;
static bool       finger_down;
static void       read_finger(lv_indev_t *input, lv_indev_data_t *data)
{
    (void)input;
    data->point = finger;
    data->state = finger_down ? LV_INDEV_STATE_PRESSED : LV_INDEV_STATE_RELEASED;
}
static void pointer(lv_indev_t *input, int x, int y, bool down, unsigned ms)
{
    host_ms += ms;
    lv_tick_inc(ms);
    finger      = (lv_point_t){gx(x), gy(y)};
    finger_down = down;
    lv_indev_read(input);
    tick(0);
}
static void stroke(lv_indev_t *input, int dx, unsigned duration)
{
    pointer(input, 120, 165, true, 40);
    pointer(input, 120 + dx, 120, true, duration);
    pointer(input, 120 + dx, 100, false, 0);
}
static void test_gestures(ui_t *ui, lv_display_t *display)
{
    lv_indev_t *input = lv_indev_create();
    lv_indev_set_type(input, LV_INDEV_TYPE_POINTER);
    lv_indev_set_display(input, display);
    lv_indev_set_read_cb(input, read_finger);
    lv_indev_set_mode(input, LV_INDEV_MODE_EVENT);
    ui->touch_cb  = gesture_touch;
    ui->simulated = false;
    ui_configure_page_action(ui,
                             (page_action_config_t){.enabled = true, .target_page = 1, .count = 3, .window_ms = 5000});
    ui_set_action_context(ui, true);
    ui_show_pairing_code(ui, UI_PAIRING_HIDDEN);
    tick(60);
    pointer(input, 120, 160, true, 60);
    assert(ui_action_pending(ui));
    pointer(input, 120, 160, false, 100);
    assert(gesture_clicks == 1 && !gesture_holds && !gesture_jumps);
    stroke(input, 0, 60);
    assert(ui->action.progress == 1 && ui_action_pending(ui));
    ui_show_pairing_code(ui, UI_PAIRING_HIDDEN);
    tick(60);
    assert(ui->action.progress == 1);
    ui_set_diagnostics(ui, true, true, false, 4, "P0123");
    tick(60);
    assert(!lv_obj_has_flag(ui->action_label, LV_OBJ_FLAG_HIDDEN));
    assert(!strcmp(lv_label_get_text(ui->action_label), "1/3"));
    assert(!lv_obj_has_flag(ui->widgets.diagnostics_icon, LV_OBJ_FLAG_HIDDEN));
    stroke(input, 0, 100);
    stroke(input, 0, 100);
    assert(gesture_jumps == 1 && gesture_clicks == 1 && !gesture_holds);
    stroke(input, 0, 100);
    tick(60);
    assert(gesture_jumps == 1 && !strcmp(lv_label_get_text(ui->action_label), "Wait"));
    tick(5100);
    assert(!ui->action_feedback);
    stroke(input, 35, 100);
    assert(!ui->action.progress && gesture_clicks == 1 && !gesture_holds);
    pointer(input, 120, 165, true, 60);
    pointer(input, 120, 145, true, 250);
    pointer(input, 120, 120, true, 250);
    pointer(input, 120, 100, false, 100);
    assert(ui->action.progress == 1 && !gesture_holds);
    /* A slow moved stroke must not become a hold or owner-reset release. */
    pointer(input, 120, 165, true, 60);
    pointer(input, 120, 145, true, 100);
    pointer(input, 120, 145, true, 12000);
    pointer(input, 120, 100, false, 100);
    assert(!ui->action.progress && !gesture_holds && !gesture_resets && gesture_clicks == 1);
    pointer(input, 120, 165, true, 60);
    pointer(input, 120, 145, true, 80);
    lv_obj_send_event(lv_screen_active(), LV_EVENT_PRESS_LOST, NULL);
    pointer(input, 120, 100, false, 60);
    assert(!ui->action.progress && gesture_clicks == 1 && gesture_jumps == 1);
    pointer(input, 75, 120, true, 60);
    pointer(input, 140, 120, true, 80);
    lv_obj_send_event(lv_screen_active(), LV_EVENT_PRESS_LOST, NULL);
    pointer(input, 160, 120, false, 60);
    assert(gesture_clicks == 1);
    ui_show_pairing_code(ui, UI_PAIRING_RESET_CONFIRM);
    tick(60);
    stroke(input, 35, 100);
    assert(!gesture_resets);
    /* Browsing fallbacks must not confirm reset, with or without a binding. */
    pointer(input, 75, 120, true, 60);
    pointer(input, 140, 120, true, 100);
    pointer(input, 160, 120, false, 60);
    assert(!gesture_resets);
    ui_configure_page_action(ui, (page_action_config_t){0});
    stroke(input, 35, 100);
    assert(!gesture_resets);
    pointer(input, 75, 120, true, 60);
    pointer(input, 140, 120, true, 100);
    pointer(input, 160, 120, false, 60);
    assert(!gesture_resets);
    pointer(input, 120, 165, true, 60);
    pointer(input, 120, 145, true, 100);
    pointer(input, 120, 145, true, 12000);
    pointer(input, 120, 100, false, 100);
    assert(!gesture_resets && !gesture_holds);
    pointer(input, 120, 160, true, 60);
    pointer(input, 120, 160, false, 100);
    assert(gesture_resets == 1 && gesture_clicks == 1 && !gesture_holds);
    ui_configure_page_action(ui,
                             (page_action_config_t){.enabled = true, .target_page = 1, .count = 3, .window_ms = 5000});
    ui_show_pairing_code(ui, UI_PAIRING_HIDDEN);
    tick(60);
    stroke(input, 0, 100);
    assert(ui->action.progress == 1);
    ui_set_alert_full(ui, 2, false, "Coolant", true);
    tick(60);
    assert(!ui->action.progress);
    ui_set_alert(ui, 0, false, "");
    tick(60);
    stroke(input, 0, 100);
    tick(5001);
    assert(!ui_action_pending(ui));
    stroke(input, 0, 100);
    ui_set_action_context(ui, false);
    tick(60);
    assert(!ui_action_pending(ui));
    ui_set_action_context(ui, true);
    ui_show_pairing_code(ui, UI_PAIRING_READY);
    tick(60);
    stroke(input, 0, 100);
    assert(gesture_jumps == 1 && !ui->action.progress && gesture_clicks == 1);
    ui_show_pairing_code(ui, UI_PAIRING_HIDDEN);
    tick(60);
    pointer(input, 120, 160, true, 60);
    pointer(input, 120, 160, true, 500);
    pointer(input, 120, 160, false, 60);
    assert(gesture_holds == 1 && gesture_clicks == 1);
    pointer(input, 75, 120, true, 60);
    pointer(input, 140, 120, true, 100);
    pointer(input, 160, 120, false, 60);
    assert(gesture_clicks == 2);
    ui_configure_page_action(ui, (page_action_config_t){0});
    stroke(input, 0, 100);
    assert(gesture_clicks == 3);
    /* Preserve the newer action arrows and cancellation before deferred entry. */
    ui_configure_page_action(
        ui, (page_action_config_t){.enabled = true, .count = 3, .window_ms = 5000, .vehicle_menu = true});
    stroke(input, 0, 60);
    tick(60);
    assert(ui->action.progress == 1 && !lv_obj_has_flag(ui->action_arrows[0], LV_OBJ_FLAG_HIDDEN));
    assert(lv_obj_has_flag(ui->action_label, LV_OBJ_FLAG_HIDDEN));
    stroke(input, 0, 60);
    stroke(input, 0, 60);
    assert(ui->action_enter_pending);
    ui_set_alert_full(ui, 2, false, "Coolant", true);
    tick(400);
    assert(!ui->action_enter_pending && gesture_jumps == 1);
    ui_set_alert(ui, 0, false, "");
    ui_set_diagnostics(ui, false, false, false, 0, "");
    ui_set_action_context(ui, false);
    ui_configure_page_action(ui, (page_action_config_t){0});
    tick(60);
    lv_indev_delete(input);
    puts("PASS: production pointer taps, fast/slow/diagonal strokes, reconnect, cooldown, lost input and hold "
         "arbitration");
}
