#include "display_settings.h"
#include <stdbool.h>
#include "nvs.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

static SemaphoreHandle_t  settings_lock;
static display_settings_t current;
static int                active_smart_slot = -1;
static portMUX_TYPE       snapshot_mux      = portMUX_INITIALIZER_UNLOCKED;
typedef struct
{
    uint8_t  version;
    uint8_t  rotation;
    uint8_t  brightness;
    uint8_t  units;
    uint32_t revision;
    uint16_t cycle_seconds;
    uint16_t reserved;
} display_record_t;

/* Keep the v3 legacy record unchanged for downgrade compatibility. The legacy
 * revision activates one sidecar only after its own successful commit. */
typedef struct
{
    uint8_t  version;
    uint8_t  flags;
    uint16_t reserved;
    uint32_t revision;
} smart_record_t;
_Static_assert(sizeof(display_record_t) == 12, "Legacy display record layout changed");
_Static_assert(sizeof(smart_record_t) == 8, "Smart sidecar layout changed");
static const char *const smart_keys[] = {"smart0", "smart1"};

/* This independent boot hint never activates owner settings or changes revision. */
typedef struct
{
    uint8_t  version;
    uint8_t  rotation;
    uint16_t reserved;
} auto_rotation_record_t;
_Static_assert(sizeof(auto_rotation_record_t) == 4, "Auto boot hint layout changed");
static bool     auto_hint_valid, auto_candidate_valid, auto_attempted;
static uint8_t  auto_hint, auto_candidate;
static uint64_t auto_candidate_since_ms, auto_attempted_at_ms;
#define AUTO_ROTATION_STABLE_MS UINT64_C(5000)
#define AUTO_ROTATION_WRITE_INTERVAL_MS UINT64_C(60000)

static void load_auto_rotation(nvs_handle_t nvs)
{
    auto_rotation_record_t record = {0};
    size_t                 length = sizeof(record);
    if (nvs_get_blob(nvs, "auto_angle", &record, &length) == ESP_OK && length == sizeof(record) &&
        record.version == 1 && record.rotation <= 3 && record.reserved == 0)
    {
        auto_hint       = record.rotation;
        auto_hint_valid = true;
    }
}

static uint8_t load_smart(nvs_handle_t nvs, uint32_t revision)
{
    active_smart_slot = -1;
    for (int slot = 0; slot < 2; ++slot)
    {
        smart_record_t record = {0};
        size_t         length = sizeof(record);
        if (nvs_get_blob(nvs, smart_keys[slot], &record, &length) == ESP_OK && length == sizeof(record) &&
            record.version == 1 && record.flags <= 3 && record.reserved == 0 && record.revision == revision)
        {
            active_smart_slot = slot;
            return record.flags;
        }
    }
    return false;
}

static bool valid(uint8_t rotation, uint8_t brightness, uint8_t units, uint16_t cycle_seconds)
{
    return rotation <= 3 && brightness >= 5 && brightness <= 100 && units <= 1 &&
           (cycle_seconds == 0 || cycle_seconds == 5 || cycle_seconds == 10 || cycle_seconds == 15 ||
            cycle_seconds == 30 || cycle_seconds == 60);
}

