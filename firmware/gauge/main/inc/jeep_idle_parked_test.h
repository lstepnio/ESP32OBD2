#pragma once
#include "vehicle_commands.h"

#define JEEP_IDLE_PARKED_MAX_SECONDS 300U
#define JEEP_IDLE_PARKED_INPUT_AGE_MS 2000U

typedef enum
{
    JEEP_TEST_CALIBRATION,
    JEEP_TEST_CVN,
    JEEP_TEST_RPM,
    JEEP_TEST_SPEED
} jeep_test_input_t;

/* Temporary owner-supervised test policy, not a qualified vehicle definition.
 * Owned by the primary worker; removable with its build switch and adapter. */
typedef struct
{
    uint32_t revision, link, rpm_at, speed_at, identity_at, proof_operation, proof_transition;
    uint32_t proof_first, proof_last, baseline_operation;
    unsigned proof_count;
    double   rpm, baseline_rpm;
    uint8_t  speed;
    bool     calibration, cvn, rpm_valid, speed_valid, baseline_valid;
    bool     recovery_baseline;
} jeep_idle_parked_test_t;

void jeep_idle_parked_test_scope(jeep_idle_parked_test_t *test, vehicle_command_context_t context);
bool jeep_idle_parked_test_feed(jeep_idle_parked_test_t *test,
                                jeep_test_input_t        input,
                                const elm_payload_t     *payload,
                                uint32_t                 now);
void jeep_idle_parked_test_context(jeep_idle_parked_test_t       *test,
                                   vehicle_command_context_t     *context,
                                   const vehicle_command_state_t *idle,
                                   uint32_t                       now);
void jeep_idle_parked_test_started(jeep_idle_parked_test_t *test, const vehicle_command_state_t *idle);

void jeep_brake_owner_test_context(const jeep_idle_parked_test_t *test,
                                   vehicle_command_context_t     *context,
                                   const vehicle_command_state_t  states[VEHICLE_COMMAND_KIND_COUNT],
                                   uint32_t                       now);
