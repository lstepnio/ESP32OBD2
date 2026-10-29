#include "vehicle_commands.h"
#include "vehicle_idle_coolant.h"
#include "jeep_idle_parked_test.h"
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
    return kind >= VEHICLE_IDLE && kind <= VEHICLE_BURNOUT;
}
static bool qualified(vehicle_command_context_t c, uint8_t kind)
{
    unsigned mask    = 1U << index_of(kind);
    bool     enabled = ((c.qualified_mask & mask) && (c.identity_mask & mask)) ||
                       (c.parked_idle_test && kind == VEHICLE_IDLE && (c.identity_mask & 1U)) ||
                       (kind != VEHICLE_IDLE && (c.supervised_mask & mask));
    return c.connected && c.revision && c.link && c.identity && enabled;
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
static bool inputs_fresh(vehicle_command_context_t c, uint32_t now)
{
    return c.guards_fresh_and_met && c.guard_at && now - c.guard_at <= 2000;
}
static bool command_inputs(vehicle_command_context_t c, uint8_t kind, uint32_t now, bool start)
{
    unsigned bit = 1U << index_of(kind);
    if (kind != VEHICLE_IDLE && (c.supervised_mask & bit))
        return ((start ? c.supervised_start_mask : c.supervised_run_mask) & bit) && c.supervised_at &&
               now - c.supervised_at <= 2000;
    return inputs_fresh(c, now);
}
static bool guarded(const vehicle_commands_t *s, vehicle_command_context_t c, uint8_t kind, uint32_t now)
{
    if (!command_inputs(c, kind, now, s->states[index_of(kind)].phase != VEHICLE_ACTIVE))
        return false;
    if (kind == VEHICLE_BURNOUT)
        return c.burnout_held && c.burnout_hold_at && now - c.burnout_hold_at <= VEHICLE_BURNOUT_HOLD_MS;
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
    bool recovery = kind == VEHICLE_IDLE && s->driver.idle.request.kind == JEEP_IDLE_RECOVERY_REPORT;
    return (idle_context_t){c.revision, c.link, c.connected,
                            recovery ? inputs_fresh(c, now) : guarded(s, c, kind, now)};
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
bool vehicle_command_marker_decode(const uint8_t *b, size_t n, vehicle_command_marker_t *out)
{
    if (!b || !out || n < 4 || b[2] || b[3] ||
        !((b[0] == 1 && n == 28 && !(b[1] & ~3U)) || (b[0] == 2 && n == VEHICLE_COMMAND_MARKER_SIZE && !(b[1] & ~7U))))
        return false;
    vehicle_command_marker_t marker = {0};
    marker.mask                     = b[1];
    unsigned count                  = b[0] == 1 ? 2 : VEHICLE_COMMAND_KIND_COUNT;
    for (unsigned i = 0; i < count; ++i)
    {
        marker.operation[i] = u32(b + 4 + i * 12);
        marker.revision[i]  = u32(b + 8 + i * 12);
        marker.identity[i]  = u32(b + 12 + i * 12);
        if ((marker.mask & (1U << i)) && (!marker.operation[i] || !marker.revision[i] || !marker.identity[i]))
            return false;
    }
    *out = marker;
    return true;
}
size_t vehicle_command_marker_encode(const vehicle_command_marker_t *marker, uint8_t out[VEHICLE_COMMAND_MARKER_SIZE])
{
    memset(out, 0, VEHICLE_COMMAND_MARKER_SIZE);
    bool extended = (marker->mask & 4U) != 0;
    out[0]        = extended ? 2 : 1;
    out[1]        = marker->mask;
    for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT; ++i)
    {
        put32(out + 4 + i * 12, marker->operation[i]);
        put32(out + 8 + i * 12, marker->revision[i]);
        put32(out + 12 + i * 12, marker->identity[i]);
    }
    return extended ? VEHICLE_COMMAND_MARKER_SIZE : 28U;
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
    if (restored && (restored->mask & ~7U))
        s->journal_ready = false;
    if (restored && s->journal_ready)
        s->marker = *restored;
    for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT; ++i)
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
            if (v->request.operation > s->last_operation)
                s->last_operation = v->request.operation;
        }
    }
}
bool vehicle_command_decode(const uint8_t *b, size_t n, vehicle_command_request_t *out)
{
    if (!b || !out || n != VEHICLE_COMMAND_WIRE_SIZE || (b[0] != (b[1] == VEHICLE_BURNOUT ? 2 : 1)) ||
        !valid_kind(b[1]) || (b[2] != VEHICLE_START && b[2] != VEHICLE_STOP) || b[22] || b[23])
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
    if (r.verb == VEHICLE_OWNER_CONFIRM && (!r.gauge_confirmed || r.kind == VEHICLE_IDLE))
        return false;
    uint8_t b[24] = {r.kind == VEHICLE_BURNOUT ? 2 : 1, r.kind, r.verb == VEHICLE_OWNER_CONFIRM ? VEHICLE_STOP : r.verb,
                     r.preset};
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
    if (r.verb == VEHICLE_OWNER_CONFIRM)
    {
        unsigned bit      = 1U << index_of(r.kind);
        bool     eligible = r.kind != VEHICLE_IDLE && (c.supervised_mask & bit) && r.gauge_confirmed &&
                            qualified(c, r.kind) && command_inputs(c, r.kind, now, true) && !s->worker_kind &&
                            !v->pending_start && !v->pending_stop && r.operation == v->request.operation &&
                            c.identity == v->identity && c.revision == v->request.revision && (s->marker.mask & bit) &&
                            (v->phase == VEHICLE_VERIFYING || v->phase == VEHICLE_RESTART_REQUIRED ||
                             (r.kind == VEHICLE_ABS_TCS && v->phase == VEHICLE_ACTIVE) ||
                             (v->phase == VEHICLE_UNKNOWN && (r.kind == VEHICLE_ABS_TCS || v->release_attempted)));
        if (!eligible)
            return false;
        vehicle_command_marker_t marker = s->marker;
        marker.mask &= ~bit;
        if (!save_marker(s, marker))
            return false;
        v->request.link     = c.link;
        v->request.boot     = s->boot;
        v->restart_required = v->pending_start = v->pending_stop = false;
        transition(s, r.kind, VEHICLE_RESTORED, VEHICLE_OK, now);
        /* Provenance stays distinct from a decoded controller-state proof. */
        s->history[(s->history_next + VEHICLE_COMMAND_HISTORY_SIZE - 1) % VEHICLE_COMMAND_HISTORY_SIZE].reserved = 1;
        return true;
    }
    if (r.verb == VEHICLE_STOP)
    {
        /* Only an explicit release may rebind durable Burnout uncertainty after
         * a reboot/link loss, using the same independently verified controller. */
        bool rebind = r.kind == VEHICLE_BURNOUT && v->phase == VEHICLE_UNKNOWN && qualified(c, r.kind) &&
                      v->identity == c.identity && v->request.revision == c.revision &&
                      v->request.operation == r.operation;
        if (rebind)
        {
            v->request.link = c.link;
            v->request.boot = s->boot;
        }
        if (r.operation != v->request.operation || !scoped(v, c) || v->pending_stop ||
            (v->phase != VEHICLE_STARTING && v->phase != VEHICLE_ACTIVE &&
             !(r.kind == VEHICLE_BURNOUT && v->phase == VEHICLE_UNKNOWN && qualified(c, r.kind))))
            return false;
        v->pending_stop = true;
        if (s->worker_kind == r.kind && r.kind == VEHICLE_IDLE)
            jeep_idle_driver_cancel(&s->driver.idle);
        if (s->worker_kind == r.kind && r.kind != VEHICLE_IDLE)
            jeep_abs_driver_cancel(&s->driver.abs);
        return true;
    }
    if (r.operation <= s->last_operation || v->pending_start || v->pending_stop || v->phase == VEHICLE_STARTING ||
        v->phase == VEHICLE_ACTIVE || v->phase == VEHICLE_STOPPING || v->phase == VEHICLE_VERIFYING ||
        v->phase == VEHICLE_RESTART_REQUIRED || v->phase == VEHICLE_UNKNOWN || s->sequence == UINT32_MAX)
        return false;
    /* Burnout does not overlap another control or uncertain brake state. */
    for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT; ++i)
    {
        if (i == index_of(r.kind) || (r.kind != VEHICLE_BURNOUT && i != 2))
            continue;
        vehicle_command_phase_t phase = s->states[i].phase;
        if (phase == VEHICLE_STARTING || phase == VEHICLE_ACTIVE || phase == VEHICLE_STOPPING ||
            phase == VEHICLE_VERIFYING || phase == VEHICLE_UNKNOWN || phase == VEHICLE_RESTART_REQUIRED)
            return false;
    }
    s->last_operation     = r.operation;
    v->recovery_verifying = false;
    v->release_attempted  = false;
    v->request            = r;
    v->identity           = c.identity;
    if (!qualified(c, r.kind) || !s->journal_ready)
    {
        transition(s, r.kind, VEHICLE_REJECTED, s->journal_ready ? VEHICLE_UNQUALIFIED : VEHICLE_STORAGE, now);
        return false;
    }
    if ((c.parked_idle_test || (c.supervised_mask & (1U << index_of(r.kind)))) && !r.gauge_confirmed)
    {
        transition(s, r.kind, VEHICLE_REJECTED, VEHICLE_GUARD, now);
        return false;
    }
    if (r.kind == VEHICLE_IDLE && c.parked_idle_test && v->request.seconds > JEEP_IDLE_PARKED_MAX_SECONDS)
        v->request.seconds = JEEP_IDLE_PARKED_MAX_SECONDS;
    if (r.kind == VEHICLE_BURNOUT && (!r.gauge_confirmed || !guarded(s, c, r.kind, now)))
    {
        transition(s, r.kind, VEHICLE_REJECTED, VEHICLE_GUARD, now);
        return false;
    }
    if (r.kind == VEHICLE_BURNOUT)
        v->request.seconds = VEHICLE_BURNOUT_MAX_SECONDS;
    bool fresh = r.kind != VEHICLE_IDLE || coolant_fresh(c, now);
    if (!command_inputs(c, r.kind, now, true) ||
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
        if (d->request.kind == JEEP_IDLE_RECOVERY_REPORT)
            v->recovery_at = now; /* Retry from completion, never catch up after slow IO. */
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
            {
                v->recovery_verifying = d->request.kind == JEEP_IDLE_RECOVERY_REPORT;
                transition(s, kind, VEHICLE_VERIFYING,
                           (v->reason == VEHICLE_GUARD || v->reason == VEHICLE_COOLANT_TIMER) ? v->reason : VEHICLE_OK,
                           now);
            }
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
            v->restart_required = kind == VEHICLE_ABS_TCS;
            if (d->request.kind == JEEP_ABS_DISABLE && kind != VEHICLE_BURNOUT)
                v->active_at = now;
            transition(s, kind,
                       d->request.kind == JEEP_ABS_DISABLE ? VEHICLE_ACTIVE
                       : kind == VEHICLE_BURNOUT           ? VEHICLE_VERIFYING
                                                           : VEHICLE_RESTART_REQUIRED,
                       VEHICLE_OK, now);
        }
        else if (d->phase == JEEP_ABS_CANCELLED && !(s->marker.mask & (1U << index_of(kind))))
            transition(s, kind, VEHICLE_RESTORED, VEHICLE_OK, now);
        else
            transition(s, kind, (s->marker.mask & (1U << index_of(kind))) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED,
                       d->error == JEEP_IDLE_ERROR_TIMEOUT ? VEHICLE_TIMEOUT : VEHICLE_RESPONSE, now);
    }
    s->worker_kind = 0;
    vehicle_commands_observe(s, c, now);
}
void vehicle_commands_observe(vehicle_commands_t *s, vehicle_command_context_t c, uint32_t now)
{
    for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT; ++i)
    {
        uint8_t                  kind = i + 1;
        vehicle_command_state_t *v    = &s->states[i];
        /* An inactive recovery report is a short proof window, not a lease
         * that can be combined with unrelated normal RPM much later. */
        if (kind == VEHICLE_IDLE && v->phase == VEHICLE_VERIFYING && v->recovery_verifying &&
            now - v->changed_at >= 5000)
        {
            v->recovery_verifying = false;
            transition(s, kind, VEHICLE_UNKNOWN, VEHICLE_TIMEOUT, now);
            continue;
        }
        bool evidence = qualified(c, kind) && (!v->recovery_verifying || scoped(v, c)) &&
                        c.verified_operation[i] == v->request.operation && c.proof_at[i] &&
                        now - c.proof_at[i] <= 2000 && (uint32_t)(c.proof_at[i] - v->changed_at) > 0 &&
                        (uint32_t)(c.proof_at[i] - v->changed_at) < 0x80000000U &&
                        (i == 0   ? c.normal_idle_verified
                         : i == 1 ? c.abs_reset_verified
                                  : c.burnout_released_verified);
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
        /* Physical release is an intent even when Start's reply was lost. One
         * same-scope Return attempt is allowed; failures never replay it. */
        if (kind == VEHICLE_BURNOUT && (s->marker.mask & 4U) && !v->release_attempted &&
            (v->phase == VEHICLE_UNKNOWN || v->phase == VEHICLE_STARTING) && qualified(c, kind) && scoped(v, c) &&
            (!guarded(s, c, kind, now) || remaining_ms(v, now) == 0))
            v->pending_stop = true;
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
            else if (v->phase == VEHICLE_ACTIVE &&
                     (!guarded(s, c, kind, now) ||
                      ((kind == VEHICLE_IDLE || kind == VEHICLE_BURNOUT) && remaining_ms(v, now) == 0)))
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
            else if ((kind == VEHICLE_IDLE || kind == VEHICLE_BURNOUT) && v->phase == VEHICLE_STARTING &&
                     !guarded(s, c, kind, now))
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
    vehicle_command_state_t *v        = &s->states[index_of(kind)];
    bool                     recovery = kind == VEHICLE_IDLE && report && v->phase == VEHICLE_UNKNOWN;
    memset(&s->driver, 0, sizeof(s->driver));
    bool ok;
    if (kind == VEHICLE_IDLE)
    {
        jeep_idle_request_t r  = {.kind      = stop     ? JEEP_IDLE_RETURN
                                               : report ? (recovery ? JEEP_IDLE_RECOVERY_REPORT : JEEP_IDLE_REPORT)
                                                        : JEEP_IDLE_START_CAPTURED,
                                  .operation = v->request.operation,
                                  .revision  = v->request.revision,
                                  .link      = v->request.link,
                                  .source    = 0};
        idle_context_t      dc = driver_context(s, c, kind, now);
        if (recovery)
            dc.guards_fresh_and_met = inputs_fresh(c, now);
        ok = jeep_idle_driver_begin(&s->driver.idle, r, dc, now);
    }
    else
    {
        jeep_abs_request_t r = {.kind      = stop ? JEEP_ABS_RETURN : JEEP_ABS_DISABLE,
                                .operation = v->request.operation,
                                .revision  = v->request.revision,
                                .link      = v->request.link,
                                .source    = 0,
                                .control   = kind == VEHICLE_BURNOUT ? JEEP_BRAKE_BURNOUT : JEEP_BRAKE_ABS_TCS};
        ok                   = jeep_abs_driver_begin(&s->driver.abs, r, driver_context(s, c, kind, now), now);
    }
    if (!ok)
    {
        transition(s, kind, VEHICLE_UNKNOWN, VEHICLE_CONTEXT, now);
        return false;
    }
    if (kind == VEHICLE_IDLE && (stop || (report && !recovery)))
        s->driver.idle.control_possible = true;
    if (kind != VEHICLE_IDLE && stop)
        s->driver.abs.control_possible = true;
    s->worker_kind = kind;
    s->marked      = false;
    if (stop && kind == VEHICLE_BURNOUT)
        v->release_attempted = true;
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
        for (unsigned priority = 0; priority < VEHICLE_COMMAND_KIND_COUNT && !s->worker_kind; ++priority)
        {
            unsigned                 i = priority == 0 ? 2 : priority - 1;
            vehicle_command_state_t *v = &s->states[i];
            if (!v->pending_stop)
                continue;
            v->pending_stop = false;
            if (v->pending_start)
            {
                v->pending_start = false;
                transition(s, i + 1, VEHICLE_RESTORED, VEHICLE_OK, now);
            }
            else if (v->phase == VEHICLE_ACTIVE || (i == 2 && v->phase == VEHICLE_UNKNOWN))
                begin_job(s, i + 1, true, false, c, now);
        }
        for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT && !s->worker_kind; ++i)
        {
            vehicle_command_state_t *v = &s->states[i];
            if (v->pending_start && v->phase == VEHICLE_STARTING)
            {
                v->pending_start = false;
                begin_job(s, i + 1, false, false, c, now);
            }
        }
        vehicle_command_state_t *idle = &s->states[0];
        if (!s->worker_kind && idle->phase == VEHICLE_UNKNOWN && s->journal_ready && (s->marker.mask & 1) &&
            c.parked_idle_test && qualified(c, VEHICLE_IDLE) && inputs_fresh(c, now) && s->marker.operation[0] &&
            c.revision == s->marker.revision[0] && c.identity == s->marker.identity[0] &&
            (!idle->recovery_attempted || now - idle->recovery_at >= 5000))
        {
            idle->recovery_attempted = true;
            idle->recovery_at        = now;
            idle->request.link       = c.link; /* RAM scope only; retain the durable original operation. */
            begin_job(s, VEHICLE_IDLE, false, true, c, now);
        }
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
        bool release_recorded       = kind == VEHICLE_BURNOUT && s->driver.abs.request.kind == JEEP_ABS_RETURN &&
                                      (s->marker.mask & 4U) && s->marker.operation[2] == m.operation[2] &&
                                      s->marker.revision[2] == m.revision[2] && s->marker.identity[2] == m.identity[2];
        if (!s->marked && !release_recorded && !save_marker(s, m))
        {
            transition(s, kind, (s->marker.mask & (1U << index_of(kind))) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED,
                       VEHICLE_STORAGE, now);
            s->worker_kind = 0;
            return none;
        }
        if (!s->marked && kind == VEHICLE_BURNOUT && s->driver.abs.request.kind == JEEP_ABS_DISABLE)
            v->active_at = now;
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
    if (kind == VEHICLE_IDLE && s->driver.idle.request.kind == JEEP_IDLE_RECOVERY_REPORT)
        s->states[0].recovery_at = now;
    transition(s, kind, (s->marker.mask & (1U << index_of(kind))) ? VEHICLE_UNKNOWN : VEHICLE_REJECTED, VEHICLE_CONTEXT,
               now);
    bool release = kind == VEHICLE_BURNOUT && s->states[2].pending_stop && !s->states[2].release_attempted;
    s->states[index_of(kind)].pending_start = false;
    s->states[index_of(kind)].pending_stop  = release;
    s->worker_kind                          = 0;
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
    uint16_t remaining = (kind == VEHICLE_IDLE || kind == VEHICLE_BURNOUT) && v->phase == VEHICLE_ACTIVE
                             ? (remaining_ms(v, now) + 999) / 1000
                             : 0;
    out[24]            = remaining;
    out[25]            = remaining >> 8;
    out[26]            = v->request.preset;
    if (kind == VEHICLE_BURNOUT)
        out[27] = 8U;
    out[27] |= (qualified(c, kind) && s->journal_ready ? 1U : 0U) | (v->restart_required ? 2U : 0U);
    if (kind == VEHICLE_IDLE && qualified(c, kind) && s->journal_ready)
    {
        if (c.parked_idle_test)
            out[27] |= 8U; /* Reuse gauge-Start-required; phone Stop stays available. */
        if (v->timed_fallback)
            out[27] |= 4U | 8U;
        if (!coolant_fresh(c, now) && fallback_safe(c))
            out[27] |= 8U | 16U;
    }
    put32(out + 28, s->last_operation);
    return 32;
}