esp_err_t display_settings_init(uint8_t default_rotation, uint8_t default_brightness, uint8_t default_units)
{
    if (!valid(default_rotation, default_brightness, default_units, 0))
        return ESP_ERR_INVALID_ARG;
    if (!settings_lock)
        settings_lock = xSemaphoreCreateMutex();
    if (!settings_lock)
        return ESP_ERR_NO_MEM;
    current           = (display_settings_t){default_rotation, default_brightness, default_units, 0, 0, false, false};
    active_smart_slot = -1;
    auto_hint_valid = auto_candidate_valid = auto_attempted = false;
    nvs_handle_t nvs;
    esp_err_t    err = nvs_open("display", NVS_READONLY, &nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND)
        return ESP_OK;
    if (err != ESP_OK)
        return err;
    load_auto_rotation(nvs);
    display_record_t stored = {0};
    size_t           length = sizeof(stored);
    err                     = nvs_get_blob(nvs, "settings", &stored, &length);
    uint8_t flags           = err == ESP_OK && stored.revision != 0 ? load_smart(nvs, stored.revision) : 0;
    nvs_close(nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND || err == ESP_ERR_NVS_INVALID_LENGTH)
        return ESP_OK;
    if (err != ESP_OK)
        return err;
    if (!((length == 8 && (stored.version == 1 || stored.version == 2)) ||
          (length == sizeof(stored) && stored.version == 3 && stored.reserved == 0)) ||
        (stored.version == 1 && stored.units != 0) ||
        !valid(stored.rotation, stored.brightness, stored.version == 1 ? 0 : stored.units,
               stored.version == 3 ? stored.cycle_seconds : 0) ||
        stored.revision == 0)
    {
        ESP_LOGW("display", "Invalid saved display settings; using defaults");
        return ESP_OK;
    }
    current = (display_settings_t){stored.rotation,
                                   stored.brightness,
                                   stored.version == 1 ? default_units : stored.units,
                                   stored.revision,
                                   stored.version == 3 ? stored.cycle_seconds : 0,
                                   (flags & 1) != 0,
                                   (flags & 2) != 0};
    return ESP_OK;
}

display_settings_t display_settings_snapshot(void)
{
    /* Never wait for NVS/flash on the Bluetooth or UI task. */
    portENTER_CRITICAL(&snapshot_mux);
    display_settings_t result = current;
    portEXIT_CRITICAL(&snapshot_mux);
    return result;
}

static esp_err_t save_settings(uint8_t             rotation,
                               uint8_t             brightness,
                               uint8_t             units,
                               uint16_t            cycle_seconds,
                               bool                smart_cycle,
                               bool                preserve_smart,
                               int                 orientation_mode,
                               uint32_t            expected_revision,
                               display_settings_t *saved)
{
    if (!valid(rotation, brightness, units, cycle_seconds))
        return ESP_ERR_INVALID_ARG;
    if (!settings_lock)
        return ESP_ERR_INVALID_STATE;
    if (xSemaphoreTake(settings_lock, pdMS_TO_TICKS(250)) != pdTRUE)
        return ESP_ERR_TIMEOUT;
    display_settings_t previous = display_settings_snapshot();
    if (previous.revision != expected_revision || previous.revision == UINT32_MAX)
    {
        xSemaphoreGive(settings_lock);
        return ESP_ERR_INVALID_STATE;
    }
    display_settings_t next      = {rotation,
                                    brightness,
                                    units,
                                    previous.revision + 1,
                                    cycle_seconds,
                                    preserve_smart ? previous.smart_cycle : smart_cycle,
                                    orientation_mode < 0 ? previous.auto_orientation && rotation == previous.rotation
                                                         : orientation_mode != 0};
    int                next_slot = active_smart_slot == 0 ? 1 : 0;
    smart_record_t     smart     = {1, (next.smart_cycle ? 1 : 0) | (next.auto_orientation ? 2 : 0), 0, next.revision};
    display_record_t   record    = {3, rotation, brightness, units, next.revision, cycle_seconds, 0};
    nvs_handle_t       nvs;
    esp_err_t          err = nvs_open("display", NVS_READWRITE, &nvs);
    if (err == ESP_OK)
    {
        err = nvs_set_blob(nvs, smart_keys[next_slot], &smart, sizeof(smart));
        if (err == ESP_OK)
            err = nvs_commit(nvs);
        if (err == ESP_OK)
            err = nvs_set_blob(nvs, "settings", &record, sizeof(record));
        if (err == ESP_OK)
            err = nvs_commit(nvs);
        nvs_close(nvs);
    }
    if (err == ESP_OK)
    {
        portENTER_CRITICAL(&snapshot_mux);
        if (current.auto_orientation != next.auto_orientation)
            auto_candidate_valid = false;
        current           = next;
        active_smart_slot = next_slot;
        portEXIT_CRITICAL(&snapshot_mux);
        if (saved)
            *saved = next;
    }
    xSemaphoreGive(settings_lock);
    return err;
}

