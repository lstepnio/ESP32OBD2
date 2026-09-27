#include <stdatomic.h>
#include <string.h>

#include "sdkconfig.h"
#include "esp_chip_info.h"
#include "esp_flash.h"
#include "esp_heap_caps.h"
#include "esp_psram.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "hardware_probe.h"

#define HARDWARE_PROBE_VERSION 10U
#define HARDWARE_PROBE_SCHEMA 1U

static atomic_uint_fast32_t initialized_features;

static void write_u16(uint8_t *out, uint16_t value)
{
    out[0] = (uint8_t)value;
    out[1] = (uint8_t)(value >> 8);
}

static void write_u32(uint8_t *out, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i) out[i] = (uint8_t)(value >> (8 * i));
}

void hardware_probe_set_ready(uint32_t features, bool ready)
{
    if (ready) atomic_fetch_or(&initialized_features, features);
    else atomic_fetch_and(&initialized_features, ~features);
}

size_t hardware_probe_status(uint8_t out[HARDWARE_PROBE_STATUS_SIZE])
{
    if (!out) return 0;
    memset(out, 0, HARDWARE_PROBE_STATUS_SIZE);

    esp_chip_info_t chip = {0};
    esp_chip_info(&chip);
    uint32_t flash_size = 0;
    (void)esp_flash_get_size(NULL, &flash_size);

    uint32_t declared = HARDWARE_FEATURE_WIFI | HARDWARE_FEATURE_BLE |
        HARDWARE_FEATURE_DISPLAY | HARDWARE_FEATURE_TOUCH |
        HARDWARE_FEATURE_BACKLIGHT | HARDWARE_FEATURE_IMU |
        HARDWARE_FEATURE_BATTERY_ADC | HARDWARE_FEATURE_EXPANSION |
        HARDWARE_FEATURE_USB_UART;
    size_t psram_total = heap_caps_get_total_size(MALLOC_CAP_SPIRAM);
    if (psram_total > 0 && esp_psram_is_initialized()) declared |= HARDWARE_FEATURE_PSRAM;

    uint32_t initialized = (uint32_t)atomic_load(&initialized_features);
    if (psram_total > 0 && esp_psram_is_initialized()) initialized |= HARDWARE_FEATURE_PSRAM;
    wifi_mode_t wifi_mode = WIFI_MODE_NULL;
    esp_err_t wifi_result = esp_wifi_get_mode(&wifi_mode);
    if (wifi_result == ESP_OK) initialized |= HARDWARE_FEATURE_WIFI;

    out[0] = HARDWARE_PROBE_VERSION;
    out[1] = HARDWARE_PROBE_SCHEMA;
    out[2] = (uint8_t)chip.model;
    out[3] = chip.cores;
    write_u16(out + 4, chip.revision);
    write_u16(out + 6, CONFIG_ESP_DEFAULT_CPU_FREQ_MHZ);
    out[8] = (uint8_t)esp_reset_reason();
    out[9] = wifi_result == ESP_OK ? (uint8_t)wifi_mode : UINT8_MAX;
    write_u32(out + 12, declared);
    write_u32(out + 16, initialized);
    write_u32(out + 20, flash_size);
    write_u32(out + 24, heap_caps_get_total_size(MALLOC_CAP_INTERNAL));
    write_u32(out + 28, heap_caps_get_free_size(MALLOC_CAP_INTERNAL));
    write_u32(out + 32, heap_caps_get_minimum_free_size(MALLOC_CAP_INTERNAL));
    write_u32(out + 36, heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL));
    write_u32(out + 40, psram_total);
    write_u32(out + 44, heap_caps_get_free_size(MALLOC_CAP_SPIRAM));
    write_u32(out + 48, heap_caps_get_minimum_free_size(MALLOC_CAP_SPIRAM));
    write_u32(out + 52, (uint32_t)(esp_timer_get_time() / 1000000ULL));
    return HARDWARE_PROBE_STATUS_SIZE;
}
