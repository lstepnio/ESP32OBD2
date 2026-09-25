// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stddef.h>

#include "util.h"

#include "esp_err.h"
#include "esp_log.h"
#include "esp_log_color.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep
#include "freertos/projdefs.h"
#include "freertos/task.h"
#include "portmacro.h"

#include "host/ble_hs.h"
#include "host/ble_store.h"
#include "nimble/nimble_port.h"

#include "ble_init.h"

// ---------------------------------------------------------------------------------------------------------------------
// Forward Declarations
// ---------------------------------------------------------------------------------------------------------------------

/* Library function declarations */
void ble_store_config_init(void);

// ---------------------------------------------------------------------------------------------------------------------
// Global Variables
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "BLE=INIT";

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void nimble_host_config_init(ble_init_config_t const *config)
{
    ble_hs_cfg.reset_cb        = config->reset_cb;
    ble_hs_cfg.sync_cb         = config->sync_cb;
    ble_hs_cfg.store_status_cb = ble_store_util_status_rr;
    ble_store_config_init();
}

static void ble_task(void *param)
{
    ESP_LOGD(TAG, "NimBLE host task started successfully");
    nimble_port_run();  // won't return until nimble_port_stop() is executed
    vTaskDelete(NULL);
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

void ble_init_stack(ble_init_config_t const *config)
{
    ESP_NULL_CHECK(config, TAG, "config is NULL");
    ESP_LOGD(TAG, "Initializing BLE stack...");

    esp_err_t ret = ESP_OK;

    /* NimBLE stack initialization */
    ret = nimble_port_init();
    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to initialize NimBLE stack, error code: %d ", ret);
        return;
    }
    ESP_LOGD(TAG, "NimBLE stack initialized successfully");

    nimble_host_config_init(config);
    ESP_LOGD(TAG, "NimBLE host initialized successfully");

    /* Start NimBLE host task */
    BaseType_t task_result = xTaskCreate(ble_task, "NimBLE_task", 4096, NULL, 5, NULL);
    ESP_CHECK(task_result == pdPASS, TAG, "Failed to create NimBLE task");
    ESP_LOGD(TAG, "BLE stack initialized successfully");
}
