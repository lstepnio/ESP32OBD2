#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

#define DIAGNOSTICS_STATUS_SIZE 32

esp_err_t diagnostics_state_init(void);
void diagnostics_state_mil(bool on, uint8_t reported_count, uint32_t now_ms);
void diagnostics_state_codes(uint8_t mode, const uint8_t *bytes, size_t length,
                             uint32_t now_ms);
void diagnostics_state_disconnected(void);
size_t diagnostics_state_status(uint8_t out[DIAGNOSTICS_STATUS_SIZE]);
