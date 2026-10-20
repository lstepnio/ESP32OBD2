#include "vehicle_commands.h"
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
                                            .guard_at             = 100};
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
                                          .guard_at             = 100};
    vehicle_commands_init(s, 17, save, NULL, NULL, true);
}
static vehicle_command_request_t request(uint8_t kind, uint8_t verb, uint32_t operation)
{
    return (vehicle_command_request_t){kind, verb, kind == VEHICLE_IDLE && verb == VEHICLE_START ? 1 : 0, operation, 7,
                                       9,    17,   kind == VEHICLE_IDLE && verb == VEHICLE_START ? 10 : 0};
}
static uint32_t exchange(vehicle_commands_t *s, const char *expected, const char *reply)
{
    context.guard_at      = now;
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
    context.guard_at = now;
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
    vehicle_command_state_t maintenance[2] = {{.phase = VEHICLE_RESTART_REQUIRED}};
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
    now              = UINT32_MAX - 50;
    context.guard_at = now;
    start_idle(&s, 1);
    now += 10001;
    context.guard_at = now;
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
int main(int argc, char **argv)
{
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
    two_actions_share_worker_and_restore_independently();
    gates_expiry_replay_and_journal();
    stale_tickets_negative_and_disconnect();
    wire_and_cancel(fixture);
    stale_guards_identity_and_wrap();
    abs_refuses_ambiguous_negative_and_expired_replies();
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
