#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "diagnostics_state.h"

static SemaphoreHandle_t lock;
static struct {
    bool mil_valid;
    bool mil_on;
    uint8_t reported_count;
    uint32_t mil_at;
    bool known[3];
    uint8_t count[3];
    uint16_t first[3];
    uint32_t observed_at[3];
} snapshot;

static int category(uint8_t mode)
{
    return mode == 3 ? 0 : mode == 7 ? 1 : mode == 10 ? 2 : -1;
}

static void put_u32(uint8_t *p, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i) p[i] = value >> (8 * i);
}

esp_err_t diagnostics_state_init(void)
{
    lock = xSemaphoreCreateMutex();
    return lock ? ESP_OK : ESP_ERR_NO_MEM;
}

void diagnostics_state_mil(bool on, uint8_t reported_count, uint32_t now_ms)
{
    if (!lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshot.mil_valid = true;
    snapshot.mil_on = on;
    snapshot.reported_count = reported_count;
    snapshot.mil_at = now_ms;
    xSemaphoreGive(lock);
}

void diagnostics_state_codes(uint8_t mode, const uint8_t *bytes, size_t length,
                             uint32_t now_ms)
{
    int index = category(mode);
    if (!lock || index < 0 || !bytes || (length & 1U)) return;
    uint8_t count = 0;
    uint16_t first = 0;
    for (size_t i = 0; i + 1 < length; i += 2) {
        uint16_t code = ((uint16_t)bytes[i] << 8) | bytes[i + 1];
        if (!code) continue;
        if (!first) first = code;
        if (count < UINT8_MAX) ++count;
    }
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshot.known[index] = true;
    snapshot.count[index] = count;
    snapshot.first[index] = first;
    snapshot.observed_at[index] = now_ms;
    xSemaphoreGive(lock);
}

void diagnostics_state_disconnected(void)
{
    if (!lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshot.mil_valid = false;
    for (unsigned i = 0; i < 3; ++i) snapshot.known[i] = false;
    xSemaphoreGive(lock);
}

size_t diagnostics_state_status(uint8_t out[DIAGNOSTICS_STATUS_SIZE])
{
    memset(out, 0, DIAGNOSTICS_STATUS_SIZE);
    if (!lock) return DIAGNOSTICS_STATUS_SIZE;
    xSemaphoreTake(lock, portMAX_DELAY);
    uint32_t now = pdTICKS_TO_MS(xTaskGetTickCount());
    out[0] = 5;
    if (snapshot.mil_valid && now - snapshot.mil_at <= 60000) out[1] |= 1;
    if (snapshot.mil_on) out[1] |= 2;
    out[2] = snapshot.reported_count;
    for (unsigned i = 0; i < 3; ++i) {
        if (snapshot.known[i] && now - snapshot.observed_at[i] <= 120000)
            out[1] |= (uint8_t)(4U << i);
        out[3 + i] = snapshot.count[i];
        out[6 + i * 2] = snapshot.first[i] >> 8;
        out[7 + i * 2] = snapshot.first[i];
        put_u32(out + 12 + i * 4, snapshot.observed_at[i]);
    }
    put_u32(out + 24, snapshot.mil_at);
    put_u32(out + 28, now);
    xSemaphoreGive(lock);
    return DIAGNOSTICS_STATUS_SIZE;
}
