#pragma once

#ifdef __cplusplus
extern "C"
{
#endif

#include "esp_system.h"

    esp_err_t bsp_init(void);

    /* Borrowed bus owned by touch initialization; callers must not delete it. */
    void *bsp_touch_shared_i2c_bus(void);

#ifdef __cplusplus
}
#endif
