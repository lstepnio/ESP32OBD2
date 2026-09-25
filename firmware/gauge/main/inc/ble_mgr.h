#pragma once

// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <assert.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "nimble/ble.h"

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef enum
{
    BLE_MGR_E_OK = 0,
    BLE_MGR_E_NULL,
    BLE_MGR_E_TIMEOUT,
    BLE_MGR_E_NOT_CONNECTED,
    BLE_MGR_E_DISCOVERY_FAILED,
    BLE_MGR_E_GATT_SEND_FAILED,
    BLE_MGR_E_API_LOCK_ERROR,
} ble_mgr_status_t;

// Forward declaration
typedef struct ble_mgr_svc_def ble_mgr_svc_def_t;

// Forward declaration
typedef struct ble_mgr_disc_cfg ble_mgr_disc_cfg_t;

// Public forward declaration
typedef struct ble_mgr_ctx ble_mgr_ctx_t;

/*
 * return: true to connect to this device, false to continue discovery
 */
typedef bool (*ble_mgr_dev_filter_cb_t)(ble_mgr_ctx_t *mgr_ctx, const ble_addr_t *addr, void *usr_ctx);

/*
 * return: true to restart discovery & connection
 */
typedef bool (*ble_mgr_disconnected_cb_t)(ble_mgr_ctx_t *mgr_ctx, void *usr_ctx);

typedef void (*ble_mgr_svc_discovery_completed_cb_t)(bool                     success,
                                                     uint16_t                 conn_handle,
                                                     ble_mgr_svc_def_t const *svc_def,
                                                     void                    *usr_ctx);

typedef void (*ble_mgr_notify_cb_t)(const uint8_t *data, size_t len, uint16_t attr_handle, void *usr_ctx);

struct ble_mgr_disc_cfg
{
    ble_mgr_svc_def_t const  *svc_def;
    ble_mgr_dev_filter_cb_t   dev_filter_cb;
    ble_mgr_disconnected_cb_t disconnected_cb;
};

typedef struct
{
    const char         *uuid;
    uint16_t            handle;
    ble_mgr_notify_cb_t notify_cb;
} ble_gatt_char_def_t;

struct ble_mgr_svc_def
{
    const char          *service_uuid;
    ble_gatt_char_def_t *chars;
    size_t               num_chars;
};

// ---------------------------------------------------------------------------------------------------------------------
// Stringify Error Codes
// ---------------------------------------------------------------------------------------------------------------------

static const char *ble_mgr_status_strs[] = {
    // clang-format off
    "BLE_MGR_E_OK",
    "BLE_MGR_E_NULL",
    "BLE_MGR_E_TIMEOUT",
    "BLE_MGR_E_NOT_CONNECTED",
    "BLE_MGR_E_DISCOVERY_FAILED",
    "BLE_MGR_E_GATT_SEND_FAILED",
    "BLE_MGR_E_API_LOCK_ERROR",
    // clang-format on
};

static_assert(ARRAY_SIZE(ble_mgr_status_strs) == BLE_MGR_E_API_LOCK_ERROR + 1,
              "ble_mgr_status_strs array size mismatch");

#define BLE_MGR_STATUS_STR(status)                                                                                     \
    ((status) < ARRAY_SIZE(ble_mgr_status_strs) ? (ble_mgr_status_strs[status] + sizeof("BLE_MGR_E_") - 1) : "UNKNOWN")

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Declarations
// ---------------------------------------------------------------------------------------------------------------------

ble_mgr_ctx_t *ble_mgr_init(int timeout_ms);

ble_mgr_status_t ble_mgr_connect_service(ble_mgr_ctx_t            *mgr_ctx,
                                         ble_mgr_disc_cfg_t const *disc_cfg,
                                         uint32_t                  timeout_ms,
                                         void                     *usr_ctx);

ble_mgr_status_t ble_mgr_send(ble_mgr_ctx_t *mgr_ctx, uint16_t chr_handle, const char *data, size_t len);

bool ble_mgr_is_connected(ble_mgr_ctx_t *mgr_ctx);
