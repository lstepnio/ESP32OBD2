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
} config_store_record_t;

/* Initialize the two reserved data slots and inspect committed generations. */
esp_err_t config_store_init(void);
esp_err_t config_store_active(config_store_record_t *record);
esp_err_t config_store_read_active(uint32_t offset, void *data, size_t length);

/* One sequential transfer at a time. All offsets are document-relative. */
esp_err_t config_store_begin(uint32_t base_revision, uint32_t length,
                             const uint8_t expected_sha256[32]);
esp_err_t config_store_write(uint32_t offset, const void *data, size_t length);
esp_err_t config_store_verify(void);
void config_store_abort(void);

/* The caller must validate the complete schema and device semantics. The
 * callback reads the staged document through its partition and byte length.
 * It runs while the store is locked and must not call config_store functions.
 * Passing no validator never activates untrusted bytes. */
typedef esp_err_t (*config_store_validator_t)(const esp_partition_t *partition,
                                               uint32_t document_offset,
                                               uint32_t length, void *context);
esp_err_t config_store_commit(config_store_validator_t validator, void *context,
                              config_store_record_t *committed);
