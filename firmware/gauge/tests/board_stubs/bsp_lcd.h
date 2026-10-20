#pragma once
#include "gauge_board.h"
esp_err_t bsp_display_start(void);
esp_err_t bsp_display_on_off(bool on);
esp_err_t bsp_display_backlight_set_percent(uint8_t percent);
esp_err_t bsp_lv_disp_set_rotation(lv_display_rotation_t rotation);
esp_err_t bsp_touch_init(void);
