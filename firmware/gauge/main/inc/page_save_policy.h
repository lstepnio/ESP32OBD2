#pragma once

#include <stdbool.h>
#include <stdint.h>

#define PAGE_SAVE_IDLE_MS 750U

/* Used by app_main only. The UI publishes a generation after each local tap. */
typedef struct {
    uint32_t generation;
    uint32_t saved_generation;
    uint32_t changed_at_ms;
} page_save_policy_t;

void page_save_observe(page_save_policy_t *state, uint32_t generation, uint32_t now_ms);
bool page_save_due(const page_save_policy_t *state, uint32_t now_ms);
void page_save_finished(page_save_policy_t *state, uint32_t attempted_generation,
                        bool saved, uint32_t now_ms);

bool page_cycle_due(uint16_t interval_seconds, unsigned page_count, bool owner_ready,
                    bool transfer_idle, uint32_t last_activity_ms, uint32_t now_ms);
