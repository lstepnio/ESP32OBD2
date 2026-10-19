#pragma once

#include <stdbool.h>
#include <stdint.h>

#define POLL_SCHEDULER_MAX_PIDS 32

typedef enum {
    POLL_JOB_NONE,
    POLL_JOB_PID,
    POLL_JOB_MIL,
    POLL_JOB_DTC,
} poll_job_kind_t;

typedef struct {
    poll_job_kind_t kind;
    uint8_t index;
    uint8_t endpoint;
} poll_job_t;

typedef struct {
    uint32_t last_pid[POLL_SCHEDULER_MAX_PIDS];
    uint8_t failures[POLL_SCHEDULER_MAX_PIDS];
    uint32_t last_background[8];
    uint8_t background_failures[8];
    uint32_t background_at;
    uint8_t background_cursor;
    uint8_t endpoint_count;
    uint32_t seen_pids;
    uint8_t seen_background;
    uint8_t cursor;
    uint8_t normal_since_background;
} poll_scheduler_t;

void poll_scheduler_init(poll_scheduler_t *scheduler);
/* Selects and accounts one due job. All time values are monotonic milliseconds;
 * unsigned subtraction keeps deadline checks valid across counter wrap. */
poll_job_t poll_scheduler_next(poll_scheduler_t *scheduler, uint32_t now_ms,
                               const uint32_t *pid_intervals_ms, uint8_t pid_count);

/* Per-reading loss backs off without delaying healthy sibling readings. */
void poll_scheduler_endpoints(poll_scheduler_t *scheduler, uint8_t count);
void poll_scheduler_result(poll_scheduler_t *scheduler, uint8_t index, bool success);

void poll_scheduler_background_result(poll_scheduler_t *scheduler,poll_job_t job,bool success);
void poll_scheduler_mil_changed(poll_scheduler_t *scheduler,unsigned endpoint);
