#include "page_save_policy.h"

void page_save_observe(page_save_policy_t *state, uint32_t generation, uint32_t now_ms)
{
    if (generation == state->generation)
        return;
    state->generation    = generation;
    state->changed_at_ms = now_ms;
}

bool page_save_due(const page_save_policy_t *state, uint32_t now_ms)
{
    return state->generation != state->saved_generation &&
           (uint32_t)(now_ms - state->changed_at_ms) >= PAGE_SAVE_IDLE_MS;
}

void page_save_finished(page_save_policy_t *state, uint32_t attempted_generation, bool saved, uint32_t now_ms)
{
    if (saved)
        state->saved_generation = attempted_generation;
    else
        state->changed_at_ms = now_ms; /* Bound flash retries after a storage failure. */
}

bool page_cycle_due(uint16_t interval_seconds,
                    unsigned page_count,
                    bool     owner_ready,
                    bool     transfer_idle,
                    uint32_t last_activity_ms,
                    uint32_t now_ms)
{
    return interval_seconds != 0 && page_count > 1 && owner_ready && transfer_idle &&
           (uint32_t)(now_ms - last_activity_ms) >= (uint32_t)interval_seconds * 1000;
}

bool page_cycle_step(page_cycle_policy_t *s,
                     uint16_t             seconds,
                     unsigned             pages,
                     bool                 owner,
                     bool                 maintenance,
                     page_cycle_reason_t  ui_reason,
                     uint32_t             activity,
                     uint32_t             now)
{
    page_cycle_reason_t reason = !seconds      ? PAGE_CYCLE_OFF
                                 : pages < 2   ? PAGE_CYCLE_SINGLE
                                 : !owner      ? PAGE_CYCLE_OWNER
                                 : maintenance ? PAGE_CYCLE_MAINTENANCE
                                               : ui_reason;
    if (!s->initialized || seconds != s->interval_seconds || reason != s->reason || activity != s->activity_ms)
        s->started_ms = now;
    s->initialized      = true;
    s->interval_seconds = seconds;
    s->reason           = reason;
    s->activity_ms      = activity;
    return reason == PAGE_CYCLE_RUNNING && (uint32_t)(now - s->started_ms) >= (uint32_t)seconds * 1000;
}
