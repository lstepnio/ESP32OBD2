#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "orientation_runtime.h"

int              test_critical_depth;
static unsigned  calls;
static bool      fail, frozen, wrong_identity;
static uint32_t  timestamp;
static esp_err_t read_sensor(void *context, uint8_t reg, uint8_t *data, size_t length)
{
    (void)context;
    assert(test_critical_depth == 0);
    ++calls;
    if (fail)
        return ESP_FAIL;
    memset(data, 0, length);
    if (reg == 0)
    {
        assert(length == 1);
        data[0] = wrong_identity ? 0 : 5;
    }
    else
    {
        assert(reg == 0x30 && length == 11);
        if (!frozen)
            ++timestamp;
        data[0] = timestamp;
        data[1] = timestamp >> 8;
        data[2] = timestamp >> 16;
        data[6] = 0x40; /* X = +1g at +/-2g scale. */
    }
    return ESP_OK;
}
static esp_err_t write_sensor(void *context, uint8_t reg, uint8_t value)
{
    (void)context;
    assert(test_critical_depth == 0);
    assert((reg == 2 && value == 0x60) || (reg == 3 && value == 0x0e) || (reg == 8 && value == 1));
    ++calls;
    return fail ? ESP_FAIL : ESP_OK;
}
static void tick(uint32_t now, bool automatic, bool suspended)
{
    unsigned before = calls;
    uint8_t  angle;
    if (orientation_runtime_tick(now, automatic, 0, suspended, &angle))
        orientation_runtime_applied(angle);
    assert(calls - before <= 1);
}
int main(void)
{
    orientation_bus_t bus = {NULL, read_sensor, write_sensor};
    orientation_runtime_init_bus(0, bus, false);
    for (uint32_t now = 0; now < 2000; now += 100)
        tick(now, true, false);
    assert(!orientation_runtime_snapshot().usable);
    assert(orientation_runtime_snapshot().applied_angle == 0);
    orientation_runtime_init_bus(0, bus, true);
    assert(orientation_runtime_supported() && !orientation_runtime_snapshot().usable);
    assert(orientation_runtime_snapshot().sensor_status == 0);
    for (uint32_t now = 0; now < 2000; now += 100)
        tick(now, true, false);
    assert(orientation_runtime_snapshot().usable && orientation_runtime_snapshot().applied_angle == 1);
    unsigned before = calls;
    tick(2000, true, true);
    assert(calls == before);
    tick(3100, true, true);
    assert(orientation_runtime_snapshot().usable && orientation_runtime_snapshot().sensor_status == 2);
    assert(orientation_runtime_snapshot().applied_angle == 1);
    fail = true;
    tick(3200, true, false);
    before = calls;
    tick(4000, true, false);
    assert(calls == before);
    fail = false;
    for (uint32_t now = 8200; now < 10200; now += 100)
        tick(now, true, false);
    assert(orientation_runtime_snapshot().usable && orientation_runtime_snapshot().applied_angle == 1);
    frozen = true;
    for (uint32_t now = 10200; now < 11600; now += 100)
        tick(now, true, false);
    assert(orientation_runtime_snapshot().usable && orientation_runtime_snapshot().sensor_status == 2 &&
           orientation_runtime_snapshot().applied_angle == 1);
    tick(11600, false, true);
    assert(orientation_runtime_snapshot().applied_angle == 0);
    wrong_identity = true;
    frozen         = false;
    orientation_runtime_init_bus(0, bus, true);
    tick(0, true, false);
    before = calls;
    tick(1000, true, false);
    assert(calls == before && !orientation_runtime_snapshot().usable);
    /* A rejected board rotation is never published as applied. Owner retries
     * are bounded independently of its 20ms loop, including timestamp wrap. */
    orientation_runtime_applied(0);
    orientation_runtime_apply_failed(2000);
    assert(orientation_runtime_snapshot().applied_angle == 0);
    assert(!orientation_runtime_retry_ready(2020));
    assert(!orientation_runtime_retry_ready(2999));
    assert(orientation_runtime_retry_ready(3000));
    orientation_runtime_applied(2);
    assert(orientation_runtime_snapshot().applied_angle == 2 && orientation_runtime_retry_ready(3001));
    orientation_runtime_apply_failed(UINT32_MAX - 500);
    assert(!orientation_runtime_retry_ready(498));
    assert(orientation_runtime_retry_ready(499));
    orientation_runtime_applied(1);
    assert(orientation_runtime_retry_ready(500));
    uint8_t requested_angle = 255;
    assert(!orientation_runtime_poll(false, 2, true, &requested_angle));
    assert(requested_angle == 255 && orientation_runtime_snapshot().applied_angle == 1);
    assert(orientation_runtime_poll(false, 2, false, &requested_angle) && requested_angle == 2);
    assert(test_critical_depth == 0);
    puts("Orientation runtime: qualification, bounded admission, suspension, failure, retry and frozen clock passed");
}
