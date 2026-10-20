#include "vehicle_commands.h"
#include "vehicle_idle_coolant.h"
#include <string.h>

static uint32_t u32(const uint8_t *p)
{
    return p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
static void put32(uint8_t *p, uint32_t n)
{
    for (unsigned i = 0; i < 4; ++i)
        p[i] = (uint8_t)(n >> (8 * i));
}
static unsigned index_of(uint8_t kind)
{
    return kind - 1U;
}
static bool valid_kind(uint8_t kind)
{
    return kind == VEHICLE_IDLE || kind == VEHICLE_ABS_TCS;
}
static bool qualified(vehicle_command_context_t c, uint8_t kind)
{
    unsigned mask = 1U << index_of(kind);
    return c.connected && c.revision && c.link && c.identity && (c.qualified_mask & mask) && (c.identity_mask & mask);
}
static bool coolant_fresh(vehicle_command_context_t c, uint32_t now)
{
    return vehicle_idle_coolant_allows(c.coolant_configured, c.coolant_valid, c.coolant_c, c.coolant_limit_c,
                                       c.coolant_at, now);
}
static bool fallback_safe(vehicle_command_context_t c)
{
    return vehicle_idle_coolant_fallback_safe(c.coolant_configured, c.coolant_valid, c.coolant_c, c.coolant_limit_c);
}
static bool guarded(const vehicle_commands_t *s, vehicle_command_context_t c, uint8_t kind, uint32_t now)
{
    if (!c.guards_fresh_and_met || !c.guard_at || now - c.guard_at > 2000)
        return false;
    if (kind != VEHICLE_IDLE)
        return true;
    const vehicle_command_state_t *v = &s->states[0];
    return fallback_safe(c) && (!v->timed_fallback || now - v->fallback_at < VEHICLE_IDLE_COOLANT_FALLBACK_MS) &&
           (coolant_fresh(c, now) || v->timed_fallback);
}
static idle_context_t driver_context(const vehicle_commands_t *s,
                                     vehicle_command_context_t c,
                                     uint8_t                   kind,
                                     uint32_t                  now)
{
    return (idle_context_t){c.revision, c.link, c.connected, guarded(s, c, kind, now)};
}
static uint32_t remaining_ms(const vehicle_command_state_t *v, uint32_t now)
{
    uint32_t total = (uint32_t)v->request.seconds * 1000, elapsed = now - v->active_at;
    uint32_t remaining = elapsed < total ? total - elapsed : 0;
    if (v->timed_fallback)
    {
        uint32_t fallback_elapsed   = now - v->fallback_at;
        uint32_t fallback_remaining = fallback_elapsed < VEHICLE_IDLE_COOLANT_FALLBACK_MS
                                          ? VEHICLE_IDLE_COOLANT_FALLBACK_MS - fallback_elapsed
                                          : 0;
        if (fallback_remaining < remaining)
            remaining = fallback_remaining;
    }
    return remaining;
}
static void transition(vehicle_commands_t      *s,
                       uint8_t                  kind,
                       vehicle_command_phase_t  phase,
                       vehicle_command_reason_t reason,
                       uint32_t                 now)
{
    vehicle_command_state_t *v = &s->states[index_of(kind)];
    if (v->phase == phase && v->reason == reason)
        return;
    v->changed_at = now;
    v->phase      = phase;
    v->reason     = reason;
    /* Saturation retains replay fences; it never wraps into apparently older history. */
    if (s->sequence != UINT32_MAX)
        ++s->sequence;
    v->sequence                 = s->sequence;
    s->history[s->history_next] = (vehicle_command_event_t){s->sequence,
                                                            v->request.operation,
                                                            v->request.revision,
                                                            v->request.link,
                                                            now,
                                                            kind,
                                                            (uint8_t)phase,
                                                            (uint8_t)reason,
                                                            0};
    s->history_next             = (s->history_next + 1U) % VEHICLE_COMMAND_HISTORY_SIZE;
    if (s->history_count < VEHICLE_COMMAND_HISTORY_SIZE)
        ++s->history_count;
}
static bool save_marker(vehicle_commands_t *s, vehicle_command_marker_t marker)
{
    if (!s->journal_ready || !s->save || !s->save(&marker, s->save_context))
        return false;
    s->marker = marker;
    return true;
}
void vehicle_commands_init(vehicle_commands_t             *s,
                           uint32_t                        boot,
                           vehicle_command_save_t          save,
                           void                           *context,
                           const vehicle_command_marker_t *restored,
                           bool                            journal_ready)
{
    memset(s, 0, sizeof(*s));
    s->boot          = boot;
    s->save          = save;
    s->save_context  = context;
    s->journal_ready = journal_ready && boot && save;
    if (restored && (restored->mask & ~3U))
        s->journal_ready = false;
    if (restored && s->journal_ready)
        s->marker = *restored;
    for (unsigned i = 0; i < 2; ++i)
    {
        vehicle_command_state_t *v = &s->states[i];
        if (!s->journal_ready || (s->marker.mask & (1U << i)))
        {
            v->phase             = VEHICLE_UNKNOWN;
            v->reason            = s->journal_ready ? VEHICLE_CONTEXT : VEHICLE_STORAGE;
            v->request.kind      = i + 1;
            v->request.operation = s->marker.operation[i];
            v->request.revision  = s->marker.revision[i];
            v->identity          = s->marker.identity[i];
            v->restart_required  = i == 1;
        }
    }
}
bool vehicle_command_decode(const uint8_t *b, size_t n, vehicle_command_request_t *out)
{
    if (!b || !out || n != VEHICLE_COMMAND_WIRE_SIZE || b[0] != 1 || !valid_kind(b[1]) ||
        (b[2] != VEHICLE_START && b[2] != VEHICLE_STOP) || b[22] || b[23])
        return false;
    vehicle_command_request_t r = {.kind      = b[1],
                                   .verb      = b[2],
                                   .preset    = b[3],
                                   .operation = u32(b + 4),
                                   .revision  = u32(b + 8),
                                   .link      = u32(b + 12),
                                   .boot      = u32(b + 16),
                                   .seconds   = b[20] | (uint16_t)b[21] << 8};
    if (!r.operation || !r.revision || !r.link || !r.boot)
        return false;
    if (r.kind == VEHICLE_IDLE && r.verb == VEHICLE_START)
    {
        if (r.preset < 1 || r.preset > 2 || r.seconds < 10 || r.seconds > 600)
            return false;
    }
    else if (r.preset || r.seconds)
        return false;
    *out = r;
    return true;
}
static bool valid_request(vehicle_command_request_t r)
{
    uint8_t b[24] = {1, r.kind, r.verb, r.preset};
    put32(b + 4, r.operation);
    put32(b + 8, r.revision);
    put32(b + 12, r.link);
    put32(b + 16, r.boot);
    b[20] = r.seconds;
    b[21] = r.seconds >> 8;
    vehicle_command_request_t ignored;
    return vehicle_command_decode(b, sizeof(b), &ignored);
}
static bool scoped(vehicle_command_state_t *v, vehicle_command_context_t c)
{
    return c.connected && c.revision == v->request.revision && c.link == v->request.link && c.identity == v->identity;
}
bool vehicle_commands_submit(vehicle_commands_t       *s,
                             vehicle_command_request_t r,
                             vehicle_command_context_t c,
                             uint32_t                  now)
{
    if (!valid_request(r) || r.boot != s->boot || r.revision != c.revision || r.link != c.link || !c.connected)
        return false;
    vehicle_command_state_t *v = &s->states[index_of(r.kind)];
    if (r.verb == VEHICLE_STOP)
    {
        if (r.operation != v->request.operation || !scoped(v, c) || v->pending_stop ||
            (v->phase != VEHICLE_STARTING && v->phase != VEHICLE_ACTIVE))
            return false;
        v->pending_stop = true;
        if (s->worker_kind == r.kind && r.kind == VEHICLE_IDLE)
            jeep_idle_driver_cancel(&s->driver.idle);
        if (s->worker_kind == r.kind && r.kind == VEHICLE_ABS_TCS)
            jeep_abs_driver_cancel(&s->driver.abs);
        return true;
    }
    if (r.operation <= s->last_operation || v->pending_start || v->pending_stop || v->phase == VEHICLE_STARTING ||
        v->phase == VEHICLE_ACTIVE || v->phase == VEHICLE_STOPPING || v->phase == VEHICLE_VERIFYING ||
        v->phase == VEHICLE_RESTART_REQUIRED || v->phase == VEHICLE_UNKNOWN || s->sequence == UINT32_MAX)
        return false;
    s->last_operation = r.operation;
    v->request        = r;
    v->identity       = c.identity;
    if (!qualified(c, r.kind) || !s->journal_ready)
    {
        transition(s, r.kind, VEHICLE_REJECTED, s->journal_ready ? VEHICLE_UNQUALIFIED : VEHICLE_STORAGE, now);
        return false;
    }
    bool fresh = r.kind != VEHICLE_IDLE || coolant_fresh(c, now);
    if (!c.guards_fresh_and_met || !c.guard_at || now - c.guard_at > 2000 ||
        (r.kind == VEHICLE_IDLE && (!fallback_safe(c) || ((!fresh || v->timed_fallback) && !r.gauge_confirmed))))
    {
        transition(s, r.kind, VEHICLE_REJECTED, VEHICLE_GUARD, now);
        return false;
    }
    v->timed_fallback = !fresh;
    v->fallback_at    = now;
    if (v->timed_fallback && v->request.seconds > VEHICLE_IDLE_COOLANT_FALLBACK_MS / 1000)
        v->request.seconds = VEHICLE_IDLE_COOLANT_FALLBACK_MS / 1000;
    v->pending_start = true;
    transition(s, r.kind, VEHICLE_STARTING, v->timed_fallback ? VEHICLE_COOLANT_TIMER : VEHICLE_OK, now);
    return true;
}
static void finish(vehicle_commands_t *s, vehicle_command_context_t c, uint32_t now)
{
    uint8_t kind = s->worker_kind;
    if (!kind)
        return;
    vehicle_command_state_t *v = &s->states[index_of(kind)];
    if (kind == VEHICLE_IDLE)
    {
        jeep_idle_driver_t *d = &s->driver.idle;
        if (d->phase == JEEP_IDLE_WORKING)
            return;
        if (d->phase == JEEP_IDLE_CONFIRMED)
        {
            if (d->result == JEEP_IDLE_START_CONFIRMED)
            {
                v->active_at = v->last_report = now;
                transition(s, kind, VEHICLE_ACTIVE, v->timed_fallback ? VEHICLE_COOLANT_TIMER : VEHICLE_OK, now);
            }
            else if (d->result == JEEP_IDLE_ACTIVE_REPORT)
                v->last_report = now;
            else if (d->result == JEEP_IDLE_RETURN_CONFIRMED || d->result == JEEP_IDLE_INACTIVE_REPORT)
                transition(s, kind, VEHICLE_VERIFYING,
                           (v->reason == VEHICLE_GUARD || v->reason == VEHICLE_COOLANT_TIMER) ? v->reason : VEHICLE_OK,
                           now);
            else
                transition(s, kind, VEHICLE_UNKNOWN, VEHICLE_RESPONSE, now);
        }
        else if (d->phase == JEEP_IDLE_CANCELLED && !(s->marker.mask & 1))
            transition(s, kind, VEHICLE_RESTORED, VEHICLE_OK, now);
        else
            transition(s, kind,
                       d->control_possible || d->uncertain || (s->marker.mask & 1) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED,
                       d->error == JEEP_IDLE_ERROR_TIMEOUT ? VEHICLE_TIMEOUT : VEHICLE_RESPONSE, now);
    }
    else
    {
        jeep_abs_driver_t *d = &s->driver.abs;
        if (d->phase == JEEP_ABS_WORKING)
            return;
        if (d->phase == JEEP_ABS_ACKNOWLEDGED)
        {
            v->restart_required = true;
            transition(s, kind, d->request.kind == JEEP_ABS_DISABLE ? VEHICLE_ACTIVE : VEHICLE_RESTART_REQUIRED,
                       VEHICLE_OK, now);
        }
        else if (d->phase == JEEP_ABS_CANCELLED && !(s->marker.mask & 2))
            transition(s, kind, VEHICLE_RESTORED, VEHICLE_OK, now);
        else
            transition(s, kind, (s->marker.mask & 2) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED,
                       d->error == JEEP_IDLE_ERROR_TIMEOUT ? VEHICLE_TIMEOUT : VEHICLE_RESPONSE, now);
    }
    s->worker_kind = 0;
    vehicle_commands_observe(s, c, now);
}
void vehicle_commands_observe(vehicle_commands_t *s, vehicle_command_context_t c, uint32_t now)
{
    for (unsigned i = 0; i < 2; ++i)
    {
        uint8_t                  kind = i + 1;
        vehicle_command_state_t *v    = &s->states[i];
        bool evidence = qualified(c, kind) && c.verified_operation[i] == v->request.operation && c.proof_at[i] &&
                        now - c.proof_at[i] <= 2000 && (uint32_t)(c.proof_at[i] - v->changed_at) > 0 &&
                        (uint32_t)(c.proof_at[i] - v->changed_at) < 0x80000000U &&
                        (i == 0 ? c.normal_idle_verified : c.abs_reset_verified);
        if (!s->journal_ready)
            continue;
        /* Only independent same-vehicle evidence can reconcile a journal after a
         * link/reboot. The caller must additionally bind its identity verifier. */
        if (!s->worker_kind && (s->marker.mask & (1U << i)) && evidence && c.revision == s->marker.revision[i] &&
            c.identity == s->marker.identity[i] &&
            (v->phase == VEHICLE_VERIFYING || v->phase == VEHICLE_UNKNOWN ||
             (i == 1 && (v->phase == VEHICLE_ACTIVE || v->phase == VEHICLE_RESTART_REQUIRED))))
        {
            vehicle_command_marker_t m = s->marker;
            m.mask &= ~(1U << i);
            if (save_marker(s, m))
            {
                v->restart_required = v->pending_start = v->pending_stop = false;
                transition(s, kind, VEHICLE_RESTORED,
                           (v->reason == VEHICLE_GUARD || v->reason == VEHICLE_COOLANT_TIMER) ? v->reason : VEHICLE_OK,
                           now);
            }
            else
                transition(s, kind, VEHICLE_UNKNOWN, VEHICLE_STORAGE, now);
        }
        if (kind == VEHICLE_IDLE && (v->phase == VEHICLE_STARTING || v->phase == VEHICLE_ACTIVE) &&
            qualified(c, kind) && scoped(v, c) && !coolant_fresh(c, now) && fallback_safe(c) && !v->timed_fallback)
        {
            v->timed_fallback = true;
            v->fallback_at    = now;
            transition(s, kind, v->phase, VEHICLE_COOLANT_TIMER, now);
        }
        if (v->phase == VEHICLE_UNAVAILABLE && qualified(c, kind))
            transition(s, kind, VEHICLE_READY, VEHICLE_OK, now);
        if (v->phase == VEHICLE_ACTIVE || v->phase == VEHICLE_STARTING || v->phase == VEHICLE_STOPPING ||
            v->phase == VEHICLE_VERIFYING)
        {
            if (!scoped(v, c) || !qualified(c, kind))
            {
                v->pending_start = v->pending_stop = false;
                transition(s, kind, (s->marker.mask & (1U << i)) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED, VEHICLE_CONTEXT,
                           now);
            }
            else if (v->phase == VEHICLE_ACTIVE && (!guarded(s, c, kind, now) || (i == 0 && remaining_ms(v, now) == 0)))
            {
                v->pending_stop = true;
                if (!guarded(s, c, kind, now))
                    transition(s, kind, VEHICLE_ACTIVE,
                               kind == VEHICLE_IDLE && v->timed_fallback && fallback_safe(c) &&
                                       c.guards_fresh_and_met && c.guard_at && now - c.guard_at <= 2000
                                   ? VEHICLE_COOLANT_TIMER
                                   : VEHICLE_GUARD,
                               now);
            }
            else if (kind == VEHICLE_IDLE && v->phase == VEHICLE_STARTING && !guarded(s, c, kind, now))
            {
                if (v->pending_start && s->worker_kind != kind)
                {
                    v->pending_start = false;
                    transition(s, kind, VEHICLE_REJECTED, VEHICLE_GUARD, now);
                }
                else
                    transition(s, kind, VEHICLE_STARTING, VEHICLE_GUARD, now);
            }
        }
    }
}
static bool begin_job(vehicle_commands_t       *s,
                      uint8_t                   kind,
                      bool                      stop,
                      bool                      report,
                      vehicle_command_context_t c,
                      uint32_t                  now)
{
    vehicle_command_state_t *v = &s->states[index_of(kind)];
    memset(&s->driver, 0, sizeof(s->driver));
    bool ok;
    if (kind == VEHICLE_IDLE)
    {
        jeep_idle_request_t r = {.kind      = stop     ? JEEP_IDLE_RETURN
                                              : report ? JEEP_IDLE_REPORT
                                                       : JEEP_IDLE_START_CAPTURED,
                                 .operation = v->request.operation,
                                 .revision  = v->request.revision,
                                 .link      = v->request.link,
                                 .source    = 0};
        ok                    = jeep_idle_driver_begin(&s->driver.idle, r, driver_context(s, c, kind, now), now);
    }
    else
    {
        jeep_abs_request_t r = {.kind      = stop ? JEEP_ABS_RETURN : JEEP_ABS_DISABLE,
                                .operation = v->request.operation,
                                .revision  = v->request.revision,
                                .link      = v->request.link,
                                .source    = 0};
        ok                   = jeep_abs_driver_begin(&s->driver.abs, r, driver_context(s, c, kind, now), now);
    }
    if (!ok)
    {
        transition(s, kind, VEHICLE_UNKNOWN, VEHICLE_CONTEXT, now);
        return false;
    }
    if (kind == VEHICLE_IDLE && (stop || report))
        s->driver.idle.control_possible = true;
    if (kind == VEHICLE_ABS_TCS && stop)
        s->driver.abs.control_possible = true;
    s->worker_kind = kind;
    s->marked      = false;
    if (stop)
        transition(s, kind, VEHICLE_STOPPING,
                   (v->reason == VEHICLE_GUARD || v->reason == VEHICLE_COOLANT_TIMER) ? v->reason : VEHICLE_OK, now);
    return true;
}
jeep_idle_command_t vehicle_commands_next(vehicle_commands_t *s, vehicle_command_context_t c, uint32_t now)
{
    jeep_idle_command_t none = {0};
    vehicle_commands_observe(s, c, now);
    if (!s->worker_kind)
    {
        for (unsigned i = 0; i < 2 && !s->worker_kind; ++i)
        {
            vehicle_command_state_t *v = &s->states[i];
            if (!v->pending_stop)
                continue;
            v->pending_stop = false;
            if (v->pending_start)
            {
                v->pending_start = false;
                transition(s, i + 1, VEHICLE_RESTORED, VEHICLE_OK, now);
            }
            else if (v->phase == VEHICLE_ACTIVE)
                begin_job(s, i + 1, true, false, c, now);
        }
        for (unsigned i = 0; i < 2 && !s->worker_kind; ++i)
        {
            vehicle_command_state_t *v = &s->states[i];
            if (v->pending_start && v->phase == VEHICLE_STARTING)
            {
                v->pending_start = false;
                begin_job(s, i + 1, false, false, c, now);
            }
        }
        vehicle_command_state_t *idle = &s->states[0];
        if (!s->worker_kind && idle->phase == VEHICLE_ACTIVE && now - idle->last_report >= 1000)
            begin_job(s, VEHICLE_IDLE, false, true, c, now);
    }
    if (!s->worker_kind)
        return none;
    uint8_t                  kind = s->worker_kind;
    vehicle_command_state_t *v    = &s->states[index_of(kind)];
    if (!qualified(c, kind) || !scoped(v, c))
    {
        vehicle_commands_transport_failed(s, now);
        return none;
    }
    jeep_idle_command_t command = kind == VEHICLE_IDLE
                                      ? jeep_idle_driver_next(&s->driver.idle, driver_context(s, c, kind, now), now)
                                      : jeep_abs_driver_next(&s->driver.abs, driver_context(s, c, kind, now), now);
    /* The journal is committed before the caller can transmit a control. Reads
     * and adapter routing need no flash writes; Stop also keeps the marker. */
    if (command.text && !strncmp(command.text, "3", 1) && strncmp(command.text, "33", 2))
    {
        vehicle_command_marker_t m = s->marker;
        m.mask |= 1U << index_of(kind);
        m.operation[index_of(kind)] = v->request.operation;
        m.revision[index_of(kind)]  = v->request.revision;
        m.identity[index_of(kind)]  = c.identity;
        if (!s->marked && !save_marker(s, m))
        {
            transition(s, kind, (s->marker.mask & (1U << index_of(kind))) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED,
                       VEHICLE_STORAGE, now);
            s->worker_kind = 0;
            return none;
        }
        s->marked = true;
    }
    if (!command.text)
        finish(s, c, now);
    else if (s->ticket == UINT32_MAX)
    {
        vehicle_commands_transport_failed(s, now);
        return none;
    }
    else
        command.ticket = ++s->ticket;
    return command;
}
bool vehicle_commands_feed(vehicle_commands_t       *s,
                           uint32_t                  ticket,
                           const uint8_t            *bytes,
                           size_t                    n,
                           vehicle_command_context_t c,
                           uint32_t                  now)
{
    if (!s->worker_kind || ticket != s->ticket)
        return false;
    vehicle_command_state_t *v = &s->states[index_of(s->worker_kind)];
    if (!qualified(c, s->worker_kind) || !scoped(v, c))
    {
        vehicle_commands_transport_failed(s, now);
        return false;
    }
    vehicle_commands_observe(s, c, now);
    uint32_t driver_ticket = s->worker_kind == VEHICLE_IDLE ? s->driver.idle.ticket : s->driver.abs.ticket;
    bool     accepted      = s->worker_kind == VEHICLE_IDLE
                                 ? jeep_idle_driver_feed(&s->driver.idle, driver_ticket, bytes, n,
                                                         driver_context(s, c, s->worker_kind, now), now)
                                 : jeep_abs_driver_feed(&s->driver.abs, driver_ticket, bytes, n,
                                                        driver_context(s, c, s->worker_kind, now), now);
    finish(s, c, now);
    return accepted;
}
void vehicle_commands_transport_failed(vehicle_commands_t *s, uint32_t now)
{
    if (!s->worker_kind)
        return;
    uint8_t kind = s->worker_kind;
    transition(s, kind, (s->marker.mask & (1U << index_of(kind))) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED, VEHICLE_CONTEXT,
               now);
    s->states[index_of(kind)].pending_start = s->states[index_of(kind)].pending_stop = false;
    s->worker_kind                                                                   = 0;
}
size_t vehicle_commands_status(const vehicle_commands_t *s,
                               uint8_t                   kind,
                               vehicle_command_context_t c,
                               uint32_t                  now,
                               uint8_t                   out[32])
{
    if (!valid_kind(kind))
        return 0;
    const vehicle_command_state_t *v = &s->states[index_of(kind)];
    memset(out, 0, 32);
    out[0] = 22;
    out[1] = kind;
    out[2] = v->phase;
    out[3] = v->reason;
    put32(out + 4, v->request.operation);
    put32(out + 8, c.revision);
    put32(out + 12, c.link);
    put32(out + 16, s->boot);
    put32(out + 20, v->sequence);
    uint16_t remaining = kind == VEHICLE_IDLE && v->phase == VEHICLE_ACTIVE ? (remaining_ms(v, now) + 999) / 1000 : 0;
    out[24]            = remaining;
    out[25]            = remaining >> 8;
    out[26]            = v->request.preset;
    out[27]            = (qualified(c, kind) && s->journal_ready ? 1U : 0U) | (v->restart_required ? 2U : 0U);
    if (kind == VEHICLE_IDLE && qualified(c, kind) && s->journal_ready)
    {
        if (v->timed_fallback)
            out[27] |= 4U | 8U;
        if (!coolant_fresh(c, now) && fallback_safe(c))
            out[27] |= 8U | 16U;
    }
    put32(out + 28, s->last_operation);
    return 32;
}

bool vehicle_commands_maintenance_allowed(const vehicle_command_state_t states[2], bool configuration)
{
    for (unsigned i = 0; i < 2; ++i)
    {
        vehicle_command_phase_t p = states[i].phase;
        if (p == VEHICLE_STARTING || p == VEHICLE_ACTIVE || p == VEHICLE_STOPPING || p == VEHICLE_VERIFYING ||
            (configuration && (p == VEHICLE_UNKNOWN || p == VEHICLE_RESTART_REQUIRED)))
            return false;
    }
    return true;
}
