
// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stddef.h>

#include "esp_err.h"
#include "esp_log.h"
#include "esp_log_color.h"

#include "nvs.h"
#include "nvs_flash.h"

#include "config.h"

// ---------------------------------------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------------------------------------

char const *TAG = "config";

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

esp_err_t config_init(void)
{
    esp_err_t ret = nvs_flash_init();
    if ((ret == ESP_ERR_NVS_NO_FREE_PAGES) || (ret == ESP_ERR_NVS_NEW_VERSION_FOUND))
    {
        ESP_LOGW(TAG, "NVS flash needs to be erased and re-initialized");
        ESP_ERROR_CHECK(nvs_flash_erase());
        ESP_ERROR_CHECK(nvs_flash_init());
    }
    ESP_LOGD(TAG, "NVS flash initialized successfully");

    return ret;
}

esp_err_t config_save(const config_t *cfg)
{
    nvs_handle_t hndl;
    esp_err_t    err = nvs_open(TAG, NVS_READWRITE, &hndl);
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to open NVS namespace '%s': %s", TAG, esp_err_to_name(err));
        return err;
    }

    err = nvs_set_blob(hndl, "app_cfg", cfg, sizeof(config_t));
    if (err == ESP_OK)
    {
        err = nvs_commit(hndl);
        if (err != ESP_OK)
        {
            ESP_LOGE(TAG, "Failed to commit NVS changes: %s", esp_err_to_name(err));
        }
        else
        {
            ESP_LOGI(TAG, "Configuration saved successfully");
        }
    }
    else
    {
        ESP_LOGE(TAG, "Failed to set blob in NVS: %s", esp_err_to_name(err));
    }

    nvs_close(hndl);

    return err;
}

esp_err_t config_load(config_t *cfg)
{
    nvs_handle_t hndl;
    esp_err_t    err = nvs_open(TAG, NVS_READONLY, &hndl);
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to open NVS namespace '%s': %s", TAG, esp_err_to_name(err));
        return err;
    }

    size_t required_size = sizeof(config_t);
    err                  = nvs_get_blob(hndl, "app_cfg", cfg, &required_size);

    if (err != ESP_OK)
    {
        ESP_LOGW(TAG, "Failed to get blob from NVS: %s", esp_err_to_name(err));
    }
    else
    {
        ESP_LOGI(TAG, "Configuration loaded successfully");
    }

    nvs_close(hndl);

    return err;
}
