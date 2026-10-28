#include "orientation_runtime.h"
#include "bsp_init.h"
#include "driver/i2c_master.h"

/* Register meanings and addresses 0x6a/0x6b follow Waveshare's QMI8658 example in
 * ESP32-S3-Touch-LCD-1.28-Demo.zip. No reset, gyro, FIFO or interrupt ownership.
 * Each synchronous transaction has a 10ms IDF timeout. IDF reuses that timeout internally, so this is
 * not a proven wall-clock bound; keep the physical qualification gate off. */
typedef struct
{
    i2c_master_dev_handle_t devices[2];
    unsigned                selected;
} sensor_bus_t;
static esp_err_t sensor_read(void *context, uint8_t reg, uint8_t *bytes, size_t length)
{
    sensor_bus_t *bus = context;
    esp_err_t     err = i2c_master_transmit_receive(bus->devices[bus->selected], &reg, 1, bytes, length, 10);
    /* The vendor example accepts both strap addresses. Probe only one on each
     * bounded retry; never issue a second transfer from this call. */
    if (reg == 0 && (err != ESP_OK || bytes[0] != 5))
        bus->selected ^= 1;
    return err;
}
static esp_err_t sensor_write(void *context, uint8_t reg, uint8_t value)
{
    sensor_bus_t *bus     = context;
    uint8_t       bytes[] = {reg, value};
    return i2c_master_transmit(bus->devices[bus->selected], bytes, sizeof(bytes), 10);
}
bool orientation_qmi8658_bus(orientation_bus_t *bus)
{
    static sensor_bus_t     sensor;
    i2c_master_bus_handle_t shared = bsp_touch_shared_i2c_bus();
    if (!shared || !bus)
        return false;
    for (unsigned index = 0; index < 2; ++index)
    {
        if (sensor.devices[index])
            continue;
        const i2c_device_config_t config = {.dev_addr_length = I2C_ADDR_BIT_LEN_7,
                                            .device_address  = 0x6a + index,
                                            .scl_speed_hz    = 100000,
                                            .scl_wait_us     = 1000};
        if (i2c_master_bus_add_device(shared, &config, &sensor.devices[index]) != ESP_OK)
            return false;
    }
    *bus = (orientation_bus_t){&sensor, sensor_read, sensor_write};
    return true;
}
