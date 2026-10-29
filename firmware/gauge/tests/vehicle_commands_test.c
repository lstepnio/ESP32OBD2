#include "vehicle_commands.h"
#include "vehicle_idle_coolant.h"
#include <math.h>
#include "transfer_gate.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

static vehicle_command_context_t context = {.revision             = 7,
                                            .link                 = 9,
                                            .identity             = 11,
                                            .qualified_mask       = 3,
                                            .identity_mask        = 3,
                                            .connected            = true,
                                            .guards_fresh_and_met = true,
                                            .guard_at             = 100,
                                            .coolant_configured   = true,
                                            .coolant_valid        = true,
                                            .coolant_c            = 90,
                                            .coolant_limit_c      = 105,
                                            .coolant_at           = 100};
static vehicle_command_marker_t  durable;
static bool                      storage_ok = true;
static uint32_t                  now;
static bool                      save(const vehicle_command_marker_t *marker, void *unused)
{
    (void)unused;
    if (!storage_ok)
        return false;
    durable = *marker;
    return true;
}
static void init(vehicle_commands_t *s)
{
    now        = 100;
    storage_ok = true;
    memset(&durable, 0, sizeof(durable));
    context = (vehicle_command_context_t){.revision             = 7,
                                          .link                 = 9,
                                          .identity             = 11,
                                          .qualified_mask       = 3,
                                          .identity_mask        = 3,
                                          .connected            = true,
                                          .guards_fresh_and_met = true,
                                          .guard_at             = 100,
                                          .coolant_configured   = true,
                                          .coolant_valid        = true,
                                          .coolant_c            = 90,
                                          .coolant_limit_c      = 105,
                                          .coolant_at           = 100};
    vehicle_commands_init(s, 17, save, NULL, NULL, true);
}
static vehicle_command_request_t request(uint8_t kind, uint8_t verb, uint32_t operation)
{
    return (vehicle_command_request_t){kind, verb, kind == VEHICLE_IDLE && verb == VEHICLE_START ? 1 : 0,  operation, 7,
                                       9,    17,   kind == VEHICLE_IDLE && verb == VEHICLE_START ? 10 : 0, false};
}
static uint32_t exchange(vehicle_commands_t *s, const char *expected, const char *reply)
{
    context.guard_at      = now;
    context.coolant_at    = now;
    jeep_idle_command_t c = vehicle_commands_next(s, context, now++);
    assert(c.text && !strcmp(c.text, expected));
    assert(!vehicle_commands_next(s, context, now).text);
    for (size_t i = 0; i < strlen(reply); ++i)
        assert(vehicle_commands_feed(s, c.ticket, (const uint8_t *)reply + i, 1, context, now));
    ++now;
    return c.ticket;
}
static void route_idle(vehicle_commands_t *s)
{
    exchange(s, "AT SH 7E0", "OK\r\r>");
    exchange(s, "AT FC SH 7E0", "OK\r\r>");
    exchange(s, "AT CRA 7E8", "OK\r\r>");
}
static void start_idle(vehicle_commands_t *s, uint32_t op)
{
    context.guard_at   = now;
    context.coolant_at = now;
    assert(vehicle_commands_submit(s, request(VEHICLE_IDLE, VEHICLE_START, op), context, now));
    route_idle(s);
    exchange(s, "10 92 1", "7E8 02 50 92\r\r>");
    exchange(s, "33 05 1", "7E8 03 73 05 01\r\r>");
    exchange(s, "31 05 06 80 1", "7E8 02 71 05\r\r>");
    assert(durable.mask & 1);
    exchange(s, "33 05 1", "7E8 03 73 05 02\r\r>");
    assert(s->states[0].phase == VEHICLE_ACTIVE);
}
static void abs_job(vehicle_commands_t *s, bool stop, uint32_t op)
{
    assert(vehicle_commands_submit(s, request(VEHICLE_ABS_TCS, stop ? VEHICLE_STOP : VEHICLE_START, op), context, now));
    exchange(s, "AT SH 620", "OK\r\r>");
    exchange(s, "AT FC SH 620", "OK\r\r>");
    exchange(s, "AT CRA 504", "OK\r\r>");
    exchange(s, "10 92 1", "504 02 50 92\r\r>");
    exchange(s, stop ? "32 A4" : "31 A4", stop ? "504 02 72 A4\r\r>" : "504 02 71 A4\r\r>");
}
static void two_actions_share_worker_and_restore_independently(void)
{
    vehicle_commands_t s;
    init(&s);
    abs_job(&s, false, 1);
    assert(s.states[1].phase == VEHICLE_ACTIVE && s.states[1].restart_required);
    start_idle(&s, 2);
    assert(durable.mask == 3);
    abs_job(&s, true, 1);
    assert(s.states[1].phase == VEHICLE_RESTART_REQUIRED);
    assert(!vehicle_commands_maintenance_allowed(s.states, false));
    assert(s.states[0].phase == VEHICLE_ACTIVE);
    assert(!vehicle_commands_submit(&s, request(VEHICLE_ABS_TCS, VEHICLE_START, 3), context, now));
    /* A preexisting normal-state boolean cannot clear a newer operation. */
    context.abs_reset_verified = true;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[1].phase == VEHICLE_RESTART_REQUIRED);
    context.verified_operation[1] = 1;
    context.proof_at[1]           = ++now;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[1].phase == VEHICLE_RESTORED && durable.mask == 1);
    assert(vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_STOP, 2), context, now));
    route_idle(&s);
    exchange(&s, "32 05 1", "7E8 02 72 05\r\r>");
    exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
    assert(s.states[0].phase == VEHICLE_VERIFYING && durable.mask == 1);
    context.normal_idle_verified = true;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].phase == VEHICLE_VERIFYING);
    context.verified_operation[0] = 2;
    context.proof_at[0]           = ++now;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].phase == VEHICLE_RESTORED && !durable.mask);
    assert(vehicle_commands_maintenance_allowed(s.states, true));
    assert(s.history_count > 5);
}
static void gates_expiry_replay_and_journal(void)
{
    vehicle_commands_t s;
    init(&s);
    context.qualified_mask = 0;
    assert(!vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 1), context, now));
    assert(!vehicle_commands_next(&s, context, now).text && !durable.mask);
    context.qualified_mask = 3;
    start_idle(&s, 2);
    now += 10001;
    route_idle(&s);
    exchange(&s, "32 05 1", "7E8 02 72 05\r\r>");
    exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
    assert(s.states[0].phase == VEHICLE_VERIFYING);
    vehicle_commands_t restored;
    vehicle_commands_init(&restored, 18, save, NULL, &durable, true);
    assert(restored.states[0].phase == VEHICLE_UNKNOWN);
    assert(!vehicle_commands_next(&restored, context, now).text);
    assert(!vehicle_commands_submit(&restored, request(VEHICLE_IDLE, VEHICLE_START, 3), context, now));
    context.normal_idle_verified  = true;
    context.verified_operation[0] = 2;
    context.proof_at[0]           = ++now;
    context.identity++;
    vehicle_commands_observe(&restored, context, now);
    assert(restored.states[0].phase == VEHICLE_UNKNOWN);
    context.identity--;
    vehicle_commands_observe(&restored, context, now);
    assert(restored.states[0].phase == VEHICLE_RESTORED);
    init(&s);
    storage_ok = false;
    assert(vehicle_commands_submit(&s, request(VEHICLE_ABS_TCS, VEHICLE_START, 1), context, now));
    exchange(&s, "AT SH 620", "OK\r\r>");
    exchange(&s, "AT FC SH 620", "OK\r\r>");
    exchange(&s, "AT CRA 504", "OK\r\r>");
    exchange(&s, "10 92 1", "504 02 50 92\r\r>");
    assert(!vehicle_commands_next(&s, context, now).text && s.states[1].phase == VEHICLE_REJECTED);
}
static void stale_tickets_negative_and_disconnect(void)
{
    vehicle_commands_t s;
    init(&s);
    start_idle(&s, 1);
    now += 1001;
    route_idle(&s);
    context.guard_at      = now;
    context.coolant_at    = now;
    jeep_idle_command_t c = vehicle_commands_next(&s, context, now++);
    assert(c.text);
    assert(!vehicle_commands_feed(&s, c.ticket - 1, (const uint8_t *)"7E8 03 73 05 01\r\r>", 19, context, now));
    const char *negative = "7E8 03 7F 33 80\r\r>";
    assert(vehicle_commands_feed(&s, c.ticket, (const uint8_t *)negative, strlen(negative), context, now));
    assert(s.states[0].phase == VEHICLE_UNKNOWN && durable.mask == 1);
    assert(!vehicle_commands_next(&s, context, now).text);
    init(&s);
    abs_job(&s, false, 1);
    context.link++;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[1].phase == VEHICLE_UNKNOWN && durable.mask == 2);
    assert(!vehicle_commands_next(&s, context, now).text);
}
static void wire_and_cancel(const uint8_t *fixture)
{
    const uint8_t b[24] = {1, 1, 1, 1, 1, 0, 0, 0, 7, 0, 0, 0, 9, 0, 0, 0, 17, 0, 0, 0, 10, 0, 0, 0};
    assert(!memcmp(b, fixture, sizeof(b)));
    vehicle_command_request_t r;
    assert(vehicle_command_decode(b, sizeof(b), &r));
    assert(!r.gauge_confirmed);
    uint8_t forged[24];
    memcpy(forged, b, sizeof(b));
    forged[22] = 1;
    assert(!vehicle_command_decode(forged, sizeof(forged), &r));
    assert(!vehicle_command_decode(b, sizeof(b) - 1, &r));
    vehicle_commands_t s;
    init(&s);
    assert(vehicle_commands_submit(&s, r, context, now));
    assert(vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_STOP, 1), context, now));
    assert(!vehicle_commands_next(&s, context, now).text && s.states[0].phase == VEHICLE_RESTORED && !durable.mask);
    uint8_t status[32];
    assert(vehicle_commands_status(&s, VEHICLE_IDLE, context, now, status) == 32);
    assert(status[0] == 22 && status[2] == VEHICLE_RESTORED && status[16] == 17 && status[27] == 1);
    assert(sizeof(s) < 2048);
    vehicle_command_state_t maintenance[VEHICLE_COMMAND_KIND_COUNT] = {{.phase = VEHICLE_RESTART_REQUIRED}};
    assert(!vehicle_commands_maintenance_allowed(maintenance, true));
    assert(vehicle_commands_maintenance_allowed(maintenance, false));
    maintenance[0].phase = VEHICLE_UNKNOWN;
    assert(!vehicle_commands_maintenance_allowed(maintenance, true));
    assert(vehicle_commands_maintenance_allowed(maintenance, false));
}
static void stale_guards_identity_and_wrap(void)
{
    vehicle_commands_t s;
    init(&s);
    context.guard_at = 0;
    assert(!vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 1), context, now));
    assert(s.states[0].reason == VEHICLE_GUARD);
    init(&s);
    start_idle(&s, 1);
    context.identity++;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].phase == VEHICLE_UNKNOWN);
    assert(!vehicle_commands_next(&s, context, now).text);
    init(&s);
    now                = UINT32_MAX - 50;
    context.guard_at   = now;
    context.coolant_at = now;
    start_idle(&s, 1);
    now += 10001;
    context.guard_at   = now;
    context.coolant_at = now;
    route_idle(&s);
    exchange(&s, "32 05 1", "7E8 02 72 05\r\r>");
    exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
    assert(s.states[0].phase == VEHICLE_VERIFYING);
    context.normal_idle_verified  = true;
    context.verified_operation[0] = 1;
    context.proof_at[0]           = s.states[0].changed_at;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].phase == VEHICLE_VERIFYING);
    context.proof_at[0] = ++now;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].phase == VEHICLE_RESTORED);
}
static void abs_refuses_ambiguous_negative_and_expired_replies(void)
{
    static const char *const replies[] = {"504 03 7F 31 78\r\r>", "504 03 7F 31 80\r\r>",
                                          "7E8 02 71 A4\r\r>",    "504 02 71 A5\r\r>",
                                          "504 03 71 A4 00\r\r>", "504 02 71 A4\r504 02 71 A4\r\r>"};
    for (unsigned i = 0; i < sizeof(replies) / sizeof(replies[0]); ++i)
    {
        vehicle_commands_t s;
        init(&s);
        assert(vehicle_commands_submit(&s, request(VEHICLE_ABS_TCS, VEHICLE_START, 1), context, now));
        exchange(&s, "AT SH 620", "OK\r\r>");
        exchange(&s, "AT FC SH 620", "OK\r\r>");
        exchange(&s, "AT CRA 504", "OK\r\r>");
        exchange(&s, "10 92 1", "504 02 50 92\r\r>");
        exchange(&s, "31 A4", replies[i]);
        assert(s.states[1].phase == VEHICLE_UNKNOWN && durable.mask == 2);
        assert(!vehicle_commands_next(&s, context, now).text);
    }
    vehicle_commands_t s;
    init(&s);
    assert(vehicle_commands_submit(&s, request(VEHICLE_ABS_TCS, VEHICLE_START, 1), context, now));
    jeep_idle_command_t c = vehicle_commands_next(&s, context, now);
    assert(c.text);
    now += JEEP_IDLE_IO_TIMEOUT_MS;
    assert(!vehicle_commands_feed(&s, c.ticket, (const uint8_t *)"OK\r>", 4, context, now));
    assert(s.states[1].phase == VEHICLE_REJECTED && !durable.mask);
}
static void coolant_limit_selection_and_freshness(void)
{
    config_runtime_t r     = {0};
    double           limit = 0;
    assert(!vehicle_idle_coolant_limit(&r, &limit));
    r.source_count = r.pid_count = r.alert_count = 1;
    r.pids[0]                                    = (runtime_pid_t){.service = 1, .responder = 0x7e8, .obd.pid = 5};
    strcpy(r.pids[0].unit, "°C");
    r.alerts[0] =
        (runtime_alert_t){.above = true, .has_warning = true, .warning = 105, .has_critical = true, .critical = 115};
    assert(vehicle_idle_coolant_limit(&r, &limit) && limit == 105);
    r.alerts[0].has_warning = false;
    assert(vehicle_idle_coolant_limit(&r, &limit) && limit == 115);
    r.alerts[0].has_warning = true;
    r.alerts[0].above       = false;
    assert(!vehicle_idle_coolant_limit(&r, &limit));
    r.alerts[0].above      = true;
    r.sources[0].simulated = true;
    assert(!vehicle_idle_coolant_limit(&r, &limit));
    r.sources[0].simulated = false;
    r.pids[0].source_index = 1;
    assert(!vehicle_idle_coolant_limit(&r, &limit));
    r.pids[0].source_index = 0;
    r.pids[0].responder    = 0x7e9;
    assert(!vehicle_idle_coolant_limit(&r, &limit));
    r.pids[0].responder  = 0x7e8;
    r.alerts[0].warning  = NAN;
    r.alerts[0].critical = INFINITY;
    assert(!vehicle_idle_coolant_limit(&r, &limit));
    assert(vehicle_idle_coolant_allows(true, true, 104.9, 105, 100, 2100));
    assert(!vehicle_idle_coolant_allows(true, true, 105, 105, 100, 100));
    assert(!vehicle_idle_coolant_allows(true, true, 106, 105, 100, 100));
    assert(!vehicle_idle_coolant_allows(true, true, NAN, 105, 100, 100));
    assert(!vehicle_idle_coolant_allows(false, true, 90, 105, 100, 100));
    assert(!vehicle_idle_coolant_allows(true, false, 0, 105, 100, 100));
    assert(!vehicle_idle_coolant_allows(true, true, 90, 105, 100, 2101));
    assert(vehicle_idle_coolant_allows(true, true, 90, 105, UINT32_MAX - 100, 100));
}
static void coolant_guard_refuses_start_and_latches_release(void)
{
    vehicle_commands_t s;
    for (unsigned scenario = 0; scenario < 4; ++scenario)
    {
        init(&s);
        if (scenario == 0)
            context.coolant_configured = false;
        if (scenario == 1)
            context.coolant_valid = false;
        if (scenario == 2)
            context.coolant_c = 105;
        if (scenario == 3)
        {
            now += 2001;
            context.guard_at = now;
        }
        assert(!vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 1), context, now));
        assert(s.states[0].reason == VEHICLE_GUARD && !durable.mask);
        assert(!vehicle_commands_next(&s, context, now).text);
    }
    init(&s);
    context.coolant_configured = false;
    abs_job(&s, false, 1); /* Coolant limits do not change ABS control admission. */
    assert(s.states[1].phase == VEHICLE_ACTIVE);
    for (unsigned scenario = 0; scenario < 3; ++scenario)
    {
        init(&s);
        start_idle(&s, 1);
        if (scenario == 0)
            context.coolant_c = 105;
        if (scenario == 1)
            context.coolant_valid = false;
        if (scenario == 2)
        {
            now += 2001;
            context.guard_at = now;
        }
        vehicle_commands_observe(&s, context, now);
        if (scenario != 0)
        {
            assert(s.states[0].timed_fallback && !s.states[0].pending_stop);
            now += 10000; /* The original ten-second session expires first. */
            context.guard_at = now;
            vehicle_commands_observe(&s, context, now);
        }
        assert(s.states[0].pending_stop);
        /* Recovery of data/temperature cannot cancel a requested release. */
        context.coolant_c     = 90;
        context.coolant_valid = true;
        route_idle(&s);
        exchange(&s, "32 05 1", "7E8 02 72 05\r\r>");
        exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
        assert(s.states[0].phase == VEHICLE_VERIFYING &&
               s.states[0].reason == (scenario == 0 ? VEHICLE_GUARD : VEHICLE_COOLANT_TIMER) && durable.mask == 1);
        assert(!vehicle_commands_next(&s, context, now).text); /* No automatic restart. */
    }
    init(&s);
    assert(vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 1), context, now));
    route_idle(&s);
    exchange(&s, "10 92 1", "7E8 02 50 92\r\r>");
    exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
    exchange(&s, "31 05 06 80 1", "7E8 02 71 05\r\r>");
    context.coolant_c = 106;
    route_idle(&s); /* Heat after Start acknowledgment cancels into Return, not another Start. */
    exchange(&s, "32 05 1", "7E8 02 72 05\r\r>");
    exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
    assert(s.states[0].phase == VEHICLE_VERIFYING && s.states[0].reason == VEHICLE_GUARD && durable.mask == 1);
    init(&s);
    start_idle(&s, 1);
    context.coolant_c = 106;
    route_idle(&s);
    exchange(&s, "32 05 1", "7E8 03 7F 32 80\r\r>");
    assert(s.states[0].phase == VEHICLE_UNKNOWN && durable.mask == 1);
    assert(!vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 2), context, now));
    init(&s);
    assert(vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 1), context, now));
    exchange(&s, "AT SH 7E0", "OK\r\r>");
    context.coolant_c = 106;
    assert(!vehicle_commands_next(&s, context, now).text);
    assert(s.states[0].phase == VEHICLE_RESTORED && !durable.mask);
}
static void coolant_timer_requires_local_start_and_never_extends(void)
{
    vehicle_commands_t        s;
    vehicle_command_request_t r;
    uint8_t                   status[32];
    for (unsigned scenario = 0; scenario < 5; ++scenario)
    {
        init(&s);
        r                     = request(VEHICLE_IDLE, VEHICLE_START, 1);
        r.gauge_confirmed     = true;
        r.seconds             = 600;
        context.coolant_valid = false;
        if (scenario == 1)
            context.coolant_configured = false;
        if (scenario == 2)
        {
            context.coolant_valid = true;
            context.coolant_c     = 105;
        }
        if (scenario == 3)
            context.guards_fresh_and_met = false;
        if (scenario == 4)
        {
            context.coolant_valid = true;
            context.coolant_c     = 106;
            now += 2001;
            context.guard_at = now;
        }
        bool admitted = vehicle_commands_submit(&s, r, context, now);
        assert(admitted == (scenario == 0));
        if (admitted)
        {
            assert(s.states[0].timed_fallback && s.states[0].request.seconds == 60);
            vehicle_commands_status(&s, VEHICLE_IDLE, context, now, status);
            assert((status[27] & 29) == 29 && status[3] == VEHICLE_COOLANT_TIMER);
        }
    }
    init(&s);
    r                     = request(VEHICLE_IDLE, VEHICLE_START, 1);
    r.gauge_confirmed     = true;
    r.seconds             = 600;
    context.coolant_valid = false;
    assert(vehicle_commands_submit(&s, r, context, now));
    route_idle(&s);
    exchange(&s, "10 92 1", "7E8 02 50 92\r\r>");
    exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
    exchange(&s, "31 05 06 80 1", "7E8 02 71 05\r\r>");
    exchange(&s, "33 05 1", "7E8 03 73 05 02\r\r>");
    assert(s.states[0].phase == VEHICLE_ACTIVE && s.states[0].timed_fallback);
    vehicle_commands_status(&s, VEHICLE_IDLE, context, now, status);
    assert(status[24] <= 60 && status[24] > 0);
    for (unsigned wrap = 0; wrap < 2; ++wrap)
    {
        init(&s);
        start_idle(&s, 1);
        s.states[0].request.seconds = 600;
        if (wrap)
            now = UINT32_MAX - 30000;
        s.states[0].active_at = now;
        context.coolant_valid = false;
        context.guard_at      = now;
        vehicle_commands_observe(&s, context, now);
        uint32_t fallback_at = now;
        assert(s.states[0].timed_fallback && !s.states[0].pending_stop);
        now += 30000;
        context.guard_at = context.coolant_at = now;
        context.coolant_valid                 = true;
        vehicle_commands_observe(&s, context, now);
        vehicle_commands_status(&s, VEHICLE_IDLE, context, now, status);
        assert(status[24] == 30 && status[25] == 0 && (status[27] & 29) == 13);
        context.coolant_valid = false;
        vehicle_commands_observe(&s, context, now);
        assert(s.states[0].fallback_at == fallback_at);
        now += 30000;
        context.guard_at = now;
        vehicle_commands_observe(&s, context, now);
        assert(s.states[0].pending_stop && s.states[0].reason == VEHICLE_COOLANT_TIMER);
        route_idle(&s);
        exchange(&s, "32 05 1", "7E8 02 72 05\r\r>");
        exchange(&s, "33 05 1", "7E8 03 73 05 01\r\r>");
        assert(s.states[0].phase == VEHICLE_VERIFYING && durable.mask == 1);
        assert(!vehicle_commands_next(&s, context, now).text);
        context.verified_operation[0] = 1;
        context.normal_idle_verified  = true;
        context.proof_at[0]           = now;
        vehicle_commands_observe(&s, context, now);
        assert(s.states[0].phase == VEHICLE_RESTORED && durable.mask == 0);
        context.coolant_valid = true;
        context.coolant_at = context.guard_at = now;
        assert(!vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 2), context, now));
        r                 = request(VEHICLE_IDLE, VEHICLE_START, 3);
        r.gauge_confirmed = true;
        r.seconds         = 600;
        assert(vehicle_commands_submit(&s, r, context, now));
        assert(!s.states[0].timed_fallback && s.states[0].request.seconds == 600);
    }
    init(&s);
    start_idle(&s, 1);
    context.coolant_valid = false;
    vehicle_commands_observe(&s, context, now);
    context.coolant_valid = true;
    context.coolant_c     = 105;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].pending_stop && s.states[0].reason == VEHICLE_GUARD);
}
static void parked_test_is_local_idle_only_and_bounded(void)
{
    vehicle_commands_t s;
    uint8_t            status[32];
    for (unsigned scenario = 0; scenario < 4; ++scenario)
    {
        init(&s);
        context.qualified_mask      = 0;
        context.identity_mask       = 1;
        context.parked_idle_test    = true;
        vehicle_command_request_t r = request(scenario == 1 ? VEHICLE_ABS_TCS : VEHICLE_IDLE, VEHICLE_START, 1);
        r.gauge_confirmed           = scenario != 0;
        if (r.kind == VEHICLE_IDLE)
            r.seconds = 600;
        if (scenario == 3)
            context.coolant_valid = false;
        bool accepted = vehicle_commands_submit(&s, r, context, now);
        assert(accepted == (scenario >= 2));
        if (accepted)
            assert(s.states[0].request.seconds == (scenario == 3 ? 60 : 300));
        vehicle_commands_status(&s, VEHICLE_IDLE, context, now, status);
        assert((status[27] & 9) == 9);
        vehicle_commands_status(&s, VEHICLE_ABS_TCS, context, now, status);
        assert(!(status[27] & 1));
    }
}
static void recovery_scope_and_retry_fences(void)
{
    vehicle_commands_t s;
    init(&s);
    durable = (vehicle_command_marker_t){.mask = 1, .operation = {42}, .revision = {7}, .identity = {11}};
    vehicle_commands_init(&s, 18, save, NULL, &durable, true);
    assert(s.last_operation == 42 && !s.states[0].request.link);
    context.parked_idle_test   = true;
    context.coolant_configured = context.coolant_valid = false;
    context.revision++;
    assert(!vehicle_commands_next(&s, context, now).text);
    context.revision--;
    context.identity++;
    assert(!vehicle_commands_next(&s, context, now).text);
    context.identity--;
    context.link = 12;
    route_idle(&s);
    exchange(&s, "10 92 1", "7E8 02 50 92\r\r>");
    assert(s.states[0].request.link == 12 && durable.mask == 1 && durable.operation[0] == 42);
    exchange(&s, "33 05 1", "7E8 03 73 05 02\r\r>");
    assert(s.states[0].phase == VEHICLE_UNKNOWN);
    assert(!vehicle_commands_next(&s, context, now).text);
    now              = s.states[0].recovery_at + 4999;
    context.guard_at = now;
    assert(!vehicle_commands_next(&s, context, now).text);
    ++now;
    context.guard_at            = now;
    jeep_idle_command_t command = vehicle_commands_next(&s, context, now);
    assert(command.text && !strcmp(command.text, "AT SH 7E0"));
    context.link++;
    assert(!vehicle_commands_feed(&s, command.ticket, (const uint8_t *)"OK\r>", 4, context, ++now));
    assert(s.states[0].phase == VEHICLE_UNKNOWN && durable.mask == 1);
    /* Late normal-idle proof cannot reuse an old inactive report. */
    s.worker_kind                  = 0;
    s.states[0].phase              = VEHICLE_VERIFYING;
    s.states[0].recovery_verifying = true;
    s.states[0].changed_at         = now;
    ++now;
    context.guard_at = context.proof_at[0] = now;
    context.normal_idle_verified           = true;
    context.verified_operation[0]          = 42;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].phase == VEHICLE_UNKNOWN && durable.mask == 1); /* Retired link proof. */
    s.states[0].phase        = VEHICLE_VERIFYING;
    s.states[0].changed_at   = now;
    s.states[0].request.link = context.link;
    now += 5000;
    context.guard_at = context.proof_at[0] = now;
    context.normal_idle_verified           = true;
    context.verified_operation[0]          = 42;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[0].phase == VEHICLE_UNKNOWN && durable.mask == 1);
    context.normal_idle_verified = false;
    /* Retry scheduling also survives the millisecond counter wrapping. */
    s.states[0].recovery_at = UINT32_MAX - 1000;
    now                     = 3998;
    context.guard_at        = now;
    assert(!vehicle_commands_next(&s, context, now).text);
    ++now;
    context.guard_at = now;
    command          = vehicle_commands_next(&s, context, now);
    assert(command.text && !strcmp(command.text, "AT SH 7E0"));
    assert(durable.mask == 1 && durable.operation[0] == 42);
}

