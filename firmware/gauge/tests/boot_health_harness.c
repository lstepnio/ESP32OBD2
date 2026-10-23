/* Host dependencies for the extracted production boot_health_task. No device IO. */
#include "boot_health.h"
#include <assert.h>
#include <inttypes.h>
#include <setjmp.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>

typedef int esp_err_t;
#define ESP_OK 0
#define ESP_LOGE(...) ((void)0)
#define ESP_LOGI(...) ((void)0)
#define pdMS_TO_TICKS(value) (value)
static atomic_uint g_app_tick_count;
static void       *g_runtime;
static struct
{
    uint32_t revision;
} g_running_config_record;
static bool     ota_confirmed, config_confirmed;
static unsigned mode, elapsed;
static jmp_buf  finished;
static bool     ble_companion_ready(void)
{
    return mode != 3;
}
static bool config_trial_needs_confirmation(uint32_t revision)
{
    (void)revision;
    return true;
}
static esp_err_t config_trial_confirm(uint32_t revision)
{
    (void)revision;
    config_confirmed = true;
    return ESP_OK;
}
static bool ota_trial_pending(void)
{
    return true;
}
static void esp_ota_mark_app_invalid_rollback_and_reboot(void)
{
    longjmp(finished, 2);
}
static void esp_restart(void)
{
    longjmp(finished, 3);
}
static void config_runtime_mark_confirmed(void)
{
}
static esp_err_t esp_ota_mark_app_valid_cancel_rollback(void)
{
    ota_confirmed = true;
    return ESP_OK;
}
static void vTaskDelete(void *value)
{
    (void)value;
    longjmp(finished, 1);
}
static void vTaskDelay(unsigned ms)
{
    elapsed += ms;
    atomic_fetch_add(&g_app_tick_count, 5);
}
static uint32_t esp_timer_get_time(void)
{
    return elapsed * 1000;
}
worker_health_t worker_health_snapshot(worker_id_t worker, uint32_t now)
{
    (void)worker;
    return (worker_health_t){.observed       = mode != 0,
                             .progress_count = mode == 0 ? 0 : now / 50,
                             .age_ms         = mode == 2 ? 1001 : 0};
}
/* The Python runner inserts the exact production function here. */
