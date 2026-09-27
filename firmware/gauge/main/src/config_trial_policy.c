#include <string.h>
#include "config_trial_policy.h"

void config_trial_policy_prepare(config_trial_policy_t *state, uint32_t revision)
{
    if (!state) return;
    state->pending_revision = revision;
    state->rejected_revision = 0;
    state->attempted = 0;
    memset(state->reserved, 0, sizeof(state->reserved));
}

bool config_trial_policy_cancel(config_trial_policy_t *state, uint32_t revision)
{
    if (!state || state->pending_revision != revision) return false;
    state->pending_revision = 0;
    state->attempted = 0;
    return true;
}

bool config_trial_policy_decide(config_trial_policy_t *state, uint32_t active_revision,
                                config_trial_policy_decision_t *decision)
{
    if (!state || !decision) return false;
    *decision = CONFIG_TRIAL_POLICY_ACTIVE;
    if (state->rejected_revision == active_revision) {
        *decision = CONFIG_TRIAL_POLICY_PREVIOUS;
        return false;
    }
    if (state->pending_revision != 0 && state->pending_revision != active_revision) {
        state->pending_revision = 0;
        state->attempted = 0;
        return true;
    }
    if (state->pending_revision != active_revision) return false;
    if (state->attempted) {
        state->pending_revision = 0;
        state->rejected_revision = active_revision;
        state->attempted = 0;
        *decision = CONFIG_TRIAL_POLICY_PREVIOUS;
    } else {
        state->attempted = 1;
        *decision = CONFIG_TRIAL_POLICY_TRIAL;
    }
    return true;
}

void config_trial_policy_reject(config_trial_policy_t *state, uint32_t revision)
{
    if (!state) return;
    state->pending_revision = 0;
    state->rejected_revision = revision;
    state->attempted = 0;
}

bool config_trial_policy_confirm(config_trial_policy_t *state, uint32_t revision)
{
    if (!state || state->pending_revision != revision || !state->attempted) return false;
    state->pending_revision = 0;
    state->rejected_revision = 0;
    state->attempted = 0;
    return true;
}

bool config_trial_policy_needs_confirmation(const config_trial_policy_t *state,
                                            uint32_t revision)
{
    return state && state->pending_revision == revision && state->attempted;
}

