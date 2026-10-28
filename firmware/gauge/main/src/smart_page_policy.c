#include "smart_page_policy.h"
#include <math.h>
#include <string.h>

_Static_assert(sizeof(smart_page_policy_t) <= 4096, "Smart state exceeds fixed PSRAM budget");
static bool fresh(const smart_page_policy_t *s, unsigned i, uint32_t now)
{
    return s->pids[i].present && now - s->pids[i].at <= s->config[i].stale_ms;
}
static void credit_interest(smart_page_policy_t *s, unsigned page, uint32_t now)
{
    if (page >= s->page_count)
        return;
    for (unsigned m = 0; m < s->pages[page].count && m < 2; ++m)
    {
        unsigned i = s->pages[page].pids[m];
        if (i >= s->pid_count || !fresh(s, i, now) || !s->pids[i].rank)
            continue;
        smart_pid_state_t *p = &s->pids[i];
        p->boosted           = true;
        p->boosted_at        = now;
        p->boosted_episode   = p->episode;
        p->boosted_severity  = p->severity;
        p->boosted_rank      = p->rank;
    }
}
void smart_page_suspend(smart_page_policy_t *s)
{
    if (s->initialized)
    {
        memset(s->pids, 0, sizeof(s->pids));
        memset(s->visited_at, 0, sizeof(s->visited_at));
    }
    s->initialized = s->evaluated = s->context_decided = s->warm_pending = false;
    s->phase                                                             = 0;
    s->early_used                                                        = false;
}
bool smart_page_prepare(smart_page_policy_t *s,
                        uint16_t             interval,
                        uint8_t              selected,
                        uint32_t             activity,
                        bool                 paused,
                        uint32_t             now)
{
    if (!interval || s->page_count < 2 || s->page_count > SMART_PAGE_PAGES || s->pid_count > SMART_PAGE_PIDS ||
        selected >= s->page_count)
    {
        smart_page_suspend(s);
        return false;
    }
    if (!s->initialized || s->activity != activity || s->interval != interval || s->selected != selected ||
        s->paused != paused)
    {
        if (s->initialized)
            credit_interest(s, s->selected, now);
        credit_interest(s, selected, now);
        s->switched_at = s->protected_at = now;
        s->cursor                        = (selected + 1U) % s->page_count;
        s->insertion_used                = false;
        s->visited_at[selected]          = now;
        /* Initial startup may respond after minimum dwell; a manual choice or
         * pause exit gets its full interval. */
        if (!s->initialized)
            s->protected_at = now - (uint32_t)interval * 1000;
    }
    s->activity    = activity;
    s->interval    = interval;
    s->selected    = selected;
    s->paused      = paused;
    s->initialized = true;
    return !paused;
}
void smart_page_observe(smart_page_policy_t *s,
                        unsigned             i,
                        bool                 valid,
                        double               value,
                        uint32_t             at,
                        uint32_t             generation,
                        uint8_t              severity,
                        bool                 suppressed,
                        uint32_t             episode)
{
    if (i >= s->pid_count || i >= SMART_PAGE_PIDS)
        return;
    smart_pid_state_t        *p = &s->pids[i];
    const smart_pid_config_t *c = &s->config[i];
    if (!valid || !isfinite(value))
    {
        /* Keep the same-session cooldown across a short unavailable interval. */
        p->present = p->multiple = p->trend = false;
        p->rank = p->approach_direction = 0;
        return;
    }
    bool changed = p->generation != generation;
    bool gap     = !p->present || changed || at - p->at > c->stale_ms;
    if (changed)
    {
        memset(p, 0, sizeof(*p));
        if (c->rule.kind == SMART_METRIC_COOLANT || c->rule.kind == SMART_METRIC_RPM ||
            c->rule.kind == SMART_METRIC_RUNTIME)
        {
            s->context_decided = s->warm_pending = false;
            s->phase                             = 0;
        }
    }
    p->severity   = severity;
    p->suppressed = suppressed;
    p->episode    = episode;
    if (!gap && p->at == at)
        return;
    if (gap)
    {
        p->anchor    = value;
        p->anchor_at = p->first_at = at;
        p->multiple = p->trend = false;
        p->approach_direction  = 0;
    }
    else
    {
        p->multiple = true;
    }
    if (c->rule.kind == SMART_METRIC_RUNTIME && !gap && value > p->value)
    {
        p->advanced     = true;
        p->advancing_at = at;
    }
    if (gap)
        p->advanced = false;
    p->value          = value;
    p->at             = at;
    p->generation     = generation;
    p->present        = true;
    uint8_t direction = c->has_high && value >= c->high - c->rule.approach ? 1
                        : c->has_low && value <= c->low + c->rule.approach ? 2
                                                                           : 0;
    if (c->rule.approach <= 0)
        direction = 0;
    if (direction != p->approach_direction || gap)
    {
        p->approach_direction = direction;
        p->approach_since     = at;
        p->approach_origin    = (float)value;
    }
    uint32_t span = at - p->anchor_at;
    if (span >= SMART_PAGE_TREND_WINDOW_MS && c->rule.rise_rate > 0 && c->rule.rise_delta > 0)
    {
        double delta  = value - p->anchor;
        bool   rising = delta >= (p->trend ? c->rule.rise_delta * 0.5 : c->rule.rise_delta) &&
                        delta * 1000 / span >= c->rule.rise_rate * (p->trend ? 0.5 : 1.0);
        if (rising && !p->trend)
            p->trend_since = at;
        p->trend     = rising;
        p->anchor    = value;
        p->anchor_at = at;
    }
}
static void rank(smart_page_policy_t *s, uint32_t now)
{
    bool running = false, stopped = false;
    int  coolant = -1;
    for (unsigned i = 0; i < s->pid_count; ++i)
    {
        if (!fresh(s, i, now))
            continue;
        unsigned kind = s->config[i].rule.kind;
        if (kind == SMART_METRIC_RPM)
        {
            running |= s->pids[i].value > 0;
            stopped |= s->pids[i].value == 0;
        }
        else if (kind == SMART_METRIC_RUNTIME)
            running |= s->pids[i].advanced && now - s->pids[i].advancing_at <= s->config[i].stale_ms;
        if (kind == SMART_METRIC_COOLANT)
            coolant = (int)i;
    }
    if (stopped)
    {
        s->context_decided = s->warm_pending = false;
        s->phase                             = 0;
        running                              = false;
    }
    if (running && coolant >= 0)
    {
        if (!s->context_decided)
        {
            s->context_decided = true;
            s->phase           = s->pids[coolant].value < SMART_PAGE_COLD_ENTRY_C ? 1 : 2;
            s->cold_at         = now;
        }
        if (s->phase == 1)
        {
            if (now - s->cold_at >= SMART_PAGE_COLD_MAX_MS)
                s->phase = 0;
            else if (s->pids[coolant].value >= SMART_PAGE_WARM_EXIT_C)
            {
                if (!s->warm_pending)
                {
                    s->warm_at      = now;
                    s->warm_pending = true;
                }
                if (now - s->warm_at >= SMART_PAGE_WARM_DWELL_MS)
                    s->phase = 2;
            }
            else
                s->warm_pending = false;
        }
    }
    else
        s->warm_pending = false;
    unsigned context = running && coolant >= 0 ? s->phase : 0;
    for (unsigned i = 0; i < s->pid_count; ++i)
    {
        smart_pid_state_t *p = &s->pids[i];
        p->rank              = 0;
        p->reason            = SMART_REASON_ORDINARY;
        if (!fresh(s, i, now))
            continue;
        if (p->severity >= 2 && p->severity <= 4)
        {
            p->rank   = 40 + p->severity;
            p->reason = SMART_REASON_ALERT;
        }
        else if (p->multiple && p->at - p->approach_since >= SMART_PAGE_PERSIST_MS &&
                 ((p->approach_direction == 1 && s->config[i].has_high) ||
                  (p->approach_direction == 2 && s->config[i].has_low)))
        {
            p->rank   = 30;
            p->reason = SMART_REASON_APPROACH;
        }
        else if (!p->suppressed && context == 2 && p->multiple && p->trend &&
                 p->at - p->trend_since >= SMART_PAGE_PERSIST_MS)
        {
            p->rank   = 20;
            p->reason = SMART_REASON_TREND;
        }
        else if (!p->suppressed && context == 1 && p->multiple && p->at - p->first_at >= SMART_PAGE_PERSIST_MS &&
                 (s->config[i].rule.kind == SMART_METRIC_COOLANT || s->config[i].rule.kind == SMART_METRIC_THERMAL))
        {
            p->rank   = 10;
            p->reason = SMART_REASON_WARMUP;
        }
    }
}
static uint32_t revisit(const smart_page_policy_t *s)
{
    uint32_t ms = (uint32_t)s->interval * 3000;
    return ms > SMART_PAGE_EARLY_MS ? ms : SMART_PAGE_EARLY_MS;
}
static unsigned page_rank(const smart_page_policy_t *s, unsigned page, uint32_t now)
{
    unsigned result = 0;
    for (unsigned m = 0; m < s->pages[page].count && m < 2; ++m)
    {
        unsigned i = s->pages[page].pids[m];
        if (i < s->pid_count && fresh(s, i, now) && s->pids[i].rank > result)
            result = s->pids[i].rank;
    }
    return result;
}
static bool best(const smart_page_policy_t *s, uint32_t now, smart_page_decision_t *out)
{
    unsigned score = 0;
    bool     found = false;
    for (unsigned page = 0; page < s->page_count; ++page)
    {
        if (page == s->selected)
            continue;
        for (unsigned m = 0; m < s->pages[page].count && m < 2; ++m)
        {
            unsigned i = s->pages[page].pids[m];
            if (i >= s->pid_count)
                continue;
            const smart_pid_state_t *p = &s->pids[i];
            bool                     escalated =
                p->rank > p->boosted_rank || (p->reason == SMART_REASON_ALERT && p->episode != p->boosted_episode);
            if (!fresh(s, i, now) || !p->rank || (p->boosted && !escalated && now - p->boosted_at < revisit(s)))
                continue;
            if (!found || p->rank > score ||
                (p->rank == score && now - s->visited_at[page] > now - s->visited_at[out->page]))
            {
                *out  = (smart_page_decision_t){page, i, p->reason, true, false};
                score = p->rank;
                found = true;
            }
        }
    }
    return found;
}
static uint8_t ordinary(const smart_page_policy_t *s)
{
    return s->cursor == s->selected ? (s->cursor + 1U) % s->page_count : s->cursor;
}
bool smart_page_fallback(const smart_page_policy_t *s, uint32_t now, smart_page_decision_t *out)
{
    if (!s->initialized || s->paused || now - s->switched_at < (uint32_t)s->interval * 1000)
        return false;
    *out = (smart_page_decision_t){ordinary(s), UINT8_MAX, SMART_REASON_ORDINARY, false, false};
    return true;
}
uint8_t smart_page_upcoming(const smart_page_policy_t *s, uint32_t now)
{
    if (!s->initialized || s->paused || s->page_count < 2)
        return s->selected;
    smart_page_decision_t choice;
    return !s->insertion_used && best(s, now, &choice) && s->pids[choice.pid].rank > page_rank(s, s->selected, now)
               ? choice.page
               : ordinary(s);
}
bool smart_page_choose(smart_page_policy_t *s, uint32_t now, smart_page_decision_t *out)
{
    if (!s->initialized || s->paused)
        return false;
    if (!s->evaluated || now - s->evaluated_at >= SMART_PAGE_EVALUATE_MS)
    {
        rank(s, now);
        s->evaluated_at = now;
        s->evaluated    = true;
    }
    uint32_t elapsed = now - s->switched_at;
    bool     due     = elapsed >= (uint32_t)s->interval * 1000;
    if (elapsed < SMART_PAGE_MIN_VIEW_MS)
        return false;
    uint8_t next = ordinary(s);
    *out         = (smart_page_decision_t){next, UINT8_MAX, SMART_REASON_ORDINARY, false, false};
    smart_page_decision_t candidate;
    if (!s->insertion_used && best(s, now, &candidate) && s->pids[candidate.pid].rank > page_rank(s, s->selected, now))
    {
        const smart_pid_state_t *p           = &s->pids[candidate.pid];
        bool                     new_warning = p->reason == SMART_REASON_ALERT && p->severity >= 3 &&
                                               (p->episode != p->boosted_episode || p->severity > p->boosted_severity);
        bool                     approaching =
            p->reason == SMART_REASON_APPROACH && ((p->approach_direction == 1 && p->value > p->approach_origin) ||
                                                   (p->approach_direction == 2 && p->value < p->approach_origin));
        bool early = !due && now - s->protected_at >= (uint32_t)s->interval * 1000 &&
                     (!s->early_used || now - s->early_at >= SMART_PAGE_EARLY_MS) &&
                     p->rank > page_rank(s, s->selected, now) && (new_warning || approaching);
        if (due || early)
        {
            *out          = candidate;
            out->priority = candidate.page != next;
            out->early    = early;
            return true;
        }
    }
    return due;
}
void smart_page_commit(smart_page_policy_t *s, const smart_page_decision_t *choice, uint32_t now)
{
    if (choice->page >= s->page_count || choice->page == s->selected)
        return;
    if (choice->priority)
        s->insertion_used = true;
    else
    {
        s->cursor         = (choice->page + 1U) % s->page_count;
        s->insertion_used = false;
    }
    credit_interest(s, s->selected, now);
    s->selected = choice->page;
    s->activity = s->switched_at = now;
    s->visited_at[choice->page]  = now;
    if (choice->early)
    {
        s->early_at   = now;
        s->early_used = true;
    }
    credit_interest(s, choice->page, now);
}
