#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"
#include "orientation_policy.h"

typedef struct
{
    void *context;
    esp_err_t (*read)(void *context, uint8_t reg, uint8_t *bytes, size_t length);
    esp_err_t (*write)(void *context, uint8_t reg, uint8_t value);
} orientation_bus_t;
typedef struct
{
    uint8_t applied_angle;
    uint8_t sensor_status; /* 0 unsupported/unqualified, 1 healthy, 2 stale */
    bool    usable;
} orientation_status_t;
/* Main runtime owner only. Initialization does not perform sensor transactions. */
void orientation_runtime_init(uint8_t manual_angle);
/* Fake bus seam exercises the exact same bounded state machine offline. */
void orientation_runtime_init_bus(uint8_t manual_angle, orientation_bus_t bus, bool qualified);
/* One short shared-bus transaction per call, 100ms sample cadence. The owner
 * skips I/O during maintenance/control work. Returns a desired new angle only. */
bool orientation_runtime_tick(uint32_t now_ms, bool automatic, uint8_t manual_angle, bool suspended, uint8_t *angle);
/* Record only an angle successfully applied with its touch transform. */
void orientation_runtime_applied(uint8_t angle);
/* Failed board application keeps applied truth and limits owner retries to 1Hz. */
void orientation_runtime_apply_failed(uint32_t now_ms);
bool orientation_runtime_retry_ready(uint32_t now_ms);
/* Stable protocol support after qualified board transport initialization; sensor
 * availability remains a separate snapshot field and can recover without rediscovery. */
bool orientation_runtime_supported(void);
/* Lock-free in the calling sense: short snapshot critical section, no bus/NVS. */
orientation_status_t orientation_runtime_snapshot(void);

/* Main/render owner: never touches I2C or waits on the worker. */
bool orientation_runtime_poll(bool automatic, uint8_t manual_angle, bool suspended, uint8_t *angle);
/* Bytes of minimum observed worker stack remaining, zero when no worker. */
uint16_t orientation_runtime_stack_margin(void);
