#pragma once
#include "jeep_abs_tcs_driver.h"

#define VEHICLE_COMMAND_WIRE_SIZE 24U
#define VEHICLE_COMMAND_STATUS_SIZE 32U
#define VEHICLE_COMMAND_HISTORY_SIZE 16U
/* Source-independent intents; no arbitrary diagnostic bytes or numeric RPM. */
typedef enum
{
    VEHICLE_IDLE    = 1,
    VEHICLE_ABS_TCS = 2
} vehicle_command_kind_t;
typedef enum
{
    VEHICLE_START = 1,
    VEHICLE_STOP  = 2
} vehicle_command_verb_t;
typedef enum
{
    VEHICLE_UNAVAILABLE,
    VEHICLE_READY,
    VEHICLE_STARTING,
    VEHICLE_ACTIVE,
    VEHICLE_STOPPING,
    VEHICLE_VERIFYING,
    VEHICLE_RESTORED,
    VEHICLE_RESTART_REQUIRED,
    VEHICLE_REJECTED,
    VEHICLE_UNKNOWN
} vehicle_command_phase_t;
typedef enum
{
    VEHICLE_OK,
    VEHICLE_UNQUALIFIED,
    VEHICLE_INVALID,
    VEHICLE_BUSY,
    VEHICLE_CONTEXT,
    VEHICLE_GUARD,
    VEHICLE_TIMEOUT,
    VEHICLE_RESPONSE,
    VEHICLE_STORAGE,
    VEHICLE_COOLANT_TIMER
} vehicle_command_reason_t;
typedef struct
{
    uint8_t  kind, verb, preset;
    uint32_t operation, revision, link, boot;
    uint16_t seconds;
    bool     gauge_confirmed; /* Local ingress only; never decoded from wire bytes. */
} vehicle_command_request_t;
typedef struct
{
    uint32_t revision, link, identity, verified_operation[2], proof_at[2], guard_at;
    uint8_t  qualified_mask, identity_mask;
    bool     connected, guards_fresh_and_met, normal_idle_verified, abs_reset_verified;
    bool     coolant_configured, coolant_valid;
    bool     parked_idle_test; /* Local Start only; never a qualified controller mask. */
    double   coolant_c, coolant_limit_c;
    uint32_t coolant_at;
} vehicle_command_context_t;
typedef struct
{
    vehicle_command_phase_t   phase;
    vehicle_command_reason_t  reason;
    vehicle_command_request_t request;
    uint32_t                  active_at, last_report, sequence, identity, changed_at, fallback_at;
    bool                      timed_fallback;
    bool                      pending_start, pending_stop, restart_required;
} vehicle_command_state_t;
typedef struct
{
    uint32_t sequence, operation, revision, link, at;
    uint8_t  kind, phase, reason, reserved;
} vehicle_command_event_t;
/* Durable uncertainty marker. Save before any control can leave the worker.
 * Storage failure refuses IO; a reboot never resumes Start or assumes restoration. */
typedef struct
{
    uint32_t operation[2], revision[2], identity[2];
    uint8_t  mask;
} vehicle_command_marker_t;
typedef bool (*vehicle_command_save_t)(const vehicle_command_marker_t *marker, void *context);
typedef struct
{
    uint32_t                boot, last_operation, sequence, ticket;
    vehicle_command_state_t states[2];
    vehicle_command_event_t history[VEHICLE_COMMAND_HISTORY_SIZE];
    uint8_t                 history_count, history_next, worker_kind;
    union
    {
        jeep_idle_driver_t idle;
        jeep_abs_driver_t  abs;
    } driver;
    vehicle_command_marker_t marker;
    vehicle_command_save_t   save;
    void                    *save_context;
    bool                     journal_ready, marked;
} vehicle_commands_t;
void vehicle_commands_init(vehicle_commands_t             *commands,
                           uint32_t                        boot,
                           vehicle_command_save_t          save,
                           void                           *save_context,
                           const vehicle_command_marker_t *restored,
                           bool                            journal_ready);
bool vehicle_command_decode(const uint8_t *bytes, size_t length, vehicle_command_request_t *request);
bool vehicle_commands_submit(vehicle_commands_t       *commands,
                             vehicle_command_request_t request,
                             vehicle_command_context_t context,
                             uint32_t                  now);
/* The existing primary worker calls next/feed with exclusive stream ownership.
 * Keep the lease while worker_kind != 0; polling runs between complete jobs.
 * Cancellation, guard loss and expiry share the same prioritized Stop path. */
jeep_idle_command_t vehicle_commands_next(vehicle_commands_t       *commands,
                                          vehicle_command_context_t context,
                                          uint32_t                  now);
bool                vehicle_commands_feed(vehicle_commands_t       *commands,
                                          uint32_t                  ticket,
                                          const uint8_t            *bytes,
                                          size_t                    length,
                                          vehicle_command_context_t context,
                                          uint32_t                  now);
void                vehicle_commands_transport_failed(vehicle_commands_t *commands, uint32_t now);
void   vehicle_commands_observe(vehicle_commands_t *commands, vehicle_command_context_t context, uint32_t now);
size_t vehicle_commands_status(const vehicle_commands_t *commands,
                               uint8_t                   kind,
                               vehicle_command_context_t context,
                               uint32_t                  now,
                               uint8_t                   out[VEHICLE_COMMAND_STATUS_SIZE]);
bool   vehicle_commands_maintenance_allowed(const vehicle_command_state_t states[2], bool configuration);
