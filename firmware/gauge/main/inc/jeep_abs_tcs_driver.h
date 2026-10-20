#pragma once
#include "jeep_idle_driver.h"

/* Combined ABS/TCS shutdown only. The fourth-button valve control is excluded. */
typedef enum
{
    JEEP_ABS_DISABLE,
    JEEP_ABS_RETURN
} jeep_abs_kind_t;
typedef enum
{
    JEEP_ABS_EMPTY,
    JEEP_ABS_WORKING,
    JEEP_ABS_ACKNOWLEDGED,
    JEEP_ABS_CANCELLED,
    JEEP_ABS_REJECTED,
    JEEP_ABS_UNKNOWN
} jeep_abs_phase_t;
typedef struct
{
    jeep_abs_kind_t kind;
    uint32_t        operation, revision, link;
    uint8_t         source;
} jeep_abs_request_t;
typedef struct
{
    jeep_abs_phase_t   phase;
    jeep_idle_error_t  error;
    jeep_abs_request_t request;
    elm_response_t     response;
    uint32_t           started_at, sent_at, ticket, last_disable, last_return;
    uint8_t            step, negative_code;
    bool               waiting, cancel_requested, control_possible;
} jeep_abs_driver_t;
void jeep_abs_driver_init(jeep_abs_driver_t *driver);
bool jeep_abs_driver_begin(jeep_abs_driver_t *driver, jeep_abs_request_t request, idle_context_t context, uint32_t now);
void jeep_abs_driver_cancel(jeep_abs_driver_t *driver);
jeep_idle_command_t jeep_abs_driver_next(jeep_abs_driver_t *driver, idle_context_t context, uint32_t now);
bool                jeep_abs_driver_feed(jeep_abs_driver_t *driver,
                                         uint32_t           ticket,
                                         const uint8_t     *bytes,
                                         size_t             length,
                                         idle_context_t     context,
                                         uint32_t           now);
/* ACKNOWLEDGED never means restored. Only an independent reset/state verifier
 * may clear the owner lifecycle's Restart required/Unknown fence. */
