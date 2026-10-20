#include <assert.h>
#include <string.h>
#include "gauge_board.h"

static unsigned              calls[4], count;
static int                   backend_result;
static uint8_t               brightness;
static bool                  power;
static lv_display_rotation_t angle;

esp_err_t bsp_init(void)
{
    calls[count++] = 1;
    return ESP_OK;
}
esp_err_t bsp_display_start(void)
{
    calls[count++] = 2;
    return ESP_OK;
}
esp_err_t bsp_lvgl_init(void)
{
    calls[count++] = 3;
    return ESP_OK;
}
esp_err_t bsp_touch_init(void)
{
    calls[count++] = 4;
    return ESP_OK;
}
esp_err_t bsp_display_on_off(bool on)
{
    power = on;
    return backend_result;
}
esp_err_t bsp_display_backlight_set_percent(uint8_t percent)
{
    brightness = percent;
    return backend_result;
}
esp_err_t bsp_lv_disp_set_rotation(lv_display_rotation_t rotation)
{
    angle = rotation;
    return backend_result;
}

int main(void)
{
    const gauge_board_t *board = gauge_board_description();
    assert(!strcmp(board->id, "waveshare-esp32-s3-touch-lcd-1.28"));
    assert(board->surface == &gauge_reference_surface);
    assert(board->display_transport == GAUGE_DISPLAY_SPI);
    assert(board->brightness_control == GAUGE_BRIGHTNESS_PWM);
    gauge_board_initialize();
    assert(count == 4);
    for (unsigned i = 0; i < count; ++i)
        assert(calls[i] == i + 1);
    for (int result = 0; result >= -1; --result)
    {
        backend_result = result;
        assert(gauge_display_set_power(true) == result && power);
        assert(gauge_display_set_power(false) == result && !power);
        for (unsigned percent = 0; percent <= 101; ++percent)
            assert(gauge_display_set_brightness(percent) == result && brightness == percent);
        for (unsigned rotation = 0; rotation < 4; ++rotation)
            assert(gauge_display_set_rotation(rotation) == result && angle == (lv_display_rotation_t)rotation);
    }
    return 0;
}
