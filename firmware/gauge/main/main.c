// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <ctype.h>
#include "sdkconfig.h"

#include "esp_err.h"
#include "esp_log.h"
#include "esp_log_color.h"
#include "esp_log_level.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep
#include "lvgl.h"               // IWYU pragma: keep

#include "freertos/projdefs.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "portmacro.h"

#include "display/lv_display.h"
#include "misc/lv_event.h"

#include "bsp_init.h"
#include "bsp_lcd.h"
#include "bsp_lvgl.h"
#include "esp_lvgl_port.h"

#include "ble_obd.h"
#include "ble_companion.h"
#include "ble_mgr.h"
#include "config.h"
#include "config_store.h"
#include "config_transfer.h"
#include "config_runtime.h"
#include "alert_engine.h"
#include "ota_transfer.h"
#include "esp_ota_ops.h"
#include "transfer_gate.h"
#include "diagnostics_state.h"
#include "esp_heap_caps.h"
#include "config_document.h"
#include "obd.h"
#include "ui.h"
#include "util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "main";

#define LV_DISPLAY_ROTATION_MAX LV_DISPLAY_ROTATION_270

// ---------------------------------------------------------------------------------------------------------------------
// Constants / Config
// ---------------------------------------------------------------------------------------------------------------------

static const obd_pid_cfg_t g_obd_pids[] = {
    {.pid = 0x0C, .len = 2, .name = "RPM", .unit = "/min",
     .decoder = {.byte_length = 2, .numerator = 1, .denominator = 4,
                 .minimum = 0, .maximum = 16383.75}},
    {.pid = 0x0D, .len = 1, .name = "SPEED", .unit = "km/h",
     .decoder = {.byte_length = 1, .numerator = 1, .denominator = 1,
                 .minimum = 0, .maximum = 255}},
    {.pid = 0x04, .len = 1, .name = "ENGINE", .unit = "%",
     .decoder = {.byte_length = 1, .numerator = 100, .denominator = 255,
                 .minimum = 0, .maximum = 100}},
    {.pid = 0x05, .len = 1, .name = "TEMP", .unit = "°C",
     .decoder = {.byte_length = 1, .numerator = 1, .denominator = 1,
                 .offset = -40, .minimum = -40, .maximum = 215}},
    {.pid = 0x2F, .len = 1, .name = "FUEL", .unit = "%",
     .decoder = {.byte_length = 1, .numerator = 100, .denominator = 255,
                 .minimum = 0, .maximum = 100}},
};

// ---------------------------------------------------------------------------------------------------------------------
// Global Variables
// ---------------------------------------------------------------------------------------------------------------------

static obd_pid_cfg_t const *g_current_obd_cfg = &g_obd_pids[0];
static config_runtime_t *g_runtime;
static uint32_t g_last_poll[32];
static uint8_t g_poll_cursor;
static bool g_mil_known;
static bool g_mil_on;
static uint8_t g_dtc_count;
static char g_first_dtc[6];
static uint32_t g_mil_at_ms;

static void show_diagnostics(ui_t *ui)
{
    uint32_t now = pdTICKS_TO_MS(xTaskGetTickCount());
    bool fresh = g_mil_known && now - g_mil_at_ms <= 60000;
    ui_set_diagnostics(ui, fresh, g_mil_on, g_dtc_count, g_first_dtc);
}

static void decode_first_dtc(const uint8_t *data, size_t length)
{
    static const char classes[] = "PCBU";
    static const char digits[] = "0123456789ABCDEF";
    g_first_dtc[0] = 0;
    if (length & 1U) return;
    for (size_t i = 0; i + 1 < length; i += 2) {
        uint8_t high = data[i], low = data[i + 1];
        if ((high | low) == 0) continue;
        g_first_dtc[0] = classes[high >> 6];
        g_first_dtc[1] = digits[(high >> 4) & 3];
        g_first_dtc[2] = digits[high & 15];
        g_first_dtc[3] = digits[low >> 4];
        g_first_dtc[4] = digits[low & 15];
        g_first_dtc[5] = 0;
        break;
    }
}

