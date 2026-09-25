#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include "esp_ota_ops.h"
#include "esp_partition.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "mbedtls/sha256.h"
#include "mbedtls/pk.h"
#include "ota_transfer.h"
#include "transfer_gate.h"

#define OP_BEGIN 0x20
#define OP_DIGEST 0x21
#define OP_START 0x22
#define OP_CHUNK 0x23
#define OP_VERIFY 0x24
#define OP_ACTIVATE 0x25
#define OP_ABORT 0x26
#define OP_STATUS 0x27
#define OP_SIGNATURE 0x28
#define BOARD_TAG 0x31534745U /* EGS1 little endian */

typedef struct {
    uint16_t length;
    uint8_t data[173];
} request_t;

static QueueHandle_t queue;
static SemaphoreHandle_t lock;
static const esp_partition_t *target;
static esp_ota_handle_t handle;
extern const uint8_t update_public_key_start[] asm("_binary_dev_update_public_pem_start");
extern const uint8_t update_public_key_end[] asm("_binary_dev_update_public_pem_end");
static struct {
    uint8_t phase; /* 0 idle, 1 metadata, 2 receiving, 3 ready, 4 activating */
    uint8_t result; /* 0 ok, 1 pending, 2 invalid, 3 conflict, 4 flash, 5 unsupported */
    uint8_t last_op;
    uint8_t digest_parts;
    uint32_t sequence;
    uint32_t id;
    uint32_t length;
    uint32_t accepted;
    uint32_t last_activity;
    uint8_t digest[32];
    uint8_t signature[72];
    uint8_t signature_length;
    uint16_t signature_parts;
} state;

static uint32_t u32(const uint8_t *p)
{
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static void put_u32(uint8_t *p, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i) p[i] = value >> (8 * i);
}

static void abort_transfer(void)
{
    if (state.phase == 2) esp_ota_abort(handle);
    target = NULL;
    handle = 0;
    state.phase = 0;
    transfer_gate_release(2);
}

static uint8_t verify_image(void)
{
    if (state.accepted != state.length) return 3;
    uint8_t bytes[1024], actual[32];
    mbedtls_sha256_context hash;
    mbedtls_sha256_init(&hash);
    int rc = mbedtls_sha256_starts(&hash, 0);
    for (uint32_t offset = 0; rc == 0 && offset < state.length;) {
        size_t count = state.length - offset;
        if (count > sizeof(bytes)) count = sizeof(bytes);
        if (esp_partition_read(target, offset, bytes, count) != ESP_OK) {
            mbedtls_sha256_free(&hash);
            return 4;
        }
        rc = mbedtls_sha256_update(&hash, bytes, count);
        offset += count;
    }
    if (rc == 0) rc = mbedtls_sha256_finish(&hash, actual);
    mbedtls_sha256_free(&hash);
    if (rc != 0 || memcmp(actual, state.digest, 32) != 0) return 2;
    uint8_t signed_bytes[40];
    put_u32(signed_bytes, BOARD_TAG);
    put_u32(signed_bytes + 4, state.length);
    memcpy(signed_bytes + 8, actual, 32);
    uint8_t signed_digest[32];
    if (mbedtls_sha256(signed_bytes, sizeof(signed_bytes), signed_digest, 0) != 0)
        return 2;
    mbedtls_pk_context key;
    mbedtls_pk_init(&key);
    rc = mbedtls_pk_parse_public_key(&key, update_public_key_start,
                                     update_public_key_end - update_public_key_start);
    if (rc == 0) rc = mbedtls_pk_verify(&key, MBEDTLS_MD_SHA256, signed_digest,
                                       sizeof(signed_digest), state.signature,
                                       state.signature_length);
    mbedtls_pk_free(&key);
    if (rc != 0) return 2;
    if (esp_ota_end(handle) != ESP_OK) {
        handle = 0;
        return 2;
    }
    handle = 0;
    state.phase = 3;
    return 0;
}

