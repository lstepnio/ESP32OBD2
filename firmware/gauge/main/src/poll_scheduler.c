#include <string.h>
#include "poll_scheduler.h"

static bool due(bool seen, uint32_t now, uint32_t last, uint32_t interval)
{
    return !seen || now - last >= interval;
}

void poll_scheduler_init(poll_scheduler_t *scheduler)
{
    if (!scheduler) return;
    memset(scheduler, 0, sizeof(*scheduler));
    scheduler->normal_since_background = 10;
}

poll_job_t poll_scheduler_next(poll_scheduler_t *scheduler, uint32_t now_ms,
                               const uint32_t *pid_intervals_ms, uint8_t pid_count)
{
    poll_job_t none = {0};
    if (!scheduler || !pid_intervals_ms || pid_count == 0 ||
        pid_count > POLL_SCHEDULER_MAX_PIDS) return none;

    int chosen = -1;
    for (uint8_t step = 0; step < pid_count; ++step) {
        uint8_t index = (scheduler->cursor + step) % pid_count;
        if (due((scheduler->seen_pids & (1UL << index)) != 0, now_ms,
                scheduler->last_pid[index], pid_intervals_ms[index])) {
            chosen = index;
            break;
        }
    }

    bool mil_due = due((scheduler->seen_background & 1U) != 0, now_ms,
                       scheduler->last_mil, 10000);
    int dtc = -1;
    for (uint8_t i = 0; i < 3; ++i)
        if (due((scheduler->seen_background & (uint8_t)(2U << i)) != 0, now_ms,
                scheduler->last_dtc[i], 30000)) { dtc = i; break; }

    if ((mil_due || dtc >= 0) && scheduler->normal_since_background >= 10) {
        scheduler->normal_since_background = 0;
        if (mil_due) {
            scheduler->last_mil = now_ms;
            scheduler->seen_background |= 1U;
            return (poll_job_t){.kind = POLL_JOB_MIL};
        }
        scheduler->last_dtc[dtc] = now_ms;
        scheduler->seen_background |= (uint8_t)(2U << dtc);
        return (poll_job_t){.kind = POLL_JOB_DTC, .index = (uint8_t)dtc};
    }

    if (chosen >= 0) {
        scheduler->last_pid[chosen] = now_ms;
        scheduler->seen_pids |= 1UL << chosen;
        scheduler->cursor = (uint8_t)((chosen + 1) % pid_count);
        if (scheduler->normal_since_background < UINT8_MAX)
            scheduler->normal_since_background++;
        return (poll_job_t){.kind = POLL_JOB_PID, .index = (uint8_t)chosen};
    }
    return none;
}