esp_err_t display_settings_save(uint8_t             rotation,
                                uint8_t             brightness,
                                uint8_t             units,
                                uint16_t            cycle_seconds,
                                uint32_t            expected_revision,
                                display_settings_t *saved)
{
    return save_settings(rotation, brightness, units, cycle_seconds, false, true, 0, expected_revision, saved);
}

esp_err_t display_settings_save_smart(uint8_t             rotation,
                                      uint8_t             brightness,
                                      uint8_t             units,
                                      uint16_t            cycle_seconds,
                                      bool                smart_cycle,
                                      uint32_t            expected_revision,
                                      display_settings_t *saved)
{
    return save_settings(rotation, brightness, units, cycle_seconds, smart_cycle, false, -1, expected_revision, saved);
}

esp_err_t display_settings_save_orientation(bool                automatic,
                                            uint8_t             manual_rotation,
                                            uint32_t            expected_revision,
                                            display_settings_t *saved)
{
    display_settings_t previous = display_settings_snapshot();
    return save_settings(manual_rotation, previous.brightness, previous.units, previous.cycle_seconds, false, true,
                         automatic ? 1 : 0, expected_revision, saved);
}

uint8_t display_settings_boot_rotation(void)
{
    portENTER_CRITICAL(&snapshot_mux);
    uint8_t rotation = current.auto_orientation && auto_hint_valid ? auto_hint : current.rotation;
    portEXIT_CRITICAL(&snapshot_mux);
    return rotation;
}

void display_settings_observe_auto_rotation(uint8_t applied_rotation, uint64_t now_ms)
{
    portENTER_CRITICAL(&snapshot_mux);
    if (!current.auto_orientation || applied_rotation > 3)
        auto_candidate_valid = false;
    else if (!auto_candidate_valid || auto_candidate != applied_rotation || now_ms < auto_candidate_since_ms)
    {
        auto_candidate          = applied_rotation;
        auto_candidate_since_ms = now_ms;
        auto_candidate_valid    = true;
    }
    portEXIT_CRITICAL(&snapshot_mux);
}

esp_err_t display_settings_persist_auto_rotation(uint64_t now_ms)
{
    if (!settings_lock)
        return ESP_ERR_INVALID_STATE;
    /* Never delay the application loop behind a settings writer. */
    if (xSemaphoreTake(settings_lock, 0) != pdTRUE)
        return ESP_ERR_TIMEOUT;
    portENTER_CRITICAL(&snapshot_mux);
    bool ready = current.auto_orientation && auto_candidate_valid &&
                 (!auto_hint_valid || auto_hint != auto_candidate) && now_ms >= auto_candidate_since_ms &&
                 now_ms - auto_candidate_since_ms >= AUTO_ROTATION_STABLE_MS &&
                 (!auto_attempted ||
                  (now_ms >= auto_attempted_at_ms && now_ms - auto_attempted_at_ms >= AUTO_ROTATION_WRITE_INTERVAL_MS));
    uint8_t rotation = auto_candidate;
    if (ready)
    {
        auto_attempted       = true;
        auto_attempted_at_ms = now_ms;
    }
    portEXIT_CRITICAL(&snapshot_mux);
    esp_err_t err = ESP_OK;
    if (ready)
    {
        auto_rotation_record_t record = {1, rotation, 0};
        nvs_handle_t           nvs;
        err = nvs_open("display", NVS_READWRITE, &nvs);
        if (err == ESP_OK)
        {
            err = nvs_set_blob(nvs, "auto_angle", &record, sizeof(record));
            if (err == ESP_OK)
                err = nvs_commit(nvs);
            nvs_close(nvs);
        }
        if (err == ESP_OK)
        {
            portENTER_CRITICAL(&snapshot_mux);
            auto_hint       = rotation;
            auto_hint_valid = true;
            portEXIT_CRITICAL(&snapshot_mux);
        }
    }
    xSemaphoreGive(settings_lock);
    return err;
}
