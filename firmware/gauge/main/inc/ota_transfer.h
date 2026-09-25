#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

#define OTA_TRANSFER_STATUS_SIZE 56

esp_err_t ota_transfer_init(void);
bool ota_transfer_command(const uint8_t *bytes, size_t length);
size_t ota_transfer_status(uint8_t out[OTA_TRANSFER_STATUS_SIZE]);
