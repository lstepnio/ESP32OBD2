#include <string.h>
#include "nvs.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "config_trial.h"
#include "config_trial_policy.h"

#define TRIAL_MAGIC 0x31544345U /* ECT1 */

typedef struct {
    uint32_t magic;
    config_trial_policy_t policy;
} trial_record_t;

static SemaphoreHandle_t lock;
static trial_record_t state;

static esp_err_t save_locked(void)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open("eg_cfg_trial", NVS_READWRITE, &handle);
    if (err != ESP_OK) return err;
    err = nvs_set_blob(handle, "state", &state, sizeof(state));
    if (err == ESP_OK) err = nvs_commit(handle);
    nvs_close(handle);
    return err;
}

esp_err_t config_trial_init(void)
{
    if (!lock) lock = xSemaphoreCreateMutex();
    if (!lock) return ESP_ERR_NO_MEM;
    memset(&state, 0, sizeof(state));
    nvs_handle_t handle;
    esp_err_t err = nvs_open("eg_cfg_trial", NVS_READONLY, &handle);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        state.magic = TRIAL_MAGIC;
        return save_locked();
    }
    if (err != ESP_OK) return err;
    size_t length = sizeof(state);
    err = nvs_get_blob(handle, "state", &state, &length);
    nvs_close(handle);
    if (err == ESP_ERR_NVS_NOT_FOUND || length != sizeof(state) ||
        state.magic != TRIAL_MAGIC) {
        memset(&state, 0, sizeof(state));
        state.magic = TRIAL_MAGIC;
        return save_locked();
    }
    return err;
}

esp_err_t config_trial_prepare(uint32_t revision)
{
    if (!lock || revision == 0) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(lock, portMAX_DELAY);
    config_trial_policy_prepare(&state.policy, revision);
    esp_err_t err = save_locked();
    xSemaphoreGive(lock);
    return err;
}

esp_err_t config_trial_cancel(uint32_t revision)
{
    if (!lock || revision == 0) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(lock, portMAX_DELAY);
    esp_err_t err = ESP_OK;
    if (config_trial_policy_cancel(&state.policy, revision)) {
        err = save_locked();
    }
    xSemaphoreGive(lock);
    return err;
}

esp_err_t config_trial_decide(uint32_t active_revision,
                              config_trial_decision_t *decision)
{
    if (!lock || !decision || active_revision == 0) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(lock, portMAX_DELAY);
    esp_err_t err = ESP_OK;
    config_trial_policy_decision_t policy_decision;
    bool changed = config_trial_policy_decide(&state.policy, active_revision,
                                               &policy_decision);
    if (changed) err = save_locked();
    if (policy_decision == CONFIG_TRIAL_POLICY_PREVIOUS)
        *decision = CONFIG_TRIAL_USE_PREVIOUS;
    else if (policy_decision == CONFIG_TRIAL_POLICY_TRIAL && err == ESP_OK)
        *decision = CONFIG_TRIAL_USE_ACTIVE_TRIAL;
    else if (policy_decision == CONFIG_TRIAL_POLICY_TRIAL)
        *decision = CONFIG_TRIAL_USE_PREVIOUS;
    else *decision = CONFIG_TRIAL_USE_ACTIVE;
    xSemaphoreGive(lock);
    return err;
}

esp_err_t config_trial_reject(uint32_t revision)
{
    if (!lock || revision == 0) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(lock, portMAX_DELAY);
    config_trial_policy_reject(&state.policy, revision);
    esp_err_t err = save_locked();
    xSemaphoreGive(lock);
    return err;
}

esp_err_t config_trial_confirm(uint32_t revision)
{
    if (!lock || revision == 0) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(lock, portMAX_DELAY);
    esp_err_t err = ESP_OK;
    if (config_trial_policy_confirm(&state.policy, revision)) {
        err = save_locked();
    }
    xSemaphoreGive(lock);
    return err;
}

bool config_trial_needs_confirmation(uint32_t revision)
{
    if (!lock || revision == 0) return false;
    xSemaphoreTake(lock, portMAX_DELAY);
    bool pending = config_trial_policy_needs_confirmation(&state.policy, revision);
    xSemaphoreGive(lock);
    return pending;
}
