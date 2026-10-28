#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include "orientation_policy.h"

static void settle(orientation_policy_t *p, uint32_t start, int x, int y, int z, uint8_t expected)
{
    for (uint32_t t = 0; t <= 1000; t += 100)
        (void)orientation_policy_observe(p, start + t, x, y, z);
    assert(p->applied == expected);
}
int main(void)
{
    orientation_policy_t p;
    orientation_policy_init(&p, 0);
    settle(&p, 0, 1000, 0, 0, 2);
    settle(&p, 1100, 0, -1000, 0, 3);
    settle(&p, 2200, -1000, 0, 0, 0); /* Labelled USB-down reference. */
    settle(&p, 3300, 0, 1000, 0, 1);  /* Labelled USB-left reference. */
    /* Diagonal noise, flat poses and acceleration spikes never rotate. */
    for (uint32_t t = 4400; t < 8000; t += 100)
    {
        assert(!orientation_policy_observe(&p, t, 700, -700, 0));
        assert(!orientation_policy_observe(&p, t, 0, 0, 1000));
        assert(!orientation_policy_observe(&p, t, 1800, 0, 0));
        assert(!orientation_policy_observe(&p, t, INT32_MIN, 0, 0));
    }
    assert(p.applied == 1);
    assert(!orientation_policy_observe(&p, 8100, 1000, 0, 0));
    assert(!orientation_policy_observe(&p, 8600, 1000, 0, 0));
    assert(!orientation_policy_healthy(&p, 9601));
    assert(!orientation_policy_observe(&p, 10000, 1000, 0, 0));
    assert(!orientation_policy_observe(&p, 10700, 1000, 0, 0));
    assert(orientation_policy_observe(&p, 10800, 1000, 0, 0));
    orientation_policy_init(&p, 0);
    settle(&p, UINT32_MAX - 500, 1000, 0, 0, 2);
    puts("Orientation policy: cardinal mapping, dwell, jitter, motion, flat, stale and wrap passed");
}
