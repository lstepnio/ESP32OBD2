#include <string.h>
#include <stdatomic.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "ble_mgr.h"
#include "adapter_status.h"

static SemaphoreHandle_t lock;
static uint8_t snapshot[ADAPTER_SOURCE_STATUS_SIZE];
static uint32_t observed_at;
static atomic_bool source_ready;
static void u32(uint8_t *p, uint32_t value) { for (unsigned i=0; i<4; ++i) p[i] = value >> (8*i); }
static uint32_t get32(const uint8_t *p) { return (uint32_t)p[0] | ((uint32_t)p[1]<<8) | ((uint32_t)p[2]<<16) | ((uint32_t)p[3]<<24); }

void adapter_status_init(const config_runtime_t *runtime)
{
    lock = xSemaphoreCreateMutex();
    memset(snapshot, 0, sizeof(snapshot));
    snapshot[0] = 14;
    snapshot[1] = !runtime || runtime->legacy_auto_discovery || runtime->adapter_address[0] ? 1 : 0;
    if (runtime) {
        snapshot[3] = (runtime->adapter_address[0] ? 1 : 0) | (runtime->simulated_adapter ? 2 : 0);
        memcpy(snapshot + 16, runtime->vehicle_id, strlen(runtime->vehicle_id));
        memcpy(snapshot + 80, runtime->source_id, strlen(runtime->source_id));
        snapshot[118] = runtime->adapter_address_type;
        snapshot[119] = runtime->simulated_adapter ? 2 : 1;
    }
}

void adapter_status_event(unsigned source, uint32_t generation, uint8_t phase, int result)
{
    if (source || !lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    if (generation != get32(snapshot+4) || phase <= 1) {
        snapshot[120] = 0;
        memset(snapshot+121, 0, 32);
    }
    atomic_store(&source_ready, phase == 4);
    snapshot[1] = phase;
    snapshot[2] = (uint8_t)result;
    u32(snapshot+4, generation);
    xSemaphoreGive(lock);
}

void adapter_status_support(unsigned source, uint32_t generation, uint32_t ecu,
                            uint8_t base, const uint8_t *bytes, size_t length)
{
    if (source || !lock || !bytes || length != 4 || base%32 || base > 224) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    if (generation == get32(snapshot+4)) {
        u32(snapshot+12, ecu);
        snapshot[120] |= 1U << (base/32);
        memcpy(snapshot+121+(base/32)*4, bytes, 4);
        observed_at = pdTICKS_TO_MS(xTaskGetTickCount());
    }
    xSemaphoreGive(lock);
}

size_t adapter_status_snapshot(uint8_t out[ADAPTER_SOURCE_STATUS_SIZE])
{
    if (!lock || !out) return 0;
    xSemaphoreTake(lock, portMAX_DELAY);
    memcpy(out, snapshot, sizeof(snapshot));
    uint32_t now = pdTICKS_TO_MS(xTaskGetTickCount());
    u32(out+8, now-observed_at);
    u32(out+153, now);
    if (ble_mgr_is_paused()) { out[1] = 6; out[120] = 0; }
    xSemaphoreGive(lock);
    return sizeof(snapshot);
}

bool adapter_status_ready(void) { return atomic_load(&source_ready) && !ble_mgr_is_paused(); }
