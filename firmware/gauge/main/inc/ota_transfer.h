#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

#define OTA_TRANSFER_STATUS_SIZE 56
#define OTA_BOOT_IDENTITY_SIZE 60

esp_err_t ota_transfer_init(void);
/* Nonblocking queue admission from the authenticated GATT callback. */
bool ota_transfer_command(const uint8_t *bytes, size_t length);
/* Status and identity are bounded copies and perform no image hashing. */
size_t ota_transfer_status(uint8_t out[OTA_TRANSFER_STATUS_SIZE]);
size_t ota_transfer_boot_identity(uint8_t out[OTA_BOOT_IDENTITY_SIZE]);
