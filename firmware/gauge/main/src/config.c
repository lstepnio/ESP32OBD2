
// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stddef.h>
#include <stdbool.h>

#include "esp_err.h"
#include "esp_log.h"
#include "esp_log_color.h"

#include "nvs.h"
#include "nvs_flash.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "config.h"

// ---------------------------------------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------------------------------------

char const *TAG = "config";
static SemaphoreHandle_t config_lock;

static esp_err_t save_locked(const config_t *cfg, uint32_t expected_revision,
                             bool compare_revision, uint32_t *new_revision)
{
    nvs_handle_t hndl;
    esp_err_t err = nvs_open(TAG, NVS_READWRITE, &hndl);
    if (err != ESP_OK) return err;
    uint32_t revision = 0;
    err = nvs_get_u32(hndl, "legacy_rev", &revision);
    if (err == ESP_ERR_NVS_NOT_FOUND) { revision = 0; err = ESP_OK; }
    if (err == ESP_OK && compare_revision && revision != expected_revision)
        err = ESP_ERR_INVALID_STATE;
    if (err == ESP_OK && revision == UINT32_MAX) err = ESP_ERR_INVALID_STATE;
    if (err == ESP_OK) err = nvs_set_blob(hndl, "app_cfg", cfg, sizeof(*cfg));
    if (err == ESP_OK) err = nvs_set_u32(hndl, "legacy_rev", revision + 1);
    if (err == ESP_OK) err = nvs_commit(hndl);
    if (err == ESP_OK && new_revision) *new_revision = revision + 1;
    nvs_close(hndl);
    return err;
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

esp_err_t config_init(void)
{
    esp_err_t ret = nvs_flash_init();
    /* NVS also contains the owner identity and NimBLE bond keys. Never erase
     * it automatically when an image cannot open the existing partition. */
    if (ret != ESP_OK) return ret;
    if (config_lock == NULL) config_lock = xSemaphoreCreateMutex();
    return config_lock != NULL ? ESP_OK : ESP_ERR_NO_MEM;
}

esp_err_t config_save(const config_t *cfg)
{
    if (cfg == NULL || config_lock == NULL) return ESP_ERR_INVALID_STATE;
    xSemaphoreTake(config_lock, portMAX_DELAY);
    esp_err_t err = save_locked(cfg, 0, false, NULL);
    if (err == ESP_OK) ESP_LOGI(TAG, "Configuration saved successfully");
    else ESP_LOGE(TAG, "Failed to save configuration: %s", esp_err_to_name(err));
    xSemaphoreGive(config_lock);

    return err;
}

esp_err_t config_save_if_revision(const config_t *cfg, uint32_t expected_revision,
                                  uint32_t *new_revision)
{
    if (!cfg || !config_lock) return ESP_ERR_INVALID_STATE;
    xSemaphoreTake(config_lock, portMAX_DELAY);
    esp_err_t err = save_locked(cfg, expected_revision, true, new_revision);
    xSemaphoreGive(config_lock);
    return err;
}

esp_err_t config_load(config_t *cfg)
{
    uint32_t revision;
    return config_read_snapshot(cfg, &revision);
}

esp_err_t config_read_snapshot(config_t *cfg, uint32_t *revision)
{
    if (cfg == NULL || revision == NULL || config_lock == NULL) return ESP_ERR_INVALID_STATE;
    xSemaphoreTake(config_lock, portMAX_DELAY);
    nvs_handle_t hndl;
    esp_err_t    err = nvs_open(TAG, NVS_READONLY, &hndl);
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to open NVS namespace '%s': %s", TAG, esp_err_to_name(err));
        xSemaphoreGive(config_lock);
        return err;
    }

    size_t required_size = sizeof(config_t);
    err                  = nvs_get_blob(hndl, "app_cfg", cfg, &required_size);

    if (err == ESP_OK && required_size != sizeof(config_t)) err = ESP_ERR_INVALID_SIZE;
    if (err == ESP_OK) {
        err = nvs_get_u32(hndl, "legacy_rev", revision);
        if (err == ESP_ERR_NVS_NOT_FOUND) { *revision = 0; err = ESP_OK; }
    }
    if (err != ESP_OK)
    {
        ESP_LOGW(TAG, "Failed to get blob from NVS: %s", esp_err_to_name(err));
    }
    else
    {
        ESP_LOGI(TAG, "Configuration loaded successfully");
    }

    nvs_close(hndl);
    xSemaphoreGive(config_lock);

    return err;
}
