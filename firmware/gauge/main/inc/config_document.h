#pragma once

#include <stdint.h>
#include "esp_err.h"
#include "esp_partition.h"

typedef struct {
    uint32_t base_revision;
    uint8_t max_adapter_links;
} config_document_context_t;

/* Call once at startup, before any cJSON users start. JSON nodes use PSRAM. */
void config_document_init(void);

/* Callback for config_store_commit. Validation never executes PID expressions or
 * changes active state. The caller supplies the revision accepted at begin. */
esp_err_t config_document_validate(const esp_partition_t *partition,
                                   uint32_t document_offset, uint32_t length,
                                   void *context);
