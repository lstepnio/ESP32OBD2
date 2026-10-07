#pragma once
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include "esp_err.h"
#define ADAPTER_SCAN_STATUS_SIZE 140
/* Owner-only, bounded discovery. Advertisements are candidates, not verified adapters. */
esp_err_t adapter_registry_init(void);
bool adapter_registry_command(const uint8_t *bytes, size_t length);
size_t adapter_registry_status(uint8_t out[ADAPTER_SCAN_STATUS_SIZE]);
