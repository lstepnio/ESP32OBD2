#include <string.h>
#include <stdatomic.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "ble_mgr.h"
#include "adapter_status.h"

static portMUX_TYPE status_mux = portMUX_INITIALIZER_UNLOCKED;
static bool initialized;
static uint8_t snapshots[2][ADAPTER_SOURCE_STATUS_SIZE];
static uint32_t observed_at[2];
static atomic_bool source_ready[2];
static unsigned source_count;
static void u32(uint8_t *p, uint32_t value) { for (unsigned i=0; i<4; ++i) p[i] = value >> (8*i); }
static uint32_t get32(const uint8_t *p) { return (uint32_t)p[0] | ((uint32_t)p[1]<<8) | ((uint32_t)p[2]<<16) | ((uint32_t)p[3]<<24); }

void adapter_status_init(const config_runtime_t *runtime)
{
    portENTER_CRITICAL(&status_mux);
    memset(snapshots, 0, sizeof(snapshots));
    source_count = runtime ? runtime->source_count : 1;
    for (unsigned source = 0; source < source_count; ++source) {
        uint8_t *snapshot = snapshots[source];
        const runtime_source_t *configured = runtime ? &runtime->sources[source] : NULL;
        snapshot[0] = 14;
        snapshot[1] = !runtime || runtime->legacy_auto_discovery || configured->address[0] ? 1 : 0;
        if (configured) {
            snapshot[3] = (configured->address[0] ? 1 : 0) | (configured->simulated ? 2 : 0);
            memcpy(snapshot + 16, runtime->vehicle_id, strlen(runtime->vehicle_id));
            memcpy(snapshot + 80, configured->id, strlen(configured->id));
            snapshot[118] = configured->address_type;
            snapshot[119] = configured->simulated ? 2 : 1;
        }
        atomic_store(&source_ready[source], false);
        observed_at[source] = 0;
    }
    initialized = true;
    portEXIT_CRITICAL(&status_mux);
}

void adapter_status_event(unsigned source, uint32_t generation, uint8_t phase, int result)
{
    if (source >= source_count || !initialized) return;
    portENTER_CRITICAL(&status_mux);
    uint8_t *snapshot = snapshots[source];
    if ((int32_t)(generation-get32(snapshot+4)) < 0) {
        portEXIT_CRITICAL(&status_mux);
        return;
    }
    if (generation != get32(snapshot+4) || phase <= 1) {
        snapshot[120] = 0;
        memset(snapshot+121, 0, 32);
    }
    atomic_store(&source_ready[source], phase == 4);
    snapshot[1] = phase;
    snapshot[2] = (uint8_t)result;
    u32(snapshot+4, generation);
    portEXIT_CRITICAL(&status_mux);
}

void adapter_status_support(unsigned source, uint32_t generation, uint32_t ecu,
                            uint8_t base, const uint8_t *bytes, size_t length)
{
    if (source >= source_count || !initialized || !bytes || length != 4 || base%32 || base > 224) return;
    portENTER_CRITICAL(&status_mux);
    uint8_t *snapshot = snapshots[source];
    if (generation == get32(snapshot+4)) {
        u32(snapshot+12, ecu);
        snapshot[120] |= 1U << (base/32);
        memcpy(snapshot+121+(base/32)*4, bytes, 4);
        observed_at[source] = pdTICKS_TO_MS(xTaskGetTickCount());
    }
    portEXIT_CRITICAL(&status_mux);
}

size_t adapter_status_snapshot_for(unsigned source, uint8_t out[ADAPTER_SOURCE_STATUS_SIZE])
{
    if (!initialized || source >= source_count || !out) return 0;
    portENTER_CRITICAL(&status_mux);
    memcpy(out, snapshots[source], ADAPTER_SOURCE_STATUS_SIZE);
    uint32_t now = pdTICKS_TO_MS(xTaskGetTickCount());
    u32(out+8, now-observed_at[source]);
    u32(out+153, now);
    if (ble_mgr_is_paused()) { out[1] = 6; out[120] = 0; }
    portEXIT_CRITICAL(&status_mux);
    return ADAPTER_SOURCE_STATUS_SIZE;
}

bool adapter_status_ready_for(unsigned source) { return source < source_count && atomic_load(&source_ready[source]) && !ble_mgr_is_paused(); }
uint32_t adapter_status_generation(unsigned source) {
    if (source >= source_count) return 0;
    portENTER_CRITICAL(&status_mux);
    uint32_t value = get32(snapshots[source]+4);
    portEXIT_CRITICAL(&status_mux);
    return value;
}
size_t adapter_status_snapshot(uint8_t out[ADAPTER_SOURCE_STATUS_SIZE]) { return adapter_status_snapshot_for(0, out); }
bool adapter_status_ready(void) { return adapter_status_ready_for(0); }

unsigned adapter_status_count(void) { return source_count; }
