#include "orientation_runtime.h"
#include "gauge_board.h"
#include "freertos/FreeRTOS.h"
#ifdef ESP_PLATFORM
#include "sdkconfig.h"
#include "freertos/task.h"
#include "esp_attr.h"
#include "esp_log.h"
#endif

__attribute__((weak)) bool gauge_board_orientation_bus(orientation_bus_t *transport)
{
    (void)transport;
    return false;
}
__attribute__((weak)) bool gauge_board_orientation_qualified(void)
{
    return false;
}

static orientation_bus_t    bus;
static orientation_policy_t policy;
static orientation_status_t status;
static portMUX_TYPE         mux = portMUX_INITIALIZER_UNLOCKED;
static uint8_t              phase, desired_auto_angle;
static bool                 requested_auto, requested_suspended;
static uint8_t              requested_manual;
static uint16_t             stack_margin;
static bool                 apply_retry_pending;
static uint32_t             apply_failed_at;
static void                 start_worker(void);
static bool                 qualified, have_timestamp;
static uint32_t             next_io, last_timestamp, timestamp_since;

static void publish(uint32_t now)
{
    bool healthy = phase == 4 && orientation_policy_healthy(&policy, now);
    portENTER_CRITICAL(&mux);
    status.usable        = qualified && (status.usable || healthy);
    status.sensor_status = !qualified || !bus.read || !status.usable ? 0 : healthy ? 1 : 2;
    portEXIT_CRITICAL(&mux);
}
void orientation_runtime_init_bus(uint8_t manual, orientation_bus_t transport, bool physically_qualified)
{
    bus       = transport;
    qualified = physically_qualified;
    phase     = 0;
    next_io = last_timestamp = timestamp_since = 0;
    have_timestamp                             = false;
    desired_auto_angle                         = manual;
    requested_auto = requested_suspended = false;
    requested_manual                     = manual;
    stack_margin                         = 0;
    apply_retry_pending                  = false;
    apply_failed_at                      = 0;
    orientation_policy_init(&policy, manual);
    portENTER_CRITICAL(&mux);
    status = (orientation_status_t){.applied_angle = policy.applied};
    portEXIT_CRITICAL(&mux);
}
void orientation_runtime_init(uint8_t manual)
{
    orientation_bus_t transport = {0};
    bool              ready     = false;
#if defined(ESP_PLATFORM) && CONFIG_EGAUGE_AUTO_ORIENTATION_PROBE_ENABLED
    ready = gauge_board_orientation_bus(&transport);
#endif
    orientation_runtime_init_bus(manual, transport, ready && gauge_board_orientation_qualified());
    start_worker();
}
bool orientation_runtime_supported(void)
{
    return qualified && bus.read && bus.write;
}
orientation_status_t orientation_runtime_snapshot(void)
{
    portENTER_CRITICAL(&mux);
    orientation_status_t result = status;
    portEXIT_CRITICAL(&mux);
    return result;
}
void orientation_runtime_applied(uint8_t angle)
{
    if (angle > 3)
        return;
    portENTER_CRITICAL(&mux);
    status.applied_angle = angle;
    apply_retry_pending  = false;
    portEXIT_CRITICAL(&mux);
}
void orientation_runtime_apply_failed(uint32_t now)
{
    portENTER_CRITICAL(&mux);
    apply_failed_at     = now;
    apply_retry_pending = true;
    portEXIT_CRITICAL(&mux);
}
bool orientation_runtime_retry_ready(uint32_t now)
{
    portENTER_CRITICAL(&mux);
    bool ready = !apply_retry_pending || (uint32_t)(now - apply_failed_at) >= 1000;
    portEXIT_CRITICAL(&mux);
    return ready;
}
static void failed(uint32_t now)
{
    phase              = 0;
    have_timestamp     = false;
    policy.have_sample = false;
    orientation_policy_hold(&policy);
    next_io = now + 5000;
}
bool orientation_runtime_tick(uint32_t now, bool automatic, uint8_t manual, bool suspended, uint8_t *angle)
{
    if (!automatic)
    {
        policy.applied = manual <= 3 ? manual : 0;
        orientation_policy_hold(&policy);
    }
    if (!orientation_policy_healthy(&policy, now))
        orientation_policy_hold(&policy);
    if (!suspended && bus.read && bus.write && (int32_t)(now - next_io) >= 0)
    {
        next_io = now + (phase == 4 && !automatic ? 500 : 100);
        esp_err_t err;
        if (phase == 0)
        {
            uint8_t identity = 0;
            err              = bus.read(bus.context, 0x00, &identity, 1);
            if (err == ESP_OK && identity == 0x05)
                phase = 1;
            else
                failed(now);
        }
        else if (phase < 4)
        {
            /* Waveshare reference QMI8658 example: CTRL1 auto-increment 0x60,
             * CTRL2 +/-2g low-power 11Hz 0x0e, CTRL7 accel only 0x01. */
            static const uint8_t regs[]   = {0, 2, 3, 8};
            static const uint8_t values[] = {0, 0x60, 0x0e, 0x01};
            err                           = bus.write(bus.context, regs[phase], values[phase]);
            if (err == ESP_OK)
                ++phase;
            else
                failed(now);
        }
        else
        {
            uint8_t data[11];
            err = bus.read(bus.context, 0x30, data, sizeof(data));
            if (err != ESP_OK)
                failed(now);
            else
            {
                uint32_t timestamp = data[0] | ((uint32_t)data[1] << 8) | ((uint32_t)data[2] << 16);
                if (!have_timestamp || timestamp != last_timestamp)
                {
                    have_timestamp  = true;
                    last_timestamp  = timestamp;
                    timestamp_since = now;
                    int32_t x       = (int16_t)(data[5] | ((uint16_t)data[6] << 8));
                    int32_t y       = (int16_t)(data[7] | ((uint16_t)data[8] << 8));
                    int32_t z       = (int16_t)(data[9] | ((uint16_t)data[10] << 8));
                    (void)orientation_policy_observe(&policy, now, x * 1000 / 16384, y * 1000 / 16384,
                                                     z * 1000 / 16384);
                }
                else if ((uint32_t)(now - timestamp_since) > ORIENTATION_STALE_MS)
                    failed(now);
            }
        }
    }
    publish(now);
    orientation_status_t current = orientation_runtime_snapshot();
    uint8_t              desired =
        automatic && current.usable && current.sensor_status == 1 ? policy.applied : current.applied_angle;
    if (!automatic)
        desired = manual;
    if (desired > 3 || desired == current.applied_angle || !angle)
        return false;
    *angle = desired;
    return true;
}

