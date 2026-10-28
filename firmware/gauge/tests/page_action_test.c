#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include "page_action.h"
#include "page_save_policy.h"
static bool swipe(page_action_t *s, uint32_t end)
{
    bool fired = false;
    page_action_press(s, 120, 160, end - 100);
    page_action_move(s, 120, 125);
    assert(page_action_release(s, 120, 100, end, &fired));
    return fired;
}
int main(void)
{
    page_action_t        s;
    page_action_config_t cfg = {.enabled = true, .target_page = 2, .count = 3, .window_ms = 5000};
    page_action_init(&s, cfg);
    assert(!swipe(&s, 1000) && !swipe(&s, 3000) && swipe(&s, 6000));  // inclusive window
    assert(!swipe(&s, 6200));
    assert(s.progress == 0);  // cooldown consumes but cannot fire
    page_action_reset(&s);
    assert(!swipe(&s, 6400));  // reset cannot bypass cooldown
    assert(!swipe(&s, 11000) && !swipe(&s, 12000) && swipe(&s, 13000));
    page_action_init(&s, cfg);
    assert(!swipe(&s, 1000) && !swipe(&s, 3000) && !swipe(&s, 6001));
    assert(s.progress == 1);  // expired sequence starts again
    page_action_reset(&s);
    assert(!swipe(&s, 7000) && s.progress == 1);
    bool fired = true;
    assert(!page_action_release(&s, 120, 100, 7050, &fired) && !fired && !s.progress);  // orphan release
    page_action_press(&s, 120, 160, 8000);
    page_action_move(&s, 155, 130);
    assert(!page_action_release(&s, 120, 100, 8100, &fired));  // diagonal excursion cannot be repaired
    page_action_press(&s, 120, 160, 9000);
    assert(!page_action_release(&s, 120, 100, 9850, &fired));  // hold
    page_action_press(&s, 120, 160, 10000);
    assert(!page_action_release(&s, 120, 100, 10020, &fired));  // noise
    page_action_press(&s, 120, 160, 11000);
    assert(!page_action_release(&s, 120, 180, 11100, &fired));  // reverse
    assert(!swipe(&s, 12000));
    page_action_reset(&s);
    assert(!swipe(&s, 12100) && s.progress == 1);
    page_action_tick(&s, 17101);
    assert(!s.progress);
    page_action_init(&s, cfg);
    assert(!swipe(&s, UINT32_MAX - 500) && !swipe(&s, 100) && swipe(&s, 500));  // clock wrap
    cfg.count = 1;
    page_action_init(&s, cfg);
    page_action_press(&s, 120, 160, 1000);
    assert(!page_action_release(&s, 120, 100, 1100, &fired));
    cfg.count     = 5;
    cfg.window_ms = 2000;
    page_action_init(&s, cfg);
    for (unsigned i = 0; i < 5; i++)
        assert(swipe(&s, 1000 + i * 200) == (i == 4));
    cfg.count     = 3;
    cfg.window_ms = 10000;
    page_action_init(&s, cfg);
    assert(!swipe(&s, 1000));
    assert(
        !page_cycle_due(5, 3, true, s.progress == 0, 1000, 7000));  // short cycle cannot interrupt long gesture window
    page_action_tick(&s, 11001);
    assert(page_cycle_due(5, 3, true, s.progress == 0, 1000, 11001));  // expiry resumes cycling
    page_action_press(&s, 120, 160, UINT32_MAX - 400);
    page_action_tick(&s, 401);
    assert(!s.pressed && !s.progress); /* Lost release, including wrap, never fires. */
    assert(!page_action_release(&s, 120, 100, 500, &fired) && !fired);
    page_cycle_policy_t cycle = {0};
    assert(!page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, 1000));
    assert(!page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, 5999));
    assert(page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, 6000));
    for (page_cycle_reason_t reason = PAGE_CYCLE_TOUCH; reason <= PAGE_CYCLE_SHOWCASE; ++reason)
    {
        assert(!page_cycle_step(&cycle, 5, 3, true, false, reason, 0, 7000));
        assert(!page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, 8000));
        assert(!page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, 12999));
        assert(page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, 13000));
    }
    assert(!page_cycle_step(&cycle, 10, 3, true, false, PAGE_CYCLE_RUNNING, 0, 14000));
    assert(page_cycle_step(&cycle, 10, 3, true, false, PAGE_CYCLE_RUNNING, 0, 24000));
    assert(!page_cycle_step(&cycle, 10, 3, true, false, PAGE_CYCLE_RUNNING, 24001, 24001));
    assert(!page_cycle_step(&cycle, 10, 3, true, true, PAGE_CYCLE_RUNNING, 24001, 35000));
    assert(cycle.reason == PAGE_CYCLE_MAINTENANCE);
    assert(!page_cycle_step(&cycle, 10, 3, false, false, PAGE_CYCLE_RUNNING, 24001, 36000));
    assert(cycle.reason == PAGE_CYCLE_OWNER);
    assert(!page_cycle_step(&cycle, 0, 3, true, false, PAGE_CYCLE_RUNNING, 24001, 37000));
    assert(cycle.reason == PAGE_CYCLE_OFF);
    assert(!page_cycle_step(&cycle, 5, 1, true, false, PAGE_CYCLE_RUNNING, 24001, 38000));
    assert(cycle.reason == PAGE_CYCLE_SINGLE);
    cycle = (page_cycle_policy_t){0};
    assert(!page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, UINT32_MAX - 1000));
    assert(page_cycle_step(&cycle, 5, 3, true, false, PAGE_CYCLE_RUNNING, 0, 3999));
    puts("PASS: page actions, lost release, cycle pause/resume, interaction and wrap");
}
