#include "temporary_idle.h"
#include <string.h>

void temporary_idle_init(temporary_idle_t *s)
{
    memset(s, 0, sizeof(*s));
}
static bool busy(const temporary_idle_t *s)
{
    return s->phase == IDLE_STARTING || s->phase == IDLE_ACTIVE || s->phase == IDLE_STOPPING ||
           s->phase == IDLE_UNKNOWN;
}
static void unknown(temporary_idle_t *s, idle_reason_t why)
{
    s->phase          = IDLE_UNKNOWN;
    s->reason         = why;
    s->in_flight.kind = IDLE_NO_JOB;
}
bool temporary_idle_start(temporary_idle_t *s, idle_request_t r, idle_definition_t d, idle_context_t c)
{
    if (!r.operation || r.operation <= s->last_operation || busy(s))
        return false;
    s->last_operation = r.operation;
    s->request        = r;
    s->definition     = d;
    s->reason         = IDLE_INVALID;
    s->phase          = IDLE_REJECTED;
    if (!d.qualified)
    {
        s->reason = IDLE_UNSUPPORTED;
        return false;
    }
    if (!d.rpm_step || d.minimum_rpm < 400 || d.maximum_rpm > 3000 || d.minimum_rpm > d.maximum_rpm || !d.refresh_ms ||
        d.refresh_ms > 10000 || !d.io_timeout_ms || d.io_timeout_ms > 10000 ||
        d.lease_timeout_ms <= d.refresh_ms + d.io_timeout_ms || d.lease_timeout_ms > 60000 || d.maximum_seconds > 600 ||
        r.preset < 1 || r.preset > 2 || r.rpm < d.minimum_rpm || r.rpm > d.maximum_rpm ||
        (r.rpm - d.minimum_rpm) % d.rpm_step || r.seconds < 10 || r.seconds > d.maximum_seconds)
        return false;
    if (!c.connected || !r.link || !r.revision || c.link != r.link || c.revision != r.revision)
    {
        s->reason = IDLE_CONTEXT;
        return false;
    }
    if (!c.guards_fresh_and_met)
    {
        s->reason = IDLE_GUARD;
        return false;
    }
    s->phase          = IDLE_STARTING;
    s->reason         = IDLE_OK;
    s->stop_requested = false;
    s->in_flight.kind = IDLE_NO_JOB;
    return true;
}
bool temporary_idle_stop(temporary_idle_t *s, uint32_t operation)
{
    if (s->request.operation != operation || !busy(s) ||
        (s->phase == IDLE_UNKNOWN && !s->definition.release_idempotent))
        return false;
    s->stop_requested = true;
    if (s->phase == IDLE_UNKNOWN)
        s->phase = IDLE_STOPPING;
    return true;
}
uint16_t temporary_idle_remaining(const temporary_idle_t *s, uint32_t now)
{
    if (s->phase != IDLE_ACTIVE)
        return 0;
    uint32_t elapsed = now - s->active_at, total = (uint32_t)s->request.seconds * 1000;
    return elapsed >= total ? 0 : (uint16_t)((total - elapsed + 999) / 1000);
}
idle_job_t temporary_idle_next(temporary_idle_t *s, idle_context_t c, uint32_t now)
{
    idle_job_t none = {0};
    if (!busy(s) || s->phase == IDLE_UNKNOWN)
        return none;
    if (!c.connected || c.link != s->request.link || c.revision != s->request.revision)
    {
        unknown(s, IDLE_CONTEXT);
        return none;
    }
    if (s->in_flight.kind)
    {
        if (now - s->sent_at >= s->definition.io_timeout_ms)
            unknown(s, IDLE_TIMEOUT);
        return none;
    }
    if (s->phase == IDLE_ACTIVE && !s->stop_requested && temporary_idle_remaining(s, now) && c.guards_fresh_and_met &&
        now - s->refreshed_at >= s->definition.refresh_ms + s->definition.io_timeout_ms)
    {
        unknown(s, IDLE_TIMEOUT);
        return none;
    }
    if (!c.guards_fresh_and_met)
        s->stop_requested = true;
    if (s->phase == IDLE_STARTING && s->stop_requested)
    {
        s->phase = IDLE_RESTORED;
        return none;
    }
    idle_job_kind_t kind = IDLE_NO_JOB;
    if (s->phase == IDLE_ACTIVE && !temporary_idle_remaining(s, now))
        s->stop_requested = true;
    if (s->stop_requested || s->phase == IDLE_STOPPING)
    {
        s->phase = IDLE_STOPPING;
        kind     = IDLE_RELEASE;
    }
    else if (s->phase == IDLE_STARTING)
        kind = IDLE_START;
    else if (now - s->refreshed_at >= s->definition.refresh_ms)
        kind = IDLE_REFRESH;
    if (!kind)
        return none;
    s->sent_at = now;
    s->in_flight =
        (idle_job_t){.kind = kind, .operation = s->request.operation, .ticket = ++s->ticket, .rpm = s->request.rpm};
    return s->in_flight;
}
bool temporary_idle_complete(temporary_idle_t *s, idle_job_t job, bool verified, idle_context_t c, uint32_t now)
{
    if (!job.kind || job.kind != s->in_flight.kind || job.operation != s->in_flight.operation ||
        job.ticket != s->in_flight.ticket)
        return false;
    s->in_flight.kind = IDLE_NO_JOB;
    if (!c.connected || c.link != s->request.link || c.revision != s->request.revision)
    {
        unknown(s, IDLE_CONTEXT);
        return false;
    }
    if (!c.guards_fresh_and_met)
        s->stop_requested = true;
    if (now - s->sent_at >= s->definition.io_timeout_ms)
    {
        unknown(s, IDLE_TIMEOUT);
        return false;
    }
    if (!verified)
    {
        unknown(s, IDLE_RESPONSE);
        return true;
    }
    if (job.kind == IDLE_RELEASE)
        s->phase = IDLE_RESTORED;
    else
    {
        if (job.kind == IDLE_START)
            s->active_at = now;
        s->refreshed_at = now;
        s->phase        = IDLE_ACTIVE;
    }
    return true;
}
static uint32_t le32(const uint8_t *p)
{
    return (uint32_t)p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
bool temporary_idle_decode(const uint8_t *b, size_t n, uint8_t *command, idle_request_t *out)
{
    if (!b || !out || !command || n != 24 || b[0] != 1 || b[1] < 1 || b[1] > 3 || b[3] || le32(b + 20))
        return false;
    idle_request_t r = {.preset    = b[2],
                        .operation = le32(b + 4),
                        .revision  = le32(b + 8),
                        .link      = le32(b + 12),
                        .rpm       = b[16] | (uint16_t)b[17] << 8,
                        .seconds   = b[18] | (uint16_t)b[19] << 8};
    if (!r.operation || !r.revision || !r.link)
        return false;
    if (b[1] == 1)
    {
        if (r.preset < 1 || r.preset > 2 || r.rpm < 400 || r.rpm > 3000 || r.seconds < 10 || r.seconds > 600)
            return false;
    }
    else if (r.preset || r.rpm || r.seconds)
        return false;
    *out     = r;
    *command = b[1];
    return true;
}

bool temporary_idle_reconcile(temporary_idle_t *s, idle_context_t c, bool verified)
{
    if (s->phase != IDLE_UNKNOWN || !s->definition.qualified || !verified || !c.connected || !c.link ||
        c.revision != s->request.revision)
        return false;
    s->phase  = IDLE_RESTORED;
    s->reason = IDLE_OK;
    return true;
}
