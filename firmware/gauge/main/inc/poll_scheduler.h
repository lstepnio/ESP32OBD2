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
} poll_job_t;

typedef struct {
    uint32_t last_pid[POLL_SCHEDULER_MAX_PIDS];
    uint32_t last_mil;
    uint32_t last_dtc[3];
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
