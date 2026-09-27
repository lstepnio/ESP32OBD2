#pragma once

#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"
#include "esp_partition.h"

#define EGAUGE_CONFIG_MAX_BYTES (64U * 1024U)

typedef struct {
    uint32_t revision;
    uint32_t length;
    uint8_t sha256[32];
    uint8_t slot;
} config_store_record_t;

/* Initialize the two reserved data slots and inspect committed generations.
 * Call once during startup before transfer workers or GATT registration. */
esp_err_t config_store_init(void);
/* Metadata copies are nonblocking and return ESP_ERR_TIMEOUT during a short
 * metadata update. Record/document reads do not retain the store mutex while
 * accessing flash. Callers must handle a generation mismatch. */
esp_err_t config_store_active(config_store_record_t *record);
esp_err_t config_store_previous(config_store_record_t *record);
/* Boot-only recovery selection. The record must name a currently committed,
 * valid slot. Call before starting the config transfer worker. */
esp_err_t config_store_select(const config_store_record_t *record);
esp_err_t config_store_read_record(const config_store_record_t *record,
                                   uint32_t offset, void *data, size_t length);
esp_err_t config_store_read_active(uint32_t offset, void *data, size_t length);

/* One sequential transfer at a time, serialized by the config transfer worker.
 * These calls may erase, write, hash, or parse flash and must never run from a
 * BLE callback or LVGL callback. All offsets are document-relative. */
esp_err_t config_store_begin(uint32_t base_revision, uint32_t length,
                             const uint8_t expected_sha256[32]);
esp_err_t config_store_write(uint32_t offset, const void *data, size_t length);
esp_err_t config_store_verify(void);
/* Validate the staged schema and semantic references without committing. */
esp_err_t config_store_validate(void);
void config_store_abort(void);

/* The store enforces the version 1 document validator before commit and at
 * boot. The caller callback applies any further runtime capability checks.
 * The callback reads the staged document through its partition and byte length.
 * Hashing, schema validation, and the callback run without the metadata lock.
 * The worker rechecks transfer identity and base revision before activation.
 * Passing no validator never activates untrusted bytes. */
typedef esp_err_t (*config_store_validator_t)(const esp_partition_t *partition,
                                               uint32_t document_offset,
                                               uint32_t length, void *context);
esp_err_t config_store_commit(config_store_validator_t validator, void *context,
                              config_store_record_t *committed);
