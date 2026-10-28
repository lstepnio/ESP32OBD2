#pragma once

#include <stdbool.h>
#include <stdint.h>

#define PAGE_SAVE_IDLE_MS 750U

/* Used by app_main only. The UI publishes a generation after each local tap. */
typedef struct
{
    uint32_t generation;
    uint32_t saved_generation;
    uint32_t changed_at_ms;
} page_save_policy_t;

void page_save_observe(page_save_policy_t *state, uint32_t generation, uint32_t now_ms);
bool page_save_due(const page_save_policy_t *state, uint32_t now_ms);
void page_save_finished(page_save_policy_t *state, uint32_t attempted_generation, bool saved, uint32_t now_ms);

bool page_cycle_due(uint16_t interval_seconds,
                    unsigned page_count,
                    bool     owner_ready,
                    bool     transfer_idle,
                    uint32_t last_activity_ms,
                    uint32_t now_ms);

/* Reasons are diagnostic state, never user-facing error messages. */
typedef enum
{
    PAGE_CYCLE_RUNNING,
    PAGE_CYCLE_OFF,
    PAGE_CYCLE_SINGLE,
    PAGE_CYCLE_OWNER,
    PAGE_CYCLE_MAINTENANCE,
    PAGE_CYCLE_TOUCH,
    PAGE_CYCLE_ACTIONS,
    PAGE_CYCLE_ATTENTION,
    PAGE_CYCLE_SHOWCASE
} page_cycle_reason_t;
typedef struct
{
    bool                initialized;
    uint16_t            interval_seconds;
    page_cycle_reason_t reason;
    uint32_t            activity_ms, started_ms;
} page_cycle_policy_t;
/* A resume/interaction starts one full interval. No catch-up or implicit reset
 * for routine adapter polling, command ownership or phone status reads. */
bool page_cycle_step(page_cycle_policy_t *state,
                     uint16_t             interval_seconds,
                     unsigned             pages,
                     bool                 owner,
                     bool                 maintenance,
                     page_cycle_reason_t  ui_reason,
                     uint32_t             activity_ms,
                     uint32_t             now_ms);
