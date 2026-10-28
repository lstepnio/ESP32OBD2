#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "smart_page_policy.h"
#include "smart_metric_catalog.h"
#include "alert_events.h"

static smart_page_policy_t s;
static double              values[SMART_PAGE_PIDS];
static uint8_t             severities[SMART_PAGE_PIDS];
static uint32_t            episodes[SMART_PAGE_PIDS], generations[SMART_PAGE_PIDS];
static bool                valid[SMART_PAGE_PIDS], suppressed[SMART_PAGE_PIDS];
static uint32_t            activity;
static uint8_t             selected;
static unsigned            switches;
static void                init(unsigned pages, unsigned seconds, uint32_t now)
{
    memset(&s, 0, sizeof(s));
    memset(severities, 0, sizeof(severities));
    memset(episodes, 0, sizeof(episodes));
    memset(suppressed, 0, sizeof(suppressed));
    s.page_count = pages;
    s.pid_count  = pages > 4 ? pages : 4;
    for (unsigned i = 0; i < s.pid_count; ++i)
    {
        s.config[i].stale_ms = 2000;
        generations[i]       = 1;
        values[i]            = 40;
        valid[i]             = true;
    }
    for (unsigned i = 0; i < pages; ++i)
        s.pages[i] = (smart_page_definition_t){1, {i, 0}};
    s.config[0].rule.kind = SMART_METRIC_RPM;
    s.config[1].rule      = (smart_metric_rule_t){5, 0.2f, 3, SMART_METRIC_COOLANT};
    s.config[2].rule      = (smart_metric_rule_t){5, 0.2f, 3, SMART_METRIC_THERMAL};
    values[0]             = 700;
    values[1]             = 92;
    selected              = 0;
    activity              = now;
    switches              = 0;
    assert(smart_page_prepare(&s, seconds, selected, activity, false, now) == (pages > 1));
}
static bool tick(uint32_t now, smart_page_decision_t *d, bool commit)
{
    if (!smart_page_prepare(&s, s.interval, selected, activity, false, now))
        return false;
    for (unsigned i = 0; i < s.pid_count; ++i)
        smart_page_observe(&s, i, valid[i], values[i], now, generations[i], severities[i], suppressed[i], episodes[i]);
    bool due = smart_page_choose(&s, now, d);
    if (due && commit)
    {
        smart_page_commit(&s, d, now);
        selected = d->page;
        activity = now;
        switches++;
    }
    return due;
}
static void typical_and_early(void)
{
    smart_page_decision_t d;
    init(4, 10, 0);
    for (uint32_t t = 0; t <= 40000; t += 200)
    {
        bool changed = tick(t, &d, true);
        assert(changed == (t && t % 10000 == 0));
        if (changed)
            assert(d.page == (t / 10000) % 4 && d.reason == SMART_REASON_ORDINARY);
    }
    init(4, 10, 0);
    for (uint32_t t = 0; t <= 26000; t += 200)
    {
        if (t == 6000)
        {
            severities[2] = 3;
            episodes[2]   = 8;
        }
        bool changed = tick(t, &d, true);
        if (t == 6000)
            assert(changed && d.page == 2 && d.early && s.insertion_used);
        else if (t == 16000)
            assert(changed && d.page == 1 && !d.priority);
        else if (t == 26000)
            assert(changed && d.page == 2 && !d.priority);
        else
            assert(!changed);
    }
    assert(switches == 3);
}
static void manual_pause_and_lock(void)
{
    smart_page_decision_t d, retry;
    init(4, 10, 0);
    for (uint32_t t = 0; t <= 4000; t += 200)
        tick(t, &d, true);
    selected      = 3;
    activity      = 4200;
    severities[2] = 3;
    episodes[2]   = 1;
    for (uint32_t t = 4200; t < 14200; t += 200)
        assert(!tick(t, &d, true));
    assert(tick(14200, &d, false) && d.page == 2);
    assert(!s.insertion_used);
    assert(smart_page_choose(&s, 14200, &retry) && retry.page == d.page);
    smart_page_commit(&s, &d, 14200);
    selected = d.page;
    activity = 14200;
    assert(!smart_page_prepare(&s, 10, selected, activity, true, 15000));
    assert(!smart_page_choose(&s, 100000, &d));
    assert(smart_page_prepare(&s, 10, selected, activity, false, 100000));
    for (uint32_t t = 100000; t < 110000; t += 200)
        assert(!tick(t, &d, true));
    assert(tick(110000, &d, true));
}
static void approach_and_suppression(void)
{
    smart_page_decision_t d;
    init(4, 60, 0);
    s.config[1].has_high = true;
    s.config[1].high     = 110;
    for (uint32_t t = 0; t <= 5000; t += 200)
    {
        values[1]    = 105 + t / 1000.0;
        bool changed = tick(t, &d, true);
        assert(changed == (t == 5000));
        if (changed)
            assert(d.reason == SMART_REASON_APPROACH && d.page == 1 && d.early);
    }
    init(4, 60, 0);
    s.config[1].has_low = true;
    s.config[1].low     = 20;
    for (uint32_t t = 0; t <= 5000; t += 200)
    {
        values[1]    = 25 - t / 1000.0;
        bool changed = tick(t, &d, true);
        assert(changed == (t == 5000));
        if (changed)
            assert(d.reason == SMART_REASON_APPROACH);
    }
    init(4, 10, 0);
    s.config[1].has_high = true;
    s.config[1].high     = 110;
    suppressed[1]        = true;
    severities[1]        = 0;
    episodes[1]          = 1;
    s.config[1].has_high = false; /* The projection excludes this acknowledged rule. */
    values[1]            = 112;
    for (uint32_t t = 0; t <= 30000; t += 200)
    {
        bool changed = tick(t, &d, true);
        if (changed)
            assert(d.reason == SMART_REASON_ORDINARY);
    }
    /* No guessed approach heuristic for a custom PID, even with a real limit. */
    init(4, 60, 0);
    s.config[3].has_high = true;
    s.config[3].high     = 110;
    values[3]            = 115;
    for (uint32_t t = 0; t <= 10000; t += 200)
        assert(!tick(t, &d, true));
    severities[3] = 3;
    episodes[3]   = 9;
    assert(tick(10200, &d, true) && d.page == 3 && d.reason == SMART_REASON_ALERT);
}
static void freshness_context_and_trend(void)
{
    smart_page_decision_t d;
    init(4, 60, 0);
    severities[2] = 3;
    episodes[2]   = 1;
    valid[2]      = false;
    for (uint32_t t = 0; t <= 10000; t += 200)
        assert(!tick(t, &d, true));
    valid[2] = true;
    assert(tick(10200, &d, true) && d.page == 2);
    assert(s.pids[0].generation == 1);
    valid[2] = false;
    tick(10400, &d, true);
    generations[2] = 2;
    valid[2]       = true;
    tick(10600, &d, true);
    assert(s.pids[0].generation == 1 && s.pids[2].generation == 2);
    /* Repeated evaluation of one cached observation is not persistence. */
    init(4, 60, 0);
    values[1]            = 108;
    s.config[1].has_high = true;
    s.config[1].high     = 110;
    smart_page_observe(&s, 1, true, 108, 0, 1, 0, false, 0);
    for (uint32_t t = 0; t <= 6000; t += 200)
        assert(!smart_page_choose(&s, t, &d));
    init(4, 60, 0);
    values[1] = 40;
    for (uint32_t t = 0; t <= 20000; t += 200)
    {
        values[1] = 40 + t / 2500.0;
        tick(t, &d, true);
    }
    assert(s.phase == 1 && s.pids[1].reason == SMART_REASON_WARMUP);
    /* At normal context, a sustained rise may prefer a page only at the interval. */
    init(4, 15, 0);
    values[1]          = 80;
    s.pages[1].pids[0] = 3;
    s.pages[3].pids[0] = 1;
    for (uint32_t t = 0; t <= 15000; t += 200)
    {
        values[1]    = 80 + t / 2500.0;
        bool changed = tick(t, &d, true);
        assert(changed == (t == 15000));
        if (changed)
            assert(d.page == 3 && d.reason == SMART_REASON_TREND && !d.early);
    }
    init(4, 60, 0);
    valid[0]  = false;
    values[1] = 40;
    for (uint32_t t = 0; t <= 20000; t += 200)
        tick(t, &d, true);
    assert(s.phase == 0 && s.pids[1].rank == 0);
}
static void fairness_and_wrap(void)
{
    const unsigned        intervals[] = {5, 10, 15, 30, 60};
    smart_page_decision_t d;
    for (unsigned k = 0; k < sizeof(intervals) / sizeof(*intervals); ++k)
    {
        uint32_t start = UINT32_MAX - 12000;
        init(SMART_PAGE_PAGES, intervals[k], start);
        uint32_t last[SMART_PAGE_PAGES] = {0};
        uint32_t duration               = 5U * 2 * SMART_PAGE_PAGES * intervals[k] * 1000;
        uint32_t prior                  = start;
        for (uint32_t elapsed = 0; elapsed <= duration; elapsed += 200)
        {
            for (unsigned i = 0; i < SMART_PAGE_PAGES; ++i)
            {
                severities[i] = i == (elapsed / 3000) % SMART_PAGE_PAGES ? 3 : 0;
                episodes[i]   = elapsed / 3000 + 1;
            }
            if (tick(start + elapsed, &d, true))
            {
                assert(start + elapsed - prior >= SMART_PAGE_MIN_VIEW_MS);
                prior = start + elapsed;
                assert(d.page < SMART_PAGE_PAGES);
                last[d.page] = elapsed;
            }
            for (unsigned i = 0; i < SMART_PAGE_PAGES; ++i)
                assert(elapsed - last[i] <= 2U * SMART_PAGE_PAGES * intervals[k] * 1000);
        }
    }
    init(1, 10, 0);
    assert(!smart_page_choose(&s, 100000, &d));
    init(4, 10, 0);
    assert(!smart_page_fallback(&s, 9999, &d));
    assert(smart_page_fallback(&s, 10000, &d) && d.page == 1 && !d.priority);
    smart_page_commit(&s, &d, 10000);
    assert(!smart_page_fallback(&s, 10000, &d));
    assert(!smart_page_prepare(&s, 0, 0, 0, false, 0));
}
static void visible_priority_dual_and_runtime(void)
{
    smart_page_decision_t d;
    init(4, 10, 0);
    /* A weaker alternate must not interrupt the ordinary cursor when the
     * strongest alert is already visible. A dual page is not additive. */
    severities[0] = 3;
    episodes[0]   = 1;
    severities[2] = 2;
    episodes[2]   = 2;
    s.pages[2]    = (smart_page_definition_t){2, {2, 3}};
    severities[3] = 2;
    episodes[3]   = 3;
    for (uint32_t t = 0; t <= 10000; t += 200)
    {
        bool changed = tick(t, &d, true);
        assert(changed == (t == 10000));
        if (changed)
            assert(d.page == 1 && d.reason == SMART_REASON_ORDINARY);
    }
    /* Independent eligible warning wins even when another condition on the
     * same PID has suppressed the lower heuristics. */
    init(4, 60, 0);
    suppressed[2] = true;
    severities[2] = 3;
    episodes[2]   = 5;
    for (uint32_t t = 0; t <= 5000; t += 200)
    {
        bool changed = tick(t, &d, true);
        assert(changed == (t == 5000));
        if (changed)
            assert(d.page == 2 && d.reason == SMART_REASON_ALERT);
    }
    /* A frozen run-time counter is not evidence that the engine is running. */
    init(4, 60, 0);
    s.config[0].rule.kind = SMART_METRIC_RUNTIME;
    values[0]             = 100;
    values[1]             = 40;
    for (uint32_t t = 0; t <= 12000; t += 200)
        tick(t, &d, true);
    assert(s.phase == 0 && s.pids[1].rank == 0);
    for (uint32_t t = 12200; t <= 15000; t += 200)
    {
        values[0] = 100 + t / 1000.0;
        tick(t, &d, true);
    }
    assert(s.phase == 1 && s.pids[1].reason == SMART_REASON_WARMUP);
}

