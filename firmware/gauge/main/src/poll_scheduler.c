#include <limits.h>
#include <string.h>
#include "poll_scheduler.h"

static void increment(uint32_t *value)
{
    if (*value < UINT32_MAX)
        ++*value;
}

static uint32_t bounded_sum(uint32_t a, uint32_t b)
{
    return a >= INT32_MAX || b >= INT32_MAX - a ? INT32_MAX : a + b;
}

static bool due(bool seen, uint32_t now, uint32_t last, uint32_t interval)
{
    return !seen || now - last >= interval;
}

static uint32_t pid_interval(const poll_scheduler_t *s, const poll_pid_policy_t *p, unsigned i)
{
    uint32_t interval = p[i].interval_ms;
    if (s->failures[i])
    {
        uint32_t retry = 500U << s->failures[i];
        if (retry > 5000U)
            retry = 5000U;
        if (retry > interval)
            interval = retry;
    }
    return interval;
}

static bool eligible(const poll_scheduler_t *s, const poll_pid_policy_t *p, unsigned i, uint32_t now)
{
    return due((s->seen_pids & (1UL << i)) != 0, now, s->last_pid[i], pid_interval(s, p, i));
}

/* Backoff is never bypassed by freshness preflight. Healthy PIDs may be refreshed
 * before their nominal cadence to make room for one long diagnostic. */
static bool retry_ready(const poll_scheduler_t *s, const poll_pid_policy_t *p, unsigned i, uint32_t now)
{
    return !s->failures[i] || eligible(s, p, i, now);
}

static bool at_risk(const poll_scheduler_t *s, const poll_pid_policy_t *p, unsigned i, uint32_t now, uint32_t reserve)
{
    if (!p[i].display_age_ms)
        return false;
    return !(s->successful_pids & (1UL << i)) || bounded_sum(now - s->last_success[i], reserve) >= p[i].display_age_ms;
}

void poll_scheduler_init(poll_scheduler_t *scheduler)
{
    if (!scheduler)
        return;
    memset(scheduler, 0, sizeof(*scheduler));
    scheduler->normal_since_background = 10;
    scheduler->endpoint_count          = 1;
}

static int background_due(const poll_scheduler_t *s, uint32_t now)
{
    unsigned jobs = s->endpoint_count * 4U;
    for (unsigned step = 0; step < jobs; ++step)
    {
        unsigned i        = (s->background_cursor + step) % jobs;
        uint32_t interval = i % 4 == 3 ? 120000U : 60000U;
        if (!(s->settled_background & (1U << i)) && s->background_failures[i] > 0)
        {
            interval = 1000U << s->background_failures[i];
            if (interval > 240000U)
                interval = 240000U;
        }
        else if (s->background_failures[i])
        {
            uint32_t retry = (15000U << s->background_failures[i]) + ((i * 997U) % 1000U);
            if (retry > 240000U)
                retry = 240000U;
            if (retry > interval)
                interval = retry;
        }
        /* Lamp checks are at most once a minute, including cold failures.
         * Incomplete code lists may retry sooner to classify configured filters. */
        if (i % 4 == 0 && interval < 60000U)
            interval = 60000U;
        if (due((s->seen_background & (1U << i)) != 0, now, s->last_background[i], interval))
            return (int)i;
    }
    return -1;
}

static poll_job_t select_pid(poll_scheduler_t        *s,
                             unsigned                 index,
                             uint8_t                  count,
                             const poll_pid_policy_t *p,
                             bool                     ordinary)
{
    s->cursor = (uint8_t)((index + 1) % count);
    if (s->normal_since_background < UINT8_MAX)
        ++s->normal_since_background;
    if (ordinary)
    {
        if (s->priority_streak < 8)
            ++s->priority_streak;
        else
        {
            s->priority_streak = 0;
            s->fairness_cursor = (uint8_t)((index + 1) % count);
        }
    }
    s->pending_had_sample = (s->successful_pids & (1UL << index)) != 0;
    s->pending_sample_at  = s->last_success[index];
    s->pending_age_ms     = p[index].display_age_ms;
    return (poll_job_t){.kind = POLL_JOB_PID, .index = (uint8_t)index};
}

