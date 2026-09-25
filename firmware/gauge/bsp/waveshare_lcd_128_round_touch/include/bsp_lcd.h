#pragma once

#ifdef __cplusplus
extern "C"
{
#endif

#include <stdbool.h>
#include <stdint.h>

#include "esp_system.h"

#include "display/lv_display.h"

    esp_err_t bsp_display_start(void);
    esp_err_t bsp_display_on_off(bool on);
    esp_err_t bsp_display_backlight_set(bool on);
    esp_err_t bsp_lv_disp_set_rotation(lv_display_rotation_t rotation);

    esp_err_t bsp_touch_init(void);

#ifdef __cplusplus
}
#endif
