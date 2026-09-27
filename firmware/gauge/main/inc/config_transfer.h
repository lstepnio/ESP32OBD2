#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

/* Protocol 0 extension on the existing owner-only control and state handles.
 * ATT writes acknowledge queueing; read status with the matching sequence to
 * learn whether flash and validation completed. */
#define CONFIG_TRANSFER_MAX_REQUEST 1040
#define CONFIG_TRANSFER_STATUS_SIZE 64

esp_err_t config_transfer_init(void);
/* Nonblocking queue admission from the authenticated GATT callback. */
bool config_transfer_command(const uint8_t *bytes, size_t length);
/* Copies an immutable published status under a short critical section. */
size_t config_transfer_status(uint8_t out[CONFIG_TRANSFER_STATUS_SIZE]);
void config_transfer_disconnect(void);
