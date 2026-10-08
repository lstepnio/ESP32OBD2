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
    scheduler->endpoint_count = 1;
}

poll_job_t poll_scheduler_next(poll_scheduler_t *scheduler, uint32_t now_ms,
                               const uint32_t *pid_intervals_ms, uint8_t pid_count)
{
    poll_job_t none = {0};
    if (!scheduler || !pid_intervals_ms ||
        pid_count > POLL_SCHEDULER_MAX_PIDS) return none;

    int chosen = -1;
    for (uint8_t step = 0; step < pid_count; ++step) {
        uint8_t index = (scheduler->cursor + step) % pid_count;
        uint32_t interval = pid_intervals_ms[index];
        if (scheduler->failures[index]) {
            uint32_t retry = 500U << scheduler->failures[index];
            if (retry > 5000U) retry = 5000U;
            if (retry > interval) interval = retry;
        }
        if (due((scheduler->seen_pids & (1UL << index)) != 0, now_ms,
                scheduler->last_pid[index], interval)) {
            chosen = index;
            break;
        }
    }

    /* Round-robin deadlines prevent MIL from starving code categories. A
     * minimum spacing bounds diagnostics even when normal reads are absent. */
    int background = -1;
    unsigned jobs = scheduler->endpoint_count * 4U;
    for (unsigned step = 0; step < jobs; ++step) {
        unsigned i = (scheduler->background_cursor + step) % jobs;
        uint32_t interval = i % 4 == 0 ? 15000U : i % 4 == 3 ? 120000U : 60000U;
        if(scheduler->background_failures[i]) { uint32_t retry=(15000U<<scheduler->background_failures[i])+((i*997U)%1000U);if(retry>240000U)retry=240000U;if(retry>interval)interval=retry; }
        if (due((scheduler->seen_background & (1U << i)) != 0, now_ms,
                scheduler->last_background[i], interval)) { background = (int)i; break; }
    }
    bool spaced = scheduler->seen_background == 0 || now_ms - scheduler->background_at >= 2000U;
    if (background >= 0 && spaced &&
        (chosen < 0 || scheduler->normal_since_background >= 10 ||
         (scheduler->normal_since_background > 0 && now_ms - scheduler->background_at >= 10000U))) {
        unsigned i = (unsigned)background;
        scheduler->normal_since_background = 0;
        scheduler->background_at = scheduler->last_background[i] = now_ms;
        scheduler->seen_background |= (uint8_t)(1U << i);
        scheduler->background_cursor = (uint8_t)((i + 1) % jobs);
        return (poll_job_t){.kind = i % 4 == 0 ? POLL_JOB_MIL : POLL_JOB_DTC,
            .index = (uint8_t)(i % 4 == 0 ? 0 : i % 4 - 1), .endpoint = (uint8_t)(i / 4)};
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

void poll_scheduler_result(poll_scheduler_t *scheduler, uint8_t index, bool success)
{
    if (!scheduler || index >= POLL_SCHEDULER_MAX_PIDS) return;
    if (success) scheduler->failures[index] = 0;
    else if (scheduler->failures[index] < 4) scheduler->failures[index]++;
}

void poll_scheduler_endpoints(poll_scheduler_t *scheduler, uint8_t count) {
    if (scheduler && count >= 1 && count <= 2) scheduler->endpoint_count = count;
}

void poll_scheduler_background_result(poll_scheduler_t *s,poll_job_t job,bool success) {
    if(!s || job.endpoint>=s->endpoint_count || (job.kind!=POLL_JOB_MIL && job.kind!=POLL_JOB_DTC))return;
    unsigned index=job.endpoint*4+(job.kind==POLL_JOB_MIL?0:job.index+1);
    if(index>=8)return;
    if(success)s->background_failures[index]=0;else if(s->background_failures[index]<4)s->background_failures[index]++;
}
void poll_scheduler_mil_changed(poll_scheduler_t *s,unsigned endpoint) {
    if(!s || endpoint>=s->endpoint_count)return;
    s->seen_background&=~(uint8_t)(0xeU<<(endpoint*4));
}
