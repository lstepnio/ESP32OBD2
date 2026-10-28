#include "orientation_policy.h"

void orientation_policy_init(orientation_policy_t *p, uint8_t manual)
{
    *p = (orientation_policy_t){.applied = manual <= 3 ? manual : 0};
}
void orientation_policy_hold(orientation_policy_t *p)
{
    p->pending = false;
}
bool orientation_policy_healthy(const orientation_policy_t *p, uint32_t now)
{
    return p->have_sample && (uint32_t)(now - p->last_sample) <= ORIENTATION_STALE_MS;
}
bool orientation_policy_observe(orientation_policy_t *p, uint32_t now, int32_t x, int32_t y, int32_t z)
{
    /* Reject timestamp reversals/gaps before dwell can accumulate across loss. */
    if (!orientation_policy_healthy(p, now))
        p->pending = false;
    p->have_sample = true;
    p->last_sample = now;
    if (x < -2000 || x > 2000 || y < -2000 || y > 2000 || z < -2000 || z > 2000)
    {
        p->pending = false;
        return false;
    }
    int32_t ax = x < 0 ? -x : x, ay = y < 0 ? -y : y;
    int64_t magnitude = (int64_t)x * x + (int64_t)y * y + (int64_t)z * z;
    int32_t major = ax > ay ? ax : ay, minor = ax > ay ? ay : ax;
    /* 0.85..1.15g, enough in-plane gravity, and a 1.5:1 cardinal margin.
     * The dead band around diagonals is deliberate angular hysteresis. */
    if (magnitude < 722500 || magnitude > 1322500 || major < 650 || major * 2 < minor * 3)
    {
        p->pending = false;
        return false;
    }
    /* On this board, USB down is -X and needs the native LCD angle; USB left
     * is +Y and needs 90 degrees. Keep manual angles independent of this map. */
    uint8_t candidate = ay >= ax ? (y >= 0 ? 1 : 3) : (x >= 0 ? 2 : 0);
    if (candidate == p->applied)
    {
        p->pending = false;
        return false;
    }
    if (!p->pending || candidate != p->candidate)
    {
        p->pending         = true;
        p->candidate       = candidate;
        p->candidate_since = now;
        return false;
    }
    if ((uint32_t)(now - p->candidate_since) < ORIENTATION_DWELL_MS)
        return false;
    p->applied = candidate;
    p->pending = false;
    return true;
}
