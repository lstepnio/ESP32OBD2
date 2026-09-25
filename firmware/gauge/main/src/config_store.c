#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "esp_partition.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "mbedtls/sha256.h"
#include "config_store.h"
#include "config_document.h"

#define HEADER_MAGIC 0x31434645U /* EFC1 on little-endian flash */
#define COMMIT_MAGIC 0x54494d43U /* CMIT */
#define DOCUMENT_OFFSET 0x1000U
#define SLOT_MIN_SIZE 0x20000U

typedef struct __attribute__((packed)) {
    uint32_t magic;
    uint32_t schema_version;
    uint32_t revision;
    uint32_t length;
    uint8_t sha256[32];
    uint32_t header_crc32;
    uint32_t committed;
} slot_header_t;

static const esp_partition_t *slots[2];
static int active_slot = -1;
static config_store_record_t active_record;
static SemaphoreHandle_t store_lock;
static struct {
    bool open;
    int slot;
    uint32_t length;
    uint32_t written;
    uint8_t sha256[32];
} transfer;

static uint32_t header_crc32(const slot_header_t *header)
{
    const uint8_t *bytes = (const uint8_t *)header;
    uint32_t crc = UINT32_MAX;
    for (size_t i = 0; i < offsetof(slot_header_t, header_crc32); ++i) {
        crc ^= bytes[i];
        for (int bit = 0; bit < 8; ++bit)
            crc = (crc >> 1) ^ (0xedb88320U & (0U - (crc & 1U)));
    }
    return ~crc;
}

static esp_err_t hash_document(const esp_partition_t *partition, uint32_t length,
                               uint8_t digest[32])
{
    uint8_t buffer[1024];
    mbedtls_sha256_context sha;
    mbedtls_sha256_init(&sha);
    int rc = mbedtls_sha256_starts(&sha, 0);
    for (uint32_t offset = 0; rc == 0 && offset < length;) {
        size_t count = length - offset;
        if (count > sizeof(buffer)) count = sizeof(buffer);
        esp_err_t err = esp_partition_read(partition, DOCUMENT_OFFSET + offset, buffer, count);
        if (err != ESP_OK) { mbedtls_sha256_free(&sha); return err; }
        rc = mbedtls_sha256_update(&sha, buffer, count);
        offset += count;
    }
    if (rc == 0) rc = mbedtls_sha256_finish(&sha, digest);
    mbedtls_sha256_free(&sha);
    return rc == 0 ? ESP_OK : ESP_FAIL;
}

static bool valid_slot(int index, config_store_record_t *record)
{
    slot_header_t header;
    if (esp_partition_read(slots[index], 0, &header, sizeof(header)) != ESP_OK ||
        header.magic != HEADER_MAGIC || header.committed != COMMIT_MAGIC ||
        header.header_crc32 != header_crc32(&header) ||
        header.schema_version != 1 || header.revision == 0 ||
        header.length == 0 || header.length > EGAUGE_CONFIG_MAX_BYTES) return false;
    uint8_t actual[32];
    if (hash_document(slots[index], header.length, actual) != ESP_OK ||
        memcmp(actual, header.sha256, sizeof(actual)) != 0) return false;
    config_document_context_t context = {
        .base_revision = header.revision - 1,
        .max_adapter_links = 2,
    };
    if (config_document_validate(slots[index], DOCUMENT_OFFSET, header.length,
                                 &context) != ESP_OK) return false;
    record->revision = header.revision;
    record->length = header.length;
    memcpy(record->sha256, actual, sizeof(actual));
    return true;
}

esp_err_t config_store_init(void)
{
    if (store_lock == NULL) store_lock = xSemaphoreCreateMutex();
    if (store_lock == NULL) return ESP_ERR_NO_MEM;
    slots[0] = esp_partition_find_first(ESP_PARTITION_TYPE_DATA,
                                        (esp_partition_subtype_t)0x40, "config_a");
    slots[1] = esp_partition_find_first(ESP_PARTITION_TYPE_DATA,
                                        (esp_partition_subtype_t)0x41, "config_b");
    if (slots[0] == NULL || slots[1] == NULL ||
        slots[0]->size < SLOT_MIN_SIZE || slots[1]->size < SLOT_MIN_SIZE)
        return ESP_ERR_NOT_FOUND;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    active_slot = -1;
    memset(&active_record, 0, sizeof(active_record));
    memset(&transfer, 0, sizeof(transfer));
    for (int i = 0; i < 2; ++i) {
        config_store_record_t candidate;
        if (valid_slot(i, &candidate) && candidate.revision > active_record.revision) {
            active_slot = i;
            active_record = candidate;
        }
    }
    xSemaphoreGive(store_lock);
    return ESP_OK;
}

esp_err_t config_store_active(config_store_record_t *record)
{
    if (store_lock == NULL || record == NULL) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    esp_err_t err = active_slot < 0 ? ESP_ERR_NOT_FOUND : ESP_OK;
    if (err == ESP_OK) *record = active_record;
    xSemaphoreGive(store_lock);
    return err;
}

