#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include "display_idle_policy.h"
int main(void)
{
    display_idle_policy_t s = {0};
    assert(display_idle_waiting(&s, false, 0, false, 0));
    display_idle_touch(&s, 1, true);
    assert(!display_idle_waiting(&s, false, 0, false, 15000));
    assert(display_idle_waiting(&s, false, 0, false, 15001));
    assert(!display_idle_waiting(&s, true, 30002, false, 30002));
    assert(!display_idle_waiting(&s, false, 0, false, 90001));
    assert(display_idle_waiting(&s, false, 0, false, 90002));
    assert(!display_idle_waiting(&s, false, 0, true, 100000));
    assert(s.last_data == 30002); /* Priority UI never fabricates data. */
    assert(!display_idle_waiting(&s, true, 99999, false, 99999));
    display_idle_touch(&s, 100000, true);
    assert(!display_idle_waiting(&s, false, 0, false, 114999));
    assert(display_idle_waiting(&s, false, 0, false, 115000));
    assert(!display_idle_waiting(&s, true, 130001, false, 130001));
    assert(!display_idle_waiting(&s, true, 10, false, 130002));
    assert(s.last_data == 130001); /* Retired observations cannot renew age. */
    display_idle_touch(&s, 130003, true);
    assert(!display_idle_waiting(&s, true, 130004, false, 130004));
    assert(!display_idle_waiting(&s, true, 145003, false, 145003));
    s              = (display_idle_policy_t){0};
    uint32_t start = UINT32_MAX - 10000;
    assert(!display_idle_waiting(&s, true, start, false, start));
    assert(!display_idle_waiting(&s, false, 0, false, start + DISPLAY_IDLE_DATA_MS - 1));
    assert(display_idle_waiting(&s, false, 0, false, start + DISPLAY_IDLE_DATA_MS));
    puts("Logo waiting/wake policy, original age, priority preemption and wrap passed");
}
