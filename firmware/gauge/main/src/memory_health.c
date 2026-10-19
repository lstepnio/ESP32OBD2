#include <limits.h>
#include <stddef.h>
#include "memory_health.h"
#include "esp_attr.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"

static portMUX_TYPE failure_mux = portMUX_INITIALIZER_UNLOCKED;
static unsigned failures, last_size, last_caps;

void IRAM_ATTR memory_health_allocation_failed(size_t size, uint32_t caps, const char *function)
{
    (void)function;
    portENTER_CRITICAL_SAFE(&failure_mux);
    last_size = size > UINT_MAX ? UINT_MAX : (unsigned)size;
    last_caps = caps;
    ++failures;
    portEXIT_CRITICAL_SAFE(&failure_mux);
}

void memory_health_init(void)
{
    esp_err_t err = heap_caps_register_failed_alloc_callback(memory_health_allocation_failed);
    if (err != ESP_OK) ESP_LOGW("memory", "Allocation failure hook unavailable (%d)", err);
    memory_health_log("boot");
}

void memory_health_log(const char *stage)
{
    const uint32_t internal = MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT;
    const uint32_t dma = MALLOC_CAP_INTERNAL | MALLOC_CAP_DMA;
    const uint32_t external = MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT;
    portENTER_CRITICAL_SAFE(&failure_mux);
    unsigned failed_count = failures, failed_size = last_size, failed_caps = last_caps;
    portEXIT_CRITICAL_SAFE(&failure_mux);
    // Diagnostic samples are approximate, not one atomic heap transaction.
    ESP_LOGI("memory", "stage=%s internal_free=%u largest=%u min=%u dma_free=%u dma_largest=%u"
             " psram_free=%u psram_largest=%u alloc_failures=%u last_size=%u last_caps=0x%x",
             stage, (unsigned)heap_caps_get_free_size(internal),
             (unsigned)heap_caps_get_largest_free_block(internal),
             (unsigned)heap_caps_get_minimum_free_size(internal),
             (unsigned)heap_caps_get_free_size(dma),
             (unsigned)heap_caps_get_largest_free_block(dma),
             (unsigned)heap_caps_get_free_size(external),
             (unsigned)heap_caps_get_largest_free_block(external),
             failed_count, failed_size, failed_caps);
}
