#pragma once

#include <stdbool.h>
#include <stdint.h>

#define POLL_SCHEDULER_MAX_PIDS 32

/* All intervals/budgets and time between calls must be below INT32_MAX ms.
 * Only one transaction may be in flight per scheduler. No control guards belong
 * in this list: the transport owner services those before ordinary work. */
typedef enum
{
    POLL_JOB_NONE,
    POLL_JOB_PID,
    POLL_JOB_MIL,
    POLL_JOB_DTC,
} poll_job_kind_t;

typedef struct
{
    poll_job_kind_t kind;
    uint8_t         index;
    uint8_t         endpoint;
} poll_job_t;

typedef enum
{
    POLL_DIAGNOSTIC_RETRY,
    POLL_DIAGNOSTIC_OK,
    POLL_DIAGNOSTIC_UNSUPPORTED,
} poll_diagnostic_result_t;

typedef struct
{
    uint32_t interval_ms;
    uint32_t display_age_ms; /* Zero disables freshness reservation, not polling. */
    uint8_t  priority;       /* Larger values win after deadline urgency. */
} poll_pid_policy_t;

typedef struct
{
    uint32_t pid_timeout_ms;
    uint32_t mil_timeout_ms;
    uint32_t dtc_timeout_ms;
    uint32_t recovery_ms;   /* Full prompt recovery / worker gap after each request. */
    uint32_t foreground_ms; /* Other owner work before the next normal read. */
} poll_scheduler_budget_t;

typedef struct
{
    uint32_t last_pid[POLL_SCHEDULER_MAX_PIDS]; /* Attempt completion. */
    uint8_t  failures[POLL_SCHEDULER_MAX_PIDS];
    uint32_t last_background[8];
    uint8_t  background_failures[8];
    uint32_t background_at;
    uint8_t  background_cursor;
    uint8_t  endpoint_count;
    uint32_t seen_pids;
    uint8_t  seen_background;
    uint8_t  settled_background; /* Valid or definitively unsupported in this scope. */
    uint8_t  cursor;
    uint8_t  normal_since_background;
    uint32_t last_success[POLL_SCHEDULER_MAX_PIDS];
    uint32_t successful_pids;
    uint32_t preflight_pids;
    uint32_t deferred_diagnostics; /* Saturating admission-deferral count. */
    uint32_t forced_diagnostics;   /* Saturating overload/failure admission count. */
    uint32_t deadline_misses;      /* Completed refreshes beyond the prior sample budget. */
    uint32_t pending_age_ms;
    uint32_t pending_sample_at;
    uint8_t  preflight_background;       /* 1..8, zero means no pending diagnostic. */
    uint8_t  preflight_since_foreground; /* At most two preflight reads before a due visible slot. */
    uint8_t  priority_streak;
    uint8_t  fairness_cursor;
    bool     pending_had_sample;
} poll_scheduler_t;

void poll_scheduler_init(poll_scheduler_t *scheduler);
void poll_scheduler_endpoints(poll_scheduler_t *scheduler, uint8_t count);
/* Selection does not set completion/sample timestamps. Complete every selected
 * transaction, using the actual completion time, including failed transactions. */
poll_job_t poll_scheduler_next_budgeted(poll_scheduler_t              *scheduler,
                                        uint32_t                       now_ms,
                                        const poll_pid_policy_t       *policies,
                                        uint8_t                        pid_count,
                                        const poll_scheduler_budget_t *budget);
void       poll_scheduler_complete(poll_scheduler_t *scheduler, poll_job_t job, uint32_t completed_ms, bool success);
/* Unsupported settles coverage; a transient unanswered request does not. */
void poll_scheduler_diagnostic_complete(poll_scheduler_t        *scheduler,
                                        poll_job_t               job,
                                        uint32_t                 completed_ms,
                                        poll_diagnostic_result_t result);
/* A correctly attributed sample reused from another transport consumer counts
 * toward cadence/freshness too. Pass its original timestamp, never arrival time.
 * Retired sessions must be rejected by the caller; reset scheduler on new scope. */
void poll_scheduler_sample(poll_scheduler_t *scheduler, uint8_t index, uint32_t sampled_ms);

/* Legacy dispatch-time accounting. New transport owners use the API above. */
poll_job_t poll_scheduler_next(poll_scheduler_t *scheduler,
                               uint32_t          now_ms,
                               const uint32_t   *pid_intervals_ms,
                               uint8_t           pid_count);
void       poll_scheduler_result(poll_scheduler_t *scheduler, uint8_t index, bool success);
void       poll_scheduler_background_result(poll_scheduler_t *scheduler, poll_job_t job, bool success);
void       poll_scheduler_mil_changed(poll_scheduler_t *scheduler, unsigned endpoint);