static void process(const request_t *request)
{
    const uint8_t *p = request->data;
    uint8_t op = p[0];
    xSemaphoreTake(lock, portMAX_DELAY);
    if (op == OP_STATUS) {
        state.last_activity = xTaskGetTickCount();
        xSemaphoreGive(lock);
        return;
    }
    state.last_op = op;
    state.sequence = u32(p + 1);
    state.last_activity = xTaskGetTickCount();
    state.result = 2;
    uint32_t id = request->length >= 9 ? u32(p + 5) : 0;
    if (op == OP_BEGIN && request->length == 17 && id != 0) {
        const esp_partition_t *next = esp_ota_get_next_update_partition(NULL);
        uint32_t length = u32(p + 9);
        if (state.phase != 0) state.result = 3;
        else if (u32(p + 13) != BOARD_TAG || !next ||
                 length < 1024 || length > next->size) state.result = 5;
        else if (!transfer_gate_claim(2)) state.result = 3;
        else {
            target = next;
            state.id = id;
            state.length = length;
            state.accepted = 0;
            state.digest_parts = 0;
            state.signature_parts = 0;
            state.signature_length = 0;
            memset(state.digest, 0, 32);
            memset(state.signature, 0, sizeof(state.signature));
            state.phase = 1;
            state.result = 0;
        }
    } else if (id && id == state.id) {
        if (op == OP_DIGEST && request->length == 18 && state.phase == 1 &&
            p[9] < 4 && (state.digest_parts & (1U << p[9])) == 0) {
            memcpy(state.digest + p[9] * 8, p + 10, 8);
            state.digest_parts |= 1U << p[9];
            state.result = 0;
        } else if (op == OP_SIGNATURE && request->length >= 12 &&
                   state.phase == 1 && p[9] < 9 && p[10] >= 64 && p[10] <= 72 &&
                   (state.signature_length == 0 || state.signature_length == p[10])) {
            uint8_t part = p[9];
            uint8_t length = p[10];
            size_t remaining = length - part * 8;
            size_t expected = remaining > 8 ? 8 : remaining;
            if (remaining > 0 && request->length - 11 == expected &&
                (state.signature_parts & (1U << part)) == 0) {
                memcpy(state.signature + part * 8, p + 11, expected);
                state.signature_length = length;
                state.signature_parts |= 1U << part;
                state.result = 0;
            }
        } else if (op == OP_START && request->length == 9 && state.phase == 1 &&
                   state.digest_parts == 15 && state.signature_length != 0 &&
                   state.signature_parts ==
                     ((1U << ((state.signature_length + 7) / 8)) - 1U)) {
            if (esp_ota_begin(target, OTA_SIZE_UNKNOWN, &handle) == ESP_OK) {
                state.phase = 2;
                state.result = 0;
            } else state.result = 4;
        } else if (op == OP_CHUNK && request->length >= 14 && state.phase == 2) {
            uint32_t offset = u32(p + 9);
            size_t count = request->length - 13;
            if (offset == state.accepted && count <= state.length - offset) {
                if (esp_ota_write(handle, p + 13, count) == ESP_OK) {
                    state.accepted += count;
                    state.result = 0;
                } else state.result = 4;
            } else if (offset < state.accepted && count <= state.accepted - offset) {
                uint8_t previous[160];
                if (esp_partition_read(target, offset, previous, count) == ESP_OK &&
                    memcmp(previous, p + 13, count) == 0) state.result = 0;
                else state.result = 3;
            } else state.result = 3;
        } else if (op == OP_VERIFY && request->length == 9 && state.phase == 2) {
            state.result = verify_image();
        } else if (op == OP_ACTIVATE && request->length == 9 && state.phase == 3) {
            if (esp_ota_set_boot_partition(target) == ESP_OK) {
                state.phase = 4;
                state.result = 0;
                transfer_gate_release(2);
            } else state.result = 4;
        } else if (op == OP_ABORT && request->length == 9 && state.phase != 4) {
            abort_transfer();
            state.result = 0;
        }
    } else state.result = 3;
    xSemaphoreGive(lock);
}

static void worker(void *arg)
{
    (void)arg;
    request_t request;
    for (;;) {
        if (xQueueReceive(queue, &request, pdMS_TO_TICKS(1000)) == pdTRUE)
            process(&request);
        xSemaphoreTake(lock, portMAX_DELAY);
        if (state.phase > 0 && state.phase < 4 &&
            xTaskGetTickCount() - state.last_activity > pdMS_TO_TICKS(600000)) {
            abort_transfer();
            state.result = 3;
        }
        bool restart = state.phase == 4 &&
            xTaskGetTickCount() - state.last_activity > pdMS_TO_TICKS(5000);
        xSemaphoreGive(lock);
        if (restart) esp_restart();
    }
}

esp_err_t ota_transfer_init(void)
{
    lock = xSemaphoreCreateMutex();
    queue = xQueueCreate(4, sizeof(request_t));
    if (!lock || !queue) return ESP_ERR_NO_MEM;
    return xTaskCreate(worker, "ota_transfer", 6144, NULL, 4, NULL) == pdPASS
        ? ESP_OK : ESP_ERR_NO_MEM;
}

bool ota_transfer_command(const uint8_t *bytes, size_t length)
{
    if (!queue || !bytes || length < 5 || length > 173 ||
        bytes[0] < OP_BEGIN || bytes[0] > OP_SIGNATURE) return false;
    request_t request = {.length = length};
    memcpy(request.data, bytes, length);
    return xQueueSend(queue, &request, 0) == pdTRUE;
}

size_t ota_transfer_status(uint8_t out[OTA_TRANSFER_STATUS_SIZE])
{
    memset(out, 0, OTA_TRANSFER_STATUS_SIZE);
    xSemaphoreTake(lock, portMAX_DELAY);
    out[0] = 4;
    out[1] = state.phase;
    out[2] = state.result;
    out[3] = state.last_op;
    put_u32(out + 4, state.sequence);
    put_u32(out + 8, state.id);
    put_u32(out + 12, state.accepted);
    put_u32(out + 16, state.length);
    out[20] = state.digest_parts;
    out[21] = state.signature_length;
    out[22] = state.signature_parts;
    out[23] = state.signature_parts >> 8;
    memcpy(out + 24, state.digest, 32);
    xSemaphoreGive(lock);
    return OTA_TRANSFER_STATUS_SIZE;
}