static unsigned page_count(void)
{
    return g_runtime ? g_runtime->page_count : ARRAY_SIZE(g_obd_pids);
}

static const obd_pid_cfg_t *page_cfg(unsigned index)
{
    if (!g_runtime) return &g_obd_pids[index % ARRAY_SIZE(g_obd_pids)];
    return &g_runtime->pids[g_runtime->page_pids[index % g_runtime->page_count]].obd;
}

static uint32_t page_stale_ms(unsigned index)
{
    return g_runtime ? g_runtime->pids[g_runtime->page_pids[index % g_runtime->page_count]].stale_ms
                     : 1500;
}

static const obd_pid_cfg_t *poll_cfg(unsigned index)
{
    return g_runtime ? &g_runtime->pids[index].obd : &g_obd_pids[index];
}

static unsigned poll_count(void)
{
    return g_runtime ? g_runtime->pid_count : ARRAY_SIZE(g_obd_pids);
}

static config_t g_config = {
    .cfg_idx  = 0,
    .disp_rot = LV_DISPLAY_ROTATION_0,
};
static QueueHandle_t g_phone_command_queue;

static void ota_trial_health_task(void *arg)
{
    (void)arg;
    for (unsigned i = 0; i < 60; ++i) {
        if (ble_companion_ready()) {
            esp_err_t err = esp_ota_mark_app_valid_cancel_rollback();
            if (err == ESP_OK) ESP_LOGI(TAG, "Trial firmware confirmed after UI and BLE startup");
            else ESP_LOGE(TAG, "Failed to confirm trial firmware: %s", esp_err_to_name(err));
            vTaskDelete(NULL);
        }
        vTaskDelay(pdMS_TO_TICKS(500));
    }
    ESP_LOGE(TAG, "Trial BLE startup timed out; requesting rollback");
    esp_ota_mark_app_invalid_rollback_and_reboot();
    vTaskDelete(NULL);
}

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

    if (pid == 0x01) {
        if (len >= 4) {
            g_mil_known = true;
            g_mil_on = (data[0] & 0x80) != 0;
            g_dtc_count = data[0] & 0x7f;
            g_mil_at_ms = pdTICKS_TO_MS(xTaskGetTickCount());
            diagnostics_state_mil(g_mil_on, g_dtc_count, g_mil_at_ms);
            show_diagnostics(ui);
        }
        return;
    }

    const obd_pid_cfg_t *definition = NULL;
    for (unsigned i = 0; i < poll_count(); ++i)
        if (poll_cfg(i)->pid == pid) { definition = poll_cfg(i); break; }
    if (!definition) return;
    if (len != definition->len)
    {
        ESP_LOGW(TAG, "Invalid length for PID 0x%02X: expected %zu, got %zu", pid, definition->len, len);
        return;
    }

    double decoded;
    if (!pid_decoder_eval(&definition->decoder, data, len, &decoded) ||
        decoded < INT32_MIN || decoded > INT32_MAX) {
        ESP_LOGW(TAG, "Rejected invalid value for PID 0x%02X", pid);
        return;
    }
    int32_t value = (int32_t)decoded;

    if (g_runtime) {
        for (unsigned i = 0; i < g_runtime->pid_count; ++i)
            if (g_runtime->pids[i].obd.pid == pid) {
                alert_engine_sample(i, decoded, pdTICKS_TO_MS(xTaskGetTickCount()));
                break;
            }
    }

    ESP_LOGI(TAG, "Received PID 0x%02X (%s): %" PRId32, pid, definition->name, value);
    if (pid == g_current_obd_cfg->pid) ui_set_value(ui, &value);
}