static void test_put32(uint8_t *p, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i)
        p[i] = value >> (8 * i);
}

static vehicle_command_request_t burnout_request(uint8_t verb, uint32_t op)
{
    vehicle_command_request_t r = request(VEHICLE_BURNOUT, verb, op);
    r.gauge_confirmed           = true;
    return r;
}
static void burnout_context(void)
{
    context.qualified_mask = context.identity_mask = 7;
    context.burnout_held                           = true;
    context.guard_at = context.burnout_hold_at = now;
}
static void burnout_route(vehicle_commands_t *s)
{
    exchange(s, "AT SH 784", "OK\r\r>");
    exchange(s, "AT FC SH 784", "OK\r\r>");
    exchange(s, "AT CRA 785", "OK\r\r>");
    exchange(s, "10 92 1", "785 02 50 92\r\r>");
}
static void burnout_start(vehicle_commands_t *s)
{
    burnout_context();
    assert(vehicle_commands_submit(s, burnout_request(VEHICLE_START, 1), context, now));
    burnout_route(s);
    exchange(s, "30 11 07 A0 00", "785 03 70 11 07\r\r>");
    assert(s->states[2].phase == VEHICLE_ACTIVE && durable.mask == 4);
}
static void burnout_hold_release_and_uncertainty(void)
{
    vehicle_commands_t s;
    init(&s);
    burnout_context();
    vehicle_command_request_t r = burnout_request(VEHICLE_START, 1);
    r.gauge_confirmed           = false;
    assert(!vehicle_commands_submit(&s, r, context, now));
    assert(!vehicle_commands_next(&s, context, now).text && !durable.mask);
    init(&s);
    burnout_start(&s);
    assert(!vehicle_commands_submit(&s, request(VEHICLE_IDLE, VEHICLE_START, 2), context, now));
    assert(!vehicle_commands_submit(&s, request(VEHICLE_ABS_TCS, VEHICLE_START, 2), context, now));
    /* Release is independent of queue admission. ACK does not clear uncertainty. */
    context.burnout_held = false;
    burnout_route(&s);
    exchange(&s, "30 11 07 00 00", "785 03 70 11 07\r\r>");
    assert(s.states[2].phase == VEHICLE_VERIFYING && durable.mask == 4);
    assert(!s.states[2].restart_required);
    assert(!vehicle_commands_maintenance_allowed(s.states, false));
    context.burnout_released_verified = true;
    context.verified_operation[2]     = 1;
    context.proof_at[2]               = s.states[2].changed_at; /* not newer evidence */
    vehicle_commands_observe(&s, context, now);
    assert(durable.mask == 4);
    context.proof_at[2] = ++now;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[2].phase == VEHICLE_RESTORED && durable.mask == 0);
    assert(vehicle_commands_maintenance_allowed(s.states, true));

    init(&s);
    burnout_start(&s);
    now += VEHICLE_BURNOUT_HOLD_MS + 1;
    context.guard_at = now;
    burnout_route(&s); /* stale UI heartbeat causes Return, even with fresh vehicle guards */
    exchange(&s, "30 11 07 00 00", "785 03 70 11 07\r\r>");
    assert(s.states[2].phase == VEHICLE_VERIFYING);

    init(&s);
    burnout_start(&s);
    now += 10001;
    burnout_context();
    burnout_route(&s); /* fresh held contact cannot extend the hard deadline */
    exchange(&s, "30 11 07 00 00", "785 03 70 11 07\r\r>");
    assert(s.states[2].phase == VEHICLE_VERIFYING);

    init(&s);
    burnout_context();
    assert(vehicle_commands_submit(&s, burnout_request(VEHICLE_START, 1), context, now));
    burnout_route(&s);
    exchange(&s, "30 11 07 A0 00", "504 03 70 11 07\r\r>");
    assert(s.states[2].phase == VEHICLE_UNKNOWN && durable.mask == 4);
    assert(!vehicle_commands_maintenance_allowed(s.states, false));
    assert(!vehicle_commands_next(&s, context, now).text); /* no blind replay */
    assert(vehicle_commands_submit(&s, burnout_request(VEHICLE_STOP, 1), context, now));
    burnout_route(&s);
    exchange(&s, "30 11 07 00 00", "785 03 70 11 07\r\r>");
    assert(s.states[2].phase == VEHICLE_VERIFYING && durable.mask == 4);
    vehicle_commands_t reboot;
    vehicle_commands_init(&reboot, 18, save, NULL, &durable, true);
    assert(reboot.states[2].phase == VEHICLE_UNKNOWN && !reboot.states[2].restart_required);
    assert(!vehicle_commands_next(&reboot, context, now).text);

    /* Lost Start acknowledgement cannot defeat the release-initiation deadline. */
    init(&s);
    burnout_context();
    assert(vehicle_commands_submit(&s, burnout_request(VEHICLE_START, 1), context, now));
    burnout_route(&s);
    jeep_idle_command_t uncertain = vehicle_commands_next(&s, context, now++);
    assert(uncertain.text && !strcmp(uncertain.text, "30 11 07 A0 00"));
    vehicle_commands_transport_failed(&s, now);
    assert(s.states[2].phase == VEHICLE_UNKNOWN && !s.states[2].pending_stop);
    now = s.states[2].active_at + 10001;
    burnout_context();
    burnout_route(&s);
    exchange(&s, "30 11 07 00 00", "785 03 70 11 07\r\r>");
    assert(s.states[2].phase == VEHICLE_VERIFYING && s.states[2].release_attempted);

    /* Lost activation reply plus physical release attempts one Return, then
     * keeps uncertainty if that reply is lost too. No release retry loop. */
    init(&s);
    burnout_context();
    assert(vehicle_commands_submit(&s, burnout_request(VEHICLE_START, 1), context, now));
    burnout_route(&s);
    jeep_idle_command_t sent = vehicle_commands_next(&s, context, now++);
    assert(sent.text && !strcmp(sent.text, "30 11 07 A0 00") && durable.mask == 4);
    context.burnout_held = false;
    vehicle_commands_observe(&s, context, now);
    vehicle_commands_transport_failed(&s, now);
    burnout_route(&s);
    sent = vehicle_commands_next(&s, context, now++);
    assert(sent.text && !strcmp(sent.text, "30 11 07 00 00"));
    vehicle_commands_transport_failed(&s, now);
    assert(s.states[2].phase == VEHICLE_UNKNOWN && !s.states[2].pending_stop);
    assert(!vehicle_commands_next(&s, context, now).text);
    /* A new live link needs an explicit same-identity release request. */
    vehicle_commands_init(&reboot, 18, save, NULL, &durable, true);
    context.link                      = 10;
    vehicle_command_request_t release = burnout_request(VEHICLE_STOP, 1);
    release.boot                      = 18;
    release.link                      = 10;
    context.identity                  = 12;
    assert(!vehicle_commands_submit(&reboot, release, context, now));
    context.identity = 11;
    assert(vehicle_commands_submit(&reboot, release, context, now));
    burnout_route(&reboot);
    exchange(&reboot, "30 11 07 00 00", "785 03 70 11 07\r\r>");
    assert(reboot.states[2].phase == VEHICLE_VERIFYING && durable.mask == 4);

    /* A previously durable uncertainty marker permits protective Return even
     * when a later flash write fails; proof cannot clear it without storage. */
    init(&s);
    burnout_start(&s);
    context.burnout_held = false;
    storage_ok           = false;
    burnout_route(&s);
    exchange(&s, "30 11 07 00 00", "785 03 70 11 07\r\r>");
    assert(s.states[2].phase == VEHICLE_VERIFYING && durable.mask == 4);
    context.burnout_released_verified = true;
    context.verified_operation[2]     = 1;
    context.proof_at[2]               = ++now;
    vehicle_commands_observe(&s, context, now);
    assert(s.states[2].phase == VEHICLE_UNKNOWN && durable.mask == 4);

    /* A stored combined-shutdown state blocks Burnout, not just adapter IO. */
    init(&s);
    abs_job(&s, false, 1);
    burnout_context();
    assert(!vehicle_commands_submit(&s, burnout_request(VEHICLE_START, 2), context, now));
}
static void burnout_wire_and_marker_compatibility(void)
{
    uint8_t bytes[24] = {2, 3, 1};
    test_put32(bytes + 4, 1);
    test_put32(bytes + 8, 7);
    test_put32(bytes + 12, 9);
    test_put32(bytes + 16, 17);
    vehicle_command_request_t r;
    assert(vehicle_command_decode(bytes, sizeof(bytes), &r) && r.kind == VEHICLE_BURNOUT && !r.gauge_confirmed);
    bytes[0] = 1;
    assert(!vehicle_command_decode(bytes, sizeof(bytes), &r));
    bytes[0] = 2;
    bytes[3] = 1;
    assert(!vehicle_command_decode(bytes, sizeof(bytes), &r));
    bytes[3]  = 0;
    bytes[20] = 10;
    assert(!vehicle_command_decode(bytes, sizeof(bytes), &r)); /* deadline cannot come from a client */

    uint8_t legacy[28] = {1, 2};
    test_put32(legacy + 16, 5);
    test_put32(legacy + 20, 7);
    test_put32(legacy + 24, 11);
    vehicle_command_marker_t marker;
    assert(vehicle_command_marker_decode(legacy, sizeof(legacy), &marker));
    assert(marker.mask == 2 && marker.operation[1] == 5 && !marker.operation[2]);
    marker.mask |= 4;
    marker.operation[2] = 6;
    marker.revision[2]  = 7;
    marker.identity[2]  = 11;
    uint8_t encoded[VEHICLE_COMMAND_MARKER_SIZE];
    vehicle_command_marker_encode(&marker, encoded);
    vehicle_command_marker_t decoded;
    assert(encoded[0] == 2 && vehicle_command_marker_decode(encoded, sizeof(encoded), &decoded));
    assert(decoded.mask == 6 && decoded.operation[1] == 5 && decoded.operation[2] == 6);
    assert(!vehicle_command_marker_decode(encoded, 28, &decoded));
    encoded[0] = 3;
    assert(!vehicle_command_marker_decode(encoded, sizeof(encoded), &decoded));
    encoded[0] = 2;
    encoded[1] |= 8;
    assert(!vehicle_command_marker_decode(encoded, sizeof(encoded), &decoded));
    encoded[1] = 4;
    memset(encoded + 28, 0, 4);
    assert(!vehicle_command_marker_decode(encoded, sizeof(encoded), &decoded));
    marker.mask = 2;
    assert(vehicle_command_marker_encode(&marker, encoded) == 28 && encoded[0] == 1);
    assert(vehicle_command_marker_decode(encoded, 28, &decoded) && decoded.mask == 2);
}

