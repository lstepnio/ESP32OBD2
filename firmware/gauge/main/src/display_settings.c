#include "display_settings.h"
#include <stdbool.h>
#include "nvs.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

static SemaphoreHandle_t settings_lock;
static display_settings_t current;
typedef struct {
    uint8_t version;
    uint8_t rotation;
    uint8_t brightness;
    uint8_t units;
    uint32_t revision;
    uint16_t cycle_seconds;
    uint16_t reserved;
} display_record_t;

static bool valid(uint8_t rotation, uint8_t brightness, uint8_t units, uint16_t cycle_seconds)
{
    return rotation <= 3 && brightness >= 5 && brightness <= 100 && units <= 1 &&
           (cycle_seconds == 0 || cycle_seconds == 5 || cycle_seconds == 10 ||
            cycle_seconds == 15 || cycle_seconds == 30 || cycle_seconds == 60);
}

esp_err_t display_settings_init(uint8_t default_rotation, uint8_t default_brightness,
                                uint8_t default_units)
{
    if (!valid(default_rotation, default_brightness, default_units, 0)) return ESP_ERR_INVALID_ARG;
    if (!settings_lock) settings_lock = xSemaphoreCreateMutex();
    if (!settings_lock) return ESP_ERR_NO_MEM;
    current = (display_settings_t){default_rotation, default_brightness, default_units, 0, 0};
    nvs_handle_t nvs;
    esp_err_t err = nvs_open("display", NVS_READONLY, &nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND) return ESP_OK;
    if (err != ESP_OK) return err;
    display_record_t stored = {0};
    size_t length = sizeof(stored);
    err = nvs_get_blob(nvs, "settings", &stored, &length);
    nvs_close(nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND || err == ESP_ERR_NVS_INVALID_LENGTH) return ESP_OK;
    if (err != ESP_OK) return err;
    if (!((length == 8 && (stored.version == 1 || stored.version == 2)) ||
          (length == sizeof(stored) && stored.version == 3 && stored.reserved == 0)) ||
        (stored.version == 1 && stored.units != 0) ||
        !valid(stored.rotation, stored.brightness, stored.version == 1 ? 0 : stored.units,
               stored.version == 3 ? stored.cycle_seconds : 0) ||
        stored.revision == 0) {
        ESP_LOGW("display", "Invalid saved display settings; using defaults");
        return ESP_OK;
    }
    current = (display_settings_t){stored.rotation, stored.brightness,
                                   stored.version == 1 ? default_units : stored.units, stored.revision,
                                   stored.version == 3 ? stored.cycle_seconds : 0};
    return ESP_OK;
}

display_settings_t display_settings_snapshot(void)
{
    xSemaphoreTake(settings_lock, portMAX_DELAY);
    display_settings_t result = current;
    xSemaphoreGive(settings_lock);
    return result;
}

esp_err_t display_settings_save(uint8_t rotation, uint8_t brightness, uint8_t units, uint16_t cycle_seconds,
                                uint32_t expected_revision, display_settings_t *saved)
{
    if (!valid(rotation, brightness, units, cycle_seconds)) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(settings_lock, portMAX_DELAY);
    if (current.revision != expected_revision || current.revision == UINT32_MAX) {
        xSemaphoreGive(settings_lock);
        return ESP_ERR_INVALID_STATE;
    }
    display_settings_t next = {rotation, brightness, units, current.revision + 1, cycle_seconds};
    display_record_t record = {3, rotation, brightness, units, next.revision, cycle_seconds, 0};
    nvs_handle_t nvs;
    esp_err_t err = nvs_open("display", NVS_READWRITE, &nvs);
    if (err == ESP_OK) {
        err = nvs_set_blob(nvs, "settings", &record, sizeof(record));
        if (err == ESP_OK) err = nvs_commit(nvs);
        nvs_close(nvs);
    }
    if (err == ESP_OK) {
        current = next;
        if (saved) *saved = next;
    }
    xSemaphoreGive(settings_lock);
    return err;
}
