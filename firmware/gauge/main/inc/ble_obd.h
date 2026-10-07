#pragma once

// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "elm_response.h"

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

typedef void (*ble_obd_response_cb_t)(int pid, uint8_t const *data, size_t len, uint32_t generation, void *usr_ctx);

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Declarations
// ---------------------------------------------------------------------------------------------------------------------

ble_obd_ctx_t *ble_obd_connect(unsigned source_id, const char *peer_mac,
                               ble_obd_response_cb_t response_cb, void *usr_ctx);

/* Blocking adapter-worker APIs. Connect includes discovery, subscription, and
 * bounded ELM initialization. Request calls permit one outstanding transaction
 * per context and invoke response_cb before returning on a decoded PID reply. */
int ble_obd_rxtx(ble_obd_ctx_t *obd, uint8_t mode, uint8_t pid, uint32_t timeout_ms);

bool ble_obd_is_connected(ble_obd_ctx_t *ctx);
int ble_obd_read_service(ble_obd_ctx_t *obd, uint8_t mode, uint32_t timeout_ms,
                         uint8_t *data, size_t *length);
int ble_obd_read_service_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint32_t ecu,
                            uint32_t timeout_ms, uint8_t *data, size_t *length);
int ble_obd_read_service_status_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint32_t ecu,
    uint32_t timeout_ms, uint8_t *data, size_t *length, elm_result_t *status);
int ble_obd_rxtx_status_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint16_t pid,
    uint32_t ecu, uint32_t timeout_ms, elm_result_t *status);

/* Persisted selection includes BLE address type. Changing it invalidates the link. */
ble_obd_ctx_t *ble_obd_connect_bound(unsigned source_id, const char *peer_mac, uint8_t address_type,
                                     ble_obd_response_cb_t response_cb, void *usr_ctx);
int ble_obd_rxtx_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint16_t pid, uint32_t ecu, uint32_t timeout_ms);
void ble_obd_disconnect(ble_obd_ctx_t *obd);

ble_obd_ctx_t *ble_obd_connect_profile(unsigned source_id, const char *peer_mac, uint8_t address_type,
    const char *driver, ble_obd_response_cb_t response_cb, void *usr_ctx);

/* Independent source context, explicitly initialized for ECM or TCM. TCM uses 7E1/7E9. */
ble_obd_ctx_t *ble_obd_connect_profile_ecu(unsigned source_id, const char *peer_mac, uint8_t address_type,
    const char *driver, uint32_t ecu, ble_obd_response_cb_t response_cb, void *usr_ctx);