esp_err_t config_store_read_active(uint32_t offset, void *data, size_t length)
{
    if (store_lock == NULL || data == NULL) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    esp_err_t err = ESP_ERR_NOT_FOUND;
    if (active_slot >= 0) {
        err = offset <= active_record.length && length <= active_record.length - offset
            ? esp_partition_read(slots[active_slot], DOCUMENT_OFFSET + offset, data, length)
            : ESP_ERR_INVALID_SIZE;
    }
    xSemaphoreGive(store_lock);
    return err;
}

esp_err_t config_store_begin(uint32_t base_revision, uint32_t length,
                             const uint8_t expected_sha256[32])
{
    if (store_lock == NULL || expected_sha256 == NULL || length == 0 ||
        length > EGAUGE_CONFIG_MAX_BYTES) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    esp_err_t err = ESP_ERR_INVALID_STATE;
    if (transfer.open || base_revision != active_record.revision ||
        active_record.revision == UINT32_MAX) goto done;
    int target = active_slot == 0 ? 1 : 0;
    err = esp_partition_erase_range(slots[target], 0, slots[target]->size);
    if (err != ESP_OK) goto done;
    transfer.open = true;
    transfer.slot = target;
    transfer.length = length;
    transfer.written = 0;
    memcpy(transfer.sha256, expected_sha256, 32);
done:
    xSemaphoreGive(store_lock);
    return err;
}

esp_err_t config_store_write(uint32_t offset, const void *data, size_t length)
{
    if (store_lock == NULL || data == NULL || length == 0 || length > 1024)
        return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    esp_err_t err = ESP_ERR_INVALID_STATE;
    if (!transfer.open || offset != transfer.written || length > transfer.length - offset)
        goto done;
    err = esp_partition_write(slots[transfer.slot], DOCUMENT_OFFSET + offset, data, length);
    if (err == ESP_OK) transfer.written += length;
done:
    xSemaphoreGive(store_lock);
    return err;
}

esp_err_t config_store_verify(void)
{
    if (store_lock == NULL) return ESP_ERR_INVALID_STATE;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    esp_err_t err = ESP_ERR_INVALID_STATE;
    if (!transfer.open || transfer.written != transfer.length) goto done;
    uint8_t actual[32];
    err = hash_document(slots[transfer.slot], transfer.length, actual);
    if (err == ESP_OK && memcmp(actual, transfer.sha256, sizeof(actual)) != 0)
        err = ESP_ERR_INVALID_CRC;
done:
    xSemaphoreGive(store_lock);
    return err;
}

void config_store_abort(void)
{
    if (store_lock == NULL) return;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    memset(&transfer, 0, sizeof(transfer));
    xSemaphoreGive(store_lock);
}

esp_err_t config_store_commit(config_store_validator_t validator, void *context,
                              config_store_record_t *committed)
{
    if (store_lock == NULL || validator == NULL || committed == NULL)
        return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(store_lock, portMAX_DELAY);
    esp_err_t err = ESP_ERR_INVALID_STATE;
    if (!transfer.open || transfer.written != transfer.length) goto done;
    const esp_partition_t *partition = slots[transfer.slot];
    uint8_t actual[32];
    err = hash_document(partition, transfer.length, actual);
    if (err != ESP_OK) goto done;
    if (memcmp(actual, transfer.sha256, sizeof(actual)) != 0) {
        err = ESP_ERR_INVALID_CRC;
        goto done;
    }
    config_document_context_t limits = {
        .base_revision = active_record.revision,
        .max_adapter_links = 2,
    };
    err = config_document_validate(partition, DOCUMENT_OFFSET, transfer.length, &limits);
    if (err != ESP_OK) goto done;
    err = validator(partition, DOCUMENT_OFFSET, transfer.length, context);
    if (err != ESP_OK) goto done;
    slot_header_t header = {
        .magic = HEADER_MAGIC,
        .schema_version = 1,
        .revision = active_record.revision + 1,
        .length = transfer.length,
        .committed = UINT32_MAX,
    };
    memcpy(header.sha256, actual, sizeof(actual));
    header.header_crc32 = header_crc32(&header);
    err = esp_partition_write(partition, 0, &header, offsetof(slot_header_t, committed));
    if (err != ESP_OK) goto done;
    slot_header_t readback;
    err = esp_partition_read(partition, 0, &readback, sizeof(readback));
    if (err != ESP_OK) goto done;
    if (memcmp(&readback, &header, sizeof(header)) != 0) { err = ESP_FAIL; goto done; }
    uint32_t marker = COMMIT_MAGIC;
    err = esp_partition_write(partition, offsetof(slot_header_t, committed),
                              &marker, sizeof(marker));
    if (err != ESP_OK) goto done;
    if (!valid_slot(transfer.slot, committed)) { err = ESP_FAIL; goto done; }
    active_slot = transfer.slot;
    active_record = *committed;
    memset(&transfer, 0, sizeof(transfer));
done:
    xSemaphoreGive(store_lock);
    return err;
}