static void persistent_revisit_cooldown(void)
{
    smart_page_decision_t d;
    init(4, 10, 0);
    severities[2] = 3;
    episodes[2]   = 1;
    for (uint32_t t = 0; t <= 70000; t += 200)
    {
        bool changed = tick(t, &d, true);
        if (t == 5000)
            assert(changed && d.page == 2 && d.early);
        if (changed && d.priority && t > 5000)
        {
            /* Ordinary visits may show it more often; priority reinsertion
             * cannot bypass the same-interest cooldown. */
            assert(t - 5000 >= 30000);
        }
        if (t == 5000)
            assert(s.pids[2].boosted_rank == 43);
        if (t == 25000)
            assert(changed && d.page == 2 && !d.priority);
        if (t == 45000)
            assert(changed && d.page == 0 && !d.priority);
    }
}

static void catalog(void)
{
    pid_numeric_decoder_t coolant =
        {.byte_length = 1, .numerator = 1, .denominator = 1, .offset = -40, .minimum = -40, .maximum = 215};
    smart_metric_rule_t rule = smart_metric_rule("engine.coolant", 1, 5, 0x7e8, "degC", &coolant);
    assert(rule.kind == SMART_METRIC_COOLANT && rule.approach == 5);
    coolant.offset = -39;
    assert(smart_metric_rule("engine.coolant", 1, 5, 0x7e8, "degC", &coolant).kind == SMART_METRIC_NONE);
    coolant.offset = -40;
    assert(smart_metric_rule("engine.coolant", 1, 5, 0x7e8, "degF", &coolant).kind == SMART_METRIC_NONE);
    assert(smart_metric_rule("engine.coolant", 1, 5, 0x7e9, "degC", &coolant).kind == SMART_METRIC_NONE);
}
static void shared_alert_projection(void)
{
    static alert_events_t events;
    alert_page_interest_t interests[32];
    alert_events_init(&events, 1);
    alert_events_observe(&events, 0, 3, false, "Low", "degC", 10, 20, 0, 0);
    alert_events_observe(&events, 1, 3, false, "High", "degC", 110, 105, 0, 0);
    alert_events_page_interest(&events, 0, interests);
    assert(interests[0].severity == 3 && interests[1].severity == 3);
    assert(alert_events_ack(&events, 0, events.episodes[0].current.episode, events.boot, 0, 0));
    alert_events_page_interest(&events, 1, interests);
    assert(interests[0].suppressed && interests[0].severity == 0);
    assert(!interests[1].suppressed && interests[1].severity == 3);
    alert_events_observe(&events, 1, 3, true, "High", "degC", 110, 105, 0, 2);
    alert_events_page_interest(&events, 2, interests);
    assert(!interests[1].suppressed && interests[1].severity == 0);
    alert_events_observe(&events, 0, 4, false, "Low", "degC", 5, 10, 3, 3);
    alert_events_page_interest(&events, 3, interests);
    assert(!interests[0].suppressed && interests[0].severity == 4);
}

int main(void)
{
    typical_and_early();
    manual_pause_and_lock();
    approach_and_suppression();
    freshness_context_and_trend();
    fairness_and_wrap();
    visible_priority_dual_and_runtime();
    catalog();
    shared_alert_projection();
    persistent_revisit_cooldown();
    printf("Smart replay: timing, fairness, context, thresholds, suppression, source isolation and wrap passed; "
           "state=%zu bytes\n",
           sizeof(s));
    return 0;
}
