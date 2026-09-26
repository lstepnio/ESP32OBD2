#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "config_store.h"
#include "config_runtime.h"
#include "config_transfer.h"
#include "esp_system.h"
#include "transfer_gate.h"

#define OP_BEGIN 0x10
#define OP_DIGEST 0x11
#define OP_START 0x12
#define OP_CHUNK 0x13
#define OP_VERIFY 0x14
#define OP_COMMIT 0x15
#define OP_ABORT 0x16
#define OP_STATUS 0x17
#define PHASE_IDLE 0
#define PHASE_METADATA 1
#define PHASE_RECEIVING 2
#define PHASE_VERIFIED 3
#define PHASE_APPLIED 4
#define RESULT_OK 0
#define RESULT_PENDING 1
#define RESULT_BAD_REQUEST 2
#define RESULT_CONFLICT 3
#define RESULT_STORAGE 4
#define RESULT_INVALID_DOCUMENT 5
#define RESULT_UNAVAILABLE 6

typedef struct {
    uint16_t length;
    uint8_t data[CONFIG_TRANSFER_MAX_REQUEST];
} request_t;

static QueueHandle_t requests;
static SemaphoreHandle_t lock;
static struct {
    uint8_t phase;
    uint8_t result;
    uint8_t last_op;
    uint8_t digest_parts;
    uint32_t sequence;
    uint32_t transfer_id;
    uint32_t base_revision;
    uint32_t total_length;
    uint32_t accepted;
    uint32_t last_activity;
    uint8_t digest[32];
} session;
static const char *TAG = "config_transfer";

static uint32_t u32(const uint8_t *data)
{
    return (uint32_t)data[0] | ((uint32_t)data[1] << 8) |
           ((uint32_t)data[2] << 16) | ((uint32_t)data[3] << 24);
}

static void put_u32(uint8_t *data, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i) data[i] = (uint8_t)(value >> (8 * i));
}

static uint8_t result_for_error(esp_err_t err)
{
    if (err == ESP_OK) return RESULT_OK;
    if (err == ESP_ERR_INVALID_CRC || err == ESP_ERR_INVALID_ARG) return RESULT_INVALID_DOCUMENT;
    if (err == ESP_ERR_INVALID_STATE) return RESULT_CONFLICT;
    if (err == ESP_ERR_NOT_SUPPORTED) return RESULT_UNAVAILABLE;
    return RESULT_STORAGE;
}

static void process(const request_t *request)
{
    const uint8_t *p = request->data;
    uint8_t op = p[0];
    uint32_t seq = u32(p + 1);
    uint32_t id = request->length >= 9 ? u32(p + 5) : 0;
    xSemaphoreTake(lock, portMAX_DELAY);
    if (op == OP_STATUS) {
        /* Rejoin must preserve the previous operation result. */
        session.last_activity = xTaskGetTickCount();
        xSemaphoreGive(lock);
        return;
    }
    session.sequence = seq;
    session.last_op = op;
    session.result = RESULT_BAD_REQUEST;
    session.last_activity = xTaskGetTickCount();
    if (op == OP_BEGIN && request->length == 17 && id != 0) {
        config_store_record_t active;
        uint32_t revision = config_store_active(&active) == ESP_OK ? active.revision : 0;
        uint32_t base = u32(p + 9), length = u32(p + 13);
        if (session.phase != PHASE_IDLE)
            session.result = RESULT_CONFLICT;
        else if (base != revision) session.result = RESULT_CONFLICT;
        else if (length == 0 || length > EGAUGE_CONFIG_MAX_BYTES)
            session.result = RESULT_BAD_REQUEST;
        else if (!transfer_gate_claim(1)) session.result = RESULT_CONFLICT;
        else {
            memset(session.digest, 0, sizeof(session.digest));
            session.digest_parts = 0;
            session.transfer_id = id;
            session.base_revision = base;
            session.total_length = length;
            session.accepted = 0;
            session.phase = PHASE_METADATA;
            session.result = RESULT_OK;
        }
    } else if (id != 0 && id == session.transfer_id) {
        if (op == OP_DIGEST && request->length == 18 && session.phase == PHASE_METADATA &&
            p[9] < 4 && (session.digest_parts & (1U << p[9])) == 0) {
            memcpy(session.digest + p[9] * 8, p + 10, 8);
            session.digest_parts |= 1U << p[9];
            session.result = RESULT_OK;
        } else if (op == OP_START && request->length == 9 &&
                   session.phase == PHASE_METADATA && session.digest_parts == 15) {
            esp_err_t err = config_store_begin(session.base_revision, session.total_length,
                                               session.digest);
            session.result = result_for_error(err);
            if (err == ESP_OK) session.phase = PHASE_RECEIVING;
        } else if (op == OP_CHUNK && request->length >= 14 &&
                   session.phase == PHASE_RECEIVING) {
            uint32_t offset = u32(p + 9);
            if (offset <= session.accepted &&
                request->length - 13 <= session.total_length - offset) {
                esp_err_t err = config_store_write(offset, p + 13, request->length - 13);
                session.result = result_for_error(err);
                if (err == ESP_OK && offset == session.accepted)
                    session.accepted += request->length - 13;
            } else session.result = RESULT_CONFLICT;
        } else if (op == OP_VERIFY && request->length == 9 &&
                   session.phase == PHASE_RECEIVING) {
            esp_err_t err = config_store_validate();
            session.result = result_for_error(err);
            if (err == ESP_OK) session.phase = PHASE_VERIFIED;
        } else if (op == OP_COMMIT && request->length == 9 &&
                   session.phase == PHASE_VERIFIED) {
            config_store_record_t committed;
            esp_err_t err = config_store_commit(config_runtime_validate, NULL, &committed);
            session.result = result_for_error(err);
            if (err == ESP_OK) {
                session.phase = PHASE_APPLIED;
                transfer_gate_release(1);
            }
        } else if (op == OP_ABORT && request->length == 9) {
            config_store_abort();
            session.phase = PHASE_IDLE;
            transfer_gate_release(1);
            session.result = RESULT_OK;
        }
    } else if (op == OP_COMMIT) session.result = RESULT_UNAVAILABLE;
    ESP_LOGI(TAG, "op=%u seq=%lu result=%u offset=%lu", op, (unsigned long)seq,
             session.result, (unsigned long)session.accepted);
    xSemaphoreGive(lock);
}

