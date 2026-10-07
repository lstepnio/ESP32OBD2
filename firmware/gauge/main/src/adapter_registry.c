#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "util.h"
#include "ble_mgr.h"
#include "adapter_registry.h"

static SemaphoreHandle_t lock;
static QueueHandle_t requests;
static uint8_t snapshot[ADAPTER_SCAN_STATUS_SIZE];

static void found(const ble_addr_t *addr, const char *name, uint8_t driver, void *context)
{
    (void)context;
    if (addr->type > 1) return; /* Public or static/random address only. */
    xSemaphoreTake(lock, portMAX_DELAY);
    unsigned count = snapshot[8];
    for (unsigned i = 0; i < count; ++i) {
        const uint8_t *entry = snapshot + 12 + 32*i;
        if (entry[6] == addr->type && !memcmp(entry, addr->val, 6)) {
            xSemaphoreGive(lock);
            return;
        }
    }
    if (count < 4) {
        uint8_t *entry = snapshot + 12 + 32*count;
        memcpy(entry, addr->val, 6);
        entry[6] = addr->type;
        entry[7] = driver; /* elm-18f0-v1, still requires characteristic validation. */
        strncpy((char *)entry + 8, name, 23);
        snapshot[8] = count + 1;
    }
    xSemaphoreGive(lock);
}

static void worker(void *arg)
{
    (void)arg;
    uint32_t sequence;
    for (;;) {
        if (xQueueReceive(requests, &sequence, portMAX_DELAY) != pdTRUE) continue;
        ble_mgr_status_t status = ble_mgr_scan_adapters(found, NULL);
        xSemaphoreTake(lock, portMAX_DELAY);
        snapshot[1] = status == BLE_MGR_E_OK ? 2 : 3;
        snapshot[2] = status;
        xSemaphoreGive(lock);
    }
}

esp_err_t adapter_registry_init(void)
{
    lock = xSemaphoreCreateMutex();
    requests = xQueueCreate(1, sizeof(uint32_t));
    snapshot[0] = 13;
    if (!lock || !requests) return ESP_ERR_NO_MEM;
    return xTaskCreate(worker, "adapter_scan", 3072, NULL, 3, NULL) == pdPASS ? ESP_OK : ESP_ERR_NO_MEM;
}

bool adapter_registry_command(const uint8_t *bytes, size_t length)
{
    if (!lock || !requests || !bytes || length != 5) return false;
    if (bytes[0] == 0x51) return true;
    if (bytes[0] != 0x50 || ble_mgr_is_paused()) return false;
    uint32_t sequence = (uint32_t)bytes[1] | ((uint32_t)bytes[2] << 8) |
                        ((uint32_t)bytes[3] << 16) | ((uint32_t)bytes[4] << 24);
    if (!sequence) return false;
    xSemaphoreTake(lock, portMAX_DELAY);
    bool accepted = snapshot[1] != 1;
    if (accepted) {
        memset(snapshot, 0, sizeof(snapshot));
        snapshot[0] = 13;
        snapshot[1] = 1;
        memcpy(snapshot + 4, bytes + 1, 4);
        if (xQueueSend(requests, &sequence, 0) != pdTRUE) {
            snapshot[1] = 3;
            accepted = false;
        }
    }
    xSemaphoreGive(lock);
    return accepted;
}

size_t adapter_registry_status(uint8_t out[ADAPTER_SCAN_STATUS_SIZE])
{
    if (!lock || !out) return 0;
    xSemaphoreTake(lock, portMAX_DELAY);
    memcpy(out, snapshot, sizeof(snapshot));
    xSemaphoreGive(lock);
    return sizeof(snapshot);
}
