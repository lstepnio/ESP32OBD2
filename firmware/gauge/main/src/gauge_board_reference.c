#include "gauge_board.h"
#ifdef ESP_PLATFORM
#include "sdkconfig.h"
#endif
#include "bsp_init.h"
#include "bsp_lcd.h"
#include "bsp_lvgl.h"

static const gauge_board_t reference_board = {
    .id                 = "waveshare-esp32-s3-touch-lcd-1.28",
    .surface            = &gauge_reference_surface,
    .display_transport  = GAUGE_DISPLAY_SPI,
    .brightness_control = GAUGE_BRIGHTNESS_PWM,
};

const gauge_board_t *gauge_board_description(void)
{
    return &reference_board;
}

void gauge_board_initialize(void)
{
    /* Preserve the working bootstrap sequence and the BSP's existing error policy. */
    bsp_init();
    bsp_display_start();
    bsp_lvgl_init();
    bsp_touch_init();
}

esp_err_t gauge_display_set_power(bool on)
{
    return bsp_display_on_off(on);
}

esp_err_t gauge_display_set_brightness(uint8_t percent)
{
    return bsp_display_backlight_set_percent(percent);
}

esp_err_t gauge_display_set_rotation(lv_display_rotation_t rotation)
{
    return bsp_lv_disp_set_rotation(rotation);
}

/* Kept in the sensor adapter so display-only host fixtures remain independent. */
extern bool orientation_qmi8658_bus(orientation_bus_t *bus);
bool        gauge_board_orientation_bus(orientation_bus_t *bus)
{
    return orientation_qmi8658_bus(bus);
}
bool gauge_board_orientation_qualified(void)
{
#ifdef CONFIG_EGAUGE_AUTO_ORIENTATION_QUALIFIED
    return CONFIG_EGAUGE_AUTO_ORIENTATION_QUALIFIED;
#else
    return false;
#endif
}
