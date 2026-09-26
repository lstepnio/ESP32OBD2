#include <string.h>
#include "alert_engine.h"

typedef struct {
    uint8_t severity;
    uint8_t pending;
    uint32_t pending_since;
    uint32_t last_sample;
    bool sampled;
} alert_state_t;

static const config_runtime_t *rules;
static alert_state_t states[EGAUGE_RUNTIME_ALERTS];

static bool crosses(const runtime_alert_t *rule, double value, double limit)
{
    return rule->above ? value >= limit : value <= limit;
}

static bool releases(const runtime_alert_t *rule, double value, double limit)
{
    return rule->above ? value <= limit - rule->hysteresis
                       : value >= limit + rule->hysteresis;
}

void alert_engine_init(const config_runtime_t *runtime)
{
    rules = runtime;
    memset(states, 0, sizeof(states));
}

void alert_engine_sample(uint8_t pid_index, double value, uint32_t now_ms)
{
    if (!rules) return;
    for (unsigned i = 0; i < rules->alert_count; ++i) {
        const runtime_alert_t *rule = &rules->alerts[i];
        if (rule->pid_index != pid_index) continue;
        alert_state_t *state = &states[i];
        uint32_t stale = rules->pids[pid_index].stale_ms;
        if (state->sampled && now_ms - state->last_sample > stale) {
            state->pending = 0;
            state->pending_since = 0;
        }
        state->sampled = true;
        state->last_sample = now_ms;
        uint8_t target = 0;
        if (rule->has_critical && crosses(rule, value, rule->critical)) target = 2;
        else if (rule->has_warning && crosses(rule, value, rule->warning)) target = 1;
        if (target > state->severity) {
            if (state->pending != target) {
                state->pending = target;
                state->pending_since = now_ms;
            } else if (now_ms - state->pending_since >= rule->trigger_dwell_ms) {
                state->severity = target;
                state->pending = 0;
            }
        } else if (target < state->severity) {
            double active_limit = state->severity == 2 ? rule->critical : rule->warning;
            if (!releases(rule, value, active_limit)) {
                state->pending = 0;
            } else if (state->pending != 3) {
                state->pending = 3;
                state->pending_since = now_ms;
            } else if (now_ms - state->pending_since >= rule->clear_dwell_ms) {
                state->severity = target;
                state->pending = 0;
            }
        } else state->pending = 0;
    }
}

alert_summary_t alert_engine_tick(uint32_t now_ms)
{
    alert_summary_t summary = {0};
    uint8_t chosen_priority = 0;
    if (!rules) return summary;
    for (unsigned i = 0; i < rules->alert_count; ++i) {
        const runtime_alert_t *rule = &rules->alerts[i];
        alert_state_t *state = &states[i];
        bool stale = !state->sampled ||
            now_ms - state->last_sample > rules->pids[rule->pid_index].stale_ms;
        if (stale) state->pending = 0;
        if (state->severity > summary.severity ||
            (state->severity != 0 && state->severity == summary.severity &&
             rule->priority > chosen_priority)) {
            summary.severity = state->severity;
            summary.unavailable = stale;
            summary.label = rules->pids[rule->pid_index].name;
            chosen_priority = rule->priority;
        }
    }
    return summary;
}