static void obd_task(void *arg)
{
    ESP_NULL_CHECK(arg, TAG, "task arg is NULL");
    ui_t *ui = (ui_t *)arg;

    ESP_LOGI(TAG, "RX/TX task started");

    ble_obd_ctx_t *obd = NULL;

    while (true)
    {
        ble_companion_tick();
        alert_summary_t alert = alert_engine_tick(pdTICKS_TO_MS(xTaskGetTickCount()));
        ui_set_alert(ui, alert.severity, alert.unavailable, alert.label);
        if (transfer_gate_current() == 2) {
            ui_set_value(ui, NULL);
            vTaskDelay(pdMS_TO_TICKS(250));
            continue;
        }
        obd = ble_obd_connect(0, CONFIG_EGAUGE_ECM_ADAPTER_MAC, obd_response_cb, ui);
        if (obd != NULL)
        {
            break;
        }
        ESP_LOGW(TAG, "Failed to connect to RX/TX service. Retrying...");
        vTaskDelay(pdMS_TO_TICKS(500));
    }
    ESP_LOGI(TAG, "RX/TX BLE service connected");

    TickType_t last_wake = xTaskGetTickCount();

    const uint32_t period_ms  = 100;
    const uint32_t timeout_ms = 300;
    uint32_t last_mil_poll = 0;
    uint32_t last_dtc_poll[3] = {0};
    const uint8_t dtc_modes[3] = {3, 7, 10};

    while (true)
    {
        vTaskDelayUntil(&last_wake, pdMS_TO_TICKS(period_ms));
        ble_companion_tick();
        alert_summary_t current_alert = alert_engine_tick(pdTICKS_TO_MS(xTaskGetTickCount()));
        ui_set_alert(ui, current_alert.severity, current_alert.unavailable,
                     current_alert.label);

        if (transfer_gate_current() == 2) {
            ui_set_value(ui, NULL);
            continue;
        }

        if (!ble_obd_is_connected(obd))
        {
            ui_set_value(ui, NULL);
            g_mil_known = false;
            diagnostics_state_disconnected();
            show_diagnostics(ui);
            continue;
        }

        uint32_t now_ms = pdTICKS_TO_MS(xTaskGetTickCount());
        if (last_mil_poll == 0 || now_ms - last_mil_poll >= 10000) {
            last_mil_poll = now_ms;
            ble_obd_rxtx(obd, 1, 0x01, 700);
        }
        for (unsigned category = 0; category < 3; ++category) {
            if (last_dtc_poll[category] == 0 ||
                now_ms - last_dtc_poll[category] >= 30000) {
                last_dtc_poll[category] = now_ms;
                uint8_t codes[64];
                size_t code_length = sizeof(codes);
                if (ble_obd_read_service(obd, dtc_modes[category], 1500,
                                         codes, &code_length) == 0) {
                    diagnostics_state_codes(dtc_modes[category], codes, code_length,
                                            pdTICKS_TO_MS(xTaskGetTickCount()));
                    if (category == 0 && (code_length & 1U) == 0) {
                        decode_first_dtc(codes, code_length);
                        show_diagnostics(ui);
                    }
                }
            }
        }

        unsigned chosen = poll_count();
        uint32_t now = xTaskGetTickCount();
        for (unsigned step = 0; step < poll_count(); ++step) {
            unsigned index = (g_poll_cursor + step) % poll_count();
            uint32_t interval = g_runtime ? g_runtime->pids[index].poll_ms : 500;
            if (g_last_poll[index] == 0 ||
                now - g_last_poll[index] >= pdMS_TO_TICKS(interval)) {
                chosen = index;
                break;
            }
        }
        if (chosen == poll_count()) continue;
        g_poll_cursor = (chosen + 1) % poll_count();
        g_last_poll[chosen] = now;
        const obd_pid_cfg_t *requested = poll_cfg(chosen);
        const uint8_t obd_mode = 0x01;  // OBD-II mode

        int status = ble_obd_rxtx(obd, obd_mode, requested->pid, timeout_ms);

        if (status != 0)
        {
            ESP_LOGW(TAG, "Failed to send request: %d", status);
        }
    }
}

