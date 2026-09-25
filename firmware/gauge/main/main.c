// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <stdbool.h>
#include <stdlib.h>

#include "esp_err.h"
#include "esp_log.h"
#include "esp_log_color.h"
#include "esp_log_level.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep
#include "lvgl.h"               // IWYU pragma: keep

#include "freertos/projdefs.h"
#include "freertos/task.h"
#include "portmacro.h"

#include "display/lv_display.h"
#include "misc/lv_event.h"

#include "bsp_init.h"
#include "bsp_lcd.h"
#include "bsp_lvgl.h"

#include "ble_obd.h"
#include "config.h"
#include "obd.h"
#include "ui.h"
#include "util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "main";

#define LV_DISPLAY_ROTATION_MAX LV_DISPLAY_ROTATION_270

// ---------------------------------------------------------------------------------------------------------------------
// OBD Conversion Functions
// ---------------------------------------------------------------------------------------------------------------------

static int obd_conv_rpm(int32_t *value, uint8_t const *data, size_t len)
{
    ESP_NULL_CHECK(value, TAG, "value pointer is NULL");
    ESP_NULL_CHECK(data, TAG, "data pointer is NULL");

    if (len < 2)
    {
        ESP_LOGW(TAG, "Invalid RPM data length: %zu", len);
        return -1;
    }
    // RPM is calculated as (A * 256 + B) / 4
    *value = ((data[0] << 8) | data[1]) / 4;
    return 0;
}

static int obd_conf_percent(int32_t *value, uint8_t const *data, size_t len)
{
    ESP_NULL_CHECK(value, TAG, "value pointer is NULL");
    ESP_NULL_CHECK(data, TAG, "data pointer is NULL");

    if (len < 1)
    {
        ESP_LOGW(TAG, "obd_conf_percent: invalid data length: %zu", len);
        return -1;
    }
    // percentage value (A * 100) / 255
    *value = (data[0] * 100) / 255;
    return 0;
}

static int obd_conf_temperature(int32_t *value, uint8_t const *data, size_t len)
{
    ESP_NULL_CHECK(value, TAG, "value pointer is NULL");
    ESP_NULL_CHECK(data, TAG, "data pointer is NULL");

    if (len < 1)
    {
        ESP_LOGW(TAG, "obd_conf_temperature: invalid data length: %zu", len);
        return -1;
    }
    // Temperature is calculated as A - 40
    *value = data[0] - 40;
    return 0;
}

// ---------------------------------------------------------------------------------------------------------------------
// Constants / Config
// ---------------------------------------------------------------------------------------------------------------------

static const obd_pid_cfg_t g_obd_pids[] = {
    {0x0C, 2, "RPM", "/min", obd_conv_rpm},         // Engine RPM
    {0x0D, 1, "SPEED", "km/h", NULL},               // Vehicle Speed
    {0x04, 1, "ENGINE", "%", obd_conf_percent},     // Engine Load
    {0x05, 1, "TEMP", "°C", obd_conf_temperature},  // Coolant Temperature
    {0x2F, 1, "FUEL", "%", obd_conf_percent},       // Fuel Level
};

// ---------------------------------------------------------------------------------------------------------------------
// Global Variables
// ---------------------------------------------------------------------------------------------------------------------

static obd_pid_cfg_t const *g_current_obd_cfg = &g_obd_pids[0];

static config_t g_config = {
    .cfg_idx  = 0,
    .disp_rot = LV_DISPLAY_ROTATION_0,
};

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void obd_response_cb(int pid, uint8_t const *data, size_t len, void *usr_ctx)
{
    ESP_NULL_CHECK(usr_ctx, TAG, "User context is NULL");
    ui_t *ui = (ui_t *)usr_ctx;

    if (pid < 0)
    {
        ESP_LOGW(TAG, "Error in RX/TX response callback: %d", pid);
        return;
    }

    if (data == NULL || len == 0)
    {
        ESP_LOGE(TAG, "Received empty data for PID 0x%02X", pid);
        return;
    }

    ESP_NULL_CHECK(g_current_obd_cfg, TAG, "Current OBD PID config is NULL");

    if (pid != g_current_obd_cfg->pid)
    {
        ESP_LOGW(TAG, "Received PID 0x%02X, expected 0x%02X", pid, g_current_obd_cfg->pid);
        return;
    }

    if (len != g_current_obd_cfg->len)
    {
        ESP_LOGW(TAG, "Invalid length for PID 0x%02X: expected %zu, got %zu", pid, g_current_obd_cfg->len, len);
        return;
    }

    int32_t value = 0;
    if (g_current_obd_cfg->conversion != NULL)
    {
        if (g_current_obd_cfg->conversion(&value, data, len) != 0)
        {
            ESP_LOGW(TAG, "Failed to convert data for PID 0x%02X", pid);
            return;
        }
    }
    else if (len == 1)
    {
        value = data[0];
    }
    else
    {
        ESP_LOGW(TAG, "No conversion function for PID 0x%02X", pid);
        return;
    }

    ESP_LOGI(TAG, "Received PID 0x%02X (%s): %" PRId32, pid, g_current_obd_cfg->name, value);
    ui_set_value(ui, &value);
}