int main(int argc, char **argv)
{
    recovery_scope_and_retry_fences();
    assert(argc == 2);
    FILE *file = fopen(argv[1], "r");
    assert(file);
    uint8_t fixture[24];
    for (unsigned i = 0; i < 24; ++i)
    {
        unsigned value;
        assert(fscanf(file, "%2x", &value) == 1);
        fixture[i] = value;
    }
    fclose(file);
    parked_test_is_local_idle_only_and_bounded();
    coolant_timer_requires_local_start_and_never_extends();
    burnout_hold_release_and_uncertainty();
    burnout_wire_and_marker_compatibility();
    coolant_limit_selection_and_freshness();
    coolant_guard_refuses_start_and_latches_release();
    two_actions_share_worker_and_restore_independently();
    gates_expiry_replay_and_journal();
    stale_tickets_negative_and_disconnect();
    wire_and_cancel(fixture);
    stale_guards_identity_and_wrap();
    abs_refuses_ambiguous_negative_and_expired_replies();
    vehicle_command_context_t visual = context;
    visual.connected                 = true;
    visual.identity_mask             = 3;
    visual.qualified_mask            = 3;
    visual.identity                  = 11;
    visual.revision                  = 7;
    visual.link                      = 9;
    visual.coolant_configured = visual.coolant_valid = true;
    visual.coolant_c                                 = 105;
    visual.coolant_limit_c                           = 105;
    assert(vehicle_commands_ui_flags(VEHICLE_IDLE, visual) & VEHICLE_UI_COOLANT_HIGH);
    assert(!(vehicle_commands_ui_flags(VEHICLE_ABS_TCS, visual) & VEHICLE_UI_COOLANT_HIGH));
    visual.coolant_c = 90;
    assert(!(vehicle_commands_ui_flags(VEHICLE_IDLE, visual) & VEHICLE_UI_COOLANT_HIGH));
    visual.connected = false;
    assert(vehicle_commands_ui_flags(VEHICLE_IDLE, visual) & VEHICLE_UI_RECONNECTING);
    visual.qualified_mask   = 0;
    visual.parked_idle_test = false;
    assert(!vehicle_commands_ui_flags(VEHICLE_IDLE, visual));
    assert(transfer_gate_vehicle_allowed());
    assert(transfer_gate_claim(TRANSFER_GATE_VEHICLE_COMMAND));
    assert(transfer_gate_vehicle_allowed());
    assert(!transfer_gate_claim(1) && !transfer_gate_claim(2) && !transfer_gate_claim(TRANSFER_GATE_WIFI_STARTUP));
    transfer_gate_release(1);
    assert(transfer_gate_current() == TRANSFER_GATE_VEHICLE_COMMAND);
    transfer_gate_release(TRANSFER_GATE_VEHICLE_COMMAND);
    assert(transfer_gate_claim(TRANSFER_GATE_WIFI_STARTUP));
    assert(!transfer_gate_vehicle_allowed() && !transfer_gate_claim(TRANSFER_GATE_VEHICLE_COMMAND));
    transfer_gate_release(TRANSFER_GATE_WIFI_STARTUP);
    puts("vehicle command shared worker policy passed");
    return 0;
}