bool vehicle_commands_maintenance_allowed(const vehicle_command_state_t states[VEHICLE_COMMAND_KIND_COUNT],
                                          bool                          configuration)
{
    for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT; ++i)
    {
        vehicle_command_phase_t p = states[i].phase;
        if (p == VEHICLE_STARTING || p == VEHICLE_ACTIVE || p == VEHICLE_STOPPING || p == VEHICLE_VERIFYING ||
            (i == 2 && p == VEHICLE_UNKNOWN && states[i].request.operation) ||
            (configuration && (p == VEHICLE_UNKNOWN || p == VEHICLE_RESTART_REQUIRED)))
            return false;
    }
    return true;
}

uint8_t vehicle_commands_ui_flags(uint8_t kind, vehicle_command_context_t c)
{
    if (!valid_kind(kind))
        return 0;
    bool supported = c.parked_idle_test ? kind == VEHICLE_IDLE : (c.qualified_mask & (1U << index_of(kind))) != 0;
    supported |= kind != VEHICLE_IDLE && c.brake_owner_test;
    uint8_t flags = supported && !qualified(c, kind) ? VEHICLE_UI_RECONNECTING : 0;
    if (kind != VEHICLE_IDLE && c.brake_owner_test)
        flags |= VEHICLE_UI_OWNER_TEST;
    if (kind == VEHICLE_IDLE && c.coolant_configured && c.coolant_valid &&
        !vehicle_idle_coolant_fallback_safe(true, true, c.coolant_c, c.coolant_limit_c))
        flags |= VEHICLE_UI_COOLANT_HIGH;
    return flags;
}