/* Only this function is called by the rendering owner after initialization. */
bool orientation_runtime_poll(bool automatic, uint8_t manual, bool suspended, uint8_t *angle)
{
    portENTER_CRITICAL(&mux);
    requested_auto      = automatic;
    requested_manual    = manual;
    requested_suspended = suspended;
    uint8_t desired     = automatic ? desired_auto_angle : manual;
    bool    change      = angle && desired <= 3 && desired != status.applied_angle && !suspended &&
                          (!automatic || (status.usable && status.sensor_status == 1));
    portEXIT_CRITICAL(&mux);
    if (change)
        *angle = desired;
    return change;
}
uint16_t orientation_runtime_stack_margin(void)
{
    portENTER_CRITICAL(&mux);
    uint16_t result = stack_margin;
    portEXIT_CRITICAL(&mux);
    return result;
}
#if defined(ESP_PLATFORM) && CONFIG_EGAUGE_AUTO_ORIENTATION_PROBE_ENABLED
/* Fixed internal RAM, no queue or per-observation allocation. IDF stack depths
 * and watermarks are bytes. One priority-1 worker isolates touch/control owners
 * from the shared-bus driver's tick-granularity and repeated internal waits. */
static DRAM_ATTR StackType_t  sensor_stack[2048 / sizeof(StackType_t)];
static DRAM_ATTR StaticTask_t sensor_task;
static void                   sensor_worker(void *unused)
{
    (void)unused;
    for (;;)
    {
        portENTER_CRITICAL(&mux);
        bool    automatic = requested_auto, suspended = requested_suspended;
        uint8_t manual = requested_manual;
        portEXIT_CRITICAL(&mux);
        uint8_t angle;
        (void)orientation_runtime_tick(pdTICKS_TO_MS(xTaskGetTickCount()), automatic, manual, suspended, &angle);
        uint16_t margin = (uint16_t)uxTaskGetStackHighWaterMark(NULL);
        portENTER_CRITICAL(&mux);
        desired_auto_angle = policy.applied;
        stack_margin       = margin;
        portEXIT_CRITICAL(&mux);
        vTaskDelay(pdMS_TO_TICKS(100));
    }
}
static void start_worker(void)
{
    if (bus.read && bus.write)
    {
        TaskHandle_t task = xTaskCreateStatic(sensor_worker, "orientation", 2048, NULL, tskIDLE_PRIORITY + 1,
                                              sensor_stack, &sensor_task);
        ESP_LOGI("orientation", "Candidate probe worker %s; internal stack=2048 mapping=%u qualified=%u",
                 task ? "started" : "unavailable", ORIENTATION_MAPPING_VERSION, qualified);
    }
}
#else
static void start_worker(void)
{
}
#endif