poll_job_t poll_scheduler_next_budgeted(poll_scheduler_t              *s,
                                        uint32_t                       now,
                                        const poll_pid_policy_t       *p,
                                        uint8_t                        count,
                                        const poll_scheduler_budget_t *budget)
{
    poll_job_t none = {0};
    if (!s || (!p && count) || !budget || count > POLL_SCHEDULER_MAX_PIDS)
        return none;
    int     chosen        = -1;
    int64_t best_deadline = INT64_MAX;
    /* Every ninth PID dispatch follows round robin, irrespective of priority
     * or lateness. A continuously eligible hidden PID gets a slot within at
     * most 9*count ordinary PID dispatches, plus bounded diagnostic/preflight work. */
    for (unsigned step = 0; step < count; ++step)
    {
        unsigned i = ((s->priority_streak >= 8 ? s->fairness_cursor : s->cursor) + step) % count;
        if (!eligible(s, p, i, now))
            continue;
        if (s->priority_streak >= 8)
        {
            chosen = (int)i;
            break;
        }
        bool    has_sample = (s->successful_pids & (1UL << i)) != 0;
        int64_t remaining  = !has_sample           ? INT32_MIN
                             : p[i].display_age_ms ? (int64_t)p[i].display_age_ms - (now - s->last_success[i])
                                                   : INT32_MAX;
        /* Urgent readings precede nonurgent ones; otherwise priority orders
         * due work, with deadline and rotating cursor breaking ties. */
        bool urgent      = remaining <= (int64_t)bounded_sum(budget->pid_timeout_ms, budget->recovery_ms);
        bool best_urgent = best_deadline <= (int64_t)bounded_sum(budget->pid_timeout_ms, budget->recovery_ms);
        if (chosen < 0 || (urgent && !best_urgent) ||
            (urgent == best_urgent &&
             ((urgent && remaining < best_deadline) ||
              ((!urgent || remaining == best_deadline) && p[i].priority > p[chosen].priority) ||
              (!urgent && p[i].priority == p[chosen].priority && remaining < best_deadline))))
        {
            chosen        = (int)i;
            best_deadline = remaining;
        }
    }

    int  background = s->preflight_background ? s->preflight_background - 1 : background_due(s, now);
    bool spaced     = !s->seen_background || now - s->background_at >= 2000U;
    bool admit      = s->preflight_background || (background >= 0 && spaced &&
                                                  (chosen < 0 || s->normal_since_background >= 10 ||
                                                   (s->normal_since_background && now - s->background_at >= 10000U)));
    if (background >= 0 && admit)
    {
        if (!s->preflight_background)
        {
            s->preflight_background = (uint8_t)(background + 1);
            s->preflight_pids       = 0;
        }
        uint32_t diagnostic = background % 4 == 0 ? budget->mil_timeout_ms : budget->dtc_timeout_ms;
        /* Reserve the diagnostic's full budget, its recovery, and the next
         * normal read plus recovery. No measured-fast average hides a timeout. */
        uint32_t reserve = bounded_sum(bounded_sum(diagnostic, budget->recovery_ms),
                                       bounded_sum(budget->pid_timeout_ms, budget->recovery_ms));
        reserve          = bounded_sum(reserve, budget->foreground_ms);
        bool blocked     = false;
        for (unsigned step = 0; step < count; ++step)
        {
            unsigned i = (s->cursor + step) % count;
            if (!at_risk(s, p, i, now, reserve))
                continue;
            blocked = true;
            if (!(s->preflight_pids & (1UL << i)) && retry_ready(s, p, i, now))
            {
                s->preflight_pids |= 1UL << i;
                increment(&s->deferred_diagnostics);
                return select_pid(s, i, count, p, false);
            }
        }
        /* An impossible window or failed/backed-off reading must not suppress
         * diagnostic progress forever. Each preflight touches a PID once. */
        if (blocked)
            increment(&s->forced_diagnostics);
        s->normal_since_background = 0;
        s->preflight_background    = 0;
        s->preflight_pids          = 0;
        s->background_cursor       = (uint8_t)((background + 1) % (s->endpoint_count * 4U));
        return (poll_job_t){.kind     = background % 4 == 0 ? POLL_JOB_MIL : POLL_JOB_DTC,
                            .index    = (uint8_t)(background % 4 == 0 ? 0 : background % 4 - 1),
                            .endpoint = (uint8_t)(background / 4)};
    }
    return chosen >= 0 ? select_pid(s, (unsigned)chosen, count, p, true) : none;
}

void poll_scheduler_sample(poll_scheduler_t *s, uint8_t index, uint32_t sampled)
{
    if (!s || index >= POLL_SCHEDULER_MAX_PIDS)
        return;
    uint32_t bit = 1UL << index;
    if ((s->successful_pids & bit) && (int32_t)(sampled - s->last_success[index]) <= 0)
        return;
    s->last_success[index] = sampled;
    s->successful_pids |= bit;
    /* A reused sample can predate a failed attempt. It must not move the
     * attempt clock backward, while still restoring known sample freshness. */
    if (!(s->seen_pids & bit) || (int32_t)(sampled - s->last_pid[index]) >= 0)
        s->last_pid[index] = sampled;
    s->seen_pids |= bit;
    s->failures[index] = 0;
}