static void obd_task(void *arg)
{
    ESP_NULL_CHECK(arg, TAG, "task arg is NULL");
    ui_t *ui = (ui_t *)arg;

    ESP_LOGI(TAG, "RX/TX task started");

    ble_obd_ctx_t *obd = NULL;

    while (true)
    {
        obd = ble_obd_connect(obd_response_cb, ui);
        if (obd != NULL)
        {
            break;
        }
        ESP_LOGW(TAG, "Failed to connect to RX/TX service. Retrying...");
    }
    ESP_LOGI(TAG, "RX/TX BLE service connected");

    TickType_t last_wake = xTaskGetTickCount();

    const uint32_t period_ms  = 200;
    const uint32_t timeout_ms = 200;

    while (true)
    {
        vTaskDelayUntil(&last_wake, pdMS_TO_TICKS(period_ms));

        if (!ble_obd_is_connected(obd))
        {
            ui_set_value(ui, NULL);
            continue;
        }

        ESP_NULL_CHECK(g_current_obd_cfg, TAG, "Current OBD PID config is NULL");
        const uint8_t obd_mode = 0x01;  // OBD-II mode

        int status = ble_obd_rxtx(obd, obd_mode, g_current_obd_cfg->pid, timeout_ms);

        if (status != 0)
        {
            ESP_LOGW(TAG, "Failed to send request: %d", status);
        }
    }
}

static void init_obd_task(ui_t *ui)
{
    BaseType_t handle = xTaskCreate(obd_task, "obd_task", 4096, ui, 5, NULL);
    ESP_CHECK(handle == pdPASS, TAG, "Failed to create RX/TX task");
}

static void ui_touch_callback(ui_t *ui, lv_event_code_t event_code)
{
    switch (event_code)
    {
    case LV_EVENT_CLICKED:
        g_config.cfg_idx  = (g_config.cfg_idx + 1) % ARRAY_SIZE(g_obd_pids);
        g_current_obd_cfg = &g_obd_pids[g_config.cfg_idx];
        ESP_LOGI(TAG, "Switched to PID: 0x%02X (%s)", g_current_obd_cfg->pid, g_current_obd_cfg->name);
        ui_set_obd_cfg(ui, g_current_obd_cfg);
        config_save(&g_config);
        break;
    case LV_EVENT_LONG_PRESSED:
        g_config.disp_rot = (g_config.disp_rot + LV_DISPLAY_ROTATION_MAX) % (LV_DISPLAY_ROTATION_MAX + 1);
        bsp_lv_disp_set_rotation(g_config.disp_rot);
        config_save(&g_config);
        break;
    default:
        // ignore
        break;
    }
}

static void init_config(void)
{
    ESP_LOGI(TAG, "Initializing configuration...");

    config_init();

    esp_err_t err = config_load(&g_config);

    if (err != ESP_OK)
    {
        ESP_LOGW(TAG, "Failed to load configuration: %s", esp_err_to_name(err));

        err = config_save(&g_config);
        if (err != ESP_OK)
        {
            ESP_LOGE(TAG, "Failed to save default configuration: %s", esp_err_to_name(err));
        }
        else
        {
            ESP_LOGI(TAG, "Default configuration saved successfully");
        }
    }
    else
    {
        ESP_LOGI(TAG, "Configuration loaded successfully: idx=%d, disp_rot=%d", g_config.cfg_idx, g_config.disp_rot);
        g_current_obd_cfg = &g_obd_pids[g_config.cfg_idx % ARRAY_SIZE(g_obd_pids)];
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Main Function
// ---------------------------------------------------------------------------------------------------------------------

void app_main(void)
{
    esp_log_level_set("*", ESP_LOG_INFO);
    esp_log_level_set("NimBLE", ESP_LOG_WARN);
    // esp_log_level_set("BLE=INIT", ESP_LOG_DEBUG);
    // esp_log_level_set("BLE_MGR", ESP_LOG_DEBUG);
    // esp_log_level_set("BLE_GATT", ESP_LOG_DEBUG);
    // esp_log_level_set("OBD", ESP_LOG_DEBUG);
    // esp_log_level_set("UI", ESP_LOG_DEBUG);
    esp_log_level_set("main", ESP_LOG_DEBUG);

    init_config();

    bsp_init();
    bsp_display_start();
    bsp_lvgl_init();
    bsp_touch_init();

    bsp_lv_disp_set_rotation(g_config.disp_rot);

    const uint32_t ui_interval_ms = 50;
    ui_t          *ui             = ui_init(g_current_obd_cfg, ui_interval_ms, ui_touch_callback);
    ESP_NULL_CHECK(ui, TAG, "Failed to initialize UI");

    bsp_display_on_off(true);

    init_obd_task(ui);

    while (true)
    {
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}