static void worker(void *arg)
{
    (void)arg;
    request_t request;
    for (;;) {
        if (xQueueReceive(requests, &request, pdMS_TO_TICKS(1000)) == pdTRUE)
            process(&request);
        xSemaphoreTake(lock, portMAX_DELAY);
        if (session.phase != PHASE_IDLE && session.phase != PHASE_APPLIED &&
            xTaskGetTickCount() - session.last_activity > pdMS_TO_TICKS(600000)) {
            config_store_abort();
            session.phase = PHASE_IDLE;
            transfer_gate_release(1);
            session.result = RESULT_CONFLICT;
        }
        bool restart = session.phase == PHASE_APPLIED &&
            xTaskGetTickCount() - session.last_activity > pdMS_TO_TICKS(5000);
        xSemaphoreGive(lock);
        if (restart) esp_restart();
    }
}

esp_err_t config_transfer_init(void)
{
    lock = xSemaphoreCreateMutex();
    requests = xQueueCreate(4, sizeof(request_t));
    if (!lock || !requests) return ESP_ERR_NO_MEM;
    return xTaskCreate(worker, "cfg_transfer", 6144, NULL, 4, NULL) == pdPASS
        ? ESP_OK : ESP_ERR_NO_MEM;
}

bool config_transfer_command(const uint8_t *bytes, size_t length)
{
    if (!requests || !bytes || length < 5 || length > CONFIG_TRANSFER_MAX_REQUEST ||
        bytes[0] < OP_BEGIN || bytes[0] > OP_STATUS) return false;
    request_t request = {.length = length};
    memcpy(request.data, bytes, length);
    return xQueueSend(requests, &request, 0) == pdTRUE;
}

size_t config_transfer_status(uint8_t out[CONFIG_TRANSFER_STATUS_SIZE])
{
    config_store_record_t active;
    bool has_active = config_store_active(&active) == ESP_OK;
    uint32_t revision = has_active ? active.revision : 0;
    memset(out, 0, CONFIG_TRANSFER_STATUS_SIZE);
    xSemaphoreTake(lock, portMAX_DELAY);
    out[0] = 3;
    out[1] = session.phase;
    out[2] = session.result;
    out[3] = session.last_op;
    put_u32(out + 4, session.sequence);
    put_u32(out + 8, session.transfer_id);
    put_u32(out + 12, session.accepted);
    put_u32(out + 16, session.total_length);
    put_u32(out + 20, revision);
    put_u32(out + 24, session.base_revision);
    out[28] = session.digest_parts;
    if (has_active) memcpy(out + 32, active.sha256, 32);
    xSemaphoreGive(lock);
    return CONFIG_TRANSFER_STATUS_SIZE;
}

void config_transfer_disconnect(void)
{
    /* A staged transfer may rejoin after a phone disconnect within ten minutes. */
}
