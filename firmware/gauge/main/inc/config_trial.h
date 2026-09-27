#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

typedef enum {
    CONFIG_TRIAL_USE_ACTIVE,
    CONFIG_TRIAL_USE_ACTIVE_TRIAL,
    CONFIG_TRIAL_USE_PREVIOUS,
} config_trial_decision_t;

/* Persistent boot journal for a newly committed configuration. All functions
 * run in application/worker task context and may commit NVS. */
esp_err_t config_trial_init(void);
esp_err_t config_trial_prepare(uint32_t revision);
esp_err_t config_trial_cancel(uint32_t revision);
esp_err_t config_trial_decide(uint32_t active_revision,
                              config_trial_decision_t *decision);
esp_err_t config_trial_reject(uint32_t revision);
esp_err_t config_trial_confirm(uint32_t revision);
bool config_trial_needs_confirmation(uint32_t revision);