void poll_scheduler_complete(poll_scheduler_t *s, poll_job_t job, uint32_t completed, bool success)
{
    if (!s)
        return;
    if (job.kind == POLL_JOB_PID && job.index < POLL_SCHEDULER_MAX_PIDS)
    {
        if (s->pending_had_sample && s->pending_age_ms && completed - s->pending_sample_at > s->pending_age_ms)
            increment(&s->deadline_misses);
        s->pending_had_sample  = false;
        s->last_pid[job.index] = completed;
        s->seen_pids |= 1UL << job.index;
        if (success)
            poll_scheduler_sample(s, job.index, completed);
        else
            poll_scheduler_result(s, job.index, false);
    }
    else if (job.endpoint < s->endpoint_count &&
             (job.kind == POLL_JOB_MIL || (job.kind == POLL_JOB_DTC && job.index < 3)))
    {
        unsigned i            = job.endpoint * 4 + (job.kind == POLL_JOB_MIL ? 0 : job.index + 1);
        s->last_background[i] = s->background_at = completed;
        s->seen_background |= (uint8_t)(1U << i);
        poll_scheduler_background_result(s, job, success);
    }
}

void poll_scheduler_diagnostic_complete(poll_scheduler_t        *s,
                                        poll_job_t               job,
                                        uint32_t                 completed,
                                        poll_diagnostic_result_t result)
{
    if (!s || job.endpoint >= s->endpoint_count || (unsigned)result > POLL_DIAGNOSTIC_UNSUPPORTED ||
        (job.kind != POLL_JOB_MIL && (job.kind != POLL_JOB_DTC || job.index >= 3)))
        return;
    poll_scheduler_complete(s, job, completed, result == POLL_DIAGNOSTIC_OK);
    if (result == POLL_DIAGNOSTIC_UNSUPPORTED)
        s->settled_background |= (uint8_t)(1U << (job.endpoint * 4 + (job.kind == POLL_JOB_MIL ? 0 : job.index + 1)));
}

poll_job_t poll_scheduler_next(poll_scheduler_t *s, uint32_t now, const uint32_t *intervals, uint8_t count)
{
    poll_job_t none = {0};
    if (!intervals || count > POLL_SCHEDULER_MAX_PIDS)
        return none;
    poll_pid_policy_t p[POLL_SCHEDULER_MAX_PIDS] = {0};
    for (unsigned i = 0; i < count; ++i)
        p[i].interval_ms = intervals[i];
    const poll_scheduler_budget_t budget = {0};
    poll_job_t                    job    = poll_scheduler_next_budgeted(s, now, p, count, &budget);
    poll_scheduler_complete(s, job, now, true);
    return job;
}

void poll_scheduler_result(poll_scheduler_t *s, uint8_t index, bool success)
{
    if (!s || index >= POLL_SCHEDULER_MAX_PIDS)
        return;
    if (success)
        s->failures[index] = 0;
    else if (s->failures[index] < 4)
        ++s->failures[index];
}

void poll_scheduler_endpoints(poll_scheduler_t *s, uint8_t count)
{
    if (s && count <= 2)
    {
        s->endpoint_count       = count;
        s->preflight_background = 0;
        s->preflight_pids       = 0;
    }
}

void poll_scheduler_background_result(poll_scheduler_t *s, poll_job_t job, bool success)
{
    if (!s || job.endpoint >= s->endpoint_count || (job.kind != POLL_JOB_MIL && job.kind != POLL_JOB_DTC) ||
        (job.kind == POLL_JOB_DTC && job.index >= 3))
        return;
    unsigned i = job.endpoint * 4 + (job.kind == POLL_JOB_MIL ? 0 : job.index + 1);
    if (success)
    {
        s->background_failures[i] = 0;
        s->settled_background |= (uint8_t)(1U << i);
    }
    else if (s->background_failures[i] < ((s->settled_background & (1U << i)) ? 4 : 8))
        ++s->background_failures[i];
}

void poll_scheduler_mil_changed(poll_scheduler_t *s, unsigned endpoint)
{
    if (!s || endpoint >= s->endpoint_count)
        return;
    s->seen_background &= ~(uint8_t)(0xeU << (endpoint * 4));
    s->settled_background &= ~(uint8_t)(0xeU << (endpoint * 4));
    for (unsigned i = 1; i < 4; ++i)
        s->background_failures[endpoint * 4 + i] = 0;
}
