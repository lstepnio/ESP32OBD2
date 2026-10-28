#pragma once
#include <stdbool.h>
#include <stdint.h>

/* Mapping v1 is a candidate reference-board mapping. Qualification is a separate
 * board gate; changing axes requires a new mapping version and physical checks. */
#define ORIENTATION_MAPPING_VERSION 1
#define ORIENTATION_STALE_MS 1000U
#define ORIENTATION_DWELL_MS 800U

typedef struct
{
    uint8_t  applied, candidate;
    bool     pending, have_sample;
    uint32_t candidate_since, last_sample;
} orientation_policy_t;
void orientation_policy_init(orientation_policy_t *policy, uint8_t manual_angle);
/* Acceleration is signed milli-g. Only fresh, steady gravity changes the angle. */
bool orientation_policy_observe(orientation_policy_t *policy, uint32_t now_ms, int32_t x, int32_t y, int32_t z);
void orientation_policy_hold(orientation_policy_t *policy);
bool orientation_policy_healthy(const orientation_policy_t *policy, uint32_t now_ms);