static bool adapter_mac_valid(const char *mac)
{
    if (strlen(mac) != 17) return false;
    for (size_t i = 0; i < 17; i++) {
        if (i % 3 == 2) { if (mac[i] != ':') return false; }
        else if (!isxdigit((unsigned char)mac[i])) return false;
    }
    return true;
}

static void tcm_link_task(void *arg)
{
    (void)arg;
    while (true) {
        ble_obd_ctx_t *tcm = ble_obd_connect(1, CONFIG_EGAUGE_TCM_ADAPTER_MAC, NULL, NULL);
        if (tcm) {
            ESP_LOGI(TAG, "TCM adapter link connected; awaiting TCM PID configuration");
            while (ble_obd_is_connected(tcm)) vTaskDelay(pdMS_TO_TICKS(500));
        }
        vTaskDelay(pdMS_TO_TICKS(1000));
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
    {
        config_t updated = g_config;
        updated.cfg_idx = (updated.cfg_idx + 1) % page_count();
        if (config_save(&updated) != ESP_OK) break;
        g_config = updated;
        g_current_obd_cfg = page_cfg(g_config.cfg_idx);
        ESP_LOGI(TAG, "Switched to PID: 0x%02X (%s)", g_current_obd_cfg->pid, g_current_obd_cfg->name);
        ui_set_obd_cfg(ui, g_current_obd_cfg);
        ui_set_freshness(ui, page_stale_ms(g_config.cfg_idx));
        ble_companion_selection_applied(g_config.cfg_idx);
        break;
    }
    case LV_EVENT_LONG_PRESSED:
        ble_companion_open_pairing_window();
        break;
    case LV_EVENT_RELEASED:
        ble_companion_forget_owner();
        break;
    default:
        // ignore
        break;
    }
}

