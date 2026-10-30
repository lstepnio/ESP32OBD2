#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define ADAPTER_ROLE_ECM 1U
#define ADAPTER_ROLE_TCM 2U
#define ADAPTER_ROLE_EVIDENCE_WINDOW_MS 15000U
#define ADAPTER_ROLE_REFRESH_INTERVAL_MS 30000U

typedef struct
{
    unsigned transport[2]; /* Logical source -> saved Bluetooth binding. */
    uint32_t generation[2], observed_at[2], revision, refresh_at[2];
    uint8_t  roles[2], observed, retired, refresh_requested, refresh_issued;
    bool     pending, primary_proven;
} adapter_role_policy_t;

void adapter_role_policy_init(adapter_role_policy_t *policy);
void adapter_role_policy_scope(adapter_role_policy_t *policy, unsigned source, uint32_t generation);
bool adapter_role_identity_match(unsigned role, uint16_t pid, uint32_t responder, const uint8_t *data, size_t length);
/* Caller serializes policy access. An observation is a complete four-read cycle. */
void adapter_role_policy_observe(adapter_role_policy_t *policy,
                                 unsigned               source,
                                 uint32_t               generation,
                                 uint8_t                roles,
                                 uint8_t                contradicted_roles,
                                 uint32_t               now);
/* Acknowledge only after the source has disconnected and invalidated its caches.
 * No map changes until BOTH workers reach this boundary. */
bool adapter_role_policy_take_refresh(adapter_role_policy_t *policy, unsigned source);
bool adapter_role_policy_retire(adapter_role_policy_t *policy, unsigned source);
void adapter_role_policy_reject_primary(adapter_role_policy_t *policy, uint32_t generation);
bool adapter_role_policy_primary_verified(const adapter_role_policy_t *policy, uint32_t generation);
