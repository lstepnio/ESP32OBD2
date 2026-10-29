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

typedef void (*ble_obd_response_cb_t)(uint8_t        service,
                                      uint16_t       pid,
                                      uint32_t       responder,
                                      uint8_t const *data,
                                      size_t         len,
                                      uint32_t       generation,
                                      uint32_t       observed_at_ms,
                                      void          *usr_ctx);

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Declarations
// ---------------------------------------------------------------------------------------------------------------------

ble_obd_ctx_t *ble_obd_connect(unsigned              source_id,
                               const char           *peer_mac,
                               ble_obd_response_cb_t response_cb,
                               void                 *usr_ctx);

/* Blocking adapter-worker APIs. Connect includes discovery, subscription, and
 * bounded ELM initialization. Request calls permit one outstanding transaction
 * per context and invoke response_cb before returning on a decoded PID reply. */
int ble_obd_rxtx(ble_obd_ctx_t *obd, uint8_t mode, uint8_t pid, uint32_t timeout_ms);

bool ble_obd_is_connected(ble_obd_ctx_t *ctx);
int  ble_obd_read_service(ble_obd_ctx_t *obd, uint8_t mode, uint32_t timeout_ms, uint8_t *data, size_t *length);
int  ble_obd_read_service_ecu(ble_obd_ctx_t *obd,
                              uint8_t        mode,
                              uint32_t       ecu,
                              uint32_t       timeout_ms,
                              uint8_t       *data,
                              size_t        *length);
int  ble_obd_read_service_status_ecu(ble_obd_ctx_t *obd,
                                     uint8_t        mode,
                                     uint32_t       ecu,
                                     uint32_t       timeout_ms,
                                     uint8_t       *data,
                                     size_t        *length,
                                     elm_result_t  *status);
int  ble_obd_rxtx_status_ecu(ble_obd_ctx_t *obd,
                             uint8_t        mode,
                             uint16_t       pid,
                             uint32_t       ecu,
                             uint32_t       timeout_ms,
                             elm_result_t  *status);

/* Persisted selection includes BLE address type. Changing it invalidates the link. */
ble_obd_ctx_t *ble_obd_connect_bound(unsigned              source_id,
                                     const char           *peer_mac,
                                     uint8_t               address_type,
                                     ble_obd_response_cb_t response_cb,
                                     void                 *usr_ctx);
int            ble_obd_rxtx_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint16_t pid, uint32_t ecu, uint32_t timeout_ms);
void           ble_obd_disconnect(ble_obd_ctx_t *obd);

ble_obd_ctx_t *ble_obd_connect_profile(unsigned              source_id,
                                       const char           *peer_mac,
                                       uint8_t               address_type,
                                       const char           *driver,
                                       ble_obd_response_cb_t response_cb,
                                       void                 *usr_ctx);

/* Independent source context, explicitly initialized for ECM or TCM. TCM uses 7E1/7E9. */
ble_obd_ctx_t *ble_obd_connect_profile_ecu(unsigned              source_id,
                                           const char           *peer_mac,
                                           uint8_t               address_type,
                                           const char           *driver,
                                           uint32_t              ecu,
                                           ble_obd_response_cb_t response_cb,
                                           void                 *usr_ctx);

int ble_obd_read_pid(ble_obd_ctx_t *obd,
                     uint8_t        mode,
                     uint16_t       pid,
                     uint32_t       ecu,
                     uint32_t       timeout_ms,
                     elm_result_t  *status,
                     elm_payload_t *out);

/* Internal worker-only bridge for fixed driver literals. Caller owns the whole
 * command job; no arbitrary App/config text is admitted. Every exchange retires
 * routing caches, and uncertain RX disconnects before any later normal poll. */
int ble_obd_control_exchange(ble_obd_ctx_t *obd,
                             const char    *command,
                             uint32_t       timeout_ms,
                             uint8_t       *response,
                             size_t        *length);
int ble_obd_control_restore(ble_obd_ctx_t *obd, uint32_t timeout_ms, uint8_t *scratch, size_t capacity);

/* Primary worker evidence only: fixed 7E0/7E8 calibration, CVN, RPM, speed or
 * coolant reads. Numeric successes also publish to the scoped reading observer;
 * identity replies never do. No arbitrary identifiers or secondary transport. */
int ble_obd_read_primary_input(ble_obd_ctx_t *obd,
                               uint8_t        mode,
                               uint8_t        pid,
                               uint32_t       timeout_ms,
                               elm_result_t  *status,
                               elm_payload_t *out);

/* Worker-only read-only role evidence. Fixed Mode09 PID04/06, exact 7E8/7E9
 * responder and physical 7E0/7E1 request header. No session or actuator request.
 * The existing per-request route owner selects the next poll header as usual. */
int ble_obd_read_role_identity(ble_obd_ctx_t *obd,
                               uint32_t       ecu,
                               uint16_t       pid,
                               uint32_t       timeout_ms,
                               elm_result_t  *status,
                               elm_payload_t *out);

/* Read-only role admission: establish/subcribe/setup the ELM transport without
 * role-dependent Mode01 support-map discovery. Caller must gate normal polling
 * until its positive identity policy accepts the source. No saved binding change. */
ble_obd_ctx_t *ble_obd_connect_role_probe(unsigned              source_id,
                                          const char           *peer_mac,
                                          uint8_t               address_type,
                                          const char           *driver,
                                          uint32_t              ecu,
                                          ble_obd_response_cb_t response_cb,
                                          void                 *usr_ctx);

/* One deferred support-map page, exact logical ECU and physical header. Fixed
 * Mode01 bases0..224 in steps32; publishes support only on a complete4-byte reply.
 * Caller schedules one page per phase and retires progress on source generation. */
int ble_obd_read_support_map(ble_obd_ctx_t *obd, uint8_t base, uint32_t timeout_ms, bool *more);
