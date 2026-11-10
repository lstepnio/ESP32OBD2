#include "sunset_runtime.h"

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "nvs.h"

typedef struct
{
    uint32_t revision;
    int16_t  latitude;
    int16_t  longitude;
    uint8_t  version;
    uint8_t  night_brightness;
    uint8_t  automatic;
    uint8_t  night;
} sunset_record_t;

_Static_assert(sizeof(sunset_record_t) == 12, "Sunset record layout changed");

static sunset_record_t record = {.version = 1, .night_brightness = SUNSET_NIGHT_DEFAULT};
static sunset_policy_t policy;
static uint32_t        anchor_utc;
static uint64_t        anchor_ms, next_check_ms;
static sunset_status_t published;
static portMUX_TYPE    status_lock = portMUX_INITIALIZER_UNLOCKED;
static const char     *TAG         = "sunset";

static void publish(void)
{
    sunset_status_t status = {record.revision,  record.latitude, record.longitude, record.night_brightness,
                              record.automatic, policy.night,    anchor_utc != 0};
    portENTER_CRITICAL(&status_lock);
    published = status;
    portEXIT_CRITICAL(&status_lock);
}

sunset_status_t sunset_runtime_status(void)
{
    portENTER_CRITICAL(&status_lock);
    sunset_status_t status = published;
    portEXIT_CRITICAL(&status_lock);
    return status;
}

static esp_err_t persist(const sunset_record_t *value)
{
    nvs_handle_t nvs;
    esp_err_t    err = nvs_open("display", NVS_READWRITE, &nvs);
    if (err != ESP_OK)
        return err;
    err = nvs_set_blob(nvs, "sunset", value, sizeof(*value));
    if (err == ESP_OK)
        err = nvs_commit(nvs);
    nvs_close(nvs);
    return err;
}

void sunset_runtime_init(void)
{
    nvs_handle_t nvs;
    if (nvs_open("display", NVS_READONLY, &nvs) == ESP_OK)
    {
        sunset_record_t saved;
        size_t          size = sizeof(saved);
        if (nvs_get_blob(nvs, "sunset", &saved, &size) == ESP_OK && size == sizeof(saved) && saved.version == 1 &&
            saved.night_brightness >= 5 && saved.night_brightness <= 100 && saved.automatic <= 1 && saved.night <= 1 &&
            sunset_location_valid(saved.latitude, saved.longitude))
            record = saved;
        nvs_close(nvs);
    }
    policy.night = record.night != 0;
    publish();
}

void sunset_runtime_sync(uint32_t utc, uint64_t now_ms)
{
    if (!sunset_utc_valid(utc))
        return;
    anchor_utc    = utc;
    anchor_ms     = now_ms;
    next_check_ms = 0;
    sunset_runtime_poll(now_ms);
    publish();
}

esp_err_t sunset_runtime_save(bool     automatic,
                              uint8_t  night_brightness,
                              int16_t  latitude,
                              int16_t  longitude,
                              uint32_t utc,
                              uint32_t expected_revision,
                              uint64_t now_ms)
{
    if (night_brightness < 5 || night_brightness > 100 || !sunset_location_valid(latitude, longitude) ||
        !sunset_utc_valid(utc))
        return ESP_ERR_INVALID_ARG;
    if (expected_revision != record.revision || record.revision == UINT32_MAX)
        return ESP_ERR_INVALID_STATE;
    sunset_policy_t next_policy = sunset_policy_reconfigure(policy, record.automatic, automatic);
    if (automatic)
    {
        bool solar_night;
        if (!sunset_is_night(utc, latitude, longitude, &solar_night))
            return ESP_ERR_INVALID_ARG;
        sunset_policy_observe(&next_policy, solar_night);
    }
    sunset_record_t next = {record.revision + 1, latitude,  longitude,        1,
                            night_brightness,    automatic, next_policy.night};
    esp_err_t       err  = persist(&next);
    if (err == ESP_OK)
    {
        record = next;
        policy = next_policy;
        sunset_runtime_sync(utc, now_ms);
    }
    return err;
}

static void checkpoint_phase(void)
{
    if (record.night == policy.night)
        return;
    record.night  = policy.night;
    esp_err_t err = persist(&record);
    if (err != ESP_OK)
        ESP_LOGW(TAG, "Unable to save display phase: %s", esp_err_to_name(err));
    publish();
}

void sunset_runtime_toggle(void)
{
    sunset_policy_toggle(&policy);
    checkpoint_phase();
}

void sunset_runtime_poll(uint64_t now_ms)
{
    if (!record.automatic || anchor_utc == 0 || now_ms < next_check_ms || now_ms < anchor_ms)
        return;
    next_check_ms = now_ms + 10000;
    uint64_t utc  = anchor_utc + (now_ms - anchor_ms) / 1000;
    bool     night;
    if (utc > SUNSET_MAX_UTC || !sunset_is_night((uint32_t)utc, record.latitude, record.longitude, &night))
    {
        anchor_utc = 0;
        publish();
        return;
    }
    sunset_policy_observe(&policy, night);
    checkpoint_phase();
}

uint8_t sunset_runtime_level(uint8_t day_brightness)
{
    if (!policy.night || day_brightness < record.night_brightness)
        return day_brightness;
    return record.night_brightness;
}
