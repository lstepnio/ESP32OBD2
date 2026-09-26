#pragma once

// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

#define BLE_OBD_MAX_DATA_LEN 256

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct ble_obd_ctx ble_obd_ctx_t;

typedef struct
{
    uint8_t mode;
    uint8_t pid;
    uint8_t data[BLE_OBD_MAX_DATA_LEN];
    size_t  data_len;
} obd_response_t;

typedef void (*ble_obd_response_cb_t)(int pid, uint8_t const *data, size_t len, void *usr_ctx);

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Declarations
// ---------------------------------------------------------------------------------------------------------------------

ble_obd_ctx_t *ble_obd_connect(unsigned source_id, const char *peer_mac,
                               ble_obd_response_cb_t response_cb, void *usr_ctx);

int ble_obd_rxtx(ble_obd_ctx_t *obd, uint8_t mode, uint8_t pid, uint32_t timeout_ms);

bool ble_obd_is_connected(ble_obd_ctx_t *ctx);
int ble_obd_read_service(ble_obd_ctx_t *obd, uint8_t mode, uint32_t timeout_ms,
                         uint8_t *data, size_t *length);
