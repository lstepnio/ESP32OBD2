#pragma once
#include "elm_response.h"
#include "temporary_idle.h"

typedef enum
{
    JEEP_IDLE_START_CAPTURED,
    JEEP_IDLE_REPORT,
    JEEP_IDLE_RETURN,
    JEEP_IDLE_RECOVERY_REPORT /* Captured session + status; never Start/Return, including cancellation. */
} jeep_idle_kind_t;
typedef enum
{
    JEEP_IDLE_EMPTY,
    JEEP_IDLE_WORKING,
    JEEP_IDLE_CONFIRMED,
    JEEP_IDLE_CANCELLED,
    JEEP_IDLE_REJECTED,
    JEEP_IDLE_UNKNOWN
} jeep_idle_phase_t;
typedef enum
{
    JEEP_IDLE_NO_RESULT,
    JEEP_IDLE_ACTIVE_REPORT,
    JEEP_IDLE_INACTIVE_REPORT,
    JEEP_IDLE_START_CONFIRMED,
    JEEP_IDLE_RETURN_CONFIRMED,
    JEEP_IDLE_RECONCILED
} jeep_idle_result_t;
typedef enum
{
    JEEP_IDLE_ERROR_NONE,
    JEEP_IDLE_ERROR_CONTEXT,
    JEEP_IDLE_ERROR_GUARD,
    JEEP_IDLE_ERROR_TIMEOUT,
    JEEP_IDLE_ERROR_FRAMING,
    JEEP_IDLE_ERROR_NEGATIVE,
    JEEP_IDLE_ERROR_PENDING,
    JEEP_IDLE_ERROR_STATE,
    JEEP_IDLE_ERROR_BUDGET
} jeep_idle_error_t;
typedef struct
{
    jeep_idle_kind_t kind;
    uint32_t         operation, revision, link;
    uint8_t          source;
} jeep_idle_request_t;
typedef struct
{
    const char *text;
    uint32_t    ticket;
} jeep_idle_command_t;
typedef struct
{
    jeep_idle_phase_t   phase;
    jeep_idle_result_t  result;
    jeep_idle_error_t   error;
    jeep_idle_request_t request;
    elm_response_t      response;
    uint32_t            started_at, sent_at, ticket, last_operation, last_start_operation, last_return_operation;
    uint8_t             step, negative_code;
    bool                waiting, cancel_requested, control_possible, uncertain;
} jeep_idle_driver_t;

/* Offline candidate, not registered with a worker or capability. No tasks, heap,
 * transport callbacks, RPM mapping or live qualification. Only captured 06 80
 * is available; a real controller definition must qualify its target separately.
 * The existing primary adapter owner must provide exclusive stream ownership,
 * fresh identity/guards and a synchronized, echo-off, headered ELM session.
 * Never clear Unknown by reinitializing this object to retry Start. */
void jeep_idle_driver_init(jeep_idle_driver_t *driver);
bool jeep_idle_driver_begin(jeep_idle_driver_t *driver,
                            jeep_idle_request_t request,
                            idle_context_t      context,
                            uint32_t            now);
void jeep_idle_driver_cancel(jeep_idle_driver_t *driver);
/* Empty command means no new idle IO, not permission for parallel adapter IO.
 * Keep the worker's exclusive route/transaction lease until a terminal phase;
 * normal polling runs between bounded jobs. Commands exclude trailing CR.
 * Each issued ticket accepts only its own bounded prompt-terminated response. */
jeep_idle_command_t jeep_idle_driver_next(jeep_idle_driver_t *driver, idle_context_t context, uint32_t now);
bool                jeep_idle_driver_feed(jeep_idle_driver_t *driver,
                                          uint32_t            ticket,
                                          const uint8_t      *bytes,
                                          size_t              length,
                                          idle_context_t      context,
                                          uint32_t            now);
/* RETURN_CONFIRMED requires a positive Return and inactive report. It is not
 * physical restoration: the lifecycle owner must independently verify fresh,
 * settled RPM before completing its RELEASE job with verified=true. */

/* An independent qualified identity/state/RPM verifier completes restoration or reconciles Unknown.
 * Reads alone never clear it. Retain operation/ticket replay fences. */
bool jeep_idle_driver_reconcile(jeep_idle_driver_t *driver, idle_context_t context, bool release_verified);

/* Candidate budgets, not measured ECU lost-refresh timeouts. They never extend
 * on fragmented RX or NRC 78. A real admission policy must budget the whole job. */
#define JEEP_IDLE_IO_TIMEOUT_MS 1200U
#define JEEP_IDLE_JOB_TIMEOUT_MS 9000U
