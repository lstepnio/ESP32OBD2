#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"
#include "display/lv_display.h"
#include "gauge_surface.h"
#include "orientation_runtime.h"

typedef enum
{
    GAUGE_DISPLAY_SPI = 0,
    GAUGE_DISPLAY_QSPI,
    GAUGE_DISPLAY_RGB,
} gauge_display_transport_t;

typedef enum
{
    GAUGE_BRIGHTNESS_PWM = 0,
    GAUGE_BRIGHTNESS_PANEL,
} gauge_brightness_control_t;

typedef struct
{
    const char                *id;
    const gauge_surface_t     *surface;
    gauge_display_transport_t  display_transport;
    gauge_brightness_control_t brightness_control;
} gauge_board_t;

const gauge_board_t *gauge_board_description(void);
void                 gauge_board_initialize(void);
esp_err_t            gauge_display_set_power(bool on);
esp_err_t            gauge_display_set_brightness(uint8_t percent);
esp_err_t            gauge_display_set_rotation(lv_display_rotation_t rotation);

/* Optional board seam. Reuse the touch-owned I2C bus; never create a second bus. */
bool gauge_board_orientation_bus(orientation_bus_t *bus);
bool gauge_board_orientation_qualified(void);
