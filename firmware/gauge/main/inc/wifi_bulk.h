#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

#define WIFI_BULK_STATUS_SIZE 112

/* Owner-only BLE commands configure and authorize the optional Wi-Fi station.
 * Credentials and session secrets must never be exposed by a public endpoint. */
esp_err_t wifi_bulk_init(void);
bool wifi_bulk_command(const uint8_t *bytes, size_t length);
size_t wifi_bulk_status(uint8_t out[WIFI_BULK_STATUS_SIZE]);

/* Internal transport generation check; does not expose credentials or wire state. */
bool wifi_bulk_session_current(uint32_t generation);
