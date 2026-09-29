#include "page_save_policy.h"

void page_save_observe(page_save_policy_t *state, uint32_t generation, uint32_t now_ms)
{
    if (generation == state->generation) return;
    state->generation = generation;
    state->changed_at_ms = now_ms;
}

bool page_save_due(const page_save_policy_t *state, uint32_t now_ms)
{
    return state->generation != state->saved_generation &&
           (uint32_t)(now_ms - state->changed_at_ms) >= PAGE_SAVE_IDLE_MS;
}

void page_save_finished(page_save_policy_t *state, uint32_t attempted_generation,
                        bool saved, uint32_t now_ms)
{
    if (saved) state->saved_generation = attempted_generation;
    else state->changed_at_ms = now_ms; /* Bound flash retries after a storage failure. */
}
