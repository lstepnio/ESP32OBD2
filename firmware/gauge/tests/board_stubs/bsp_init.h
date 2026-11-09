#pragma once
#include "esp_err.h"
esp_err_t bsp_init(void);
void     *bsp_touch_shared_i2c_bus(void);