static void init_config(void)
{
    ESP_LOGI(TAG, "Initializing configuration...");

    ESP_ERROR_CHECK(config_init());
    ESP_ERROR_CHECK(config_store_init());
    ESP_ERROR_CHECK(config_transfer_init());
    ESP_ERROR_CHECK(ota_transfer_init());
    ESP_ERROR_CHECK(diagnostics_state_init());

    g_runtime = heap_caps_malloc(sizeof(*g_runtime), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (g_runtime && config_runtime_load(g_runtime) == ESP_OK) {
        ESP_LOGI(TAG, "Activated document with %u PIDs, %u pages, %u alerts",
                 g_runtime->pid_count, g_runtime->page_count, g_runtime->alert_count);
    } else {
        if (g_runtime) heap_caps_free(g_runtime);
        g_runtime = NULL;
    }

    esp_err_t err = config_load(&g_config);

    if (err == ESP_OK && (g_config.cfg_idx >= page_count() ||
                          g_config.disp_rot > LV_DISPLAY_ROTATION_270)) {
        ESP_LOGE(TAG, "Saved legacy configuration is invalid");
        g_config = (config_t){.cfg_idx = 0, .disp_rot = LV_DISPLAY_ROTATION_0};
        err = ESP_ERR_INVALID_ARG;
    }

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
        g_current_obd_cfg = page_cfg(g_config.cfg_idx);
    }
    if (g_runtime) g_config.disp_rot = (lv_display_rotation_t)g_runtime->rotation;
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

    config_document_init();
    init_config();
    alert_engine_init(g_runtime);

    bsp_init();
    bsp_display_start();
    bsp_lvgl_init();
    bsp_touch_init();

    bsp_lv_disp_set_rotation(g_config.disp_rot);

    const uint32_t ui_interval_ms = 50;
    ui_t          *ui             = ui_init(g_current_obd_cfg, ui_interval_ms, ui_touch_callback);
    ESP_NULL_CHECK(ui, TAG, "Failed to initialize UI");
    if (lvgl_port_lock(portMAX_DELAY)) {
        ui_set_freshness(ui, page_stale_ms(g_config.cfg_idx));
        lvgl_port_unlock();
    }

    bsp_display_on_off(true);

    g_phone_command_queue = xQueueCreate(4, sizeof(companion_command_t));
    ESP_NULL_CHECK(g_phone_command_queue, TAG, "Phone command queue creation failed");
    ble_companion_set_control(ui, g_phone_command_queue, g_config.cfg_idx, g_runtime != NULL);

    ESP_NULL_CHECK(ble_mgr_init(0, 2000), TAG, "BLE ECM slot initialization failed");
    ESP_NULL_CHECK(ble_mgr_init(1, 2000), TAG, "BLE TCM slot initialization failed");
    if (CONFIG_EGAUGE_ECM_ADAPTER_MAC[0] && !adapter_mac_valid(CONFIG_EGAUGE_ECM_ADAPTER_MAC)) {
        ESP_LOGE(TAG, "Invalid ECM adapter MAC in firmware configuration");
        return;
    }
    init_obd_task(ui);
    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t image_state;
    if (esp_ota_get_state_partition(running, &image_state) == ESP_OK &&
        image_state == ESP_OTA_IMG_PENDING_VERIFY) {
        BaseType_t started = xTaskCreate(ota_trial_health_task, "ota_health", 3072,
                                         NULL, 5, NULL);
        ESP_CHECK(started == pdPASS, TAG, "OTA health task creation failed");
    }
    if (CONFIG_EGAUGE_TCM_ADAPTER_MAC[0]) {
        if (!adapter_mac_valid(CONFIG_EGAUGE_TCM_ADAPTER_MAC) ||
            (CONFIG_EGAUGE_ECM_ADAPTER_MAC[0] &&
             strcasecmp(CONFIG_EGAUGE_ECM_ADAPTER_MAC, CONFIG_EGAUGE_TCM_ADAPTER_MAC) == 0)) {
            ESP_LOGE(TAG, "Invalid or duplicate TCM adapter MAC; second link disabled");
        } else {
            BaseType_t started = xTaskCreate(tcm_link_task, "tcm_link", 4096, NULL, 5, NULL);
            ESP_CHECK(started == pdPASS, TAG, "TCM link task creation failed");
        }
    }

    while (true)
    {
        companion_command_t command;
        if (xQueueReceive(g_phone_command_queue, &command, pdMS_TO_TICKS(10)) != pdTRUE)
            continue;
        if (command.opcode == 1 && command.value < page_count()) {
            config_t updated = g_config;
            updated.cfg_idx = command.value;
            if (config_save(&updated) == ESP_OK) {
                g_config = updated;
                g_current_obd_cfg = page_cfg(command.value);
                if (lvgl_port_lock(portMAX_DELAY)) {
                    ui_set_obd_cfg(ui, g_current_obd_cfg);
                    ui_set_freshness(ui, page_stale_ms(command.value));
                    lvgl_port_unlock();
                    ble_companion_selection_applied(command.value);
                }
            }
        } else if (command.opcode == 2 && command.value <= LV_DISPLAY_ROTATION_270) {
            config_t saved;
            uint32_t revision;
            if (config_read_snapshot(&saved, &revision) != ESP_OK ||
                revision != command.base_revision) continue;
            saved.disp_rot = (lv_display_rotation_t)command.value;
            if (config_save(&saved) != ESP_OK) continue;
            g_config = saved;
            if (lvgl_port_lock(portMAX_DELAY)) {
                bsp_lv_disp_set_rotation(saved.disp_rot);
                lvgl_port_unlock();
            }
            ESP_LOGI(TAG, "Display rotation saved: %u", (unsigned)command.value * 90);
        }
    }
}
