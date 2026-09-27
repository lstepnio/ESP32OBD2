#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef enum {
    CONFIG_TRIAL_POLICY_ACTIVE,
    CONFIG_TRIAL_POLICY_TRIAL,
    CONFIG_TRIAL_POLICY_PREVIOUS,
} config_trial_policy_decision_t;

typedef struct {
    uint32_t pending_revision;
    uint32_t rejected_revision;
    uint8_t attempted;
    uint8_t reserved[3];
} config_trial_policy_t;

void config_trial_policy_prepare(config_trial_policy_t *state, uint32_t revision);
bool config_trial_policy_cancel(config_trial_policy_t *state, uint32_t revision);
bool config_trial_policy_decide(config_trial_policy_t *state, uint32_t active_revision,
                                config_trial_policy_decision_t *decision);
void config_trial_policy_reject(config_trial_policy_t *state, uint32_t revision);
bool config_trial_policy_confirm(config_trial_policy_t *state, uint32_t revision);
bool config_trial_policy_needs_confirmation(const config_trial_policy_t *state,
                                            uint32_t revision);

