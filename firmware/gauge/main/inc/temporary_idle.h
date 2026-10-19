#pragma once
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

typedef enum
{
    IDLE_READY,
    IDLE_STARTING,
    IDLE_ACTIVE,
    IDLE_STOPPING,
    IDLE_RESTORED,
    IDLE_REJECTED,
    IDLE_UNKNOWN
} idle_phase_t;
typedef enum
{
    IDLE_OK,
    IDLE_UNSUPPORTED,
    IDLE_INVALID,
    IDLE_GUARD,
    IDLE_CONTEXT,
    IDLE_TIMEOUT,
    IDLE_RESPONSE
} idle_reason_t;
typedef enum
{
    IDLE_NO_JOB,
    IDLE_START,
    IDLE_REFRESH,
    IDLE_RELEASE
} idle_job_kind_t;
typedef struct
{
    uint32_t revision, link;
    bool     connected, guards_fresh_and_met;
} idle_context_t;
typedef struct
{
    bool     qualified, release_idempotent;
    uint16_t minimum_rpm, maximum_rpm, rpm_step, maximum_seconds;
    uint32_t refresh_ms, io_timeout_ms, lease_timeout_ms;
} idle_definition_t;
typedef struct
{
    uint32_t operation, revision, link;
    uint16_t rpm, seconds;
    uint8_t  preset;
} idle_request_t;
typedef struct
{
    idle_job_kind_t kind;
    uint32_t        operation, ticket;
    uint16_t        rpm;
} idle_job_t;
typedef struct
{
    idle_phase_t      phase;
    idle_reason_t     reason;
    idle_definition_t definition;
    idle_request_t    request;
    idle_job_t        in_flight;
    uint32_t          last_operation, ticket, sent_at, active_at, refreshed_at;
    bool              stop_requested;
} temporary_idle_t;
/* Pure worker-owned policy. No transport, allocation or production controller definition.
 * Context/qualification must be independently established by the eventual driver. */
void       temporary_idle_init(temporary_idle_t *state);
bool       temporary_idle_start(temporary_idle_t *state,
                                idle_request_t    request,
                                idle_definition_t definition,
                                idle_context_t    context);
bool       temporary_idle_stop(temporary_idle_t *state, uint32_t operation);
idle_job_t temporary_idle_next(temporary_idle_t *state, idle_context_t context, uint32_t now);
bool       temporary_idle_complete(temporary_idle_t *state,
                                   idle_job_t        job,
                                   bool              verified,
                                   idle_context_t    context,
                                   uint32_t          now);
bool       temporary_idle_reconcile(temporary_idle_t *state, idle_context_t context, bool release_verified);
uint16_t   temporary_idle_remaining(const temporary_idle_t *state, uint32_t now);
/* Offline versioned inner payload. No BLE opcode or real driver is registered. */
bool temporary_idle_decode(const uint8_t *bytes, size_t length, uint8_t *command, idle_request_t *request);
